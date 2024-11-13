package com.google.mlkit.vision.demo.kotlin

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LogApiService {
    @POST("api/event/log") // 서버에서 수신할 엔드포인트 수정
    suspend fun sendEventLog(
        @Body eventData: EventData
    ): Response<String> // 응답 타입을 String으로 수정
}

data class EventData( // 'LogData'를 'EventData'로 변경
    val message: String
)
