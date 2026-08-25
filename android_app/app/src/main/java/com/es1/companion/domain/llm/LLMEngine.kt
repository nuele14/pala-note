package com.es1.companion.domain.llm

import android.content.Context
import android.util.Log
import com.es1.companion.data.local.NoteDao
import com.es1.companion.data.local.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class VoiceTagResult(
    val tag: String,
    val cleanBody: String
)

class LLMEngine(
    private val context: Context,
    private val noteDao: NoteDao
) {
    private val TAG = "LLMEngine"
    private var ollamaHost: String? = null // Optional e.g. "http://192.168.1.100:11434"
    private var ollamaModel: String = "qwen2.5:1.5b"

    suspend fun elaborateNote(noteId: String): NoteEntity? = withContext(Dispatchers.IO) {
        val note = noteDao.getPendingElaborations().find { it.id == noteId }
            ?: return@withContext null

        val rawText = note.transcriptionText
        if (rawText.isNullOrBlank()) {
            Log.w(TAG, "Cannot elaborate note with empty transcription")
            return@withContext null
        }

        // 1. Riconoscimento vocale automatico del Tag dalle prime parole pronunciate
        val voiceResult = detectVoiceTrigger(rawText)
        val assignedTag = voiceResult.tag
        val cleanUserText = voiceResult.cleanBody

        val tagRule = noteDao.getTagRule(assignedTag)
        val prompt = tagRule?.systemPrompt ?: "Rielabora e struttura questa nota vocale in modo chiaro."

        Log.d(TAG, "Elaborating note #${note.deviceNoteNum} (Voice Trigger Tag: $assignedTag)...")

        // 2. Prova con Ollama (se configurato) o fallback su motore euristico locale
        var result = if (!ollamaHost.isNullOrBlank()) {
            callOllama(ollamaHost!!, ollamaModel, prompt, cleanUserText)
        } else null

        if (result == null) {
            result = applyHeuristicElaboration(assignedTag, cleanUserText)
        }

        val title = extractTitle(cleanUserText, result)
        val updatedNote = note.copy(
            tag = assignedTag,
            elaboratedTitle = title,
            elaboratedMarkdown = result
        )

        noteDao.updateNote(updatedNote)
        return@withContext updatedNote
    }

    suspend fun processAllPending(): Int = withContext(Dispatchers.IO) {
        val pending = noteDao.getPendingElaborations()
        var count = 0
        for (note in pending) {
            val res = elaborateNote(note.id)
            if (res != null) count++
        }
        return@withContext count
    }

    /**
     * Riconosce i voice triggers iniziali pronunciati dall'utente (IT / EN)
     * e rimuove il prefisso dal testo finale per non sporcare il markdown.
     */
    fun detectVoiceTrigger(rawText: String): VoiceTagResult {
        val trimmed = rawText.trim()

        val triggerPatterns = listOf(
            Pair(Regex("^(?:task|todo|to-do|da fare|compito|promemoria|ricordati di|ricordami di|attività)[:\\s,-]+", RegexOption.IGNORE_CASE), "Todo"),
            Pair(Regex("^(?:idea|spunto|intuizione|pensiero|progetto nuovo)[:\\s,-]+", RegexOption.IGNORE_CASE), "Idea"),
            Pair(Regex("^(?:meeting|riunione|colloquio|call|intervista|allineamento)[:\\s,-]+", RegexOption.IGNORE_CASE), "Meeting"),
            Pair(Regex("^(?:spesa|buy|compra|comprare|acquistare|lista spesa|acquisiti)[:\\s,-]+", RegexOption.IGNORE_CASE), "Buy"),
            Pair(Regex("^(?:work|lavoro|ufficio|progetto lavoro|task lavoro)[:\\s,-]+", RegexOption.IGNORE_CASE), "Work"),
            Pair(Regex("^(?:private|privato|personale|segreto|diario)[:\\s,-]+", RegexOption.IGNORE_CASE), "Private"),
            Pair(Regex("^(?:note|nota|appunto|scrivi)[:\\s,-]+", RegexOption.IGNORE_CASE), "Note")
        )

        for ((regex, tag) in triggerPatterns) {
            val match = regex.find(trimmed)
            if (match != null) {
                val remaining = trimmed.substring(match.range.last + 1).trim()
                val capitalized = remaining.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
                return VoiceTagResult(
                    tag = tag,
                    cleanBody = if (capitalized.isNotBlank()) capitalized else trimmed
                )
            }
        }

        // Nessun prefisso esplicito: default su "Note"
        return VoiceTagResult(
            tag = "Note",
            cleanBody = trimmed
        )
    }

    private fun applyHeuristicElaboration(tag: String, cleanText: String): String {
        val sentences = cleanText.split(Regex("[.!?\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return when (tag.lowercase()) {
            "todo" -> {
                val sb = StringBuilder("### ✅ Cose da fare\n\n")
                for (s in sentences) {
                    val cap = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    sb.append("- [ ] $cap.\n")
                }
                sb.toString().trimEnd()
            }
            "meeting" -> {
                """
                ### 🤝 Verbale Riunione
                
                **Argomento Principale**:
                ${sentences.firstOrNull() ?: cleanText}
                
                **Punti Chiave Discussi**:
                ${sentences.drop(1).joinToString("\n") { "• $it" }.ifEmpty { "• " + cleanText }}
                
                **Azioni da intraprendere**:
                - [ ] Verificare i punti concordati.
                """.trimIndent()
            }
            "idea" -> {
                """
                ### 💡 Nuova Idea
                
                **Concetto**:
                $cleanText
                
                **Punti di Forza**:
                • Innovativo e mirato.
                • Implementabile a breve termine.
                
                **Prossimi Passi**:
                - [ ] Definire i requisiti dettagliati.
                - [ ] Creare un prototipo rapido.
                """.trimIndent()
            }
            "work" -> {
                """
                ### 💼 Attività di Lavoro
                
                **Descrizione**:
                $cleanText
                
                **Deliverable**:
                ${sentences.joinToString("\n") { "• $it" }}
                """.trimIndent()
            }
            "buy" -> {
                val sb = StringBuilder("### 🛒 Lista della Spesa\n\n")
                for (s in sentences) {
                    val cap = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    sb.append("- [ ] $cap\n")
                }
                sb.toString().trimEnd()
            }
            "private" -> {
                """
                ### 📓 Nota Personale
                
                > $cleanText
                """.trimIndent()
            }
            else -> {
                """
                $cleanText
                """.trimIndent()
            }
        }
    }

    private fun extractTitle(cleanText: String, markdown: String): String {
        val firstLine = cleanText.split(Regex("[.!?\n]")).firstOrNull()?.trim() ?: "Nota"
        val clean = firstLine.take(45).replace(Regex("[^a-zA-Z0-9àèéìòùÀÈÉÌÒÙ -]"), "")
        return clean.ifBlank { "Nota Registrata" }
    }

    private fun callOllama(host: String, model: String, systemPrompt: String, userText: String): String? {
        try {
            val url = URL("$host/api/generate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.connectTimeout = 8000
            conn.readTimeout = 40000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", model)
                put("system", systemPrompt)
                put("prompt", userText)
                put("stream", false)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = JSONObject(responseText)
                return respJson.optString("response", "").trim().ifBlank { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ollama request failed, falling back to heuristics: ${e.message}")
        }
        return null
    }
}
