package com.es1.companion.data.remote.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class BleDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int
)

data class BleDeviceInfo(
    val deviceId: String,
    val fwVersion: String,
    val batteryPct: Int,
    val pendingCount: Int,
    val pendingBytes: Long
)

data class BleNoteItem(
    val num: Int,
    val tag: String,
    val durationSec: Float,
    val size: Long,
    val uploaded: Boolean
)

@SuppressLint("MissingPermission")
class ES1BleManager(private val context: Context) {

    private val TAG = "ES1BleManager"

    companion object {
        val SERVICE_UUID: UUID   = UUID.fromString("0000e501-0000-1000-8000-00805f9b34fb")
        val CHAR_CMD_UUID: UUID  = UUID.fromString("0000e502-0000-1000-8000-00805f9b34fb")
        val CHAR_DATA_UUID: UUID = UUID.fromString("0000e503-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID      = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val PREFS_NAME = "es1_ble_prefs"
        private const val KEY_SAVED_MAC = "saved_es1_mac"
        private const val KEY_SAVED_NAME = "saved_es1_name"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var currentGatt: BluetoothGatt? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null

    // Channel for incoming JSON command notifications
    private val cmdNotificationChannel = Channel<String>(Channel.BUFFERED)

    // Callback for incoming audio binary stream
    private var onDataChunkReceived: ((ByteArray) -> Unit)? = null

    fun getSavedDeviceAddress(): String? = prefs.getString(KEY_SAVED_MAC, null)
    fun getSavedDeviceName(): String? = prefs.getString(KEY_SAVED_NAME, null)

    fun saveDevice(address: String, name: String) {
        prefs.edit()
            .putString(KEY_SAVED_MAC, address)
            .putString(KEY_SAVED_NAME, name)
            .apply()
        Log.d(TAG, "Saved default ES1 BLE device: $name ($address)")
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * Scans for ES1 devices nearby for up to [timeoutMs] milliseconds.
     */
    suspend fun scanForDevices(timeoutMs: Long = 3500): List<BleDeviceItem> = withContext(Dispatchers.IO) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return@withContext emptyList()
        val results = mutableMapOf<String, BleDeviceItem>()
        val deferred = CompletableDeferred<Unit>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    val device = it.device
                    val name = it.scanRecord?.deviceName ?: device.name ?: ""
                    val address = device.address
                    val serviceUuids = it.scanRecord?.serviceUuids ?: emptyList()
                    val hasEs1Service = serviceUuids.any { uuid -> uuid.uuid == SERVICE_UUID }

                    if (name.startsWith("ES1-", ignoreCase = true) || hasEs1Service) {
                        results[address] = BleDeviceItem(
                            name = name.ifBlank { "ES1 ($address)" },
                            address = address,
                            rssi = it.rssi
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed with error code $errorCode")
                deferred.complete(Unit)
            }
        }

        try {
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(filters, settings, callback)
            withTimeoutOrNull(timeoutMs) {
                delay(timeoutMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during scan: ${e.message}", e)
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (ignored: Exception) {}
            deferred.complete(Unit)
        }

        return@withContext results.values.sortedByDescending { it.rssi }
    }

    /**
     * Connects to the given BLE device address, negotiates MTU 517 and discovers services.
     */
    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        val adapter = bluetoothAdapter ?: return@withContext false
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Bluetooth address: $address", e)
            return@withContext false
        }

        val connectDeferred = CompletableDeferred<Boolean>()

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server at $address. Requesting MTU 517...")
                    gatt.requestMtu(517)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server at $address (status $status)")
                    connectDeferred.complete(false)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU changed to $mtu (status $status). Requesting HIGH connection priority...")
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null) {
                        cmdCharacteristic = service.getCharacteristic(CHAR_CMD_UUID)
                        dataCharacteristic = service.getCharacteristic(CHAR_DATA_UUID)

                        // 1. Enable notifications on Command characteristic first
                        cmdCharacteristic?.let { cmdChar ->
                            gatt.setCharacteristicNotification(cmdChar, true)
                            val desc = cmdChar.getDescriptor(CCCD_UUID)
                            if (desc != null) {
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                val ok = gatt.writeDescriptor(desc)
                                Log.d(TAG, "Writing CMD CCCD descriptor: $ok")
                            } else {
                                enableDataCccd(gatt)
                            }
                        } ?: enableDataCccd(gatt)
                    } else {
                        Log.e(TAG, "ES1 Service UUID not found in device!")
                        connectDeferred.complete(false)
                    }
                } else {
                    Log.e(TAG, "Service discovery failed with status $status")
                    connectDeferred.complete(false)
                }
            }

            private fun enableDataCccd(gatt: BluetoothGatt) {
                dataCharacteristic?.let { dataChar ->
                    gatt.setCharacteristicNotification(dataChar, true)
                    val desc = dataChar.getDescriptor(CCCD_UUID)
                    if (desc != null) {
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        val ok = gatt.writeDescriptor(desc)
                        Log.d(TAG, "Writing DATA CCCD descriptor: $ok")
                    } else {
                        Log.d(TAG, "ES1 Services & Characteristics ready (no data CCCD)!")
                        connectDeferred.complete(true)
                    }
                } ?: run {
                    Log.d(TAG, "ES1 Services & Characteristics ready!")
                    connectDeferred.complete(true)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                val charUuid = descriptor.characteristic.uuid
                Log.d(TAG, "onDescriptorWrite for char $charUuid (status=$status)")
                if (charUuid == CHAR_CMD_UUID) {
                    enableDataCccd(gatt)
                } else if (charUuid == CHAR_DATA_UUID) {
                    Log.d(TAG, "DATA CCCD enabled! ES1 BLE is ready for high-speed streaming.")
                    connectDeferred.complete(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                handleIncomingNotification(characteristic.uuid, characteristic.value ?: ByteArray(0))
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleIncomingNotification(characteristic.uuid, value)
            }

            private fun handleIncomingNotification(uuid: UUID, value: ByteArray) {
                if (uuid == CHAR_CMD_UUID) {
                    val jsonStr = String(value, Charsets.UTF_8)
                    Log.d(TAG, "[BLE CMD RX] $jsonStr")
                    cmdNotificationChannel.trySend(jsonStr)
                } else if (uuid == CHAR_DATA_UUID) {
                    onDataChunkReceived?.invoke(value)
                }
            }
        }

        currentGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        val success = withTimeoutOrNull(8000) {
            connectDeferred.await()
        } ?: false

        return@withContext success
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            currentGatt?.disconnect()
            currentGatt?.close()
        } catch (ignored: Exception) {}
        currentGatt = null
        cmdCharacteristic = null
        dataCharacteristic = null
        onDataChunkReceived = null
    }

    private suspend fun sendCmd(json: String): Boolean = withContext(Dispatchers.IO) {
        val gatt = currentGatt ?: return@withContext false
        val cmdChar = cmdCharacteristic ?: return@withContext false
        cmdChar.value = json.toByteArray(Charsets.UTF_8)
        val ok = gatt.writeCharacteristic(cmdChar)
        delay(8)
        return@withContext ok
    }

    private suspend fun awaitCmdResponse(timeoutMs: Long = 4000): String? = withTimeoutOrNull(timeoutMs) {
        cmdNotificationChannel.receive()
    }

    /**
     * Queries device info via BLE
     */
    suspend fun getDeviceInfo(): BleDeviceInfo? = withContext(Dispatchers.IO) {
        sendCmd("{\"cmd\":\"INFO\"}")
        val resp = awaitCmdResponse() ?: return@withContext null
        try {
            val json = JSONObject(resp)
            return@withContext BleDeviceInfo(
                deviceId = json.optString("device", "ES1"),
                fwVersion = json.optString("fw", "1.0.0"),
                batteryPct = json.optInt("bat", 0),
                pendingCount = json.optInt("pending", 0),
                pendingBytes = json.optLong("bytes", 0L)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse INFO response: $resp", e)
            return@withContext null
        }
    }

    /**
     * Fetches list of notes from ES1
     */
    suspend fun getNotesList(): List<BleNoteItem> = withContext(Dispatchers.IO) {
        val notes = mutableListOf<BleNoteItem>()
        sendCmd("{\"cmd\":\"LIST\"}")

        val startResp = awaitCmdResponse(3000) ?: return@withContext emptyList()
        val startJson = JSONObject(startResp)
        val total = startJson.optInt("total", 0)

        for (i in 0 until total) {
            val itemResp = awaitCmdResponse(2000) ?: break
            try {
                val j = JSONObject(itemResp)
                if (j.optString("type") == "NOTE_ITEM") {
                    notes.add(
                        BleNoteItem(
                            num = j.getInt("num"),
                            tag = j.optString("tag", "Note"),
                            durationSec = j.optDouble("dur", 0.0).toFloat(),
                            size = j.optLong("size", 0L),
                            uploaded = j.optBoolean("up", false)
                        )
                    )
                }
            } catch (ignored: Exception) {}
        }

        // Consume LIST_END if received
        awaitCmdResponse(500)
        return@withContext notes
    }

    /**
     * Downloads an audio note file over BLE Data Stream
     */
    suspend fun downloadNoteAudio(
        noteNum: Int,
        targetFile: File,
        itemIdx: Int = 0,
        totalItems: Int = 0,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val doneDeferred = CompletableDeferred<Boolean>()
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        var fos: FileOutputStream? = null
        var totalExpectedBytes = 0L
        var bytesWritten = 0L
        var lastChunkTime = System.currentTimeMillis()

        try {
            fos = FileOutputStream(targetFile)

            onDataChunkReceived = { chunk ->
                try {
                    fos?.write(chunk)
                    sha256Digest.update(chunk)
                    bytesWritten += chunk.size
                    lastChunkTime = System.currentTimeMillis()
                    onProgress(bytesWritten, totalExpectedBytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing audio chunk: ${e.message}", e)
                }
            }

            // Send command to initiate note stream
            val getCmd = if (itemIdx > 0 && totalItems > 0) {
                "{\"cmd\":\"GET_NOTE\",\"num\":$noteNum,\"idx\":$itemIdx,\"total\":$totalItems}"
            } else {
                "{\"cmd\":\"GET_NOTE\",\"num\":$noteNum}"
            }
            sendCmd(getCmd)

            // 1. Wait for NOTE_START first
            val startResp = awaitCmdResponse(8000)
            if (startResp != null) {
                try {
                    val startJson = JSONObject(startResp)
                    if (startJson.optString("type") == "NOTE_START") {
                        totalExpectedBytes = startJson.optLong("size", 0L)
                        Log.d(TAG, "Note #$noteNum stream starting, expecting $totalExpectedBytes bytes...")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not parse start response: $startResp")
                }
            }

            lastChunkTime = System.currentTimeMillis()

            // 2. Loop receiving chunks and watching for NOTE_END or inactivity
            while (true) {
                val resp = awaitCmdResponse(300)
                if (resp != null) {
                    try {
                        val json = JSONObject(resp)
                        val type = json.optString("type")
                        if (type == "NOTE_END" || type == "NOTE_DONE") {
                            Log.d(TAG, "Note #$noteNum NOTE_END received: $bytesWritten bytes written.")
                            doneDeferred.complete(true)
                            break
                        } else if (type == "ERROR") {
                            Log.e(TAG, "Device reported error: ${json.optString("msg")}")
                            doneDeferred.complete(false)
                            break
                        }
                    } catch (ignored: Exception) {}
                }

                // If all expected bytes are already received, wait briefly for NOTE_END
                if (totalExpectedBytes > 0 && bytesWritten >= totalExpectedBytes) {
                    Log.d(TAG, "All $totalExpectedBytes bytes received for note #$noteNum. Awaiting NOTE_END...")
                    val finalResp = awaitCmdResponse(3000)
                    doneDeferred.complete(true)
                    break
                }

                // Inactivity timeout: if no chunk received for 8 seconds, abort
                if (System.currentTimeMillis() - lastChunkTime > 8000) {
                    Log.w(TAG, "Inactivity timeout on note #$noteNum ($bytesWritten of $totalExpectedBytes bytes received)")
                    doneDeferred.complete(bytesWritten > 0 && bytesWritten >= totalExpectedBytes)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during note download: ${e.message}", e)
            doneDeferred.complete(false)
        } finally {
            onDataChunkReceived = null
            try {
                fos?.flush()
                fos?.close()
            } catch (ignored: Exception) {}
        }

        val success = withTimeoutOrNull(2000) { doneDeferred.await() } ?: false
        return@withContext success
    }

    suspend fun sendNoteAck(noteNum: Int): Boolean = withContext(Dispatchers.IO) {
        sendCmd("{\"cmd\":\"ACK\",\"num\":$noteNum}")
        val resp = awaitCmdResponse(3000)
        return@withContext resp?.contains("ACK_OK") == true
    }

    /**
     * Pushes a markdown article to ES1 Reader over BLE
     */
    suspend fun pushArticle(
        title: String,
        source: String,
        markdownContent: String,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val gatt = currentGatt ?: return@withContext false
        val dataChar = dataCharacteristic ?: return@withContext false
        val bytes = markdownContent.toByteArray(Charsets.UTF_8)
        val totalBytes = bytes.size.toLong()

        // 1. Initiate push
        val startCmd = "{\"cmd\":\"PUSH_ARTICLE_START\",\"title\":\"$title\",\"source\":\"$source\",\"size\":$totalBytes}"
        sendCmd(startCmd)
        val readyResp = awaitCmdResponse(4000) ?: return@withContext false
        if (!readyResp.contains("ARTICLE_READY")) return@withContext false

        // 2. Stream chunks on Data Characteristic
        val chunkSize = 400
        var offset = 0
        while (offset < bytes.size) {
            val len = minOf(chunkSize, bytes.size - offset)
            val chunk = bytes.copyOfRange(offset, offset + len)
            dataChar.value = chunk
            dataChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            gatt.writeCharacteristic(dataChar)
            offset += len
            onProgress(offset.toLong(), totalBytes)
            delay(12) // Flow control pacing
        }

        delay(50)
        // 3. End article push
        sendCmd("{\"cmd\":\"PUSH_ARTICLE_END\"}")
        val savedResp = awaitCmdResponse(4000)
        return@withContext savedResp?.contains("ARTICLE_SAVED") == true
    }

    suspend fun sendSyncDone(): Boolean = withContext(Dispatchers.IO) {
        sendCmd("{\"cmd\":\"DONE\"}")
        val resp = awaitCmdResponse(3000)
        return@withContext resp?.contains("DONE_OK") == true
    }
}
