package com.idverify.sdk.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.idverify.sdk.decision.ValidationError
import com.idverify.sdk.validation.TCKNValidator
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Front Side Pipeline for Turkish ID Card
 * 
 * The front side contains:
 * - "TÜRKİYE CUMHURİYETİ" or "TÜRKİYE" text
 * - T.C. Kimlik Numarası (11 digits)
 * - Name and Surname (UPPERCASE)
 * - Birth Date (DD.MM.YYYY format)
 * - Photo
 * 
 * Validation Rules:
 * - SOFT: "TÜRKİYE" text presence
 * - SOFT: 11-digit TCKN candidate found
 * - SOFT: Uppercase letter ratio > 60%
 * - SOFT: Name-like patterns detected
 * - SOFT: Date pattern DD.MM.YYYY found
 * - HARD: If TCKN found, it MUST pass algorithm validation
 * 
 * This pipeline does NOT aim for 100% OCR accuracy.
 * It asks: "Does this look like a T.C. ID card front?"
 */
class FrontSidePipeline {
    
    companion object {
        private const val TAG = "FrontSidePipeline"
    }
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Analysis result for front side
     */
    data class AnalysisResult(
        val isLikelyFrontSide: Boolean,
        val turkiyeTextFound: Boolean,
        val tcknCandidate: String?,
        val tcknValid: Boolean,
        val uppercaseRatio: Float,
        val namePatternFound: Boolean,
        val datePatternFound: Boolean,
        val extractedText: String,
        val extractedLines: List<String>,
        val errors: List<ValidationError>,
        val score: Int  // 0-20 points
    )
    
    /**
     * Analyze front side image
     * @param bitmap Front side image
     * @return AnalysisResult with validation scores
     */
    suspend fun analyze(bitmap: Bitmap): AnalysisResult = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.map { it.text.trim() }
                }.filter { it.isNotBlank() }
                
                val result = analyzeText(fullText, lines)
                continuation.resume(result)
            }
            .addOnFailureListener { e ->
                continuation.resume(
                    AnalysisResult(
                        isLikelyFrontSide = false,
                        turkiyeTextFound = false,
                        tcknCandidate = null,
                        tcknValid = false,
                        uppercaseRatio = 0f,
                        namePatternFound = false,
                        datePatternFound = false,
                        extractedText = "",
                        extractedLines = emptyList(),
                        errors = listOf(ValidationError.OCR_FAILED),
                        score = 0
                    )
                )
            }
    }
    
    /**
     * Analyze extracted text for front side patterns
     */
    private fun analyzeText(fullText: String, lines: List<String>): AnalysisResult {
        val errors = mutableListOf<ValidationError>()
        var score = 0
        
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "OCR Text (${fullText.length} chars): ${fullText.take(200)}")
        Log.d(TAG, "OCR Lines (${lines.size}):")
        lines.forEachIndexed { i, line -> Log.d(TAG, "  [$i] $line") }
        
        // BASE SCORE: If we got ANY readable text, give base points
        if (fullText.length >= 10) {
            score += 4  // Base points for readable content
            Log.d(TAG, "✓ Base text found: +4")
        }
        
        // 1. Check for "TÜRKİYE" or "TÜRKİYE CUMHURİYETİ" text
        val turkiyeTextFound = checkTurkiyeText(fullText)
        if (turkiyeTextFound) {
            score += 5  // Soft rule: +5 points
            Log.d(TAG, "✓ TÜRKİYE found: +5")
        } else {
            errors.add(ValidationError.FRONT_TURKIYE_NOT_FOUND)
            Log.d(TAG, "✗ TÜRKİYE not found")
        }
        
        // 2. Find TCKN candidate (11-digit number)
        val tcknCandidate = findTCKNCandidate(fullText)
        val tcknValid = if (tcknCandidate != null) {
            Log.d(TAG, "TCKN candidate: $tcknCandidate")
            val validation = TCKNValidator.validate(tcknCandidate)
            if (validation.isValid) {
                score += 6  // HARD rule passed: +6 points
                Log.d(TAG, "✓ TCKN valid: +6")
                true
            } else {
                // Give partial points for finding 11 digits (even if algorithm fails)
                score += 2
                Log.d(TAG, "⚠ TCKN found but invalid: +2 (partial)")
                errors.add(ValidationError.FRONT_TCKN_ALGORITHM_FAIL)
                false
            }
        } else {
            errors.add(ValidationError.FRONT_TCKN_NOT_FOUND)
            Log.d(TAG, "✗ TCKN not found")
            false
        }
        
        // 3. Calculate uppercase ratio
        val uppercaseRatio = calculateUppercaseRatio(fullText)
        if (uppercaseRatio > 0.5f) {  // Lowered from 0.6
            score += 2  // Soft rule: +2 points
            Log.d(TAG, "✓ Uppercase ratio ${(uppercaseRatio*100).toInt()}%: +2")
        }
        
        // 4. Check for name-like patterns (UPPERCASE words, no digits)
        val namePatternFound = checkNamePattern(lines)
        if (namePatternFound) {
            score += 2  // Soft rule: +2 points
            Log.d(TAG, "✓ Name pattern found: +2")
        } else {
            errors.add(ValidationError.FRONT_NAME_PATTERN_FAIL)
        }
        
        // 5. Check for date pattern DD.MM.YYYY
        val datePatternFound = checkDatePattern(fullText)
        if (datePatternFound) {
            score += 1  // Soft rule: +1 point
            Log.d(TAG, "✓ Date pattern found: +1")
        } else {
            errors.add(ValidationError.FRONT_DATE_PATTERN_FAIL)
        }
        
        Log.d(TAG, "FRONT SCORE: $score/20")
        Log.d(TAG, "═══════════════════════════════════════")
        
        // Determine if this looks like a front side
        // Need at least TÜRKİYE text OR valid TCKN OR reasonable score
        val isLikelyFrontSide = turkiyeTextFound || tcknValid || (score >= 8)
        
        return AnalysisResult(
            isLikelyFrontSide = isLikelyFrontSide,
            turkiyeTextFound = turkiyeTextFound,
            tcknCandidate = tcknCandidate,
            tcknValid = tcknValid,
            uppercaseRatio = uppercaseRatio,
            namePatternFound = namePatternFound,
            datePatternFound = datePatternFound,
            extractedText = fullText,
            extractedLines = lines,
            errors = errors,
            score = score.coerceAtMost(20)
        )
    }
    
    /**
     * Check for TÜRKİYE or TÜRKİYE CUMHURİYETİ text
     * Handles OCR variations (TURKIYE, TÜRKIYE, TORKIYE, etc.)
     */
    private fun checkTurkiyeText(text: String): Boolean {
        val normalized = text.uppercase()
            .replace("İ", "I")
            .replace("Ü", "U")
            .replace("Ö", "O")
            .replace("Ş", "S")
            .replace("Ğ", "G")
            .replace("Ç", "C")
        
        // Common OCR variations
        val patterns = listOf(
            "TURKIYE",
            "TORKIYE",
            "TÜRKIYE",
            "T.C.",
            "TC ",
            " TC",
            "CUMHURIYET",
            "KIMLIK",
            "NUFUS"
        )
        
        return patterns.any { normalized.contains(it) }
    }
    
    /**
     * Find 11-digit TCKN candidate
     */
    private fun findTCKNCandidate(text: String): String? {
        // Method 1: Direct 11-digit sequence
        val directMatch = Regex("\\d{11}").find(text.replace(" ", ""))
        if (directMatch != null) {
            return directMatch.value
        }
        
        // Method 2: Numbers with spaces/separators
        val digitsOnly = text.filter { it.isDigit() }
        if (digitsOnly.length >= 11) {
            // Try to extract 11-digit sequence that passes TCKN validation
            for (i in 0..digitsOnly.length - 11) {
                val candidate = digitsOnly.substring(i, i + 11)
                if (TCKNValidator.validate(candidate).isValid) {
                    return candidate
                }
            }
            // Return first 11 digits as candidate (even if invalid)
            return digitsOnly.take(11)
        }
        
        return null
    }
    
    /**
     * Calculate ratio of uppercase letters in text
     */
    private fun calculateUppercaseRatio(text: String): Float {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return 0f
        
        val uppercase = letters.count { it.isUpperCase() }
        return uppercase.toFloat() / letters.length
    }
    
    /**
     * Check for name-like patterns
     * Names on Turkish ID are UPPERCASE, contain Turkish letters
     */
    private fun checkNamePattern(lines: List<String>): Boolean {
        // Look for lines that:
        // - Are mostly uppercase letters
        // - Don't contain digits (or very few)
        // - Have 3+ characters
        
        return lines.any { line ->
            val cleaned = line.trim()
            val letterCount = cleaned.count { it.isLetter() }
            val digitCount = cleaned.count { it.isDigit() }
            val uppercaseCount = cleaned.count { it.isUpperCase() }
            
            // At least 3 letters, mostly letters, mostly uppercase
            letterCount >= 3 &&
            digitCount <= 1 &&
            (uppercaseCount.toFloat() / letterCount.coerceAtLeast(1)) > 0.5f
        }
    }
    
    /**
     * Check for date pattern DD.MM.YYYY
     */
    private fun checkDatePattern(text: String): Boolean {
        // Turkish date format: DD.MM.YYYY or DD/MM/YYYY
        val dateRegex = Regex("\\d{2}[./]\\d{2}[./]\\d{4}")
        return dateRegex.containsMatchIn(text)
    }
    
    /**
     * Release resources
     */
    fun release() {
        recognizer.close()
    }
}
