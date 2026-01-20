package com.idverify.sdk.validation

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Aspect Ratio Validator for ID-1 Cards
 * 
 * ISO/IEC 7810 ID-1 Standard:
 * - Width: 85.60mm
 * - Height: 53.98mm  
 * - Ideal Ratio: 1.5858
 * - Acceptance Range: 1.55 - 1.62 (strict for OCR gate)
 * 
 * OCR should NOT run if aspect ratio is outside tolerance
 */
object AspectRatioValidator {
    
    /** ID-1 Card ideal aspect ratio */
    const val IDEAL_RATIO = 1.5858f
    
    /** Strict tolerance for OCR gate (1.55 - 1.62) */
    const val MIN_RATIO_STRICT = 1.55f
    const val MAX_RATIO_STRICT = 1.62f
    
    /** Loose tolerance for initial detection (1.50 - 1.65) - TC_ID_SPEC */
    const val MIN_RATIO_LOOSE = 1.50f
    const val MAX_RATIO_LOOSE = 1.65f
    
    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val measuredRatio: Float,
        val deviation: Float,
        val score: Int // 0-20 points
    )
    
    /**
     * Validate aspect ratio from detected card bounds
     * Uses STRICT tolerance (1.55 - 1.62)
     * 
     * @param width Detected card width in pixels
     * @param height Detected card height in pixels
     * @return ValidationResult with score (0-20 points)
     */
    fun validateStrict(width: Int, height: Int): ValidationResult {
        if (width <= 0 || height <= 0) {
            return ValidationResult(
                isValid = false,
                measuredRatio = 0f,
                deviation = 1f,
                score = 0
            )
        }
        
        val ratio = width.toFloat() / height.toFloat()
        val deviation = kotlin.math.abs(ratio - IDEAL_RATIO) / IDEAL_RATIO
        
        val isValid = ratio in MIN_RATIO_STRICT..MAX_RATIO_STRICT
        
        // Score calculation: 
        // Perfect ratio = 20 points
        // At tolerance edge = 10 points
        // Outside tolerance = 0 points
        val score = when {
            !isValid -> 0
            deviation <= 0.01f -> 20  // Within 1% of ideal
            deviation <= 0.02f -> 18  // Within 2%
            deviation <= 0.03f -> 15  // Within 3%
            deviation <= 0.04f -> 12  // Within 4%
            else -> 10                // At tolerance edge
        }
        
        return ValidationResult(
            isValid = isValid,
            measuredRatio = ratio,
            deviation = deviation,
            score = score
        )
    }
    
    /**
     * Validate aspect ratio with loose tolerance (for initial detection)
     */
    fun validateLoose(width: Int, height: Int): ValidationResult {
        if (width <= 0 || height <= 0) {
            return ValidationResult(
                isValid = false,
                measuredRatio = 0f,
                deviation = 1f,
                score = 0
            )
        }
        
        val ratio = width.toFloat() / height.toFloat()
        val deviation = kotlin.math.abs(ratio - IDEAL_RATIO) / IDEAL_RATIO
        val isValid = ratio in MIN_RATIO_LOOSE..MAX_RATIO_LOOSE
        
        return ValidationResult(
            isValid = isValid,
            measuredRatio = ratio,
            deviation = deviation,
            score = if (isValid) calculateScore(deviation) else 0
        )
    }
    
    /**
     * Validate from bitmap dimensions
     * Note: This validates the image frame, not the card itself
     * For accurate validation, card bounds must be detected first
     */
    fun validateFromBitmap(bitmap: Bitmap): ValidationResult {
        return validateLoose(bitmap.width, bitmap.height)
    }
    
    /**
     * Validate from detected card rectangle
     */
    fun validateFromRect(rect: RectF): ValidationResult {
        return validateStrict(rect.width().toInt(), rect.height().toInt())
    }
    
    /**
     * Check if OCR should proceed based on aspect ratio
     * Uses STRICT validation
     */
    fun shouldProceedWithOCR(width: Int, height: Int): Boolean {
        return validateStrict(width, height).isValid
    }
    
    /**
     * Calculate score based on deviation
     */
    private fun calculateScore(deviation: Float): Int {
        return when {
            deviation <= 0.01f -> 20
            deviation <= 0.02f -> 18
            deviation <= 0.03f -> 15
            deviation <= 0.04f -> 12
            deviation <= 0.05f -> 10
            deviation <= 0.06f -> 8
            deviation <= 0.07f -> 5
            else -> 2
        }
    }
}
