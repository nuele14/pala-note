package com.es1.companion.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ES1ApiService {

    @GET("/api/info")
    suspend fun getDeviceInfo(): Response<DeviceInfoResponse>

    @GET("/api/notes")
    suspend fun getDeviceNotes(): Response<DeviceNotesResponse>

    @Streaming
    @GET("/api/notes/audio")
    suspend fun downloadAudio(@Query("num") noteNum: Int): Response<ResponseBody>

    @POST("/api/notes/ack")
    suspend fun sendAck(@Query("num") noteNum: Int): Response<ResponseBody>

    @POST("/api/sync/done")
    suspend fun notifySyncDone(): Response<ResponseBody>
}
