package com.es1.companion.domain.exporter

import android.content.Context
import android.os.Environment
import android.util.Log
import com.es1.companion.data.local.NoteDao
import com.es1.companion.data.local.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MarkdownExporter(
    private val context: Context,
    private val noteDao: NoteDao
) {
    private val TAG = "MarkdownExporter"

    suspend fun exportNote(noteId: String): String? = withContext(Dispatchers.IO) {
        val note = noteDao.getAllNotes() // or get note directly
        val noteItem = noteDao.getPendingTranscriptions().find { it.id == noteId }
            ?: noteDao.getPendingElaborations().find { it.id == noteId }

        val targetNote = noteItem ?: return@withContext null
        return@withContext exportNoteEntity(targetNote)
    }

    suspend fun exportNoteEntity(note: NoteEntity): String? = withContext(Dispatchers.IO) {
        try {
            val tagRule = noteDao.getTagRule(note.tag)
            val subFolder = tagRule?.targetFolder ?: "UnoNotes/${note.tag}"

            // Use App external documents directory (accessible and compatible with Obsidian / file managers)
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: File(context.filesDir, "exports")
            val targetDir = File(baseDir, subFolder).apply { mkdirs() }

            val titleSlug = (note.elaboratedTitle ?: "Nota_${note.deviceNoteNum}")
                .replace(Regex("[^a-zA-Z0-9àèéìòùÀÈÉÌÒÙ _-]"), "")
                .replace(" ", "_")
                .take(40)

            val datePrefix = note.createdUtc.take(10)
            val fileName = "${datePrefix}_${titleSlug}.md"
            val targetFile = File(targetDir, fileName)

            val markdownContent = buildString {
                appendLine("---")
                appendLine("id: \"${note.id}\"")
                appendLine("device_note_num: ${note.deviceNoteNum}")
                appendLine("device_id: \"${note.deviceId}\"")
                appendLine("tag: \"${note.tag}\"")
                appendLine("created_utc: \"${note.createdUtc}\"")
                appendLine("duration_sec: ${note.durationSec}")
                appendLine("audio_sha256: \"${note.audioSha256}\"")
                appendLine("---")
                appendLine()
                appendLine("# ${note.elaboratedTitle ?: "Nota #${note.deviceNoteNum}"}")
                appendLine()
                appendLine(note.elaboratedMarkdown ?: (note.transcriptionText ?: "Audio registrato."))
                appendLine()
                if (!note.transcriptionText.isNullOrBlank()) {
                    appendLine("<details>")
                    appendLine("<summary>🎙️ Trascrizione Grezza (STT)</summary>")
                    appendLine()
                    appendLine("> ${note.transcriptionText}")
                    appendLine()
                    appendLine("</details>")
                }
            }

            targetFile.writeText(markdownContent)
            Log.d(TAG, "Exported note #${note.deviceNoteNum} to ${targetFile.absolutePath}")

            val updated = note.copy(
                isExported = true,
                exportedPath = targetFile.absolutePath
            )
            noteDao.updateNote(updated)
            return@withContext targetFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Export failed for note #${note.deviceNoteNum}", e)
            return@withContext null
        }
    }
}
