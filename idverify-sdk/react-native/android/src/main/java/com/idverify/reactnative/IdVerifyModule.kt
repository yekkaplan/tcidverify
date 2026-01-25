package com.idverify.reactnative

import android.app.Activity
import android.util.Log
import androidx.camera.view.PreviewView
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.idverify.sdk.autocapture.AutoCaptureAnalyzer

/**
 * React Native Native Module for IDVerify SDK
 * 
 * This module bridges the native Android IDVerify SDK to React Native.
 * Logic is delegated to CameraHelper.
 */
@ReactModule(name = IdVerifyModule.MODULE_NAME)
class IdVerifyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), CameraHelper.EventListener {

    companion object {
        private const val TAG = "IdVerifyModule"
        const val MODULE_NAME = "IdVerify"
    }

    private var cameraHelper: CameraHelper? = null
    private var isInitialized = false

    override fun getName(): String = MODULE_NAME

    // ==================== Initialization ====================

    @ReactMethod
    fun init(config: ReadableMap, promise: Promise) {
        try {
            if (cameraHelper == null) {
                cameraHelper = CameraHelper(this)
            }
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

        val activity = getCurrentActivity()
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No current activity available")
            return
        }

        try {
            cameraHelper?.startAutoCapture(activity, isBackSide)
            promise.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "startAutoCapture failed", e)
            promise.reject("START_ERROR", "Failed to start auto capture: ${e.message}", e)
        }
    }

    @ReactMethod
    fun stopAutoCapture(promise: Promise) {
        try {
            cameraHelper?.stopAutoCapture()
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
        Log.d(TAG, "setPreviewView called")
        if (cameraHelper == null) {
             // In case ViewManager sets view before init or after cleanup
             Log.w(TAG, "CameraHelper not initialized yet, initializing...")
             cameraHelper = CameraHelper(this)
             isInitialized = true
        }
        cameraHelper?.setPreviewView(view, activity)
    }

    // ==================== Event Listeners (CameraHelper) ====================

    override fun onStateChange(state: AutoCaptureAnalyzer.CaptureState, message: String) {
        reactApplicationContext.runOnUiQueueThread {
             emitStateChange(state, message)
        }
    }

    override fun onQualityUpdate(metrics: AutoCaptureAnalyzer.QualityMetrics) {
        reactApplicationContext.runOnUiQueueThread {
             emitQualityUpdate(metrics)
        }
    }

    override fun onCaptured(result: AutoCaptureAnalyzer.CaptureResult, isBackSide: Boolean) {
        reactApplicationContext.runOnUiQueueThread {
             emitCaptured(result, isBackSide)
        }
    }
    
    override fun onError(code: String, message: String) {
        reactApplicationContext.runOnUiQueueThread {
             emitError(code, message)
        }
    }

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
        cameraHelper?.release()
    }

    @ReactMethod
    fun release(promise: Promise) {
        try {
            cameraHelper?.release()
            isInitialized = false
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("RELEASE_ERROR", "Failed to release: ${e.message}", e)
        }
    }
}
