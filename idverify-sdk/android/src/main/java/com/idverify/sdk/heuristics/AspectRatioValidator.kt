package com.idverify.sdk.heuristics

import android.graphics.Bitmap
import com.idverify.sdk.utils.Constants

/**
 * Validates ID card aspect ratio against ID-1 format (ISO/IEC 7810)
 */
class AspectRatioValidator {
    
    /**
     * Check if the bitmap has valid ID-1 aspect ratio
     * @param bitmap Image to validate
     * @return true if aspect ratio matches ID-1 format within tolerance
     */
    fun isValidAspectRatio(bitmap: Bitmap): Boolean {
        val actualRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val expectedRatio = Constants.CardDimensions.ASPECT_RATIO
        val tolerance = Constants.CardDimensions.ASPECT_RATIO_TOLERANCE
        
        val minRatio = expectedRatio * (1 - tolerance)
        val maxRatio = expectedRatio * (1 + tolerance)
        
        return actualRatio in minRatio..maxRatio
    }
    
    /**
     * Calculate aspect ratio score
     * @param bitmap Image to analyze
     * @return Score: 0.0 (invalid ratio) to 1.0 (perfect match)
     */
    fun calculateAspectRatioScore(bitmap: Bitmap): Float {
        val actualRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val expectedRatio = Constants.CardDimensions.ASPECT_RATIO
        
        val difference = kotlin.math.abs(actualRatio - expectedRatio)
        val maxDifference = expectedRatio * Constants.CardDimensions.ASPECT_RATIO_TOLERANCE
        
        return when {
            difference >= maxDifference -> 0.0f
            else -> (1.0f - (difference / maxDifference)).coerceIn(0.0f, 1.0f)
        }
    }
}
