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

class YoloObjectDetection(private val context: Context) {
    private lateinit var interpreter: Interpreter
    private var classLabels: List<String>? = null

    init {
        try {
            val model = loadModelFile(context)
            interpreter = Interpreter(model)
            interpreter.allocateTensors()
            Log.d("YoloObjectDetection", "Model loaded and tensors allocated successfully.")
        } catch (e: Exception) {
            Log.e("YoloObjectDetection", "Error initializing YOLO model: ${e.message}")
        }
    }

    fun processFrame(bitmap: Bitmap, boundingBox: Rect): String {
        if (bitmap.width == 0 || bitmap.height == 0) {
            return "Invalid image size"
        }
        try {
            // 이미지 크기 리사이즈 (320x320)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 320, 320, true)

            // RGB 이미지로 변환
            val inputArray = preprocessImage(resizedBitmap)

            val inputTensorIndex = 0
            val inputTensor = interpreter.getInputTensor(inputTensorIndex)
            val inputShape = inputTensor.shape()
            val bufferSize = inputShape.reduce { acc, i -> acc * i } * 4 // float 형 데이터 크기

            // 텐서의 크기를 확인하여 ByteBuffer 크기 조정
            val inputData = ByteBuffer.allocateDirect(bufferSize).apply {
                order(ByteOrder.nativeOrder())
            }

            // 이미지를 ByteBuffer에 복사
            if (inputArray.remaining() != inputData.remaining()) {
                throw IllegalArgumentException("Input array size does not match input tensor size.")
            }

            inputArray.rewind()
            inputData.put(inputArray)

            // 출력 텐서 준비 (출력 크기 확인 후 설정)
            val outputShape = interpreter.getOutputTensor(0).shape()
            Log.d("YoloObjectDetection", "Output Tensor Shape: ${outputShape.joinToString()}")

            val outputSize = outputShape.reduce { acc, i -> acc * i }
            val outputMap = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }  // [1, 6300, 85]

            // 모델 실행
            interpreter.run(inputData, outputMap)

            // 출력 결과 로그
            val results = processOutput(outputMap)

            // 클래스 인덱스와 확률을 찾아 가장 높은 확률의 클래스를 찾기
            val maxIndex = results.indices.maxByOrNull { results[it].second } ?: -1
            val detectedClassIndex: Int = maxIndex  // Ensure it's an Int
            val confidence = results.getOrNull(maxIndex)?.second ?: 0f

            // Labels.txt 로드 (클래스 이름 가져오기)
            val labels = loadClassLabels(context)

            // 결과에서 클래스 이름을 찾기
            val detectedClassName = if (detectedClassIndex in labels.indices) {
                labels[detectedClassIndex]
            } else {
                "Unknown"
            }

            // 로그 출력
            logResult(resizedBitmap, boundingBox, detectedClassIndex, confidence)

            // 결과 출력
            return if (detectedClassIndex != -1) {
                "Detected Object: $detectedClassName with Confidence: $confidence"
            } else {
                "No valid detection"
            }
        } catch (e: Exception) {
            Log.e("YoloObjectDetection", "Error during YOLO model execution: ${e.message}", e)
            return "Error during YOLO execution: ${e.message}"
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 320, 320, true)
        val width = resizedBitmap.width
        val height = resizedBitmap.height

        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Invalid image dimensions: width=$width, height=$height")
        }

        val bufferSize = 4 * width * height * 3
        val inputBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = resizedBitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                inputBuffer.putFloat(r / 255.0f)
                inputBuffer.putFloat(g / 255.0f)
                inputBuffer.putFloat(b / 255.0f)
            }
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private fun processOutput(outputMap: Array<Array<FloatArray>>): List<Pair<Float, Float>> {
        val results = mutableListOf<Pair<Float, Float>>()
        for (i in outputMap[0].indices) {
            val detectedClass = outputMap[0][i][4]
            val confidence = outputMap[0][i][5]
            results.add(Pair(detectedClass, confidence))
        }
        // 중복 로그 출력 제거
        return results
    }

    private fun logResult(
        bitmap: Bitmap,
        boundingBox: Rect,
        detectedClass: Int,
        confidence: Float
    ) {
        val confidencePercentage = confidence * 100
        Log.i(
            "YoloObjectDetection",
            "Detection Result: Class $detectedClass, Confidence: %.6f%%".format(confidencePercentage)
        )
        Log.i(
            "YoloObjectDetection",
            "Bounding Box: Left ${boundingBox.left}, Top ${boundingBox.top}, Right ${boundingBox.right}, Bottom ${boundingBox.bottom}"
        )
    }

    private fun loadClassLabels(context: Context): List<String> {
        // 이미 로드된 라벨이 있으면 반환, 없으면 새로 로드
        if (classLabels != null) return classLabels!!

        val labels = mutableListOf<String>()
        try {
            // labels.txt 경로
            context.assets.open("custom_models/labels.txt").bufferedReader().useLines { lines ->
                lines.forEach { labels.add(it) }
            }
            classLabels = labels
        } catch (e: IOException) {
            Log.e("YoloObjectDetection", "Error loading labels: ${e.message}")
            e.printStackTrace()  // 자세한 스택 트레이스 출력
        }
        return classLabels ?: emptyList()
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        try {
            val assetFileDescriptor = context.assets.openFd("custom_models/yolov5s_f16.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            ).order(ByteOrder.nativeOrder())
        } catch (e: IOException) {
            Log.e("YoloObjectDetection", "Error loading model file: ${e.message}")
            throw e  // 오류가 발생하면 예외를 던져서 문제를 추적할 수 있게 함
        }
    }
}
