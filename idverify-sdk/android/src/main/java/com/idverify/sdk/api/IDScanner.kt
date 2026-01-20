package com.idverify.sdk.api

import android.content.Context
import androidx.camera.view.PreviewView

/**
 * Main entry point for ID Document Scanner SDK
 * 
 * Usage:
 * ```
 * val scanner = IDScanner(context)
 * scanner.startScanning(previewView, callback)
 * ```
 */
interface IDScanner {
    
    /**
     * Start the ID card scanning process
     * @param previewView CameraX PreviewView for camera preview
     * @param callback Callback to receive scanning events
     */
    fun startScanning(previewView: PreviewView, callback: ScanCallback)
    
    /**
     * Stop the current scanning session
     */
    fun stopScanning()
    
    /**
     * Reset the scanner to initial state
     * Clears all captured data from memory
     */
    fun reset()
    
    /**
     * Check if camera permission is granted
     * @return true if permission is granted
     */
    fun hasCameraPermission(): Boolean
    
    /**
     * Release all resources
     * Call this when the scanner is no longer needed
     */
    fun release()
    
    companion object {
        /**
         * Create a new IDScanner instance
         * @param context Android context
         * @return IDScanner implementation
         */
        fun create(context: Context): IDScanner {
            return com.idverify.sdk.core.ScannerEngine(context)
        }
    }
}
