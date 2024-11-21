package com.google.mlkit.vision.demo.kotlin.business

import android.graphics.Rect
import com.google.mlkit.vision.objects.DetectedObject

class BoundingBoxTracker {
    private var lastBoundingBox: Rect? = null // 이전 bounding box 저장

    // 이전 bounding box와 현재 bounding box의 차이를 계산하는 함수
    fun trackBoundingBox(detectedObject: DetectedObject): Boolean {
        val currentBoundingBox = detectedObject.boundingBox
        val isMovementDetected = hasSignificantMovement(lastBoundingBox, currentBoundingBox)

        // 이전 bounding box를 현재 값으로 업데이트
        lastBoundingBox = currentBoundingBox

        return isMovementDetected
    }

    // 좌표 변화가 10 이상인지 확인하는 함수
    private fun hasSignificantMovement(lastBoundingBox: Rect?, currentBoundingBox: Rect): Boolean {
        if (lastBoundingBox == null) {
            return false // 초기 상태는 이동이 없음
        }

        // 좌표 간 차이 계산 (x, y 각각)
        val xDiff = Math.abs(lastBoundingBox.left - currentBoundingBox.left) +
                Math.abs(lastBoundingBox.right - currentBoundingBox.right)
        val yDiff = Math.abs(lastBoundingBox.top - currentBoundingBox.top) +
                Math.abs(lastBoundingBox.bottom - currentBoundingBox.bottom)

        // x 또는 y 좌표 차이가 10 이상인 경우 행동 변화로 판단
        return xDiff >= 10 || yDiff >= 10
    }

    // bounding box의 좌표를 문자열로 변환
    // (x1, y1), (x2, y2), (x3, y3), (x4, y4)
    fun getBoundingBoxCoordinates(boundingBox: Rect): String {
        return "왼쪽 상단부터 시계방향(0,0): (${boundingBox.left}, ${boundingBox.top}) -> " +
                "(${boundingBox.right}, ${boundingBox.top}) -> " +
                "(${boundingBox.right}, ${boundingBox.bottom}) -> " +
                "(${boundingBox.left}, ${boundingBox.bottom})"
    }
}
