package com.idverify.sdk.heuristics

import android.graphics.Bitmap
import com.idverify.sdk.utils.Constants

/**
 * Validates ID card aspect ratio against ID-1 format (ISO/IEC 7810)
 */
class AspectRatioValidator {
    
    /**
     * Check if the bitmap has valid ID-1 aspect ratio (TC_ID_SPEC.md)
     * @param bitmap Image to validate
     * @return true if aspect ratio matches ID-1 format within tolerance (1.50-1.65)
     */
    fun isValidAspectRatio(bitmap: Bitmap): Boolean {
        val actualRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val minRatio = Constants.CardDimensions.ASPECT_RATIO_MIN  // 1.50
        val maxRatio = Constants.CardDimensions.ASPECT_RATIO_MAX  // 1.65
        
        return actualRatio in minRatio..maxRatio
    }
    
    /**
     * Calculate aspect ratio score (TC_ID_SPEC.md based)
     * @param bitmap Image to analyze
     * @return Score: 0.0 (invalid ratio) to 1.0 (perfect match at 1.5858)
     */
    fun calculateAspectRatioScore(bitmap: Bitmap): Float {
        val actualRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val expectedRatio = Constants.CardDimensions.ASPECT_RATIO  // 1.5858
        val minRatio = Constants.CardDimensions.ASPECT_RATIO_MIN     // 1.50
        val maxRatio = Constants.CardDimensions.ASPECT_RATIO_MAX     // 1.65
        
        // Check if within acceptable range
        if (actualRatio < minRatio || actualRatio > maxRatio) {
            return 0.0f
        }
        
        // Calculate score based on distance from ideal (1.5858)
        val difference = kotlin.math.abs(actualRatio - expectedRatio)
        val maxDifference = (maxRatio - minRatio) / 2f  // Half the tolerance range
        
        // Score: 1.0 at perfect match, decreasing linearly to 0.5 at edges
        val score = 1.0f - (difference / maxDifference * 0.5f)
        return score.coerceIn(0.0f, 1.0f)
    }
}
