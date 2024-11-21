package com.google.mlkit.vision.demo.kotlin.business

import android.util.Log
import com.google.mlkit.vision.demo.kotlin.EventData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class EventSender(private val sendIntervalMillis: Long = 5000L) {
    private val lastSentTime = AtomicLong(0)

    // 서버로 이벤트 전송
    fun sendEventToServerWithThrottling(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - lastSentTime.get()
            if (elapsed >= sendIntervalMillis) {
                // 5초가 지난 경우에만 전송
                sendEventToServer(message)
                lastSentTime.set(currentTime)
            } else {
                Log.d(TAG, "Skipping event send; last sent ${elapsed}ms ago")
            }
        }
    }

    // 서버로 로그 전송하는 메서드
    private fun sendEventToServer(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Retrofit 또는 기타 네트워크 라이브러리 사용하여 서버로 전송
                val response = RetrofitClient.logApiService.sendEventLog(EventData(message))
                if (response.isSuccessful) {
                    Log.d(TAG, "Event data successfully sent to server.")
                } else {
                    Log.e(TAG, "Failed to send event data: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending event data to server", e)
            }
        }
    }

    companion object {
        private const val TAG = "EventSender"
    }
}
