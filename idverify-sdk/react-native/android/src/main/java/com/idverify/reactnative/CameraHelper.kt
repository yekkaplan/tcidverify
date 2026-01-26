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
        Log.d(TAG, "setPreviewView called. Checking attachment state...")
        
        // DEBUG PROBES
        Log.e(TAG, "PREVIEW_DEBUG: size=${view.width}x${view.height}")
        Log.e(TAG, "PREVIEW_DEBUG: attached=${view.isAttachedToWindow}")
        Log.e(TAG, "PREVIEW_DEBUG: windowToken=${view.windowToken}")
        Log.e(TAG, "PREVIEW_DEBUG: display=${view.display}")

        // GOLDEN FIX: Only bind when attached to window
        if (view.isAttachedToWindow) {
             Log.d(TAG, "View already attached, binding immediately.")
             bindCameraIfReady(activity)
        }

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                Log.e(TAG, "✅ VIEW ATTACHED TO WINDOW -> BINDING CAMERA")
                Log.e(TAG, "PREVIEW_DEBUG (Attached): windowToken=${v.windowToken}, display=${v.display}")
                bindCameraIfReady(activity)
            }

            override fun onViewDetachedFromWindow(v: View) {
                Log.e(TAG, "⚠️ VIEW DETACHED FROM WINDOW -> UNBINDING")
                try {
                    cameraProvider?.unbindAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Error unbinding on detach", e)
                }
            }
        })
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

                Log.d(TAG, "Camera analyzer prepared. Triggering re-bind if view is ready.")
                // Bind if view is already set and attached
                if (previewView != null && previewView!!.isAttachedToWindow) {
                    bindCameraIfReady(activity)
                }
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
            // We do NOT clear previewView here, but the listener handles detach
            Log.d(TAG, "Camera stopped (unbindAll called)")
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
        
        if (!view.isAttachedToWindow) {
             Log.w(TAG, "Attempted to bind but View is NOT attached to window. Skipping.")
             return
        }

        // Ensure view dimensions (secondary check)
        if (view.width == 0 || view.height == 0) {
            Log.d(TAG, "PreviewView attached but 0x0. Waiting for layout pass...")
            // The ViewManager's Choreographer hack will eventually fix the size and trigger layout
            // We can listen to layout changes just to be sure to trigger bind again
            view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                    if ((r - l) > 0 && (b - t) > 0) {
                        view.removeOnLayoutChangeListener(this)
                        Log.d(TAG, "PreviewView dimensions valid (${r-l}x${b-t}), re-attempting bind")
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
            Log.d(TAG, "Binding Camera: View size=${view.width}x${view.height}, Rotation=$rotation")

            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build()

            // KEY STEP: Set surface provider strictly here
            preview.setSurfaceProvider(view.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .setTargetResolution(Size(1280, 720))
                .build()
                .apply {
                    setAnalyzer(cameraExecutor, analyzer)
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            Log.i(TAG, "✅ CAMERA SUCCESSFULLY BOUND TO LIFECYCLE")
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
