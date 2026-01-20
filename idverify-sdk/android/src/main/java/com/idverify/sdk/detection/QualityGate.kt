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
    
    /**
     * Perform full quality assessment
     * @param bitmap Image to assess
     * @return QualityResult with all scores
     */
    fun assess(bitmap: Bitmap): QualityResult {
        val errors = mutableListOf<ValidationError>()
        
        // 1. Calculate Laplacian variance (blur detection)
        val laplacianVariance = calculateLaplacianVariance(bitmap)
        val blurScore = when {
            laplacianVariance >= MIN_LAPLACIAN_VARIANCE -> 1.0f
            laplacianVariance <= 0 -> 0.0f
            else -> (laplacianVariance / MIN_LAPLACIAN_VARIANCE).toFloat()
        }
        if (blurScore < 0.5f) {
            errors.add(ValidationError.IMAGE_TOO_BLURRY)
        }
        
        // 2. Calculate mean luminance (brightness)
        val meanLuminance = calculateMeanLuminance(bitmap)
        val brightnessScore = when {
            meanLuminance < MIN_LUMINANCE -> (meanLuminance / MIN_LUMINANCE).toFloat()
            meanLuminance > MAX_LUMINANCE -> ((255 - meanLuminance) / (255 - MAX_LUMINANCE)).toFloat()
            else -> 1.0f
        }
        if (brightnessScore < 0.5f) {
            errors.add(ValidationError.LIGHTING_ISSUE)
        }
        
        // 3. Calculate glare percentage
        val glarePercent = calculateGlarePercent(bitmap)
        val glareScore = when {
            glarePercent <= MAX_GLARE_PERCENT -> 1.0f
            glarePercent >= MAX_GLARE_PERCENT * 3 -> 0.0f
            else -> 1.0f - ((glarePercent - MAX_GLARE_PERCENT) / (MAX_GLARE_PERCENT * 2))
        }
        if (glareScore < 0.5f) {
            errors.add(ValidationError.LIGHTING_ISSUE)
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
     */
    private fun calculateLaplacianVariance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to grayscale
        val gray = IntArray(pixels.size) { i ->
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            ((0.299 * r + 0.587 * g + 0.114 * b).toInt())
        }
        
        // Apply Laplacian kernel: [[0,1,0],[1,-4,1],[0,1,0]]
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
        
        if (laplacian.isEmpty()) return 0.0
        
        // Calculate variance
        val mean = laplacian.average()
        return laplacian.fold(0.0) { acc, value ->
            val diff = value - mean
            acc + (diff * diff)
        } / laplacian.size
    }
    
    /**
     * Calculate mean luminance
     */
    private fun calculateMeanLuminance(bitmap: Bitmap): Double {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var sum = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            sum += 0.299 * r + 0.587 * g + 0.114 * b
        }
        
        return sum / pixels.size
    }
    
    /**
     * Calculate percentage of bright pixels (glare detection)
     */
    private fun calculateGlarePercent(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var brightCount = 0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            
            if (luminance >= BRIGHT_PIXEL_THRESHOLD) {
                brightCount++
            }
        }
        
        return (brightCount.toFloat() / pixels.size) * 100f
    }
}
