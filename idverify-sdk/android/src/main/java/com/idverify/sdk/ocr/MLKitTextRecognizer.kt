package com.idverify.sdk.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * ML Kit Text Recognition wrapper for MRZ detection
 */
class MLKitTextRecognizer {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Recognition result with confidence scores
     */
    data class RecognitionResult(
        val fullText: String,
        val lines: List<String>,
        val confidence: Float,  // 0.0 - 1.0 (average confidence)
        val lineConfidences: List<Float> = emptyList()  // Per-line confidence
    )
    
    /**
     * Recognize text from bitmap with confidence tracking
     * @param bitmap Image containing MRZ
     * @return RecognitionResult with detected text and confidence scores
     */
    suspend fun recognizeText(bitmap: Bitmap): RecognitionResult = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = mutableListOf<String>()
                val lineConfidences = mutableListOf<Float>()
                
                visionText.textBlocks.forEach { block ->
                    block.lines.forEach { line ->
                        val lineText = line.text.trim()
                        if (lineText.isNotBlank()) {
                            lines.add(lineText)
                            // ML Kit doesn't provide per-character confidence directly
                            // Estimate based on bounding box quality and text length
                            val estimatedConfidence = estimateLineConfidence(line, lineText)
                            lineConfidences.add(estimatedConfidence)
                        }
                    }
                }
                
                val fullText = visionText.text
                val avgConfidence = if (lineConfidences.isNotEmpty()) {
                    lineConfidences.average().toFloat()
                } else {
                    0.0f
                }
                
                val result = RecognitionResult(
                    fullText = fullText,
                    lines = lines,
                    confidence = avgConfidence,
                    lineConfidences = lineConfidences
                )
                
                continuation.resume(result)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }
    
    /**
     * Estimate confidence for a line based on bounding box and text characteristics
     */
    private fun estimateLineConfidence(line: com.google.mlkit.vision.text.Text.Line, text: String): Float {
        // Base confidence from text characteristics
        var confidence = 0.8f
        
        // MRZ lines should be exactly 30 characters
        if (text.length >= 25 && text.length <= 35) {
            confidence += 0.1f
        }
        
        // MRZ contains mostly uppercase/digits
        val uppercaseRatio = text.count { it.isUpperCase() || it.isDigit() || it == '<' }.toFloat() / text.length
        if (uppercaseRatio > 0.8f) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Extract MRZ lines from recognized text with improved filtering
     * Filters for lines that look like MRZ (30 characters, mostly uppercase/digits)
     * Uses confidence scores and multiple cleaning attempts
     */
    fun extractMRZLines(recognitionResult: RecognitionResult): List<String> {
        val parser = MRZParser()
        // Lower confidence threshold - ML Kit doesn't provide per-character confidence
        val minConfidence = 0.5f  // More lenient for now
        
        // Filter lines by confidence first
        val candidateLines = recognitionResult.lines
            .mapIndexed { index, line ->
                val confidence = recognitionResult.lineConfidences.getOrElse(index) { 0.7f }  // Default higher if missing
                Pair(line, confidence)
            }
            .filter { 
                // Accept if confidence is good OR line looks like MRZ (length, pattern)
                it.second >= minConfidence || looksLikeMRZLine(it.first)
            }
            .map { it.first }
        
        if (candidateLines.isEmpty()) {
            android.util.Log.w("MLKitTextRecognizer", "No candidate lines found from ${recognitionResult.lines.size} OCR lines")
            recognitionResult.lines.forEachIndexed { idx, line ->
                android.util.Log.v("MLKitTextRecognizer", "OCR line $idx: '$line' (length: ${line.length})")
            }
            return emptyList()
        }
        
        android.util.Log.d("MLKitTextRecognizer", "Found ${candidateLines.size} candidate lines from ${recognitionResult.lines.size} OCR lines")
        
        // Try multiple cleaning strategies
        val cleanedLines = candidateLines.flatMap { originalLine ->
            val results = mutableListOf<String>()
            
            // Strategy 1: Standard cleaning
            val cleaned1 = parser.cleanMRZText(originalLine)
            android.util.Log.v("MLKitTextRecognizer", "Strategy 1 (standard): '$originalLine' -> '$cleaned1' (len=${cleaned1.length})")
            if (cleaned1.length == com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH) {
                results.add(cleaned1)
            }
            
            // Strategy 2: More aggressive cleaning
            val cleaned2 = aggressiveCleanMRZ(originalLine)
            android.util.Log.v("MLKitTextRecognizer", "Strategy 2 (aggressive): '$originalLine' -> '$cleaned2' (len=${cleaned2.length})")
            if (cleaned2.length == com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH) {
                results.add(cleaned2)
            }
            
            // Strategy 3: Padding/trimming
            val cleaned3 = padOrTrimMRZ(cleaned1)
            android.util.Log.v("MLKitTextRecognizer", "Strategy 3 (padded): '$cleaned1' -> '$cleaned3' (len=${cleaned3.length})")
            if (cleaned3.length == com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH && cleaned3 !in results) {
                results.add(cleaned3)
            }
            
            // Strategy 4: Aggressive + padding
            val cleaned4 = padOrTrimMRZ(cleaned2)
            android.util.Log.v("MLKitTextRecognizer", "Strategy 4 (aggressive+padded): '$cleaned2' -> '$cleaned4' (len=${cleaned4.length})")
            if (cleaned4.length == com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH && cleaned4 !in results) {
                results.add(cleaned4)
            }
            
            results
        }
        
        // Filter for valid MRZ lines (30 chars, MRZ pattern)
        val validLines = cleanedLines
            .filter { it.length == com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH }
            .distinctBy { it }  // Remove duplicates
            .filter { isValidMRZPattern(it) }
            .take(com.idverify.sdk.utils.Constants.MRZ.LINE_COUNT)
        
        android.util.Log.d("MLKitTextRecognizer", "Extracted ${validLines.size} valid MRZ lines from ${cleanedLines.size} cleaned lines (${candidateLines.size} candidates)")
        validLines.forEachIndexed { idx, line ->
            android.util.Log.d("MLKitTextRecognizer", "Valid MRZ[$idx]: '$line'")
        }
        
        return validLines
    }
    
    /**
     * More aggressive MRZ text cleaning for OCR errors
     * Uses context-aware character substitutions
     */
    private fun aggressiveCleanMRZ(text: String): String {
        var cleaned = text.uppercase()
            .replace(" ", "<")  // Space -> filler
            .replace("-", "<")  // Dash -> filler
            .replace("_", "<")  // Underscore -> filler
            .replace(".", "<")  // Period -> filler
            .replace(",", "<")  // Comma -> filler
            .replace("|", "I")  // Pipe -> I
            .replace("!", "I")  // Exclamation -> I
            .replace("l", "I")  // lowercase L -> I
        
        // Common OCR misreads (context-aware)
        cleaned = cleaned.replace(Regex("[oO]{2,}"), "<".repeat(2))  // OO -> <<
        
        // Filter valid MRZ characters
        cleaned = cleaned.filter { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<" }
        
        return cleaned
    }
    
    /**
     * Pad or trim MRZ line to exactly 30 characters
     */
    private fun padOrTrimMRZ(text: String): String {
        return when {
            text.length < com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH -> {
                text + "<".repeat(com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH - text.length)
            }
            text.length > com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH -> {
                text.substring(0, com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH)
            }
            else -> text
        }
    }
    
    /**
     * Check if text looks like an MRZ line (quick heuristic before cleaning)
     */
    private fun looksLikeMRZLine(text: String): Boolean {
        if (text.length < 15 || text.length > 40) return false  // MRZ is 30, allow tolerance
        
        // Should contain mostly uppercase, digits, or < or common OCR errors
        val validChars = text.count { 
            it.isUpperCase() || it.isDigit() || it == '<' || it == ' ' || 
            it in "._-,|!lO" // Common OCR misreads
        }
        val validRatio = validChars.toFloat() / text.length
        
        // Also check if it has the characteristic MRZ pattern (lots of < or spaces)
        val fillerChars = text.count { it == '<' || it == ' ' || it == '_' }
        val hasFillers = fillerChars >= 3  // MRZ typically has multiple fillers
        
        return validRatio >= 0.6f || hasFillers  // More lenient
    }
    
    /**
     * Check if text matches MRZ pattern (starts with I< or ID, contains TUR, etc.)
     */
    private fun isValidMRZPattern(text: String): Boolean {
        if (text.length != com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH) return false
        
        // Line 1 should start with "I<" or "ID" and contain "TUR"
        if (text.startsWith("I<") || text.startsWith("ID")) {
            return text.contains("TUR") || (text.length >= 5 && text.substring(2, 5) == "TUR")
        }
        
        // Line 2 should have digits in date positions (1-6 for DOB, 9-14 for expiry)
        if (text.length >= 15) {
            val dob = text.substring(0, 6)
            val expiry = if (text.length >= 14) text.substring(8, 14) else ""
            if (dob.all { it.isDigit() || it == '<' } && 
                expiry.isNotEmpty() && expiry.all { it.isDigit() || it == '<' }) {
                return true
            }
        }
        
        // Line 3 should have name pattern (contains <<)
        if (text.contains("<<")) {
            return true
        }
        
        // Generic check: mostly alphanumeric with some <
        val validCharCount = text.count { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<" }
        return validCharCount >= text.length * 0.85f  // 85% valid chars
    }
    
    /**
     * Release resources
     */
    fun release() {
        recognizer.close()
    }
}
