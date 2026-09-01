package com.es1.companion.domain.llm

import android.content.Context
import android.util.Log
import com.es1.companion.domain.stt.ModelDownloadState
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

class LlmModelManager(private val context: Context) {

    private val TAG = "LlmModelManager"
    private val modelDir = File(context.filesDir, "models/llm")
    private val modelFile = File(modelDir, "gemma-2b-it.bin")

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    // Google Gemma 2B-IT MediaPipe Model (Quantized INT4)
    private val modelUrl = "https://huggingface.co/google/gemma-2b-it-cpu-int4/resolve/main/gemma-2b-it-cpu-int4.bin?download=true"

    init {
        checkModelStatus()
    }

    fun isModelReady(): Boolean {
        return modelFile.exists() && modelFile.length() > 500_000_000L
    }

    fun checkModelStatus() {
        if (isModelReady()) {
            _downloadState.value = ModelDownloadState.Ready
        } else {
            _downloadState.value = ModelDownloadState.NotDownloaded
        }
    }

    fun getModelPath(): String = modelFile.absolutePath

    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _downloadState.value = ModelDownloadState.Ready
            return@withContext true
        }

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        try {
            val tempFile = File(modelDir, "gemma-2b-it.bin.tmp")
            Log.d(TAG, "Downloading Gemma 2B-IT model from $modelUrl...")

            val request = Request.Builder().url(modelUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                val msg = "Download fallito per Gemma (HTTP ${response.code})"
                _downloadState.value = ModelDownloadState.Error(msg)
                return@withContext false
            }

            val body = response.body!!
            val contentLength = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (contentLength > 0) {
                            val progress = (downloadedBytes * 100 / contentLength).toInt()
                            _downloadState.value = ModelDownloadState.Downloading(progress, "Gemma 2B (INT4)")
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.renameTo(modelFile)) {
                Log.d(TAG, "Saved Gemma model successfully (${modelFile.length()} bytes)")
            } else {
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
            }

            if (isModelReady()) {
                _downloadState.value = ModelDownloadState.Ready
                return@withContext true
            } else {
                _downloadState.value = ModelDownloadState.Error("Integrità file Gemma non valida.")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemma model download", e)
            _downloadState.value = ModelDownloadState.Error("Errore download: ${e.localizedMessage}")
            return@withContext false
        }
    }
}
