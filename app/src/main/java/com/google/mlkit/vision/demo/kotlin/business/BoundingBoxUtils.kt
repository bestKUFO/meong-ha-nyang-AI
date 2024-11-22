package com.google.mlkit.vision.demo.kotlin.business

import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.objects.DetectedObject


// 좌표 데이터 클래스
data class BoundingBoxCoordinates(
    val x1: Int, val y1: Int,
    val x2: Int, val y2: Int,
    val x3: Int, val y3: Int,
    val x4: Int, val y4: Int
)

object BoundingBoxUtils {
    private const val TAG = "BoundingBoxUtils"

    fun boundingBoxCoordinates(detectedObject: DetectedObject): BoundingBoxCoordinates {
        val boundingBox = detectedObject.boundingBox

        val x1 = boundingBox.left
        val y1 = boundingBox.top
        val x2 = boundingBox.right
        val y2 = boundingBox.top
        val x3 = boundingBox.right
        val y3 = boundingBox.bottom
        val x4 = boundingBox.left
        val y4 = boundingBox.bottom

        // 좌표를 data class로 반환
        return BoundingBoxCoordinates(x1, y1, x2, y2, x3, y3, x4, y4)
    }

    // 로그 출력 함수
    fun boundingBoxLog(detectedObject: DetectedObject) {
        val coordinates = boundingBoxCoordinates(detectedObject)

        Log.i(TAG, "BoundingBox 좌상단 (${coordinates.x1}, ${coordinates.y1}), 우상단(${coordinates.x2}, ${coordinates.y2}), 우하단(${coordinates.x3}, ${coordinates.y3}), 좌하단(${coordinates.x4}, ${coordinates.y4})")
    }
    
    // Message 전달 함수
    fun boundingBoxMessage(detectedObject: DetectedObject): String {
        val coordinates = boundingBoxCoordinates(detectedObject)

        return "왼쪽 상단부터 시계방향(0,0): (${coordinates.x1}, ${coordinates.y1}) -> (${coordinates.x2}, ${coordinates.y2}) -> (${coordinates.x3}, ${coordinates.y3}) -> (${coordinates.x4}, ${coordinates.y4})"
    }

    // 좌표 변환 메서드
    fun boundingBoxCoordinatesFromRect(rect: Rect): BoundingBoxCoordinates {
        return BoundingBoxCoordinates(
            x1 = rect.left, y1 = rect.top,
            x2 = rect.right, y2 = rect.top,
            x3 = rect.right, y3 = rect.bottom,
            x4 = rect.left, y4 = rect.bottom
        )
    }

}
