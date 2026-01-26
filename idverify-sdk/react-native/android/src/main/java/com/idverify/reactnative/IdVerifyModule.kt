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
    fun startCamera(promise: Promise) {
        Log.d(TAG, "startCamera called from JS")
        CameraController.getInstance().start(promise)
    }

    @ReactMethod
    fun stopCamera(promise: Promise) {
        Log.d(TAG, "stopCamera called from JS")
        CameraController.getInstance().stop(promise)
    }

    /**
     * Set PreviewView for camera preview
     * DEPRECATED: handled by View internal logic
     */
    fun setPreviewView(view: PreviewView, activity: Activity) {
        // No-op
    }

    // ==================== Event Listeners (CameraHelper) ====================
    // Events now emitted directly by IdVerifyCameraView
    override fun onStateChange(state: AutoCaptureAnalyzer.CaptureState, message: String) {}
    override fun onQualityUpdate(metrics: AutoCaptureAnalyzer.QualityMetrics) {}
    override fun onCaptured(result: AutoCaptureAnalyzer.CaptureResult, isBackSide: Boolean) {}
    override fun onError(code: String, message: String) {}

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
