package com.idverify.sdk.detection

import android.graphics.Bitmap
import com.idverify.sdk.decision.ValidationError

/**
 * Quality Gate - Pre-OCR Image Quality Validation
 * 
 * All quality checks must pass BEFORE any OCR or MRZ analysis runs.
 * This is a critical gate to avoid wasting CPU on unusable frames.
 * 
 * Quality Criteria (TC_ID_SPEC.md based):
 * - Blur: Laplacian Variance > 100
 * - Glare: Bright pixels < 5% of card area
 * - Darkness: Mean luminance > 30
 * - Motion: (detected via blur threshold)
 * - Perspective: (requires edge detection)
 */
object QualityGate {
    
    /** Laplacian variance threshold for sharpness */
    const val MIN_LAPLACIAN_VARIANCE = 100.0
    
    /** Mean luminance range */
    const val MIN_LUMINANCE = 30
    const val MAX_LUMINANCE = 240
    
    /** Maximum bright pixel percentage (glare) */
    const val MAX_GLARE_PERCENT = 5.0f
    
    /** Bright pixel threshold */
    const val BRIGHT_PIXEL_THRESHOLD = 250
    
    /**
     * Quality assessment result
     */
    data class QualityResult(
        val passed: Boolean,
        val blurScore: Float,          // 0-1 (1 = sharp)
        val glareScore: Float,         // 0-1 (1 = no glare)
        val brightnessScore: Float,    // 0-1 (1 = optimal)
        val laplacianVariance: Double,
        val meanLuminance: Double,
        val glarePercent: Float,
        val errors: List<ValidationError>
    ) {
        val overallScore: Float get() = (blurScore + glareScore + brightnessScore) / 3f
    }
    
    /** Sample size for quality calculations (to save memory) */
    private const val SAMPLE_WIDTH = 320
    private const val SAMPLE_HEIGHT = 240
    
    /**
     * Perform full quality assessment
     * @param bitmap Image to assess
     * @return QualityResult with all scores
     */
    fun assess(bitmap: Bitmap): QualityResult {
        val errors = mutableListOf<ValidationError>()
        
        // Scale down for memory-efficient processing
        val sampleBitmap = if (bitmap.width > SAMPLE_WIDTH || bitmap.height > SAMPLE_HEIGHT) {
            Bitmap.createScaledBitmap(bitmap, SAMPLE_WIDTH, SAMPLE_HEIGHT, true)
        } else {
            bitmap
        }
        
        // 1. Calculate Laplacian variance (blur detection)
        val laplacianVariance = calculateLaplacianVariance(sampleBitmap)
        val blurScore = when {
            laplacianVariance >= MIN_LAPLACIAN_VARIANCE -> 1.0f
            laplacianVariance <= 0 -> 0.0f
            else -> (laplacianVariance / MIN_LAPLACIAN_VARIANCE).toFloat()
        }
        if (blurScore < 0.5f) {
            errors.add(ValidationError.IMAGE_TOO_BLURRY)
        }
        
        // 2 & 3. Calculate luminance and glare in single pass (memory efficient)
        val luminanceResult = calculateLuminanceAndGlare(sampleBitmap)
        val meanLuminance = luminanceResult.mean
        val glarePercent = luminanceResult.glarePercent
        
        val brightnessScore = when {
            meanLuminance < MIN_LUMINANCE -> (meanLuminance / MIN_LUMINANCE).toFloat()
            meanLuminance > MAX_LUMINANCE -> ((255 - meanLuminance) / (255 - MAX_LUMINANCE)).toFloat()
            else -> 1.0f
        }
        if (brightnessScore < 0.5f) {
            errors.add(ValidationError.LIGHTING_ISSUE)
        }
        val glareScore = when {
            glarePercent <= MAX_GLARE_PERCENT -> 1.0f
            glarePercent >= MAX_GLARE_PERCENT * 3 -> 0.0f
            else -> 1.0f - ((glarePercent - MAX_GLARE_PERCENT) / (MAX_GLARE_PERCENT * 2))
        }
        if (glareScore < 0.5f) {
            errors.add(ValidationError.LIGHTING_ISSUE)
        }
        
        // Recycle scaled bitmap if we created one
        if (sampleBitmap !== bitmap) {
            sampleBitmap.recycle()
        }
        
        // Gate passes if all individual scores are above threshold
        val passed = blurScore >= 0.5f && 
                    brightnessScore >= 0.5f && 
                    glareScore >= 0.5f
        
        return QualityResult(
            passed = passed,
            blurScore = blurScore,
            glareScore = glareScore,
            brightnessScore = brightnessScore,
            laplacianVariance = laplacianVariance,
            meanLuminance = meanLuminance,
            glarePercent = glarePercent,
            errors = errors
        )
    }
    
    /**
     * Quick check if quality is sufficient for OCR
     */
    fun passesGate(bitmap: Bitmap): Boolean {
        return assess(bitmap).passed
    }
    
    /**
     * Calculate Laplacian variance for blur detection
     * Higher = sharper image
     * 
     * Optimized: Uses Welford's online algorithm to calculate variance
     * without storing all Laplacian values (saves memory)
     */
    private fun calculateLaplacianVariance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width < 3 || height < 3) return 0.0
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Welford's online algorithm for variance
        var count = 0L
        var mean = 0.0
        var m2 = 0.0
        
        // Process in a single pass without storing intermediate values
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                // Get grayscale values inline
                val centerPixel = pixels[y * width + x]
                val topPixel = pixels[(y - 1) * width + x]
                val bottomPixel = pixels[(y + 1) * width + x]
                val leftPixel = pixels[y * width + (x - 1)]
                val rightPixel = pixels[y * width + (x + 1)]
                
                // Convert to grayscale inline
                fun toGray(p: Int): Int {
                    val r = (p shr 16) and 0xff
                    val g = (p shr 8) and 0xff
                    val b = p and 0xff
                    return ((0.299 * r + 0.587 * g + 0.114 * b).toInt())
                }
                
                val center = toGray(centerPixel)
                val top = toGray(topPixel)
                val bottom = toGray(bottomPixel)
                val left = toGray(leftPixel)
                val right = toGray(rightPixel)
                
                // Apply Laplacian kernel
                val lap = (-4 * center + top + bottom + left + right).toDouble()
                
                // Welford's algorithm update
                count++
                val delta = lap - mean
                mean += delta / count
                val delta2 = lap - mean
                m2 += delta * delta2
            }
        }
        
        return if (count > 1) m2 / count else 0.0
    }
    
    /**
     * Calculate mean luminance and glare in a single pass (memory efficient)
     */
    private data class LuminanceResult(val mean: Double, val glarePercent: Float)
    
    private fun calculateLuminanceAndGlare(bitmap: Bitmap): LuminanceResult {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        
        if (totalPixels == 0) return LuminanceResult(0.0, 0f)
        
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var sum = 0.0
        var brightCount = 0
        
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            
            sum += luminance
            if (luminance >= BRIGHT_PIXEL_THRESHOLD) {
                brightCount++
            }
        }
        
        return LuminanceResult(
            mean = sum / totalPixels,
            glarePercent = (brightCount.toFloat() / totalPixels) * 100f
        )
    }
    
    /**
     * Calculate mean luminance
     */
    private fun calculateMeanLuminance(bitmap: Bitmap): Double {
        return calculateLuminanceAndGlare(bitmap).mean
    }
    
    /**
     * Calculate percentage of bright pixels (glare detection)
     */
    private fun calculateGlarePercent(bitmap: Bitmap): Float {
        return calculateLuminanceAndGlare(bitmap).glarePercent
    }
}
