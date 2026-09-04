package com.es1.companion.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.es1.companion.data.local.AppDatabase
import com.es1.companion.data.local.NoteEntity
import com.es1.companion.data.remote.ble.BleDeviceItem
import com.es1.companion.data.remote.ble.ES1BleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class SyncProtocol { WIFI, BLE }

sealed class SyncState {
    object Idle : SyncState()
    data class DeviceSelection(val devices: List<BleDeviceItem>) : SyncState()
    data class Connecting(
        val message: String = "Connessione a ES1...",
        val protocol: SyncProtocol = SyncProtocol.BLE
    ) : SyncState()
    data class Progress(
        val protocol: SyncProtocol,
        val currentItem: Int,
        val totalItems: Int,
        val itemLabel: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val progressPct: Int
    ) : SyncState()
    data class Downloading(
        val current: Int,
        val total: Int,
        val noteNum: Int,
        val protocol: SyncProtocol = SyncProtocol.WIFI,
        val progressPct: Int = 0
    ) : SyncState()
    data class Processing(
        val message: String,
        val protocol: SyncProtocol = SyncProtocol.BLE
    ) : SyncState()
    data class Success(
        val downloadedCount: Int,
        val uploadedArticlesCount: Int = 0,
        val protocol: SyncProtocol = SyncProtocol.BLE
    ) : SyncState()
    data class Error(val message: String) : SyncState()
}

class ES1SyncManager(private val context: Context) {

    private val TAG = "ES1SyncManager"
    private val BASE_URL = "http://192.168.4.1:80/"

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val db = AppDatabase.getDatabase(context)
    private val noteDao = db.noteDao()
    private val rssDao = db.rssDao()

    val bleManager = ES1BleManager(context)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val apiService: ES1ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ES1ApiService::class.java)

    fun getApiService(): ES1ApiService = apiService

    /**
     * Controlla se il telefono è attualmente collegato all'hotspot Wi-Fi SoftAP di ES1
     * (ovvero Wi-Fi senza accesso a internet validato, oppure gateway 192.168.4.1 raggiungibile).
     */
    fun isWifiModeActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasValidatedInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        // Se siamo su Wi-Fi e la rete non ha internet esterno, molto probabilmente è il SoftAP di ES1
        if (isWifi && !hasValidatedInternet) {
            return true
        }

        // Verifica rapida di risposta dall'endpoint di ES1 (timeout brevissimo 1s)
        return try {
            val pingClient = OkHttpClient.Builder()
                .connectTimeout(1200, TimeUnit.MILLISECONDS)
                .readTimeout(1200, TimeUnit.MILLISECONDS)
                .build()
            val req = Request.Builder().url("http://192.168.4.1:80/api/info").get().build()
            val resp = pingClient.newCall(req).execute()
            val ok = resp.isSuccessful
            resp.close()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Esegue la sincronizzazione automatica:
     * - Se rileva Wi-Fi SoftAP (senza internet / gateway ES1): usa Wi-Fi
     * - Altrimenti: usa BLE Bluetooth
     */
    suspend fun performSync(
        targetBleMac: String? = null,
        onSyncArticles: (suspend (deviceId: String) -> Int)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val useWifi = isWifiModeActive()

        if (useWifi) {
            Log.d(TAG, "Rilevata modalità WI-FI (SoftAP ES1 attivo). Avvio sync Wi-Fi...")
            return@withContext performWifiSync(onSyncArticles)
        } else {
            Log.d(TAG, "Nessun Wi-Fi ES1 rilevato (o connessione internet attiva). Avvio sync BLE...")
            return@withContext performBleSync(targetBleMac, onSyncArticles)
        }
    }

    /**
     * Sincronizzazione via Wi-Fi SoftAP (HTTP REST ad alta velocità)
     */
    private suspend fun performWifiSync(
        onSyncArticles: (suspend (deviceId: String) -> Int)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Connecting("Connessione a ES1 via Wi-Fi (192.168.4.1)...", SyncProtocol.WIFI)
        try {
            // 1. Info & handshake
            val infoResponse = apiService.getDeviceInfo()
            if (!infoResponse.isSuccessful || infoResponse.body() == null) {
                val errorMsg = "Impossibile connettersi a ES1. Verifica che ES1 sia acceso su SYNC > WI-FI."
                _syncState.value = SyncState.Error(errorMsg)
                return@withContext SyncResult(false, 0, errorMsg)
            }
            val deviceInfo = infoResponse.body()!!
            val deviceId = deviceInfo.deviceId
            Log.d(TAG, "[Wi-Fi] Connected to $deviceId (v${deviceInfo.firmwareVersion}, ${deviceInfo.batteryPct}% bat)")

            // 2. Fetch list of notes
            val notesResponse = apiService.getDeviceNotes()
            if (!notesResponse.isSuccessful || notesResponse.body() == null) {
                val errorMsg = "Errore nel recupero della lista note dal dispositivo."
                _syncState.value = SyncState.Error(errorMsg)
                return@withContext SyncResult(false, 0, errorMsg)
            }
            val deviceNotes = notesResponse.body()!!.notes

            // 3. Differential sync: note non ancora scaricate
            val toDownload = mutableListOf<DeviceNoteItem>()
            for (dn in deviceNotes) {
                val existing = noteDao.getNoteByDeviceNum(dn.num, deviceId)
                if (existing == null) {
                    toDownload.add(dn)
                }
            }

            var downloadedCount = 0

            // 4. Download audio files
            if (toDownload.isNotEmpty()) {
                val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
                val nowUtc = getUtcIsoNow()

                for ((idx, dn) in toDownload.withIndex()) {
                    val pct = ((idx + 1) * 100) / toDownload.size
                    _syncState.value = SyncState.Progress(
                        protocol = SyncProtocol.WIFI,
                        currentItem = idx + 1,
                        totalItems = toDownload.size,
                        itemLabel = "Nota #${dn.num} (${dn.durationSec}s) [${dn.tag}]",
                        bytesTransferred = 0L,
                        totalBytes = dn.audioBytes,
                        progressPct = pct
                    )

                    val audioResp = apiService.downloadAudio(dn.num)
                    if (audioResp.isSuccessful && audioResp.body() != null) {
                        val targetFile = File(audioDir, "${deviceId}_note_${String.format(Locale.US, "%03d", dn.num)}.wav")
                        val body = audioResp.body()!!

                        val sha256Digest = MessageDigest.getInstance("SHA-256")
                        body.byteStream().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    sha256Digest.update(buffer, 0, read)
                                }
                            }
                        }
                        val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }

                        val noteEntity = NoteEntity(
                            deviceNoteNum = dn.num,
                            deviceId = deviceId,
                            createdUtc = nowUtc,
                            tag = dn.tag,
                            durationSec = dn.durationSec,
                            audioFileSize = targetFile.length(),
                            audioLocalPath = targetFile.absolutePath,
                            audioSha256 = sha256Hex,
                            isSyncedWithDevice = true
                        )
                        noteDao.insertNote(noteEntity)
                        downloadedCount++

                        try {
                            apiService.sendAck(dn.num)
                        } catch (e: Exception) {
                            Log.w(TAG, "ACK failed for #${dn.num}: ${e.message}")
                        }
                    }
                }
            }

            // 5. Trasferimento articoli in coda
            var uploadedArticlesCount = 0
            if (onSyncArticles != null) {
                _syncState.value = SyncState.Processing("Trasferimento articoli al Reader...", SyncProtocol.WIFI)
                try {
                    uploadedArticlesCount = onSyncArticles(deviceId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error pushing articles: ${e.message}", e)
                }
            }

            // 6. Notifica completamento sync
            try {
                apiService.notifySyncDone()
            } catch (e: Exception) {
                Log.w(TAG, "notifySyncDone() failed: ${e.message}")
            }

            _syncState.value = SyncState.Success(downloadedCount, uploadedArticlesCount, SyncProtocol.WIFI)
            val successMsg = buildString {
                if (downloadedCount > 0) append("Sincronizzate $downloadedCount note via Wi-Fi! ")
                if (uploadedArticlesCount > 0) append("$uploadedArticlesCount articoli inviati al Reader!")
                if (isEmpty()) append("Dispositivo già sincronizzato.")
            }
            return@withContext SyncResult(true, downloadedCount, successMsg)

        } catch (e: Exception) {
            Log.e(TAG, "[Wi-Fi] Sync error", e)
            val errorMsg = "Errore durante il sync Wi-Fi: ${e.localizedMessage ?: e.message}"
            _syncState.value = SyncState.Error(errorMsg)
            return@withContext SyncResult(false, 0, errorMsg)
        }
    }

    /**
     * Sincronizzazione via Bluetooth BLE (zero-touch, senza cambiare Wi-Fi)
     */
    private suspend fun performBleSync(
        targetBleMac: String? = null,
        onSyncArticles: (suspend (deviceId: String) -> Int)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!bleManager.isBluetoothEnabled()) {
            val errorMsg = "Bluetooth disattivato. Attiva il Bluetooth per sincronizzare con ES1."
            _syncState.value = SyncState.Error(errorMsg)
            return@withContext SyncResult(false, 0, errorMsg)
        }

        // Determina a quale indirizzo BLE connettersi
        var macToConnect = targetBleMac ?: bleManager.getSavedDeviceAddress()

        if (macToConnect == null) {
            _syncState.value = SyncState.Connecting("Ricerca dispositivi ES1 nelle vicinanze...", SyncProtocol.BLE)
            val devices = bleManager.scanForDevices(timeoutMs = 3000)

            if (devices.isEmpty()) {
                val errorMsg = "Nessun ES1 trovato. Assicurati che ES1 sia acceso su SYNC > BLE."
                _syncState.value = SyncState.Error(errorMsg)
                return@withContext SyncResult(false, 0, errorMsg)
            } else if (devices.size == 1) {
                // Se c'è un solo ES1 nelle vicinanze, salvalo e connettiti direttamente!
                val dev = devices.first()
                bleManager.saveDevice(dev.address, dev.name)
                macToConnect = dev.address
            } else {
                // Più dispositivi trovati: chiedi all'utente quale collegare
                _syncState.value = SyncState.DeviceSelection(devices)
                return@withContext SyncResult(false, 0, "Seleziona il dispositivo ES1 trovato.")
            }
        }

        _syncState.value = SyncState.Connecting("Connessione Bluetooth a ES1...", SyncProtocol.BLE)
        val connected = bleManager.connect(macToConnect)
        if (!connected) {
            val errorMsg = "Connessione BLE fallita. Assicurati che ES1 sia acceso su SYNC > BLE."
            _syncState.value = SyncState.Error(errorMsg)
            return@withContext SyncResult(false, 0, errorMsg)
        }

        try {
            // 1. Info handshake
            val info = bleManager.getDeviceInfo()
            val deviceId = info?.deviceId ?: "ES1"
            Log.d(TAG, "[BLE] Connected to $deviceId (bat ${info?.batteryPct}%, pending ${info?.pendingCount})")

            // 2. Note list
            val bleNotes = bleManager.getNotesList()
            val toDownload = mutableListOf<com.es1.companion.data.remote.ble.BleNoteItem>()
            for (bn in bleNotes) {
                val existing = noteDao.getNoteByDeviceNum(bn.num, deviceId)
                if (existing == null) {
                    toDownload.add(bn)
                }
            }

            var downloadedCount = 0

            // 3. Download audio files over BLE
            if (toDownload.isNotEmpty()) {
                val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
                val nowUtc = getUtcIsoNow()

                for ((idx, bn) in toDownload.withIndex()) {
                    val targetFile = File(audioDir, "${deviceId}_note_${String.format(Locale.US, "%03d", bn.num)}.wav")

                    val ok = bleManager.downloadNoteAudio(bn.num, targetFile) { bytesRx, totalBytes ->
                        val currentPct = if (totalBytes > 0) ((bytesRx * 100) / totalBytes).toInt() else 50
                        _syncState.value = SyncState.Progress(
                            protocol = SyncProtocol.BLE,
                            currentItem = idx + 1,
                            totalItems = toDownload.size,
                            itemLabel = "Nota #${bn.num} [${bn.tag}]",
                            bytesTransferred = bytesRx,
                            totalBytes = totalBytes,
                            progressPct = currentPct
                        )
                    }

                    if (ok && targetFile.exists() && targetFile.length() > 0) {
                        val sha256Hex = MessageDigest.getInstance("SHA-256")
                            .digest(targetFile.readBytes())
                            .joinToString("") { "%02x".format(it) }

                        val noteEntity = NoteEntity(
                            deviceNoteNum = bn.num,
                            deviceId = deviceId,
                            createdUtc = nowUtc,
                            tag = bn.tag,
                            durationSec = bn.durationSec,
                            audioFileSize = targetFile.length(),
                            audioLocalPath = targetFile.absolutePath,
                            audioSha256 = sha256Hex,
                            isSyncedWithDevice = true
                        )
                        noteDao.insertNote(noteEntity)
                        downloadedCount++

                        bleManager.sendNoteAck(bn.num)
                    }
                }
            }

            // 4. Invio articoli in coda via BLE
            var uploadedArticlesCount = 0
            val queuedArticles = rssDao.getQueuedArticlesList()
            if (queuedArticles.isNotEmpty()) {
                _syncState.value = SyncState.Processing("Invio articoli via Bluetooth...", SyncProtocol.BLE)
                for ((idx, art) in queuedArticles.withIndex()) {
                    val mdDoc = buildString {
                        appendLine("# ${art.title}")
                        appendLine()
                        appendLine("*${art.feedTitle}*")
                        appendLine()
                        appendLine(art.markdownContent.ifBlank { art.rawSummary })
                    }

                    val sent = bleManager.pushArticle(
                        title = art.title,
                        source = art.feedTitle.take(15),
                        markdownContent = mdDoc
                    ) { bytesSent, totalBytes ->
                        val pct = if (totalBytes > 0) ((bytesSent * 100) / totalBytes).toInt() else 50
                        _syncState.value = SyncState.Progress(
                            protocol = SyncProtocol.BLE,
                            currentItem = idx + 1,
                            totalItems = queuedArticles.size,
                            itemLabel = "Articolo: ${art.title.take(20)}...",
                            bytesTransferred = bytesSent,
                            totalBytes = totalBytes,
                            progressPct = pct
                        )
                    }

                    if (sent) {
                        uploadedArticlesCount++
                        rssDao.markArticleSyncedAndDequeue(
                            articleId = art.id,
                            deviceId = deviceId,
                            deviceName = "ES1",
                            syncedUtc = getUtcIsoNow()
                        )
                    }
                }
            }

            // 5. Concludi sessione BLE
            bleManager.sendSyncDone()
            bleManager.disconnect()

            _syncState.value = SyncState.Success(downloadedCount, uploadedArticlesCount, SyncProtocol.BLE)
            val successMsg = buildString {
                if (downloadedCount > 0) append("Sincronizzate $downloadedCount note via BLE! ")
                if (uploadedArticlesCount > 0) append("$uploadedArticlesCount articoli inviati al Reader!")
                if (isEmpty()) append("Dispositivo già sincronizzato.")
            }
            return@withContext SyncResult(true, downloadedCount, successMsg)

        } catch (e: Exception) {
            Log.e(TAG, "[BLE] Sync error", e)
            bleManager.disconnect()
            val errorMsg = "Errore durante il sync BLE: ${e.localizedMessage ?: e.message}"
            _syncState.value = SyncState.Error(errorMsg)
            return@withContext SyncResult(false, 0, errorMsg)
        }
    }

    private fun getUtcIsoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
