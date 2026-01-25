package com.idverify.reactnative

import android.app.Activity
import android.util.Log
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.idverify.sdk.autocapture.AutoCaptureAnalyzer
import java.util.concurrent.Executors

/**
 * React Native Native Module for IDVerify SDK
 * 
 * This module bridges the native Android IDVerify SDK to React Native.
 * It follows AGENTS.md architecture - no logic is moved, only binding/orchestration.
 * 
 * Architecture:
 * - Pure binding layer - no business logic
 * - EventEmitter for state changes
 * - Promise-based API
 * - Lifecycle-aware
 * - TurboModule compatible (React Native 0.76+)
 */
@ReactModule(name = IdVerifyModule.MODULE_NAME)
class IdVerifyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "IdVerifyModule"
        const val MODULE_NAME = "IdVerify"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeAnalyzer: AutoCaptureAnalyzer? = null
    private var previewView: PreviewView? = null
    private var currentSide: Boolean = false // false = front, true = back
    private var isInitialized = false

    override fun getName(): String = MODULE_NAME

    // ==================== Initialization ====================

    @ReactMethod
    fun init(config: ReadableMap, promise: Promise) {
        try {
            // Config parsing (if needed in future)
            // For now, just mark as initialized
            isInitialized = true
            Log.d(TAG, "IdVerify SDK initialized")
            promise.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            promise.reject("INIT_ERROR", "Initialization failed: ${e.message}", e)
        }
    }

    // ==================== Auto Capture API ====================

    @ReactMethod
    fun startAutoCapture(isBackSide: Boolean, promise: Promise) {
        if (!isInitialized) {
            promise.reject("NOT_INITIALIZED", "SDK not initialized. Call init() first.")
            return
        }

        val activity = currentActivity
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No current activity available")
            return
        }

        try {
            currentSide = isBackSide
            startCamera(activity, isBackSide)
            promise.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "startAutoCapture failed", e)
            promise.reject("START_ERROR", "Failed to start auto capture: ${e.message}", e)
        }
    }

    @ReactMethod
    fun stopAutoCapture(promise: Promise) {
        try {
            stopCamera()
            promise.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "stopAutoCapture failed", e)
            promise.reject("STOP_ERROR", "Failed to stop auto capture: ${e.message}", e)
        }
    }

    /**
     * Set PreviewView for camera preview
     * Called by IdVerifyCameraViewManager when view is created
     */
    fun setPreviewView(view: PreviewView, activity: Activity) {
        this.previewView = view
        Log.d(TAG, "PreviewView set, binding camera if analyzer ready...")
        bindCameraIfReady(activity)
    }

    // ==================== Camera Management ====================

    private fun startCamera(activity: Activity, isBackSide: Boolean) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                // Release previous analyzer
                activeAnalyzer?.release()
                
                // Create new analyzer with event callbacks
                activeAnalyzer = AutoCaptureAnalyzer(
                    isBackSide = isBackSide,
                    onStateChange = { state, message ->
                        // Run on UI thread for React Native
                        reactApplicationContext.runOnUiQueueThread {
                            emitStateChange(state, message)
                        }
                    },
                    onQualityUpdate = { metrics ->
                        reactApplicationContext.runOnUiQueueThread {
                            emitQualityUpdate(metrics)
                        }
                    },
                    onCaptured = { result ->
                        reactApplicationContext.runOnUiQueueThread {
                            emitCaptured(result, isBackSide)
                        }
                    }
                )
                
                Log.d(TAG, "Camera analyzer prepared for ${if (isBackSide) "back" else "front"} side")
                
                // Bind camera if PreviewView is already set
                bindCameraIfReady(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                emitError("CAMERA_INIT_ERROR", "Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun bindCameraIfReady(activity: Activity) {
        val view = previewView
        val analyzer = activeAnalyzer
        val provider = cameraProvider

        if (view == null || analyzer == null || provider == null) {
            Log.d(TAG, "Not ready to bind: view=$view, analyzer=$analyzer, provider=$provider")
            return
        }

        // Ensure view is ready (like native SDK does)
        if (view.display == null || view.width == 0) {
            Log.d(TAG, "PreviewView not ready yet, waiting for layout")
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                bindCameraIfReady(activity)
            }
            return
        }

        Log.d(TAG, "Binding camera with lifecycle...")
        val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
        if (lifecycleOwner == null) {
            Log.e(TAG, "Activity is NOT a LifecycleOwner!")
            emitError("LIFECYCLE_ERROR", "Activity must implement LifecycleOwner")
            return
        }

        try {
            // Get rotation from display
            val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
            
            // Preview with rotation (like native SDK)
            val preview = androidx.camera.core.Preview.Builder()
                .setTargetRotation(rotation)
                .build()
                .also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }

            // ImageAnalysis with rotation and resolution (like native SDK)
            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .setTargetResolution(android.util.Size(1280, 720))  // 720p like native SDK
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            Log.i(TAG, "✅ Camera successfully bound to lifecycle!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Camera bind failed", e)
            emitError("CAMERA_BIND_ERROR", "Failed to bind camera: ${e.message}")
        }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        activeAnalyzer?.release()
        activeAnalyzer = null
        previewView = null
        Log.d(TAG, "Camera stopped")
    }

    /**
     * Gets the active analyzer for camera binding
     * This is used by the CameraView component to bind the analyzer
     */
    fun getActiveAnalyzer(): AutoCaptureAnalyzer? = activeAnalyzer
    
    /**
     * Gets the camera provider for camera binding
     */
    fun getCameraProvider(): ProcessCameraProvider? = cameraProvider
    
    /**
     * Gets the camera executor
     */
    fun getCameraExecutor() = cameraExecutor

    // ==================== Event Emission ====================

    private fun emitStateChange(state: AutoCaptureAnalyzer.CaptureState, message: String) {
        val params = Arguments.createMap().apply {
            putString("state", state.name)
            putString("message", message)
        }
        sendEvent("onStateChange", params)
    }

    private fun emitQualityUpdate(metrics: AutoCaptureAnalyzer.QualityMetrics) {
        val params = Arguments.createMap().apply {
            putInt("cardConfidence", metrics.cardConfidence)
            putDouble("blurScore", metrics.blurScore.toDouble())
            putDouble("stability", metrics.stability.toDouble())
            putInt("glareScore", metrics.glareScore)
            putString("state", metrics.state.name)
            putString("message", metrics.message)
        }
        sendEvent("onQualityUpdate", params)
    }

    private fun emitCaptured(result: AutoCaptureAnalyzer.CaptureResult, isBackSide: Boolean) {
        val params = Arguments.createMap().apply {
            putBoolean("isBackSide", isBackSide)
            putMap("extractedData", convertMapToWritableMap(result.extractedData))
            putInt("mrzScore", result.mrzScore)
            putBoolean("isValid", result.isValid)
            // Note: Bitmap cannot be directly serialized to JS
            // We would need to convert to base64 or save to file
            // For now, we'll skip the image
        }
        sendEvent("onCaptured", params)
    }

    private fun emitError(code: String, message: String) {
        val params = Arguments.createMap().apply {
            putString("code", code)
            putString("message", message)
        }
        sendEvent("onError", params)
    }

    private fun sendEvent(eventName: String, params: WritableMap) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    // ==================== Helper Methods ====================

    private fun convertMapToWritableMap(map: Map<String, String>): WritableMap {
        val writableMap = Arguments.createMap()
        map.forEach { (key, value) ->
            writableMap.putString(key, value)
        }
        return writableMap
    }

    // ==================== Lifecycle ====================

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        stopCamera()
        cameraExecutor.shutdown()
    }

    // ==================== Cleanup ====================

    @ReactMethod
    fun release(promise: Promise) {
        try {
            stopCamera()
            cameraExecutor.shutdown()
            isInitialized = false
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("RELEASE_ERROR", "Failed to release: ${e.message}", e)
        }
    }
}
