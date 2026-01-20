package com.idverify.sdk.heuristics

import android.graphics.Bitmap
import com.idverify.sdk.utils.Constants
import com.idverify.sdk.utils.ImageUtils.meanLuminance

/**
 * Detects glare and lighting issues using luminance analysis
 */
class GlareDetector {
    
    /**
     * Calculate glare score for the given bitmap
     * @param bitmap Image to analyze
     * @return Glare score: 0.0 (heavy glare/poor lighting) to 1.0 (good lighting)
     */
    fun detectGlare(bitmap: Bitmap): Float {
        val meanLuminance = bitmap.meanLuminance()
        
        return when {
            // Overexposed (glare)
            meanLuminance > Constants.Quality.MAX_MEAN_LUMINANCE -> {
                val excess = meanLuminance - Constants.Quality.MAX_MEAN_LUMINANCE
                val penalty = (excess / 15.0).coerceAtMost(1.0)  // Max penalty at 255
                (1.0 - penalty).toFloat().coerceAtLeast(0.0f)
            }
            
            // Underexposed (too dark)
            meanLuminance < Constants.Quality.MIN_MEAN_LUMINANCE -> {
                (meanLuminance / Constants.Quality.MIN_MEAN_LUMINANCE).toFloat()
            }
            
            // Good lighting
            else -> 1.0f
        }
    }
    
    /**
     * Check if image has acceptable lighting
     */
    fun hasAcceptableLighting(bitmap: Bitmap): Boolean {
        return detectGlare(bitmap) >= Constants.Quality.MIN_GLARE_SCORE
    }
}
