package com.idverify.sdk.heuristics

import android.graphics.Bitmap
import com.idverify.sdk.utils.Constants
import com.idverify.sdk.utils.ImageUtils.toGrayscale

/**
 * Detects image blur using Laplacian variance method
 * 
 * The Laplacian operator highlights edges in an image. 
 * A sharp image has high variance in edge detection,
 * while a blurry image has low variance.
 */
class BlurDetector {
    
    /**
     * Calculate blur score for the given bitmap
     * @param bitmap Image to analyze
     * @return Blur score: 0.0 (very blurry) to 1.0 (sharp)
     */
    fun detectBlur(bitmap: Bitmap): Float {
        val grayscale = bitmap.toGrayscale()
        val laplacianVariance = calculateLaplacianVariance(grayscale)
        
        // Normalize variance to 0.0-1.0 score
        // Values > MIN_LAPLACIAN_VARIANCE are considered sharp
        return when {
            laplacianVariance >= Constants.Quality.MIN_LAPLACIAN_VARIANCE -> 1.0f
            laplacianVariance <= 0.0 -> 0.0f
            else -> (laplacianVariance / Constants.Quality.MIN_LAPLACIAN_VARIANCE).toFloat()
        }
    }
    
    /**
     * Calculate Laplacian variance
     * Higher variance = sharper image
     */
    private fun calculateLaplacianVariance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to grayscale values
        val gray = IntArray(pixels.size) { i ->
            pixels[i] and 0xff  // Already grayscale, just extract value
        }
        
        // Apply Laplacian kernel: [[0, 1, 0], [1, -4, 1], [0, 1, 0]]
        val laplacian = mutableListOf<Int>()
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = gray[y * width + x]
                val top = gray[(y - 1) * width + x]
                val bottom = gray[(y + 1) * width + x]
                val left = gray[y * width + (x - 1)]
                val right = gray[y * width + (x + 1)]
                
                val lap = -4 * center + top + bottom + left + right
                laplacian.add(lap)
            }
        }
        
        // Calculate variance
        if (laplacian.isEmpty()) return 0.0
        
        val mean = laplacian.average()
        val variance = laplacian.fold(0.0) { acc, value ->
            val diff = value - mean
            acc + (diff * diff)
        } / laplacian.size
        
        return variance
    }
}
