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

class LLMEngine(
    private val context: Context,
    private val noteDao: NoteDao
) {
    private val TAG = "LLMEngine"
    private var ollamaHost: String? = null // Optional e.g. "http://192.168.1.100:11434"
    private var ollamaModel: String = "qwen2.5:1.5b"

    suspend fun elaborateNote(noteId: String): NoteEntity? = withContext(Dispatchers.IO) {
        val notes = noteDao.getAllNotes()
        // Find note
        val note = noteDao.getPendingElaborations().find { it.id == noteId }
            ?: return@withContext null

        val rawText = note.transcriptionText
        if (rawText.isNullOrBlank()) {
            Log.w(TAG, "Cannot elaborate note with empty transcription")
            return@withContext null
        }

        val tagRule = noteDao.getTagRule(note.tag)
        val prompt = tagRule?.systemPrompt ?: "Rielabora e struttura questa nota vocale in modo chiaro."

        Log.d(TAG, "Elaborating note #${note.deviceNoteNum} (Tag: ${note.tag})...")

        // 1. Prova con Ollama se configurato
        var result = if (!ollamaHost.isNullOrBlank()) {
            callOllama(ollamaHost!!, ollamaModel, prompt, rawText)
        } else null

        // 2. Fallback Euristico Locale ad altissima affidabilità
        if (result == null) {
            result = applyHeuristicElaboration(note.tag, rawText)
        }

        val title = extractTitle(rawText, result)
        val updatedNote = note.copy(
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

    private fun applyHeuristicElaboration(tag: String, rawText: String): String {
        val cleanText = rawText.trim()
        val sentences = cleanText.split(Regex("[.!?\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return when (tag.lowercase()) {
            "todo" -> {
                val sb = StringBuilder()
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
                val sb = StringBuilder("### 🛒 Lista Acquisti\n\n")
                for (s in sentences) {
                    sb.append("- [ ] $s\n")
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

    private fun extractTitle(rawText: String, markdown: String): String {
        val firstLine = rawText.split(Regex("[.!?\n]")).firstOrNull()?.trim() ?: "Nota"
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
