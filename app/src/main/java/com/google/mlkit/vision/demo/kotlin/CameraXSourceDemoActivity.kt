/*
 * Copyright 2021 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.kotlin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.widget.CompoundButton
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.camera.CameraSourceConfig
import com.google.mlkit.vision.camera.CameraXSource
import com.google.mlkit.vision.camera.DetectionTaskCallback
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.InferenceInfoGraphic
import com.google.mlkit.vision.demo.R
import com.google.mlkit.vision.demo.kotlin.business.EventSender
import com.google.mlkit.vision.demo.kotlin.objectdetector.ObjectGraphic
import com.google.mlkit.vision.demo.preference.PreferenceUtils
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import java.util.Objects
import kotlin.collections.List
import com.google.mlkit.vision.demo.kotlin.business.BoundingBoxTracker
import com.google.mlkit.vision.demo.kotlin.business.BoundingBoxUtils
import com.google.mlkit.vision.demo.kotlin.business.YoloObjectDetection
import android.graphics.Bitmap
import com.google.mlkit.vision.demo.BitmapUtils
import androidx.camera.core.ImageProxy

/** Live preview demo app for ML Kit APIs using CameraXSource API. */
@KeepName
@RequiresApi(VERSION_CODES.LOLLIPOP)
class CameraXSourceDemoActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {
  private var previewView: PreviewView? = null
  private var graphicOverlay: GraphicOverlay? = null
  private var needUpdateGraphicOverlayImageSourceInfo = false
  private var lensFacing: Int = CameraSourceConfig.CAMERA_FACING_BACK
  private var cameraXSource: CameraXSource? = null
  private var customObjectDetectorOptions: CustomObjectDetectorOptions? = null
  private var targetResolution: Size? = null
  private val eventSender = EventSender()
  private val boundingBoxTracker = BoundingBoxTracker()
  private lateinit var yoloObjectDetection: YoloObjectDetection


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate")
    setContentView(R.layout.activity_vision_cameraxsource_demo)
    previewView = findViewById(R.id.preview_view)
    if (previewView == null) {
      Log.d(TAG, "previewView is null")
    }
    graphicOverlay = findViewById(R.id.graphic_overlay)
    if (graphicOverlay == null) {
      Log.d(TAG, "graphicOverlay is null")
    }
    val facingSwitch = findViewById<ToggleButton>(R.id.facing_switch)
    facingSwitch.setOnCheckedChangeListener(this)

    // 카메라 권한 체크 및 요청
    if (!allRuntimePermissionsGranted()) {
      getRuntimePermissions()
    }
    
    // 초기화
    yoloObjectDetection = YoloObjectDetection(this)
  }

  override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
    lensFacing = if (lensFacing == CameraSourceConfig.CAMERA_FACING_FRONT) {
      CameraSourceConfig.CAMERA_FACING_BACK
    } else {
      CameraSourceConfig.CAMERA_FACING_FRONT
    }
    createThenStartCameraXSource()
  }

  public override fun onResume() {
    super.onResume()
    if (isFinishing || isDestroyed) {
      return
    }
    if (cameraXSource != null &&
        PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(this, localModel)
          .equals(customObjectDetectorOptions) &&
        PreferenceUtils.getCameraXTargetResolution(getApplicationContext(), lensFacing) != null &&
        (Objects.requireNonNull(
          PreferenceUtils.getCameraXTargetResolution(getApplicationContext(), lensFacing)
        ) == targetResolution)
    ) {
      cameraXSource!!.start()
    } else {
      createThenStartCameraXSource()
    }
  }

  override fun onPause() {
    super.onPause()
    if (cameraXSource != null) {
      cameraXSource!!.stop()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (cameraXSource != null) {
      cameraXSource!!.stop()
    }
  }

  private fun createThenStartCameraXSource() {
    if (cameraXSource != null) {
      cameraXSource!!.close()
    }
    customObjectDetectorOptions =
      PreferenceUtils.getCustomObjectDetectorOptionsForLivePreview(
        getApplicationContext(),
        localModel
      )

    if (customObjectDetectorOptions == null) {
      Log.e(TAG, "CustomObjectDetectorOptions is null. Cannot proceed.")
      return
    }

    // 카메라 타겟 해상도 설정
    targetResolution = PreferenceUtils.getCameraXTargetResolution(applicationContext, lensFacing)

    if (targetResolution == null) {
      Log.e(TAG, "Target resolution is null. Setting default resolution.")
      // 기본 해상도 설정
      targetResolution = Size(1280, 720)
    }


    // 객체 탐지 클라이언트 설정
    val objectDetector: ObjectDetector = ObjectDetection.getClient(customObjectDetectorOptions!!)
    val detectionTaskCallback: DetectionTaskCallback<List<DetectedObject>> =
      DetectionTaskCallback<List<DetectedObject>> { detectionTask ->
        detectionTask
          .addOnSuccessListener { results -> onDetectionTaskSuccess(results) }
          .addOnFailureListener { e -> onDetectionTaskFailure(e) }
      }
    // 카메라 설정 및 CameraXSource 시작
    val builder: CameraSourceConfig.Builder =
      CameraSourceConfig.Builder(applicationContext, objectDetector, detectionTaskCallback)
        .setFacing(lensFacing)
        .setRequestedPreviewSize(targetResolution!!.width, targetResolution!!.height)

    cameraXSource = CameraXSource(builder.build(), previewView!!)

    // 카메라 시작
    if (isFinishing || isDestroyed) {
      Log.d(TAG, "액티비티 finishing or destroyed, 카메라 시작안됨")
      return
    }

    needUpdateGraphicOverlayImageSourceInfo = true
    cameraXSource!!.start()
  }

  private fun onDetectionTaskSuccess(results: List<DetectedObject>) {
    graphicOverlay!!.clear()

    if (needUpdateGraphicOverlayImageSourceInfo) {
      val size: Size = cameraXSource!!.getPreviewSize()!!
      if (size != null) {
        Log.d(TAG, "preview width: " + size.width)
        Log.d(TAG, "preview height: " + size.height)
        val isImageFlipped = cameraXSource!!.getCameraFacing() == CameraSourceConfig.CAMERA_FACING_FRONT
        if (isPortraitMode) {
          // Swap width and height sizes when in portrait, since it will be rotated by
          // 90 degrees. The camera preview and the image being processed have the same size.
          graphicOverlay!!.setImageSourceInfo(size.height, size.width, isImageFlipped)
        } else {
          graphicOverlay!!.setImageSourceInfo(size.width, size.height, isImageFlipped)
        }
        needUpdateGraphicOverlayImageSourceInfo = false
      } else {
        Log.d(TAG, "previewsize is null")
      }
    }

    if (results.isNotEmpty()) {
      // 객체 탐지시 출력
      val detectedObject = results[0]

      // 로그로 바운딩 박스의 네 꼭짓점 좌표 출력
      Log.i(TAG, "Tracking ID: ${detectedObject.trackingId}")
      BoundingBoxUtils.boundingBoxLog(detectedObject)

      // BoundingBoxTracker 호출
      val isMovementDetected = boundingBoxTracker.trackBoundingBox(detectedObject)

      if (isMovementDetected) {
        // 현재 프레임의 이미지를 Bitmap으로 변환
        val bitmap = previewView?.bitmap  // PreviewView에서 현재 프리뷰 이미지를 가져옴
        if (bitmap != null) {
          // YOLO 객체 탐지 실행
          val result = yoloObjectDetection.processFrame(bitmap, detectedObject.boundingBox)
          Log.i(TAG, result)  // YOLO 결과를 로그로 출력
        } else {
          Log.e(TAG, "Bitmap is null!")
        }
      }
    }

    for (`object` in results) {
      graphicOverlay!!.add(ObjectGraphic(graphicOverlay!!, `object`))
    }

    graphicOverlay!!.add(InferenceInfoGraphic(graphicOverlay!!))
    graphicOverlay!!.postInvalidate()
  }


  private fun onDetectionTaskFailure(e: Exception) {
    graphicOverlay?.clear()
    graphicOverlay?.postInvalidate()
    val error = "Failed to process. Error: " + e.localizedMessage
    Toast.makeText(
        graphicOverlay!!.getContext(),
        """
   $error
   Cause: ${e.cause}
      """.trimIndent(),
        Toast.LENGTH_SHORT
      )
      .show()
    Log.d(TAG, error)
  }

  private val isPortraitMode: Boolean
    private get() =
      (getApplicationContext().getResources().getConfiguration().orientation !==
        Configuration.ORIENTATION_LANDSCAPE)

  // 여기부터 권한 관리
  private fun allRuntimePermissionsGranted(): Boolean {
    for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
      if (!isPermissionGranted(this, permission)) {
        return false
      }
    }
    return true
  }

  private fun getRuntimePermissions() {
    val permissionsToRequest = REQUIRED_RUNTIME_PERMISSIONS.filter {
      !isPermissionGranted(this, it)
    }
    if (permissionsToRequest.isNotEmpty()) {
      ActivityCompat.requestPermissions(
        this,
        permissionsToRequest.toTypedArray(),
        PERMISSION_REQUESTS
      )
    }
  }

  private fun isPermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  companion object {
    private const val TAG = "CameraXSourcePreview"
    private const val PERMISSION_REQUESTS = 1
    private val REQUIRED_RUNTIME_PERMISSIONS = arrayOf(
      Manifest.permission.CAMERA,
      Manifest.permission.WRITE_EXTERNAL_STORAGE,
      Manifest.permission.READ_EXTERNAL_STORAGE
    )
    private val localModel = LocalModel.Builder()
      .setAssetFilePath("custom_models/object_labeler.tflite")
      .build()
  }
}