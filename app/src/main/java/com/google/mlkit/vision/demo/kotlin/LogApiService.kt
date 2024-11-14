package com.google.mlkit.vision.demo.kotlin

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LogApiService {
    @POST("api/event/log") // 서버에서 수신할 엔드포인트 수정
    suspend fun sendEventLog(
        @Body eventData: EventData
    ): Response<ResponseBody>
}

data class EventData(
    val message: String
)
