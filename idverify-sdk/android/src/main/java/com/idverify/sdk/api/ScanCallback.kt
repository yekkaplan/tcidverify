package com.idverify.sdk.api

import com.idverify.sdk.api.models.ScanError
import com.idverify.sdk.api.models.ScanResult
import com.idverify.sdk.api.models.ScanStatus

/**
 * Callback interface for ID scanner events
 */
interface ScanCallback {
    /**
     * Called when the scan status changes
     * @param status Current scan status
     * @param progress Progress value between 0.0 and 1.0
     * @param message Optional status message for user feedback
     */
    fun onStatusChanged(status: ScanStatus, progress: Float = 0f, message: String? = null)
    
    /**
     * Called when front side is captured
     * @param imageBytes Front image JPEG bytes
     * @param qualityScore Image quality score (0.0 - 1.0)
     */
    fun onFrontCaptured(imageBytes: ByteArray, qualityScore: Float)
    
    /**
     * Called when back side is captured
     * @param imageBytes Back image JPEG bytes
     * @param qualityScore Image quality score (0.0 - 1.0)
     */
    fun onBackCaptured(imageBytes: ByteArray, qualityScore: Float)
    
    /**
     * Called when scanning is completed successfully
     * @param result Complete scan result with all data
     */
    fun onCompleted(result: ScanResult)
    
    /**
     * Called when an error occurs during scanning
     * @param error Error details
     */
    fun onError(error: ScanError)
}
