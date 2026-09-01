package com.es1.companion.data.remote

import com.google.gson.annotations.SerializedName

data class DeviceInfoResponse(
    @SerializedName("device_id") val deviceId: String = "ES1",
    @SerializedName("firmware_version") val firmwareVersion: String = "1.0.0",
    @SerializedName("notes_count") val notesCount: Int = 0,
    @SerializedName("battery_pct") val batteryPct: Int = 100,
    @SerializedName("softap_ssid") val softApSsid: String? = null
)

data class DeviceNoteItem(
    @SerializedName("num") val num: Int,
    @SerializedName("tag") val tag: String = "Note",
    @SerializedName("has_text") val hasText: Boolean = false,
    @SerializedName("uploaded") val uploaded: Boolean = false,
    @SerializedName("duration_sec") val durationSec: Float = 0.0f,
    @SerializedName("audio_bytes") val audioBytes: Long = 0L,
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("created_utc") val createdUtc: String? = null,
    @SerializedName("synced_utc") val syncedUtc: String? = null
)

data class DeviceNotesResponse(
    @SerializedName("device_id") val deviceId: String = "ES1",
    @SerializedName("count") val count: Int = 0,
    @SerializedName("notes") val notes: List<DeviceNoteItem> = emptyList()
)

data class SyncResult(
    val success: Boolean,
    val downloadedCount: Int = 0,
    val message: String = ""
)
