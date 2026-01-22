package com.idverify.sdk.utils

import android.util.Log
import com.idverify.sdk.decision.DecisionResult
import com.idverify.sdk.detection.QualityGate

/**
 * Debug logger for ID verification troubleshooting
 * Enable/disable via DEBUG_ENABLED flag
 */
object DebugLogger {
    
    private const val TAG_QUALITY = "IDVerify_Quality"
    private const val TAG_DECISION = "IDVerify_Decision"
    private const val TAG_MRZ = "IDVerify_MRZ"
    private const val TAG_OCR = "IDVerify_OCR"
    
    var DEBUG_ENABLED = true  // TODO: Make configurable via SDK config
    
    /**
     * Log quality gate assessment results
     */
    fun logQualityResult(result: QualityGate.QualityResult, side: String = "Unknown") {
        if (!DEBUG_ENABLED) return
        
        Log.d(TAG_QUALITY, """
            |═══════════════════════════════════════
            |Quality Assessment - $side Side
            |═══════════════════════════════════════
            |✓ Passed: ${if (result.passed) "YES ✓" else "NO ✗"}
            |
            |Scores:
            |  • Blur Score:       ${result.blurScore.fmt()} ${threshold(result.blurScore, Constants.Quality.MIN_BLUR_SCORE)}
            |  • Glare Score:      ${result.glareScore.fmt()} ${threshold(result.glareScore, Constants.Quality.MIN_GLARE_SCORE)}
            |  • Brightness Score: ${result.brightnessScore.fmt()}
            |  • Overall Score:    ${result.overallScore.fmt()}
            |
            |Raw Metrics:
            |  • Laplacian Var: ${result.laplacianVariance.toInt()} (min: ${Constants.Quality.MIN_LAPLACIAN_VARIANCE.toInt()})
            |  • Mean Luminance: ${result.meanLuminance.toInt()} (range: ${Constants.Quality.MIN_LUMINANCE}-${Constants.Quality.MAX_LUMINANCE})
            |  • Glare %: ${result.glarePercent.fmt()}% ${threshold(result.glarePercent, Constants.Quality.MAX_GLARE_AREA_PERCENT, inverse = true)}
            |
            |Errors: ${if (result.errors.isEmpty()) "None" else result.errors.joinToString()}
            |═══════════════════════════════════════
        """.trimMargin())
    }
    
    /**
     * Log decision result with score breakdown
     */
    fun logDecisionResult(result: DecisionResult, side: String = "Unknown") {
        if (!DEBUG_ENABLED) return
        
        val breakdown = result.scoreBreakdown
        
        Log.d(TAG_DECISION, """
            |═══════════════════════════════════════
            |Decision Result - $side Side
            |═══════════════════════════════════════
            |✓ Total Score: ${result.totalScore}/100 ${scoreIndicator(result.totalScore)}
            |  Decision: ${result.decision}
            |
            |Score Breakdown:
            |  • Aspect Ratio:  ${breakdown.aspectRatioScore.toString().padStart(2)}/20 ${bar(breakdown.aspectRatioScore, 20)}
            |  • Front Text:    ${breakdown.frontTextScore.toString().padStart(2)}/20 ${bar(breakdown.frontTextScore, 20)}
            |  • MRZ Structure: ${breakdown.mrzStructureScore.toString().padStart(2)}/20 ${bar(breakdown.mrzStructureScore, 20)}
            |  • MRZ Checksum:  ${breakdown.mrzChecksumScore.toString().padStart(2)}/30 ${bar(breakdown.mrzChecksumScore, 30)}
            |  • TCKN Algorithm:${breakdown.tcknAlgorithmScore.toString().padStart(2)}/10 ${bar(breakdown.tcknAlgorithmScore, 10)}
            |
            |Validation Errors (${result.errors.size}):
            |${if (result.errors.isEmpty()) "  None ✓" else result.errors.joinToString("\n") { "  • $it" }}
            |═══════════════════════════════════════
        """.trimMargin())
    }
    
    /**
     * Log MRZ extraction attempt
     */
    fun logMRZExtraction(rawText: String, parsedLines: List<String>) {
        if (!DEBUG_ENABLED) return
        
        Log.d(TAG_MRZ, """
            |═══════════════════════════════════════
            |MRZ Extraction
            |═══════════════════════════════════════
            |Raw Text Length: ${rawText.length} chars
            |Parsed Lines: ${parsedLines.size}
            |
            |Expected Format: TD-1 (3 lines × 30 chars)
            |
            |Line 1 [${parsedLines.getOrNull(0)?.length ?: 0} chars]: '${parsedLines.getOrNull(0)?.take(30) ?: "N/A"}'
            |Line 2 [${parsedLines.getOrNull(1)?.length ?: 0} chars]: '${parsedLines.getOrNull(1)?.take(30) ?: "N/A"}'
            |Line 3 [${parsedLines.getOrNull(2)?.length ?: 0} chars]: '${parsedLines.getOrNull(2)?.take(30) ?: "N/A"}'
            |
            |Validation: ${
                if (parsedLines.size == 3 && parsedLines.all { it.length == 30 }) 
                    "✓ Format OK" 
                else 
                    "✗ Format INVALID"
            }
            |═══════════════════════════════════════
        """.trimMargin())
    }
    
    /**
     * Log OCR text recognition results
     */
    fun logOCRResult(text: String, confidence: Float, side: String = "Unknown") {
        if (!DEBUG_ENABLED) return
        
        Log.d(TAG_OCR, """
            |OCR Result - $side Side:
            |  Text Length: ${text.length} chars
            |  Confidence: ${confidence.fmt()} ${threshold(confidence, Constants.Quality.MIN_OCR_CONFIDENCE)}
            |  Preview: ${text.take(100).replace("\n", "\\n")}
        """.trimMargin())
    }
    
    // Helper functions for formatting
    
    private fun Float.fmt() = "%.2f".format(this)
    
    private fun threshold(value: Float, min: Float, inverse: Boolean = false): String {
        val passes = if (inverse) value <= min else value >= min
        return "(min: ${min.fmt()}) ${if (passes) "✓" else "✗"}"
    }
    
    private fun scoreIndicator(score: Int): String = when {
        score >= Constants.Scoring.THRESHOLD_VALID -> "✓ VALID"
        score >= Constants.Scoring.THRESHOLD_RETRY -> "⚠ RETRY"
        else -> "✗ INVALID"
    }
    
    private fun bar(score: Int, max: Int): String {
        val percentage = (score.toFloat() / max * 100).toInt()
        val filled = percentage / 10
        val empty = 10 - filled
        return "[${"█".repeat(filled)}${"░".repeat(empty)}] $percentage%"
    }
}
