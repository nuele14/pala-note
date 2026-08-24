package com.es1.companion.data.remote

import android.content.Context
import android.util.Log
import com.es1.companion.data.local.AppDatabase
import com.es1.companion.data.local.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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

sealed class SyncState {
    object Idle : SyncState()
    data class Connecting(val message: String = "Connessione a ES1 (192.168.4.1)...") : SyncState()
    data class Downloading(val current: Int, val total: Int, val noteNum: Int) : SyncState()
    data class Processing(val message: String) : SyncState()
    data class Success(val downloadedCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class ES1SyncManager(private val context: Context) {

    private val TAG = "ES1SyncManager"
    private val BASE_URL = "http://192.168.4.1:80/"

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val db = AppDatabase.getDatabase(context)
    private val noteDao = db.noteDao()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
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

    suspend fun performSync(): SyncResult = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Connecting()
        try {
            // 1. Info & handshake
            Log.d(TAG, "Connecting to ES1 info endpoint...")
            val infoResponse = apiService.getDeviceInfo()
            if (!infoResponse.isSuccessful || infoResponse.body() == null) {
                val errorMsg = "Impossibile connettersi a ES1. Verifica di essere connesso al Wi-Fi 'ES1-XXXX'."
                _syncState.value = SyncState.Error(errorMsg)
                return@withContext SyncResult(false, 0, errorMsg)
            }
            val deviceInfo = infoResponse.body()!!
            Log.d(TAG, "Connected to ${deviceInfo.deviceId} (v${deviceInfo.firmwareVersion}, ${deviceInfo.batteryPct}% bat)")

            // 2. Fetch list of notes
            val notesResponse = apiService.getDeviceNotes()
            if (!notesResponse.isSuccessful || notesResponse.body() == null) {
                val errorMsg = "Errore nel recupero della lista note dal dispositivo."
                _syncState.value = SyncState.Error(errorMsg)
                return@withContext SyncResult(false, 0, errorMsg)
            }
            val deviceNotes = notesResponse.body()!!.notes
            val deviceId = deviceInfo.deviceId

            // 3. Differential sync: find unsynced notes
            val toDownload = mutableListOf<DeviceNoteItem>()
            for (dn in deviceNotes) {
                val existing = noteDao.getNoteByDeviceNum(dn.num, deviceId)
                if (existing == null) {
                    toDownload.add(dn)
                }
            }

            if (toDownload.isEmpty()) {
                Log.d(TAG, "All ${deviceNotes.size} notes already synchronized locally.")
                try {
                    apiService.notifySyncDone()
                } catch (_: Exception) {}
                _syncState.value = SyncState.Success(0)
                return@withContext SyncResult(true, 0, "Nessuna nuova nota da sincronizzare.")
            }

            // 4. Download audio files
            val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
            val nowUtc = getUtcIsoNow()
            var downloadedCount = 0

            for ((idx, dn) in toDownload.withIndex()) {
                _syncState.value = SyncState.Downloading(idx + 1, toDownload.size, dn.num)
                Log.d(TAG, "Downloading note #${dn.num} (${dn.durationSec}s, ${dn.tag})...")

                val audioResp = apiService.downloadAudio(dn.num)
                if (audioResp.isSuccessful && audioResp.body() != null) {
                    val targetFile = File(audioDir, "${deviceId}_note_${String.format(Locale.US, "%03d", dn.num)}.wav")
                    val body = audioResp.body()!!

                    // Write to local file and compute SHA256
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

                    // Save entity in Room DB
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

                    // Send ACK to device
                    try {
                        apiService.sendAck(dn.num)
                        Log.d(TAG, "ACK sent for note #${dn.num}")
                    } catch (e: Exception) {
                        Log.w(TAG, "ACK failed for #${dn.num}: ${e.message}")
                    }
                }
            }

            // 5. Notify ESP32 sync done (allows sleep)
            try {
                apiService.notifySyncDone()
            } catch (_: Exception) {}

            _syncState.value = SyncState.Success(downloadedCount)
            return@withContext SyncResult(true, downloadedCount, "Sincronizzate $downloadedCount note con successo!")

        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            val errorMsg = "Errore durante la sincronizzazione: ${e.localizedMessage ?: e.message}"
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
