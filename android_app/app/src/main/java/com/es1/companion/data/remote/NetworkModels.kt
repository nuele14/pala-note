package com.es1.companion.data.remote

import com.google.gson.annotations.SerializedName

data class DeviceInfoResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("firmware_version") val firmwareVersion: String,
    @SerializedName("notes_count") val notesCount: Int,
    @SerializedName("battery_pct") val batteryPct: Int,
    @SerializedName("softap_ssid") val softApSsid: String? = null
)

data class DeviceNoteItem(
    @SerializedName("num") val num: Int,
    @SerializedName("tag") val tag: String,
    @SerializedName("has_text") val hasText: Boolean = false,
    @SerializedName("uploaded") val uploaded: Boolean = false,
    @SerializedName("duration_sec") val durationSec: Float = 0.0f,
    @SerializedName("audio_bytes") val audioBytes: Long = 0L,
    @SerializedName("audio_url") val audioUrl: String? = null
)

data class DeviceNotesResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("count") val count: Int,
    @SerializedName("notes") val notes: List<DeviceNoteItem>
)

data class SyncResult(
    val success: Boolean,
    val downloadedCount: Int = 0,
    val message: String = ""
)
