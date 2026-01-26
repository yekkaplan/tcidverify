package com.idverify.reactnative

import android.app.Activity
import android.util.Log
import android.util.Size
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.idverify.sdk.autocapture.AutoCaptureAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Helper class to manage CameraX and AutoCaptureAnalyzer.
 * Separates camera logic from React Native module.
 */
class CameraHelper(
    private val eventListener: EventListener
) {
    companion object {
        private const val TAG = "CameraHelper"
    }

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeAnalyzer: AutoCaptureAnalyzer? = null
    private var previewView: PreviewView? = null

    interface EventListener {
        fun onStateChange(state: AutoCaptureAnalyzer.CaptureState, message: String)
        fun onQualityUpdate(metrics: AutoCaptureAnalyzer.QualityMetrics)
        fun onCaptured(result: AutoCaptureAnalyzer.CaptureResult, isBackSide: Boolean)
        fun onError(code: String, message: String)
    }

    fun setPreviewView(view: PreviewView, activity: Activity) {
        this.previewView = view
        Log.d(TAG, "PreviewView set, attempting to bind camera...")
        bindCameraIfReady(activity)
    }

    fun startAutoCapture(activity: Activity, isBackSide: Boolean) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Release previous analyzer
                activeAnalyzer?.release()

                // Create new analyzer with event callbacks
                activeAnalyzer = AutoCaptureAnalyzer(
                    isBackSide = isBackSide,
                    onStateChange = { state, message -> eventListener.onStateChange(state, message) },
                    onQualityUpdate = { metrics -> eventListener.onQualityUpdate(metrics) },
                    onCaptured = { result -> eventListener.onCaptured(result, isBackSide) }
                )

                Log.d(TAG, "Camera analyzer prepared for ${if (isBackSide) "back" else "front"} side")
                bindCameraIfReady(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                eventListener.onError("CAMERA_INIT_ERROR", "Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    fun stopAutoCapture() {
        try {
            cameraProvider?.unbindAll()
            activeAnalyzer?.release()
            activeAnalyzer = null
            // We do NOT clear previewView here as it might be reused or handled by ViewManager
            Log.d(TAG, "Camera stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    private fun bindCameraIfReady(activity: Activity) {
        val view = previewView
        val analyzer = activeAnalyzer
        val provider = cameraProvider

        if (view == null || analyzer == null || provider == null) {
            Log.d(TAG, "Not ready to bind: view=${view != null}, analyzer=${analyzer != null}, provider=${provider != null}")
            return
        }

        // Ensure view is ready
        if (view.display == null || view.width == 0 || view.height == 0) {
            Log.d(TAG, "PreviewView not ready yet (w=${view.width}, h=${view.height}), waiting for layout")
            view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View?,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    val w = right - left
                    val h = bottom - top
                    if (w > 0 && h > 0) {
                        view.removeOnLayoutChangeListener(this)
                        Log.d(TAG, "PreviewView layout ready (w=$w, h=$h), binding now")
                        bindCameraIfReady(activity)
                    }
                }
            })
            return
        }

        val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
        if (lifecycleOwner == null) {
            Log.e(TAG, "Activity is NOT a LifecycleOwner!")
            eventListener.onError("LIFECYCLE_ERROR", "Activity must implement LifecycleOwner")
            return
        }

        try {
            val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_0

            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build()
                .also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .setTargetResolution(Size(1280, 720))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            Log.i(TAG, "✅ Camera successfully bound to lifecycle!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Camera bind failed", e)
            eventListener.onError("CAMERA_BIND_ERROR", "Failed to bind camera: ${e.message}")
        }
    }

    fun release() {
        stopAutoCapture()
        cameraExecutor.shutdown()
    }
}
