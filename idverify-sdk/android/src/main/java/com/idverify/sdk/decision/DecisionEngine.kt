package com.idverify.sdk.decision

import android.graphics.Bitmap
import com.idverify.sdk.detection.QualityGate
import com.idverify.sdk.pipeline.BackSidePipeline
import com.idverify.sdk.pipeline.FrontSidePipeline
import com.idverify.sdk.scoring.ScoringEngine
import com.idverify.sdk.validation.AspectRatioValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decision Engine - Main Orchestrator
 * 
 * This is the central decision-making component that:
 * 1. Receives live frames from CameraX
 * 2. Passes through Quality Gate
 * 3. Routes to appropriate pipeline (Front/Back)
 * 4. Buffers multiple frames
 * 5. Makes final decision based on scoring
 * 
 * Architecture Flow:
 * Camera Frame → Quality Gate → Aspect Ratio Check → OCR Gate
 *     → Front Pipeline (if no MRZ detected)
 *     → Back Pipeline (if MRZ region detected)
 *     → Scoring Engine → Frame Buffer → Final Decision
 */
class DecisionEngine {
    
    private val frontPipeline = FrontSidePipeline()
    private val backPipeline = BackSidePipeline()
    
    // Multi-frame buffers (3-5 frames before decision)
    private val frontFrameBuffer = FrameBuffer<FrameAnalysisResult>(requiredFrames = 3)
    private val backFrameBuffer = FrameBuffer<FrameAnalysisResult>(requiredFrames = 3)
    
    /**
     * Side detection mode
     */
    enum class DetectionMode {
        AUTO,       // Automatically detect front/back
        FRONT_ONLY, // User explicitly scanning front
        BACK_ONLY   // User explicitly scanning back (MRZ)
    }
    
    /**
     * Analyze a live frame
     * 
     * @param bitmap Camera frame
     * @param mode Detection mode (auto/front/back)
     * @param cardBounds Optional detected card bounds for aspect ratio
     * @return DecisionResult with current frame analysis
     */
    suspend fun analyzeFrame(
        bitmap: Bitmap,
        mode: DetectionMode = DetectionMode.AUTO,
        cardWidth: Int = bitmap.width,
        cardHeight: Int = bitmap.height
    ): DecisionResult = withContext(Dispatchers.Default) {
        
        val timestamp = System.currentTimeMillis()
        val errors = mutableListOf<ValidationError>()
        
        // Step 1: Quality Gate - MUST pass before any OCR
        val qualityResult = QualityGate.assess(bitmap)
        
        if (!qualityResult.passed) {
            errors.addAll(qualityResult.errors)
            return@withContext createFailedResult(errors, "Kalite kontrolü başarısız")
        }
        
        // Step 2: Aspect Ratio Validation
        val aspectRatioResult = AspectRatioValidator.validateStrict(cardWidth, cardHeight)
        
        if (!aspectRatioResult.isValid) {
            errors.add(ValidationError.ASPECT_RATIO_FAIL)
            // Don't fail completely - continue but with 0 aspect ratio score
        }
        
        // Step 3: Route to appropriate pipeline based on mode
        val frontResult: FrontSidePipeline.AnalysisResult?
        val backResult: BackSidePipeline.AnalysisResult?
        
        when (mode) {
            DetectionMode.FRONT_ONLY -> {
                frontResult = frontPipeline.analyze(bitmap)
                backResult = null
            }
            DetectionMode.BACK_ONLY -> {
                frontResult = null
                backResult = backPipeline.analyze(bitmap)
            }
            DetectionMode.AUTO -> {
                // Try back first (MRZ is more definitive)
                backResult = backPipeline.analyze(bitmap)
                
                // If no valid MRZ, try front
                frontResult = if (backResult.isValidMRZ) {
                    null
                } else {
                    frontPipeline.analyze(bitmap)
                }
            }
        }
        
        // Step 4: Calculate score
        val scoringInput = ScoringEngine.ScoringInput(
            aspectRatioResult = aspectRatioResult,
            frontSideResult = frontResult,
            backSideResult = backResult,
            detectedCardWidth = cardWidth,
            detectedCardHeight = cardHeight
        )
        
        val decision = ScoringEngine.calculate(scoringInput)
        
        // Step 5: Add to frame buffer
        val frameResult = FrameAnalysisResult(
            score = decision.totalScore,
            timestamp = timestamp,
            decision = decision,
            qualityPassed = qualityResult.passed
        )
        
        if (mode == DetectionMode.FRONT_ONLY || (mode == DetectionMode.AUTO && backResult?.isValidMRZ != true)) {
            frontFrameBuffer.addFrame(frameResult)
        } else {
            backFrameBuffer.addFrame(frameResult)
        }
        
        return@withContext decision
    }
    
    /**
     * Analyze front side specifically
     */
    suspend fun analyzeFrontSide(bitmap: Bitmap): DecisionResult {
        return analyzeFrame(bitmap, DetectionMode.FRONT_ONLY)
    }
    
    /**
     * Analyze back side (MRZ) specifically
     */
    suspend fun analyzeBackSide(bitmap: Bitmap): DecisionResult {
        return analyzeFrame(bitmap, DetectionMode.BACK_ONLY)
    }
    
    /**
     * Get best result from front frame buffer
     * Only returns if we have enough consistent frames
     */
    fun getBestFrontResult(): DecisionResult? {
        if (!frontFrameBuffer.hasEnoughFrames()) return null
        return frontFrameBuffer.getBestResult()?.decision
    }
    
    /**
     * Get best result from back frame buffer
     */
    fun getBestBackResult(): DecisionResult? {
        if (!backFrameBuffer.hasEnoughFrames()) return null
        return backFrameBuffer.getBestResult()?.decision
    }
    
    /**
     * Check if front side has consistent good quality
     */
    fun hasFrontSideConsistentQuality(minScore: Int = DecisionResult.SCORE_THRESHOLD_RETRY): Boolean {
        return frontFrameBuffer.hasConsistentQuality(minScore)
    }
    
    /**
     * Check if back side has consistent good quality
     */
    fun hasBackSideConsistentQuality(minScore: Int = DecisionResult.SCORE_THRESHOLD_RETRY): Boolean {
        return backFrameBuffer.hasConsistentQuality(minScore)
    }
    
    /**
     * Get current front buffer status
     */
    fun getFrontBufferStatus(): BufferStatus {
        return BufferStatus(
            frameCount = frontFrameBuffer.frameCount(),
            averageScore = frontFrameBuffer.getAverageScore(),
            stability = frontFrameBuffer.getScoreStability(),
            bestScore = frontFrameBuffer.getBestResult()?.score ?: 0
        )
    }
    
    /**
     * Get current back buffer status
     */
    fun getBackBufferStatus(): BufferStatus {
        return BufferStatus(
            frameCount = backFrameBuffer.frameCount(),
            averageScore = backFrameBuffer.getAverageScore(),
            stability = backFrameBuffer.getScoreStability(),
            bestScore = backFrameBuffer.getBestResult()?.score ?: 0
        )
    }
    
    /**
     * Clear all buffers and reset state
     */
    fun reset() {
        frontFrameBuffer.clear()
        backFrameBuffer.clear()
    }
    
    /**
     * Release resources
     */
    fun release() {
        frontPipeline.release()
        backPipeline.release()
        reset()
    }
    
    /**
     * Create failed result for early exits
     */
    private fun createFailedResult(
        errors: List<ValidationError>,
        message: String
    ): DecisionResult {
        return DecisionResult(
            decision = DecisionResult.Decision.INVALID,
            totalScore = 0,
            scoreBreakdown = DecisionResult.ScoreBreakdown(),
            confidence = 0f,
            errors = errors,
            rawData = null
        )
    }
    
    /**
     * Buffer status for monitoring
     */
    data class BufferStatus(
        val frameCount: Int,
        val averageScore: Float,
        val stability: Float,
        val bestScore: Int
    ) {
        val hasEnoughFrames: Boolean get() = frameCount >= FrameBuffer.DEFAULT_REQUIRED_FRAMES
    }
}
