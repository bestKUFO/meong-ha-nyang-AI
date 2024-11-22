package com.google.mlkit.vision.demo.kotlin.business

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.io.IOException

//todo -> 바운딩박스만큼 crop하는거 없애기
class YoloObjectDetection(private val context: Context) {

    private lateinit var interpreter: Interpreter

    init {
        try {
            val model = loadModelFile(context)
            interpreter = Interpreter(model)
        } catch (e: Exception) {
            Log.e("YoloObjectDetection", "Error initializing YOLO model: ${e.message}")
        }
    }

    // YOLO 모델을 실행하고 예측 결과를 반환하는 함수
    fun processFrame(bitmap: Bitmap, boundingBox: Rect): String {
        if (boundingBox.isEmpty) {
            return "Bounding box is empty"
        }
        try {
            // 바운딩 박스를 기준으로 이미지를 자르고 YOLO 모델 실행
            val croppedBitmap = cropImage(bitmap, boundingBox)
            val resizedBitmap = Bitmap.createScaledBitmap(croppedBitmap, 416, 416, true)
            val inputArray = preprocessImage(resizedBitmap)

            // YOLO 모델 실행
            val outputMap = Array(1) { FloatArray(255) }
            interpreter.run(inputArray, outputMap)

            val detectedClass = outputMap[0][0]
            val confidence = outputMap[0][1]

            // 예측된 객체 클래스와 신뢰도 반환
            return "Detected Object: Class $detectedClass, Confidence: ${confidence * 100}%"
        } catch (e: Exception) {
            Log.e("YoloObjectDetection", "Error during YOLO model execution: ${e.message}")
            return "Error during YOLO execution"
        }
    }

    // YOLO 모델 실행 후 결과를 로그로 출력하는 함수
    fun logResult(bitmap: Bitmap, boundingBox: Rect) {
        // YOLO 모델 실행
        val predictionResults = processFrame(bitmap, boundingBox)

        // 로그 출력
        Log.i("YoloObjectDetection", "YOLO Execution Result: $predictionResults")
    }

    // 이미지를 바운딩 박스 크기에 맞게 자르는 함수
    private fun cropImage(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        return Bitmap.createBitmap(
            bitmap,
            boundingBox.left,
            boundingBox.top,
            boundingBox.width(),
            boundingBox.height()
        )
    }

    // YOLO 모델에 맞는 입력 형식으로 이미지를 전처리하는 함수
    private fun preprocessImage(bitmap: Bitmap): Array<FloatArray> {
        val inputArray = Array(1) { FloatArray(416 * 416 * 3) }
        val pixels = IntArray(416 * 416)

        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in 0 until 416) {
            for (j in 0 until 416) {
                val pixel = pixels[i * 416 + j]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                val index = (i * 416 + j) * 3
                inputArray[0][index] = r
                inputArray[0][index + 1] = g
                inputArray[0][index + 2] = b
            }
        }

        return inputArray
    }

    // 모델 파일을 ByteBuffer로 로드하는 함수
    private fun loadModelFile(context: Context): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd("custom_models/yolov5s_f16.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            .order(ByteOrder.nativeOrder())
    }
}

