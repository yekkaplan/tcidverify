package com.idverify.sdk.heuristics

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * Main analyzer for ID card image quality and authenticity
 * Aggregates results from blur, glare, and aspect ratio checks
 */
class IDAnalyzer {
    
    private val blurDetector = BlurDetector()
    private val glareDetector = GlareDetector()
    private val aspectRatioValidator = AspectRatioValidator()
    
    /**
     * Analysis result containing individual scores
     */
    data class AnalysisResult(
        val blurScore: Float,           // 0.0 - 1.0
        val glareScore: Float,          // 0.0 - 1.0
        val aspectRatioScore: Float,    // 0.0 - 1.0
        val overallScore: Float,        // Weighted average
        val isAcceptable: Boolean       // True if meets minimum thresholds
    )
    
    /**
     * Analyze image quality for ID card detection
     * @param bitmap Image to analyze
     * @return Analysis result with quality scores
     */
    fun analyze(bitmap: Bitmap): AnalysisResult {
        val blurScore = blurDetector.detectBlur(bitmap)
        val glareScore = glareDetector.detectGlare(bitmap)
        val aspectRatioScore = aspectRatioValidator.calculateAspectRatioScore(bitmap)
        
        // Calculate weighted overall score
        // Blur and glare are more important than aspect ratio
        val overallScore = (
            blurScore * 0.4f +
            glareScore * 0.4f +
            aspectRatioScore * 0.2f
        )
        
        val isAcceptable = blurScore >= com.idverify.sdk.utils.Constants.Quality.MIN_BLUR_SCORE &&
                          glareScore >= com.idverify.sdk.utils.Constants.Quality.MIN_GLARE_SCORE
        
        return AnalysisResult(
            blurScore = blurScore,
            glareScore = glareScore,
            aspectRatioScore = aspectRatioScore,
            overallScore = overallScore,
            isAcceptable = isAcceptable
        )
    }
    
    /**
     * Convenience method to analyze ImageProxy directly
     */
    fun analyze(imageProxy: ImageProxy): AnalysisResult {
        val bitmap = com.idverify.sdk.utils.ImageUtils.run {
            imageProxy.toBitmap()
        }
        return analyze(bitmap)
    }
    
    /**
     * Quick check if image quality is acceptable
     */
    fun isQualityAcceptable(bitmap: Bitmap): Boolean {
        return analyze(bitmap).isAcceptable
    }
}
