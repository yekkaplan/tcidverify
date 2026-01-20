package com.idverify.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.idverify.sdk.api.IDScanner
import com.idverify.sdk.api.ScanCallback
import com.idverify.sdk.api.models.ScanError
import com.idverify.sdk.api.models.ScanResult
import com.idverify.sdk.api.models.ScanStatus

/**
 * React Native Native Module for ID Scanner SDK
 * Exposes SDK functionality to JavaScript layer
 */
class IDScannerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    
    private var scanner: IDScanner? = null
    private val scanCallback = object : ScanCallback {
        override fun onStatusChanged(status: ScanStatus, progress: Float, message: String?) {
            sendEvent(
                Events.STATUS_CHANGED,
                DataMapper.scanStatusToMap(status, progress, message)
            )
        }
        
        override fun onFrontCaptured(imageBytes: ByteArray, qualityScore: Float) {
            sendEvent(
                Events.FRONT_CAPTURED,
                DataMapper.imageToMap(imageBytes, qualityScore)
            )
        }
        
        override fun onBackCaptured(imageBytes: ByteArray, qualityScore: Float) {
            sendEvent(
                Events.BACK_CAPTURED,
                DataMapper.imageToMap(imageBytes, qualityScore)
            )
        }
        
        override fun onCompleted(result: ScanResult) {
            sendEvent(
                Events.SCAN_COMPLETED,
                DataMapper.scanResultToMap(result, useBase64 = true)
            )
        }
        
        override fun onError(error: ScanError) {
            sendEvent(
                Events.ERROR_OCCURRED,
                DataMapper.errorToMap(error.code, error.message, error.details)
            )
        }
    }
    
    override fun getName(): String = MODULE_NAME
    
    /**
     * Check if camera permission is granted
     */
    @ReactMethod
    fun hasCameraPermission(promise: Promise) {
        try {
            val hasPermission = getScanner().hasCameraPermission()
            promise.resolve(hasPermission)
        } catch (e: Exception) {
            promise.reject("PERMISSION_CHECK_FAILED", e.message, e)
        }
    }
    
    /**
     * Start scanning process
     * Note: This should be called after View Manager has created the preview view
     */
    @ReactMethod
    fun startScanning(promise: Promise) {
        try {
            // The actual camera binding happens in View Manager
            // This just initializes the scanner state
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("START_SCAN_FAILED", e.message, e)
        }
    }
    
    /**
     * Stop scanning process
     */
    @ReactMethod
    fun stopScanning(promise: Promise) {
        try {
            scanner?.stopScanning()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("STOP_SCAN_FAILED", e.message, e)
        }
    }
    
    /**
     * Reset scanner state
     */
    @ReactMethod
    fun reset(promise: Promise) {
        try {
            scanner?.reset()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("RESET_FAILED", e.message, e)
        }
    }
    
    /**
     * Release scanner resources
     */
    @ReactMethod
    fun release(promise: Promise) {
        try {
            scanner?.release()
            scanner = null
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("RELEASE_FAILED", e.message, e)
        }
    }
    
    /**
     * Get scanner instance (lazily initialized)
     */
    internal fun getScanner(): IDScanner {
        if (scanner == null) {
            scanner = IDScanner.create(reactApplicationContext)
        }
        return scanner!!
    }
    
    /**
     * Get scan callback
     */
    internal fun getScanCallback(): ScanCallback = scanCallback
    
    /**
     * Send event to JavaScript
     */
    private fun sendEvent(eventName: String, params: Any?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }
    
    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        scanner?.release()
        scanner = null
    }
    
    companion object {
        const val MODULE_NAME = "IDScannerModule"
    }
    
    /**
     * Event names emitted to JavaScript
     */
    object Events {
        const val STATUS_CHANGED = "IDScanner.StatusChanged"
        const val FRONT_CAPTURED = "IDScanner.FrontCaptured"
        const val BACK_CAPTURED = "IDScanner.BackCaptured"
        const val SCAN_COMPLETED = "IDScanner.ScanCompleted"
        const val ERROR_OCCURRED = "IDScanner.Error"
    }
}
