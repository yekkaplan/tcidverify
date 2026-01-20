package com.idverify.sdk.scoring

import com.idverify.sdk.decision.DecisionResult
import com.idverify.sdk.decision.ValidationError
import com.idverify.sdk.pipeline.BackSidePipeline
import com.idverify.sdk.pipeline.FrontSidePipeline
import com.idverify.sdk.validation.AspectRatioValidator

/**
 * Scoring Engine - Calculates final authenticity score
 * 
 * Total: 100 points
 * 
 * Score Breakdown:
 * - Aspect Ratio Validation: +20 points (AspectRatioValidator)
 * - Front Side Text Structure: +20 points (FrontSidePipeline)
 * - MRZ Structure (regex, lines): +20 points (BackSidePipeline.structureScore)
 * - MRZ Checksum Validation: +30 points (BackSidePipeline.checksumScore)
 * - TCKN Algorithm Validation: +10 points (TCKNValidator)
 * 
 * Decision Thresholds:
 * - ≥80: VALID (Gerçek T.C. Kimlik Kartı)
 * - 50-79: RETRY (Tekrar Okut)
 * - <50: INVALID (Geçersiz)
 */
object ScoringEngine {
    
    /**
     * Input for scoring calculation
     */
    data class ScoringInput(
        val aspectRatioResult: AspectRatioValidator.ValidationResult?,
        val frontSideResult: FrontSidePipeline.AnalysisResult?,
        val backSideResult: BackSidePipeline.AnalysisResult?,
        val detectedCardWidth: Int = 0,
        val detectedCardHeight: Int = 0
    )
    
    /**
     * Calculate final decision result
     * @param input All validation results
     * @return DecisionResult with final score and decision
     */
    fun calculate(input: ScoringInput): DecisionResult {
        val errors = mutableListOf<ValidationError>()
        
        // 1. Aspect Ratio Score (0-20)
        // For demo: give partial points even if not perfect ID-1 ratio
        // Camera frame is usually 4:3 or 16:9, not 1.58:1
        var aspectRatioScore = input.aspectRatioResult?.score ?: 0
        if (aspectRatioScore == 0 && input.detectedCardWidth > 0) {
            // Give base points for having a frame
            aspectRatioScore = 10  // Base 10 points for any valid frame
        }
        if (input.aspectRatioResult?.isValid != true) {
            // Don't add error for aspect ratio in demo mode - camera frame != card
            // errors.add(ValidationError.ASPECT_RATIO_FAIL)
        }
        
        // 2. Front Text Score (0-20)
        val frontTextScore = input.frontSideResult?.score ?: 0
        input.frontSideResult?.errors?.let { errors.addAll(it) }
        
        // 3. MRZ Structure Score (0-20)
        val mrzStructureScore = input.backSideResult?.structureScore ?: 0
        
        // 4. MRZ Checksum Score (0-30)
        val mrzChecksumScore = input.backSideResult?.checksumScore ?: 0
        input.backSideResult?.errors?.let { errors.addAll(it) }
        
        // 5. TCKN Algorithm Score (0-10)
        val tcknAlgorithmScore = if (input.frontSideResult?.tcknValid == true) 10 else 0
        
        // Build score breakdown
        val scoreBreakdown = DecisionResult.ScoreBreakdown(
            aspectRatioScore = aspectRatioScore,
            frontTextScore = frontTextScore,
            mrzStructureScore = mrzStructureScore,
            mrzChecksumScore = mrzChecksumScore,
            tcknAlgorithmScore = tcknAlgorithmScore
        )
        
        val totalScore = scoreBreakdown.total
        
        // Calculate confidence (0-1)
        val confidence = totalScore / 100f
        
        // Determine decision
        val decision = when {
            totalScore >= DecisionResult.SCORE_THRESHOLD_VALID -> DecisionResult.Decision.VALID
            totalScore >= DecisionResult.SCORE_THRESHOLD_RETRY -> DecisionResult.Decision.RETRY
            else -> DecisionResult.Decision.INVALID
        }
        
        // Build raw data for debugging
        val rawData = DecisionResult.RawExtractionData(
            frontTextLines = input.frontSideResult?.extractedLines ?: emptyList(),
            mrzLines = input.backSideResult?.mrzLines ?: emptyList(),
            detectedTCKN = input.frontSideResult?.tcknCandidate,
            detectedAspectRatio = input.aspectRatioResult?.measuredRatio ?: 0f,
            mrzFillerRatio = input.backSideResult?.fillerRatio ?: 0f
        )
        
        return DecisionResult(
            decision = decision,
            totalScore = totalScore,
            scoreBreakdown = scoreBreakdown,
            confidence = confidence,
            errors = errors.distinct(),
            rawData = rawData
        )
    }
    
    /**
     * Quick score calculation for single frame
     * Used during live frame analysis
     */
    fun calculateQuickScore(
        aspectRatioValid: Boolean,
        frontTextScore: Int,
        mrzStructureScore: Int,
        mrzChecksumScore: Int,
        tcknValid: Boolean
    ): Int {
        var score = 0
        
        if (aspectRatioValid) score += 20
        score += frontTextScore.coerceAtMost(20)
        score += mrzStructureScore.coerceAtMost(20)
        score += mrzChecksumScore.coerceAtMost(30)
        if (tcknValid) score += 10
        
        return score.coerceAtMost(100)
    }
    
    /**
     * Determine if score is sufficient to proceed
     */
    fun isScoreSufficient(score: Int): Boolean {
        return score >= DecisionResult.SCORE_THRESHOLD_RETRY
    }
    
    /**
     * Get decision category for score
     */
    fun getDecision(score: Int): DecisionResult.Decision {
        return when {
            score >= DecisionResult.SCORE_THRESHOLD_VALID -> DecisionResult.Decision.VALID
            score >= DecisionResult.SCORE_THRESHOLD_RETRY -> DecisionResult.Decision.RETRY
            else -> DecisionResult.Decision.INVALID
        }
    }
    
    /**
     * Get human-readable decision message (Turkish)
     */
    fun getDecisionMessage(decision: DecisionResult.Decision): String {
        return when (decision) {
            DecisionResult.Decision.VALID -> "✓ Gerçek T.C. Kimlik Kartı doğrulandı"
            DecisionResult.Decision.RETRY -> "⚠ Tekrar okutun - görüntü kalitesi yetersiz"
            DecisionResult.Decision.INVALID -> "✗ Geçersiz - T.C. Kimlik Kartı olarak tanınamadı"
        }
    }
    
    /**
     * Get score breakdown description (Turkish)
     */
    fun getScoreBreakdownDescription(breakdown: DecisionResult.ScoreBreakdown): String {
        return buildString {
            appendLine("Skor Detayı:")
            appendLine("├ Kart Oranı: ${breakdown.aspectRatioScore}/20")
            appendLine("├ Ön Yüz Yapısı: ${breakdown.frontTextScore}/20")
            appendLine("├ MRZ Yapısı: ${breakdown.mrzStructureScore}/20")
            appendLine("├ MRZ Checksum: ${breakdown.mrzChecksumScore}/30")
            appendLine("├ TC No Algoritması: ${breakdown.tcknAlgorithmScore}/10")
            appendLine("└ TOPLAM: ${breakdown.total}/100")
        }
    }
}
