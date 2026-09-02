package com.es1.companion.domain.llm

import android.content.Context
import android.util.Log
import com.es1.companion.data.local.NoteDao
import com.es1.companion.data.local.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.ai.edge.litertlm.Engine as LiteRtEngine
import com.google.ai.edge.litertlm.EngineConfig as LiteRtEngineConfig
import com.google.ai.edge.litertlm.Content as LiteRtContent

data class VoiceTagResult(
    val tag: String,
    val cleanBody: String
)

class LLMEngine(
    private val context: Context,
    private val noteDao: NoteDao,
    val modelManager: LlmModelManager = LlmModelManager(context)
) {
    private val TAG = "LLMEngine"

    // MediaPipe Tasks GenAI (per modelli .bin / .task con header TFL3)
    private var llmInference: LlmInference? = null
    private var currentLoadedModelPath: String? = null

    // LiteRT-LM Engine (per modelli .litertlm con container RTLM: Qwen, Gemma 3)
    private var liteRtEngine: LiteRtEngine? = null
    private var currentLiteRtModelPath: String? = null

    private var lastInitError: String? = null

    @Synchronized
    fun unloadEngine() {
        try {
            liteRtEngine?.close()
        } catch (_: Throwable) {}
        liteRtEngine = null
        currentLiteRtModelPath = null

        try {
            llmInference?.close()
        } catch (_: Throwable) {}
        llmInference = null
        currentLoadedModelPath = null

        Log.d(TAG, "On-device LLM engines unloaded from memory (Standby).")
    }

    @Synchronized
    private fun generateWithLiteRt(modelPath: String, prompt: String): String {
        var engine = liteRtEngine
        if (engine == null || currentLiteRtModelPath != modelPath) {
            try {
                engine?.close()
            } catch (_: Throwable) {}
            liteRtEngine = null

            Log.d(TAG, "Initializing LiteRT-LM Engine with: $modelPath")
            val config = LiteRtEngineConfig(modelPath = modelPath)
            val newEngine = LiteRtEngine(config)
            newEngine.initialize()
            liteRtEngine = newEngine
            currentLiteRtModelPath = modelPath
            engine = newEngine
            Log.d(TAG, "LiteRT-LM Engine initialized successfully.")
        }

        val conversation = engine.createConversation()
        val response = conversation.sendMessage(prompt)
        val textList = response.contents.contents.filterIsInstance<LiteRtContent.Text>().map { it.text }
        return if (textList.isNotEmpty()) textList.joinToString("\n") else response.toString()
    }

    @Synchronized
    private fun generateWithMediaPipe(modelPath: String, prompt: String): String {
        var engine = llmInference
        if (engine == null || currentLoadedModelPath != modelPath) {
            llmInference = null
            Log.d(TAG, "Initializing MediaPipe Tasks GenAI with: $modelPath")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTemperature(0.3f)
                .build()
            val newEngine = LlmInference.createFromOptions(context, options)
            llmInference = newEngine
            currentLoadedModelPath = modelPath
            engine = newEngine
            Log.d(TAG, "MediaPipe Tasks GenAI initialized successfully.")
        }
        return engine.generateResponse(prompt)?.trim() ?: ""
    }

    suspend fun elaborateNote(noteId: String): NoteEntity? = withContext(Dispatchers.IO) {
        val note = noteDao.getNoteByIdDirect(noteId)
            ?: noteDao.getPendingElaborations().find { it.id == noteId }
            ?: return@withContext null

        val rawText = note.transcriptionText
        val effectiveText = if (!rawText.isNullOrBlank()) {
            rawText
        } else {
            "Nota #${note.deviceNoteNum} (${note.tag})"
        }

        // 1. Riconoscimento vocale automatico del Tag dalle prime parole pronunciate
        val voiceResult = detectVoiceTrigger(effectiveText)
        val assignedTag = if (note.tag.isNotBlank() && note.tag != "Untagged" && note.tag != "Note") {
            note.tag
        } else {
            voiceResult.tag
        }
        val cleanUserText = voiceResult.cleanBody.ifBlank { effectiveText }

        val tagRule = noteDao.getTagRule(assignedTag)
        val systemPrompt = tagRule?.systemPrompt ?: "Rielabora e formatta questa nota vocale in modo strutturato e chiaro con markdown."

        Log.d(TAG, "Starting ON-DEVICE elaboration for note #${note.deviceNoteNum} (Tag: $assignedTag)...")

        val activeModelInfo = modelManager.getActiveModelInfo()

        if (!modelManager.isModelReady()) {
            Log.w(TAG, "Nessun modello LLM pronto sul dispositivo per inferenza.")
            val missingMsg = "> ⚠️ **Nessun modello scaricato sul telefono.**\n\n" +
                    "Vai in **Impostazioni** e scarica ${activeModelInfo.name} sul dispositivo.\n\n" +
                    "---\n\n### Testo trascritto:\n$cleanUserText"
            val title = extractTitle(cleanUserText, missingMsg)
            val updated = note.copy(
                tag = assignedTag,
                elaboratedTitle = title,
                elaboratedMarkdown = missingMsg
            )
            noteDao.updateNote(updated)
            return@withContext updated
        }

        val modelPath = modelManager.getModelPath()
        if (modelPath == null) {
            val missingMsg = "> ⚠️ **File modello non trovato sul dispositivo.**\n\n### Testo trascritto:\n$cleanUserText"
            val updated = note.copy(tag = assignedTag, elaboratedMarkdown = missingMsg)
            noteDao.updateNote(updated)
            return@withContext updated
        }

        // 2. Costruzione del prompt secondo il template del modello selezionato
        val formattedPrompt = if (activeModelInfo.tagTemplate == "qwen") {
            "<|im_start|>system\nSei l'assistente per note vocali ES1. Rielabora e formatta in markdown la seguente nota per il tag '$assignedTag'. Istruzioni: $systemPrompt<|im_end|>\n<|im_start|>user\n$cleanUserText<|im_end|>\n<|im_start|>assistant\n"
        } else {
            "<start_of_turn>user\nSei l'assistente per note vocali ES1. Rielabora e formatta in markdown la seguente nota per il tag '$assignedTag'. Istruzioni: $systemPrompt\n\nTesto:\n$cleanUserText<end_of_turn>\n<start_of_turn>model\n"
        }

        var result: String? = null
        val isLiteRt = modelPath.endsWith(".litertlm")

        try {
            Log.d(TAG, "Running on-device inference with ${activeModelInfo.name} (engine=${if (isLiteRt) "LiteRT-LM" else "MediaPipe"})...")
            result = if (isLiteRt) {
                generateWithLiteRt(modelPath, formattedPrompt)
            } else {
                generateWithMediaPipe(modelPath, formattedPrompt)
            }
            Log.d(TAG, "Inference completed (${result.length} chars generated).")
        } catch (t: Throwable) {
            val err = "${t.javaClass.simpleName}: ${t.localizedMessage ?: t.message}"
            Log.e(TAG, "Inference error on ${activeModelInfo.name}: $err", t)
            result = "> ⚠️ **Errore durante l'inferenza on-device (${activeModelInfo.name}):**\n\n`$err`\n\n---\n\n### Testo Trascritto:\n$cleanUserText"
        }

        val finalMarkdown = result?.ifBlank { null }
            ?: "> ⚠️ Il modello non ha generato output.\n\n### Testo Trascritto:\n$cleanUserText"

        val title = extractTitle(cleanUserText, finalMarkdown)
        val updatedNote = note.copy(
            tag = assignedTag,
            elaboratedTitle = title,
            elaboratedMarkdown = finalMarkdown
        )

        noteDao.updateNote(updatedNote)
        return@withContext updatedNote
    }

    suspend fun processPendingElaborations(): Int = withContext(Dispatchers.IO) {
        val pending = noteDao.getPendingElaborations()
        var count = 0
        for (note in pending) {
            if (elaborateNote(note.id) != null) count++
        }
        return@withContext count
    }

    suspend fun processAllPending(): Int = processPendingElaborations()

    fun detectVoiceTrigger(rawText: String): VoiceTagResult {
        val trimmed = rawText.trim()
        val triggers = listOf(
            Regex("^(?:task|todo|to-do|da fare|compito|promemoria|ricordati di|ricordami di|attività)[:\\s,-]+", RegexOption.IGNORE_CASE) to "Todo",
            Regex("^(?:idea|spunto|intuizione|pensiero|progetto nuovo)[:\\s,-]+", RegexOption.IGNORE_CASE) to "Idea",
            Regex("^(?:meeting|riunione|colloquio|call|intervista|allineamento)[:\\s,-]+", RegexOption.IGNORE_CASE) to "Meeting",
            Regex("^(?:spesa|buy|compra|comprare|acquistare|lista spesa|acquisti)[:\\s,-]+", RegexOption.IGNORE_CASE) to "Buy",
            Regex("^(?:work|lavoro|ufficio|progetto lavoro|task lavoro)[:\\s,-]+", RegexOption.IGNORE_CASE) to "Work",
            Regex("^(?:private|privato|personale|segreto|diario)[:\\s,-]+", RegexOption.IGNORE_CASE) to "Private",
            Regex("^(?:note|nota|appunto|scrivi)[:\\s,-]+", RegexOption.IGNORE_CASE) to "Note"
        )

        for ((regex, tag) in triggers) {
            val match = regex.find(trimmed)
            if (match != null) {
                val cleanBody = trimmed.substring(match.range.last + 1).trim()
                val capitalized = if (cleanBody.isNotEmpty()) {
                    cleanBody.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                } else trimmed
                return VoiceTagResult(tag, capitalized)
            }
        }
        return VoiceTagResult("Note", trimmed)
    }

    private fun extractTitle(cleanText: String, markdown: String): String {
        for (line in markdown.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                val t = trimmed.removePrefix("# ").trim().take(45)
                if (t.isNotBlank() && !t.contains("Nessun modello") && !t.contains("Errore")) return t
            }
        }

        val firstLine = cleanText.split(Regex("[.!?\\n]")).firstOrNull()?.trim() ?: "Nota"
        val clean = firstLine.take(45).replace(Regex("[^a-zA-Z0-9àèéìòùÀÈÉÌÒÙ -]"), "")
        return clean.ifBlank { "Nota Registrata" }
    }
}
