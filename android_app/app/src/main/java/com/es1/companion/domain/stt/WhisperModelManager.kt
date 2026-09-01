package com.es1.companion.domain.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    data class Downloading(val progressPct: Int, val currentFile: String) : ModelDownloadState()
    object Ready : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

class WhisperModelManager(private val context: Context) {

    private val TAG = "WhisperModelManager"
    private val modelDir = File(context.filesDir, "models/whisper")

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // OpenAI Whisper Tiny Int8 (Multilingual - Italian supported, ~39 MB total)
    private val filesToDownload = listOf(
        Pair(
            "tiny-encoder.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-encoder.int8.onnx"
        ),
        Pair(
            "tiny-decoder.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-decoder.int8.onnx"
        ),
        Pair(
            "tiny-tokens.txt",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-tokens.txt"
        )
    )

    init {
        checkModelStatus()
    }

    fun isModelReady(): Boolean {
        val encoder = File(modelDir, "tiny-encoder.int8.onnx")
        val decoder = File(modelDir, "tiny-decoder.int8.onnx")
        val tokens  = File(modelDir, "tiny-tokens.txt")
        return encoder.exists() && encoder.length() > 5_000_000L &&
               decoder.exists() && decoder.length() > 10_000_000L &&
               tokens.exists() && tokens.length() > 100_000L
    }

    fun checkModelStatus() {
        if (isModelReady()) {
            _downloadState.value = ModelDownloadState.Ready
        } else {
            _downloadState.value = ModelDownloadState.NotDownloaded
        }
    }

    fun getEncoderPath(): String = File(modelDir, "tiny-encoder.int8.onnx").absolutePath
    fun getDecoderPath(): String = File(modelDir, "tiny-decoder.int8.onnx").absolutePath
    fun getTokensPath(): String  = File(modelDir, "tiny-tokens.txt").absolutePath

    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _downloadState.value = ModelDownloadState.Ready
            return@withContext true
        }

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        try {
            val totalFiles = filesToDownload.size
            for ((index, pair) in filesToDownload.withIndex()) {
                val fileName = pair.first
                val url = pair.second
                val targetFile = File(modelDir, fileName)

                if (targetFile.exists() && targetFile.length() > 0) {
                    Log.d(TAG, "File $fileName already downloaded.")
                    continue
                }

                val tempFile = File(modelDir, "$fileName.tmp")
                Log.d(TAG, "Downloading $fileName from $url...")

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    val msg = "Download fallito per $fileName (HTTP ${response.code})"
                    _downloadState.value = ModelDownloadState.Error(msg)
                    return@withContext false
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (contentLength > 0) {
                                val fileProgress = (downloadedBytes * 100 / contentLength).toInt()
                                val overallProgress = ((index * 100 + fileProgress) / totalFiles)
                                _downloadState.value = ModelDownloadState.Downloading(overallProgress, fileName)
                            }
                        }
                        output.flush()
                    }
                }

                if (tempFile.renameTo(targetFile)) {
                    Log.d(TAG, "Saved $fileName successfully (${targetFile.length()} bytes)")
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }

            if (isModelReady()) {
                _downloadState.value = ModelDownloadState.Ready
                return@withContext true
            } else {
                _downloadState.value = ModelDownloadState.Error("Verifica integrità modello fallita.")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during model download", e)
            _downloadState.value = ModelDownloadState.Error("Errore download: ${e.localizedMessage}")
            return@withContext false
        }
    }
}
