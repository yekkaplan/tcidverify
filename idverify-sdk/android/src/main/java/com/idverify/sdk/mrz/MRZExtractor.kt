package com.idverify.sdk.mrz

import android.graphics.Bitmap
import com.idverify.sdk.decision.ValidationError

/**
 * MRZ Extractor for Turkish ID Card (TD1 Format)
 * 
 * Turkish ID Card MRZ is on the BACK side, bottom portion.
 * TD1 Format: 3 lines x 30 characters each
 * 
 * For extraction, we focus on the bottom 22-28% of the card
 * where the MRZ zone is located.
 * 
 * Validation Rules (HARD):
 * - Exactly 2 data lines (line 2 and 3 of TD1)
 * - Each line MUST be exactly 30 characters
 * - Characters MUST match [A-Z0-9<] only
 * - Filler ratio (<) must be 15-40%
 */
object MRZExtractor {
    
    /** MRZ must have exactly 30 characters per line */
    const val LINE_LENGTH = 30
    
    /** TD1 format has 3 lines, we extract bottom 2 for data */
    const val EXPECTED_DATA_LINES = 2
    
    /** MRZ extraction region: bottom 22-28% of image */
    const val EXTRACTION_RATIO_MIN = 0.22f
    const val EXTRACTION_RATIO_MAX = 0.28f
    const val EXTRACTION_RATIO_DEFAULT = 0.25f
    
    /** Filler (<) ratio acceptable range: 15-40% */
    const val MIN_FILLER_RATIO = 0.15f
    const val MAX_FILLER_RATIO = 0.40f
    
    /** Valid MRZ character set */
    private val MRZ_CHARSET = Regex("^[A-Z0-9<]+$")
    
    /** MRZ line pattern */
    private val MRZ_LINE_PATTERN = Regex("^[A-Z0-9<]{30}$")
    
    /**
     * Extraction result
     */
    data class ExtractionResult(
        val isValid: Boolean,
        val lines: List<String>,
        val fillerRatio: Float,
        val errors: List<ValidationError>,
        val score: Int  // 0-20 points for structure
    )
    
    /**
     * Extract bottom MRZ region from bitmap
     * @param bitmap Full card image
     * @param ratio Portion from bottom (0.22-0.28)
     * @return Cropped bitmap containing MRZ area
     */
    fun extractMRZRegion(bitmap: Bitmap, ratio: Float = EXTRACTION_RATIO_DEFAULT): Bitmap {
        val effectiveRatio = ratio.coerceIn(EXTRACTION_RATIO_MIN, EXTRACTION_RATIO_MAX)
        val height = (bitmap.height * effectiveRatio).toInt().coerceAtLeast(1)
        val y = bitmap.height - height
        return Bitmap.createBitmap(bitmap, 0, y, bitmap.width, height)
    }
    
    /**
     * Validate and clean raw OCR lines into MRZ format
     * @param rawLines OCR output lines
     * @return ExtractionResult with validated MRZ lines
     */
    fun validateAndClean(rawLines: List<String>): ExtractionResult {
        val errors = mutableListOf<ValidationError>()
        
        android.util.Log.d("MRZExtractor", "Processing ${rawLines.size} raw lines")
        
        // Step 1: Clean and filter potential MRZ lines (LENIENT)
        val cleanedLines = rawLines
            .map { cleanMRZLine(it) }
            .filter { it.length >= 15 }  // More lenient: at least 15 chars
        
        android.util.Log.d("MRZExtractor", "Cleaned lines (${cleanedLines.size}):")
        cleanedLines.forEachIndexed { i, line -> 
            android.util.Log.d("MRZExtractor", "  [$i] len=${line.length}: $line")
        }
        
        // Filter lines that look like MRZ
        val mrzCandidates = cleanedLines.filter { looksLikeMRZ(it) }
        android.util.Log.d("MRZExtractor", "MRZ candidates (${mrzCandidates.size}): ${mrzCandidates.joinToString(" | ")}")
        
        if (mrzCandidates.isEmpty()) {
            // Fallback: take longest lines that have uppercase + digits
            val fallbackLines = cleanedLines
                .filter { line -> 
                    val upperCount = line.count { it.isUpperCase() || it == '<' }
                    upperCount.toFloat() / line.length > 0.7f
                }
                .sortedByDescending { it.length }
                .take(3)
            
            android.util.Log.d("MRZExtractor", "Using fallback lines (${fallbackLines.size})")
            
            if (fallbackLines.isEmpty()) {
                errors.add(ValidationError.MRZ_NOT_FOUND)
                return ExtractionResult(
                    isValid = false,
                    lines = emptyList(),
                    fillerRatio = 0f,
                    errors = errors,
                    score = 0
                )
            }
            
            // Use fallback lines with partial score
            val normalizedFallback = fallbackLines.map { normalizeTo30Chars(it) }
            val fallbackScore = (normalizedFallback.size * 3).coerceAtMost(10)  // Partial score
            
            return ExtractionResult(
                isValid = false,
                lines = normalizedFallback,
                fillerRatio = 0f,
                errors = listOf(ValidationError.MRZ_NOT_FOUND),
                score = fallbackScore
            )
        }
        
        // Step 2: Normalize to exactly 30 characters
        val normalizedLines = mrzCandidates
            .map { normalizeTo30Chars(it) }
            .distinct()
            .take(3)  // TD1 has 3 lines max
        
        android.util.Log.d("MRZExtractor", "Normalized lines (${normalizedLines.size})")
        
        // Step 3: Validate line count (soft - don't fail)
        if (normalizedLines.size < EXPECTED_DATA_LINES) {
            errors.add(ValidationError.MRZ_LINE_COUNT_INVALID)
        }
        
        // Step 4: Calculate filler ratio (soft validation)
        val totalChars = normalizedLines.sumOf { it.length }
        val fillerCount = normalizedLines.sumOf { line -> line.count { it == '<' } }
        val fillerRatio = if (totalChars > 0) fillerCount.toFloat() / totalChars else 0f
        
        android.util.Log.d("MRZExtractor", "Filler ratio: $fillerRatio (valid: ${MIN_FILLER_RATIO}-${MAX_FILLER_RATIO})")
        
        // Calculate structure score (0-20) - more lenient
        val score = calculateStructureScore(normalizedLines, fillerRatio, errors)
        
        // Valid if we have at least 1 line with 30 chars
        val isValid = normalizedLines.isNotEmpty() && normalizedLines.any { it.length == LINE_LENGTH }
        
        android.util.Log.d("MRZExtractor", "Result: valid=$isValid, score=$score")
        
        return ExtractionResult(
            isValid = isValid,
            lines = normalizedLines,
            fillerRatio = fillerRatio,
            errors = errors.distinct(),
            score = score
        )
    }
    
    /**
     * Clean raw OCR text for MRZ
     */
    private fun cleanMRZLine(text: String): String {
        return text
            .uppercase()
            .replace(" ", "<")
            .replace("-", "<")
            .replace("_", "<")
            .replace(".", "<")
            .replace(",", "<")
            .replace("|", "I")
            .replace("!", "I")
            .replace("1", "I")  // In alpha context
            .replace("0", "O")  // Will be handled context-aware
            .filter { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<" }
    }
    
    /**
     * Normalize line to exactly 30 characters
     */
    private fun normalizeTo30Chars(line: String): String {
        return when {
            line.length < LINE_LENGTH -> line + "<".repeat(LINE_LENGTH - line.length)
            line.length > LINE_LENGTH -> line.take(LINE_LENGTH)
            else -> line
        }
    }
    
    /**
     * Quick check if line looks like MRZ
     * More lenient for real-world OCR variations
     */
    private fun looksLikeMRZ(line: String): Boolean {
        if (line.length < 15) return false  // Lowered from 20
        
        // Count valid MRZ characters
        val validCount = line.count { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<" }
        val ratio = validCount.toFloat() / line.length
        
        // Check for typical MRZ patterns
        val hasFillers = line.contains('<')
        val hasUppercase = line.any { it.isUpperCase() }
        val hasDigits = line.any { it.isDigit() }
        
        // MRZ-like if: mostly valid chars AND has uppercase AND (has fillers OR digits)
        return ratio >= 0.6f && hasUppercase && (hasFillers || hasDigits)
    }
    
    /**
     * Validate single MRZ line
     */
    private fun isValidMRZLine(line: String): Boolean {
        return MRZ_LINE_PATTERN.matches(line)
    }
    
    /**
     * Calculate MRZ structure score (0-20 points)
     */
    private fun calculateStructureScore(
        lines: List<String>,
        fillerRatio: Float,
        errors: List<ValidationError>
    ): Int {
        var score = 0
        
        // Line count: +8 points for 2+ lines
        if (lines.size >= EXPECTED_DATA_LINES) score += 8
        else if (lines.size == 1) score += 4
        
        // Line length: +6 points if all lines are 30 chars
        val allCorrectLength = lines.all { it.length == LINE_LENGTH }
        if (allCorrectLength) score += 6
        else if (lines.any { it.length == LINE_LENGTH }) score += 3
        
        // Charset: +3 points if all valid characters
        val allValidCharset = lines.all { MRZ_CHARSET.matches(it) }
        if (allValidCharset) score += 3
        
        // Filler ratio: +3 points if in range
        if (fillerRatio in MIN_FILLER_RATIO..MAX_FILLER_RATIO) score += 3
        
        return score.coerceAtMost(20)
    }
}
