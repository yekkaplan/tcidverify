package com.idverify.sdk.core

import android.graphics.Bitmap
import android.util.Log

/**
 * JNI Bridge to C++ Native Implementation
 * 
 * Vision-First Pipeline v2.0
 * - High-performance image processing with OpenCV
 * - Card detection and perspective correction
 * - Adaptive binarization for OCR
 * - ICAO 9303 MRZ validation with scoring
 */
object NativeProcessor {
    
    private const val TAG = "NativeProcessor"
    
    private var isLoaded = false
    
    init {
        try {
            System.loadLibrary("idverify-native")
            isLoaded = true
            Log.d(TAG, "Native library loaded: ${stringFromJNI()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            isLoaded = false
        }
    }
    
    /**
     * Check if native library is loaded
     */
    fun isAvailable(): Boolean = isLoaded

    // ==================== Core Functions ====================
    
    /**
     * Test function to verify JNI link
     */
    external fun stringFromJNI(): String
    
    // ==================== NEW: Vision-First Pipeline Functions ====================
    
    /**
     * Process image for optimal OCR
     * 
     * Applies Vision-First pipeline:
     * 1. Card corner detection
     * 2. Perspective warp to ID-1 standard (856x540)
     * 3. Adaptive binarization (hologram/glare removal)
     * 
     * @param bitmap Input camera frame (ARGB_8888)
     * @return Processed bitmap ready for OCR, or null if card not detected
     */
    external fun processImageForOCR(bitmap: Bitmap): Bitmap?
    
    /**
     * Extract MRZ region from image
     * 
     * Applies Vision-First pipeline and crops bottom 25-30% (MRZ area)
     * 
     * @param bitmap Input camera frame
     * @return Cropped and binarized MRZ region, or null if failed
     */
    external fun extractMRZRegion(bitmap: Bitmap): Bitmap?
    
    /**
     * Validate MRZ with detailed scoring
     * 
     * ICAO 9303 7-3-1 checksum validation with OCR error correction
     * 
     * Scoring (0-60 points):
     * - Document number checksum: 15 pts
     * - Birth date checksum: 15 pts
     * - Expiry date checksum: 15 pts
     * - Composite checksum: 15 pts
     * 
     * @param line1 First MRZ line (30 chars for TD1)
     * @param line2 Second MRZ line (30 chars)
     * @param line3 Third MRZ line (30 chars)
     * @return Total score 0-60
     */
    external fun validateMRZWithScore(line1: String, line2: String, line3: String): Int
    
    /**
     * Detect glare/reflection level in image
     * 
     * @param bitmap Input image
     * @return Glare score 0-100 (lower is better, <30 is acceptable)
     */
    external fun detectGlare(bitmap: Bitmap): Int
    
    /**
     * Validate TCKN using native implementation
     * @param tckn 11-digit Turkish ID number
     * @return true if valid
     */
    external fun validateTCKNNative(tckn: String): Boolean
    
    /**
     * Get card detection confidence
     * @param bitmap Input image
     * @return Confidence 0-100
     */
    external fun getCardConfidence(bitmap: Bitmap): Int
    
    // ==================== Auto-Capture Pipeline Functions ====================
    
    /**
     * ROI Types for extraction
     */
    object ROIType {
        const val TCKN = 0      // 11-digit Turkish ID number
        const val SURNAME = 1   // Surname field
        const val NAME = 2      // Name field
        const val MRZ = 3       // Machine Readable Zone
        const val PHOTO = 4     // ID Photo
        const val SERIAL = 5    // Serial number
        const val BIRTHDATE = 6 // Birth date
    }
    
    /**
     * Extract specific ROI from warped card with optimized preprocessing
     * @param bitmap Warped 856x540 card image
     * @param roiType ROI type from [ROIType]
     * @param isBackSide True if processing back side
     * @return Preprocessed ROI ready for OCR
     */
    external fun extractROI(bitmap: Bitmap, roiType: Int, isBackSide: Boolean): Bitmap?
    
    /**
     * Calculate blur/sharpness score using Laplacian variance
     * @param bitmap Input image
     * @return Blur score (higher = sharper, threshold ~100)
     */
    external fun calculateBlurScore(bitmap: Bitmap): Float
    
    /**
     * Calculate frame stability (difference from previous frame)
     * @param current Current frame bitmap
     * @param previous Previous frame bitmap
     * @return Stability score 0-1 (higher = more stable)
     */
    external fun calculateStability(current: Bitmap, previous: Bitmap): Float
    
    /**
     * Warp and normalize card to ID-1 standard (856x540)
     * @param bitmap Raw camera frame
     * @return Warped bitmap or null if card not detected
     */
    external fun warpToID1(bitmap: Bitmap): Bitmap?
    
    // ==================== Kotlin Helper Functions ====================
    
    /**
     * Check if image quality is acceptable for OCR
     * @param bitmap Input image
     * @return true if glare level is acceptable
     */
    fun isQualityAcceptable(bitmap: Bitmap): Boolean {
        if (!isLoaded) return false
        val glare = detectGlare(bitmap)
        return glare < 30
    }
    
    /**
     * Check if image is sharp enough for OCR
     * @param bitmap Input image
     * @return true if blur score > 100 (sharp)
     */
    fun isSharpEnough(bitmap: Bitmap): Boolean {
        if (!isLoaded) return false
        return calculateBlurScore(bitmap) > 100f
    }
    
    /**
     * Full Vision-First processing with validation
     * @param bitmap Input camera frame
     * @param mrzLines Extracted MRZ lines from OCR
     * @return Validation score 0-60
     */
    fun validateMRZ(mrzLines: List<String>): Int {
        if (!isLoaded || mrzLines.size < 2) return 0
        
        val line1 = mrzLines.getOrElse(0) { "" }
        val line2 = mrzLines.getOrElse(1) { "" }
        val line3 = mrzLines.getOrElse(2) { "" }
        
        return validateMRZWithScore(line1, line2, line3)
    }
}

