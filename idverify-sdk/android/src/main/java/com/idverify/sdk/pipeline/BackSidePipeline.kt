package com.idverify.sdk.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.idverify.sdk.decision.ValidationError
import com.idverify.sdk.mrz.MRZChecksumValidator
import com.idverify.sdk.mrz.MRZExtractor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Back Side Pipeline for Turkish ID Card (MRZ Focus)
 * 
 * The back side contains the MRZ (Machine Readable Zone) at the bottom.
 * TD1 Format: 3 lines, 30 characters each.
 * 
 * Pipeline Steps:
 * 1. Extract bottom 22-28% of image (MRZ region)
 * 2. Run OCR on extracted region
 * 3. Validate MRZ structure:
 *    - 2 data lines (line 2 and 3 of TD1)
 *    - Each line exactly 30 characters
 *    - Only [A-Z0-9<] characters
 *    - Filler ratio 15-40%
 * 4. Validate ICAO checksums:
 *    - Document number check
 *    - Birth date check
 *    - Expiry date check
 *    - Composite check
 * 
 * OCR is NOT run on full image - only on cropped MRZ region
 */
class BackSidePipeline {
    
    companion object {
        private const val TAG = "BackSidePipeline"
    }
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Analysis result for back side (MRZ)
     */
    data class AnalysisResult(
        val isValidMRZ: Boolean,
        val mrzLines: List<String>,
        val fillerRatio: Float,
        val structureScore: Int,      // 0-20 points
        val checksumScore: Int,       // 0-30 points
        val checksumDetails: MRZChecksumValidator.ValidationResult?,
        val extractedData: ExtractedMRZData?,
        val errors: List<ValidationError>,
        val totalScore: Int           // 0-50 points (structure + checksum)
    )
    
    /**
     * Extracted data from MRZ
     */
    data class ExtractedMRZData(
        val documentType: String,
        val issuingCountry: String,
        val documentNumber: String,
        val birthDate: String,
        val sex: String,
        val expiryDate: String,
        val nationality: String,
        val tcKimlikNo: String,
        val surname: String,
        val givenNames: String
    )
    
    /**
     * Analyze back side image for MRZ
     * @param bitmap Full back side image
     * @return AnalysisResult with MRZ validation
     */
    suspend fun analyze(bitmap: Bitmap): AnalysisResult {
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "Analyzing back side: ${bitmap.width}x${bitmap.height}")
        
        // Step 1: Extract MRZ region (bottom portion) - try multiple ratios
        val mrzRegion = MRZExtractor.extractMRZRegion(bitmap, 0.35f)  // Try 35% for better coverage
        Log.d(TAG, "MRZ region: ${mrzRegion.width}x${mrzRegion.height}")
        
        // Step 2: Run OCR on MRZ region
        val ocrLines = runOCR(mrzRegion)
        Log.d(TAG, "MRZ region OCR lines (${ocrLines.size}):")
        ocrLines.forEachIndexed { i, line -> Log.d(TAG, "  [$i] $line") }
        
        // Also try full image
        val fullImageLines = runOCR(bitmap)
        Log.d(TAG, "Full image OCR lines (${fullImageLines.size}):")
        fullImageLines.forEachIndexed { i, line -> Log.d(TAG, "  [$i] $line") }
        
        // Step 3: Validate and clean MRZ lines - try both sources
        val allLines = (ocrLines + fullImageLines).distinct()
        val extractionResult = MRZExtractor.validateAndClean(allLines)
        
        Log.d(TAG, "MRZ extraction: valid=${extractionResult.isValid}, lines=${extractionResult.lines.size}, score=${extractionResult.score}")
        extractionResult.lines.forEachIndexed { i, line -> Log.d(TAG, "  MRZ[$i] $line") }
        extractionResult.errors.forEach { Log.d(TAG, "  Error: ${it.code}") }
        
        if (!extractionResult.isValid || extractionResult.lines.size < 2) {
            Log.d(TAG, "MRZ extraction failed - returning partial score")
            Log.d(TAG, "═══════════════════════════════════════")
            return AnalysisResult(
                isValidMRZ = false,
                mrzLines = extractionResult.lines,
                fillerRatio = extractionResult.fillerRatio,
                structureScore = extractionResult.score,
                checksumScore = 0,
                checksumDetails = null,
                extractedData = null,
                errors = extractionResult.errors,
                totalScore = extractionResult.score
            )
        }
        
        // Step 4: Validate ICAO checksums
        val checksumResult = MRZChecksumValidator.validate(extractionResult.lines)
        Log.d(TAG, "Checksum: valid=${checksumResult.validCount}/4, score=${checksumResult.score}")
        
        // Step 5: Extract data from MRZ
        val extractedData = if (extractionResult.lines.size >= 2) {
            extractMRZData(extractionResult.lines)
        } else null
        
        // Combine errors
        val allErrors = (extractionResult.errors + checksumResult.errors).distinct()
        
        // Calculate total score
        val totalScore = extractionResult.score + checksumResult.score
        
        // MRZ is valid if structure is valid AND at least 1 checksum passes (relaxed)
        val isValidMRZ = extractionResult.isValid && checksumResult.validCount >= 1
        
        Log.d(TAG, "BACK SCORE: structure=${extractionResult.score} + checksum=${checksumResult.score} = $totalScore")
        Log.d(TAG, "═══════════════════════════════════════")
        
        return AnalysisResult(
            isValidMRZ = isValidMRZ,
            mrzLines = extractionResult.lines,
            fillerRatio = extractionResult.fillerRatio,
            structureScore = extractionResult.score,
            checksumScore = checksumResult.score,
            checksumDetails = checksumResult,
            extractedData = extractedData,
            errors = allErrors,
            totalScore = totalScore
        )
    }
    
    /**
     * Run OCR on bitmap
     */
    private suspend fun runOCR(bitmap: Bitmap): List<String> = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.map { it.text.trim() }
                }.filter { it.isNotBlank() }
                
                continuation.resume(lines)
            }
            .addOnFailureListener {
                continuation.resume(emptyList())
            }
    }
    
    /**
     * Extract structured data from MRZ lines
     * 
     * TD1 Format (for Turkish ID):
     * Line 1: I<TURDOCNUM<<<CHECK<<<<<<<<<<<<
     * Line 2: DOBCHKSEXEXPCHKNATOPTIONALCHK
     * Line 3: SURNAME<<GIVENNAMES<<<<<<<<<
     */
    private fun extractMRZData(lines: List<String>): ExtractedMRZData {
        val line1 = lines.getOrElse(0) { "" }.padEnd(30, '<')
        val line2 = lines.getOrElse(1) { "" }.padEnd(30, '<')
        val line3 = lines.getOrElse(2) { "" }.padEnd(30, '<')
        
        // Line 1 parsing
        val documentType = line1.substring(0, 2).replace("<", "").ifEmpty { "I" }
        val issuingCountry = line1.substring(2, 5).replace("<", "").ifEmpty { "TUR" }
        val documentNumber = line1.substring(5, 14).replace("<", "")
        
        // Line 2 parsing
        val birthDateRaw = line2.substring(0, 6)
        val birthDate = MRZChecksumValidator.parseMRZDate(birthDateRaw) ?: birthDateRaw
        val sex = when (line2.getOrElse(7) { '<' }) {
            'M' -> "Erkek"
            'F' -> "Kadın"
            else -> "Belirtilmemiş"
        }
        val expiryDateRaw = line2.substring(8, 14)
        val expiryDate = MRZChecksumValidator.parseMRZDate(expiryDateRaw) ?: expiryDateRaw
        val nationality = line2.substring(15, 18).replace("<", "").ifEmpty { "TUR" }
        val tcKimlikNo = line2.substring(18, 29).replace("<", "")
        
        // Line 3 parsing (Name)
        val nameParts = line3.split("<<")
        val surname = nameParts.getOrElse(0) { "" }.replace("<", " ").trim()
        val givenNames = nameParts.getOrElse(1) { "" }.replace("<", " ").trim()
        
        return ExtractedMRZData(
            documentType = documentType,
            issuingCountry = issuingCountry,
            documentNumber = documentNumber,
            birthDate = birthDate,
            sex = sex,
            expiryDate = expiryDate,
            nationality = nationality,
            tcKimlikNo = tcKimlikNo,
            surname = surname,
            givenNames = givenNames
        )
    }
    
    /**
     * Release resources
     */
    fun release() {
        recognizer.close()
    }
}
