package com.idverify.sdk.decision

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Frame Buffer for Multi-Frame Analysis
 * 
 * The system does NOT make decisions based on single frame.
 * Instead, it analyzes 3-5 consecutive frames and selects
 * the result with highest confidence.
 * 
 * This reduces false positives from:
 * - Momentary blur
 * - Transitional glare
 * - OCR fluctuations
 */
class FrameBuffer<T : FrameBuffer.ScoredResult>(
    private val requiredFrames: Int = DEFAULT_REQUIRED_FRAMES,
    private val maxBufferSize: Int = MAX_BUFFER_SIZE
) {
    
    companion object {
        const val DEFAULT_REQUIRED_FRAMES = 3
        const val MAX_REQUIRED_FRAMES = 5
        const val MAX_BUFFER_SIZE = 10
    }
    
    /**
     * Interface for results that can be scored and buffered
     */
    interface ScoredResult {
        val score: Int
        val timestamp: Long
    }
    
    private val buffer = ConcurrentLinkedQueue<T>()
    
    /**
     * Add a frame result to buffer
     * @param result Frame analysis result
     */
    fun addFrame(result: T) {
        buffer.add(result)
        
        // Trim buffer if too large
        while (buffer.size > maxBufferSize) {
            buffer.poll()
        }
    }
    
    /**
     * Check if we have enough frames for decision
     */
    fun hasEnoughFrames(): Boolean {
        return buffer.size >= requiredFrames
    }
    
    /**
     * Get current frame count
     */
    fun frameCount(): Int = buffer.size
    
    /**
     * Get best result from buffer (highest score)
     * @return Best result or null if buffer is empty
     */
    fun getBestResult(): T? {
        return buffer.maxByOrNull { it.score }
    }
    
    /**
     * Get average score across all frames
     */
    fun getAverageScore(): Float {
        if (buffer.isEmpty()) return 0f
        return buffer.sumOf { it.score } / buffer.size.toFloat()
    }
    
    /**
     * Get recent frames (last N)
     */
    fun getRecentFrames(count: Int = requiredFrames): List<T> {
        return buffer.toList().takeLast(count)
    }
    
    /**
     * Check if recent frames show consistent quality
     * (all scores above threshold)
     */
    fun hasConsistentQuality(minScore: Int): Boolean {
        val recent = getRecentFrames()
        return recent.size >= requiredFrames && recent.all { it.score >= minScore }
    }
    
    /**
     * Get frame with highest score from recent frames
     */
    fun getBestRecentResult(): T? {
        return getRecentFrames().maxByOrNull { it.score }
    }
    
    /**
     * Calculate score stability (variance)
     * Low variance = consistent results
     */
    fun getScoreStability(): Float {
        if (buffer.size < 2) return 0f
        
        val scores = buffer.map { it.score.toDouble() }
        val mean = scores.average()
        val variance = scores.sumOf { (it - mean) * (it - mean) } / scores.size
        
        // Normalize to 0-1 (1 = very stable, 0 = unstable)
        // Variance of 0 = perfect stability
        // Variance of 400 (±20 points) = 0 stability
        return (1.0 - (variance / 400.0)).coerceIn(0.0, 1.0).toFloat()
    }
    
    /**
     * Clear all frames
     */
    fun clear() {
        buffer.clear()
    }
    
    /**
     * Check if buffer is empty
     */
    fun isEmpty(): Boolean = buffer.isEmpty()
}

/**
 * Frame result wrapper for decision engine
 */
data class FrameAnalysisResult(
    override val score: Int,
    override val timestamp: Long,
    val decision: DecisionResult,
    val qualityPassed: Boolean
) : FrameBuffer.ScoredResult
