package com.es1.companion.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val deviceNoteNum: Int,
    val deviceId: String = "ESP32",
    val createdUtc: String,
    val tag: String = "Untagged",
    val durationSec: Float = 0.0f,
    val audioFileSize: Long = 0L,
    val audioLocalPath: String = "",
    val audioSha256: String = "",
    val isSyncedWithDevice: Boolean = true,
    val transcriptionText: String? = null,
    val transcriptionLanguage: String = "it",
    val elaboratedTitle: String? = null,
    val elaboratedMarkdown: String? = null,
    val isExported: Boolean = false,
    val exportedPath: String? = null,
    val updatedAtUtc: String = createdUtc
)
