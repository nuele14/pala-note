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

data class LlmModelInfo(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadUrl: String,
    val fallbackUrl: String? = null,
    val approxSizeMb: Int,
    val description: String,
    val tagTemplate: String // "qwen" or "gemma"
)

class LlmModelManager(private val context: Context) {

    private val TAG = "LlmModelManager"
    private val modelDir = File(context.filesDir, "models/llm")
    private val prefs = context.getSharedPreferences("es1_llm_models", Context.MODE_PRIVATE)

    companion object {
        val SUPPORTED_MODELS = listOf(
            LlmModelInfo(
                id = "qwen3_1_7b",
                name = "Qwen 3 (1.7B)",
                fileName = "qwen3-1.7b.litertlm",
                downloadUrl = "https://huggingface.co/samirsayyed/Qwen3-1.7B-litert-lm/resolve/main/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
                fallbackUrl = "https://huggingface.co/gakwayaremy/qwen3-1.7b-litertlm/resolve/main/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
                approxSizeMb = 932,
                description = "Modello compatto ad alta efficienza. Ottimo per lingua italiana, estrazione compiti e riassunti.",
                tagTemplate = "qwen"
            ),
            LlmModelInfo(
                id = "gemma3_4b",
                name = "Gemma 3 (4B)",
                fileName = "gemma3-4b.litertlm",
                downloadUrl = "https://huggingface.co/MiCkSoftware/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm",
                fallbackUrl = "https://huggingface.co/notabilia/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm",
                approxSizeMb = 4690,
                description = "Modello ad alte prestazioni. Massima comprensione del contesto e verbali di riunione complessi.",
                tagTemplate = "gemma"
            ),
            LlmModelInfo(
                id = "gemma3_1b",
                name = "Gemma 3 (1B)",
                fileName = "gemma3-1b.task",
                downloadUrl = "https://huggingface.co/nikhil2024/gemma3-1b-it-litert-mirror/resolve/main/gemma3-1b-it-int4.task",
                fallbackUrl = null,
                approxSizeMb = 529,
                description = "Ultra-leggero e rapido. Minimo impatto sulla memoria RAM, ideale per elaborazioni istantanee offline.",
                tagTemplate = "gemma"
            )
        )
    }

    private val _activeModelId = MutableStateFlow(
        prefs.getString("active_model_id", "qwen3_1_7b") ?: "qwen3_1_7b"
    )
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    private val _downloadedModelIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModelIds: StateFlow<Set<String>> = _downloadedModelIds.asStateFlow()

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .build()

    init {
        refreshDownloadedModels()
    }

    fun refreshDownloadedModels() {
        val set = mutableSetOf<String>()
        for (info in SUPPORTED_MODELS) {
            val file = File(modelDir, info.fileName)
            if (file.exists() && file.length() > 50_000_000L) {
                set.add(info.id)
            }
        }
        _downloadedModelIds.value = set

        val currentActive = _activeModelId.value
        if (set.contains(currentActive)) {
            _downloadState.value = ModelDownloadState.Ready
        } else {
            _downloadState.value = ModelDownloadState.NotDownloaded
        }
    }

    fun setActiveModel(modelId: String) {
        _activeModelId.value = modelId
        prefs.edit().putString("active_model_id", modelId).apply()
        refreshDownloadedModels()
    }

    fun getActiveModelInfo(): LlmModelInfo {
        val currentId = _activeModelId.value
        return SUPPORTED_MODELS.firstOrNull { it.id == currentId } ?: SUPPORTED_MODELS.first()
    }

    fun getActiveModelFile(): File? {
        val info = getActiveModelInfo()
        val file = File(modelDir, info.fileName)
        return if (file.exists() && file.length() > 50_000_000L) file else null
    }

    fun isModelReady(): Boolean = getActiveModelFile() != null

    fun getModelPath(): String? = getActiveModelFile()?.absolutePath

    fun deleteModel(modelId: String): Boolean {
        val info = SUPPORTED_MODELS.firstOrNull { it.id == modelId } ?: return false
        val file = File(modelDir, info.fileName)
        val ok = if (file.exists()) file.delete() else true
        Log.d(TAG, "Deleted model ${info.name}: file=${file.name} success=$ok")
        refreshDownloadedModels()
        return ok
    }

    suspend fun downloadModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val info = SUPPORTED_MODELS.firstOrNull { it.id == modelId } ?: return@withContext false

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val targetFile = File(modelDir, info.fileName)
        if (targetFile.exists() && targetFile.length() > 50_000_000L) {
            setActiveModel(modelId)
            _downloadState.value = ModelDownloadState.Ready
            return@withContext true
        }

        val tempFile = File(modelDir, "${info.fileName}.tmp")
        val urlsToTry = listOfNotNull(info.downloadUrl, info.fallbackUrl)
        var lastError = "Download non riuscito."

        for ((index, url) in urlsToTry.withIndex()) {
            try {
                Log.d(TAG, "Downloading on-device model ${info.name} from: $url")
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Android; ES1Companion/1.0)")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) {
                    lastError = "Server HTTP ${response.code} da mirror ${index + 1}"
                    Log.w(TAG, lastError)
                    continue
                }

                val body = response.body!!
                val contentLength = body.contentLength()
                var downloadedBytes = 0L
                var lastUpdateMs = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateMs > 200) {
                                lastUpdateMs = now
                                val progress = if (contentLength > 0) {
                                    ((downloadedBytes * 100) / contentLength).toInt().coerceIn(0, 100)
                                } else 0
                                val mbDone = downloadedBytes / (1024 * 1024)
                                val mbTotal = if (contentLength > 0) contentLength / (1024 * 1024) else info.approxSizeMb.toLong()
                                _downloadState.value = ModelDownloadState.Downloading(progress, "${info.name} ($mbDone / $mbTotal MB)")
                            }
                        }
                        output.flush()
                    }
                }

                if (tempFile.length() > 50_000_000L) {
                    if (tempFile.renameTo(targetFile)) {
                        Log.d(TAG, "Saved model ${info.name} successfully (${targetFile.length()} bytes)")
                    } else {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }

                    setActiveModel(modelId)
                    refreshDownloadedModels()
                    _downloadState.value = ModelDownloadState.Ready
                    return@withContext true
                } else {
                    lastError = "Dimensione file incompleta (${tempFile.length()} bytes)"
                    if (tempFile.exists()) tempFile.delete()
                }

            } catch (e: Exception) {
                lastError = "Errore: ${e.localizedMessage ?: e.message}"
                Log.w(TAG, "Download attempt failed for ${info.name}: $lastError", e)
                if (tempFile.exists()) tempFile.delete()
            }
        }

        _downloadState.value = ModelDownloadState.Error(lastError)
        return@withContext false
    }
}
