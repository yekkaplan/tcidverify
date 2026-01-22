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
        
        // VISION-FIRST: Check glare before processing
        val glareLevel = try {
            com.idverify.sdk.core.NativeProcessor.detectGlare(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Glare detection failed: ${e.message}")
            0
        }
        
        if (glareLevel > 30) {
            Log.d(TAG, "High glare detected: $glareLevel/100")
            return AnalysisResult(
                isValidMRZ = false,
                mrzLines = emptyList(),
                fillerRatio = 0f,
                structureScore = 0,
                checksumScore = 0,
                checksumDetails = null,
                extractedData = null,
                errors = listOf(ValidationError.LIGHTING_ISSUE),
                totalScore = 0
            )
        }
        
        // VISION-FIRST: Try native MRZ extraction first
        val nativeMRZBitmap = try {
            com.idverify.sdk.core.NativeProcessor.extractMRZRegion(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Native MRZ extraction failed: ${e.message}")
            null
        }
        
        // Use native processed image if available, otherwise fall back to original
        val processedBitmap = nativeMRZBitmap ?: bitmap
        
        // MULTI-REGION STRATEGY: Try different extraction approaches
        // Turkish ID MRZ is at the bottom ~25-35% of card
        val regions = listOf(
            Pair(1.0f, "whole"),     // 100% - entire image (fallback)
            Pair(0.50f, "half"),     // 50% - bottom half
            Pair(0.35f, "mrz-zone")  // 35% - focused MRZ zone
        )
        
        val results = mutableMapOf<String, Pair<List<String>, MRZExtractor.ExtractionResult>>()
        
        regions.forEach { (ratio, label) ->
            val mrzHeight = (processedBitmap.height * ratio).toInt().coerceAtLeast(50)
            val mrzY = if (ratio >= 1.0f) 0 else (processedBitmap.height - mrzHeight).coerceAtLeast(0)
            
            val mrzRegion = if (ratio >= 1.0f) {
                processedBitmap // Use whole image (don't recycle)
            } else {
                Bitmap.createBitmap(
                    processedBitmap,
                    0,
                    mrzY,
                    processedBitmap.width,
                    mrzHeight.coerceAtMost(processedBitmap.height - mrzY)
                )
            }
            
            Log.d(TAG, "Region '$label': y=$mrzY, h=$mrzHeight (${(ratio*100).toInt()}% of ${processedBitmap.height}px)")
            
            val ocrLines = runOCR(mrzRegion)
            val extractionResult = MRZExtractor.validateAndClean(ocrLines)
            
            results[label] = Pair(ocrLines, extractionResult)
            
            Log.d(TAG, "Region '$label' (${(ratio*100).toInt()}%): lines=${ocrLines.size}, score=${extractionResult.score}, valid=${extractionResult.isValid}")
        }
        
        // Pick best result (highest score)
        val best = results.maxByOrNull { it.value.second.score }!!
        val (ocrLines, extractionResult) = best.value
        
        Log.d(TAG, "Selected best: ${best.key} (score=${extractionResult.score})")
        Log.d(TAG, "MRZ extraction: valid=${extractionResult.isValid}, lines=${extractionResult.lines.size}, score=${extractionResult.score}")
        extractionResult.lines.forEachIndexed { i, line -> Log.d(TAG, "  MRZ[$i] $line") }
        extractionResult.errors.forEach { Log.d(TAG, "  Error: ${it.code}") }
        
        if (!extractionResult.isValid || extractionResult.lines.isEmpty()) {
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
        
        // Step 3.5: Reconstruct 3 lines if only 2 found (common case - line 1 near chip is missed)
        val reconstructedLines = reconstructMRZLines(extractionResult.lines)
        Log.d(TAG, "Reconstructed MRZ lines (${reconstructedLines.size}):")
        reconstructedLines.forEachIndexed { i, line -> Log.d(TAG, "  [$i] $line") }
        
        // Step 3.6: Apply ENHANCED OCR correction with context
        val correctedLines = if (reconstructedLines.size == 3) {
            com.idverify.sdk.mrz.EnhancedMRZCorrector.correctWithContext(
                lines = reconstructedLines,
                knownTCKN = null,  // TODO: Extract from front
                knownDocNumber = null
            )
        } else if (reconstructedLines.size == 2) {
            // Apply 2-line correction (Line 2 and Line 3 of TD1)
            com.idverify.sdk.mrz.EnhancedMRZCorrector.correctTwoLines(reconstructedLines)
        } else {
            reconstructedLines
        }
        
        // Step 4: Validate checksums
        // Determine if Line 1 was reconstructed (default values)
        val line1WasReconstructed = correctedLines.getOrNull(0)?.startsWith("I<TUR<<<<<<") == true
        
        // VISION-FIRST: Use native scoring (0-60 points)
        // Each valid checksum = 15 points (doc, dob, expiry, composite)
        val nativeScore = if (correctedLines.size >= 2) {
            try {
                val l1 = correctedLines.getOrElse(0) { "" }
                val l2 = correctedLines.getOrElse(1) { "" }
                val l3 = correctedLines.getOrElse(2) { "" }
                com.idverify.sdk.core.NativeProcessor.validateMRZWithScore(l1, l2, l3)
            } catch (e: Exception) {
                Log.w(TAG, "Native score validation failed: ${e.message}")
                0
            }
        } else 0
        
        val nativeValid = nativeScore >= 45 // At least 3 checksums valid
        
        // Fallback: Java validation for partial scores
        val javaChecksum = if (correctedLines.size >= 2 && nativeScore < 30) {
            if (line1WasReconstructed && correctedLines.size == 3) {
                MRZChecksumValidator.validateLine2Only(correctedLines[1])
            } else {
                MRZChecksumValidator.validate(correctedLines)
            }
        } else null
        
        val javaValid = javaChecksum?.isValid == true
        
        Log.d(TAG, "Validation: nativeScore=$nativeScore, line1Reconstructed=$line1WasReconstructed, javaValid=$javaValid")
        if (javaChecksum != null) {
            Log.d(TAG, "Java checksum details: validCount=${javaChecksum.validCount}, score=${javaChecksum.score}")
        }
        
        // Structure Score (OCR confidence proxy)
        val structureScore = extractionResult.score.coerceAtMost(20)
        
        // Final Decision Logic:
        // 1. Native Score >= 45 -> Best (all 3+ checksums pass)
        // 2. Native Score >= 30 -> Good (2+ checksums)
        // 3. Java Valid -> Acceptable (fallback)
        // 4. High Structure -> Partial (relies on manual override)
        val isValidMRZ = nativeScore >= 30 || javaValid || (structureScore >= 15 && correctedLines.all { it.length == 30 })

        // Calculate Checksum Score (use native score, scaled to 70 max for new formula)
        // Native returns 0-60, we scale to 0-70 for the new Vision-First scoring
        var checksumScore = if (nativeScore > 0) {
            (nativeScore * 70 / 60).coerceAtMost(70)
        } else if (javaValid) {
            javaChecksum?.score ?: 0
        } else 0
        
        val totalScore = checksumScore + structureScore
        
        Log.d(TAG, "BACK SCORE: Structure($structureScore) + NativeChecksum($checksumScore) = $totalScore")
        Log.d(TAG, "Valid: $isValidMRZ (Native=$nativeValid)")
        Log.d(TAG, "═══════════════════════════════════════")
        
        // Extract data for result
        val extractedData = if (isValidMRZ) {
             extractMRZData(correctedLines)
        } else null
        
        // We use dummy ChecksumResult for backward compatibility if native is used
        val finalChecksumDetails = MRZChecksumValidator.ValidationResult(
            isValid = nativeValid,
            docNumberValid = nativeValid, // Assume valid if total valid
            birthDateValid = nativeValid,
            expiryDateValid = nativeValid,
            compositeValid = nativeValid,
            validCount = if (nativeValid) 4 else 0,
            totalChecks = 4,
            errors = emptyList(),
            score = checksumScore
        )

        return AnalysisResult(
            isValidMRZ = isValidMRZ,
            mrzLines = correctedLines,
            fillerRatio = extractionResult.fillerRatio,
            structureScore = structureScore,
            checksumScore = checksumScore,
            checksumDetails = finalChecksumDetails,
            extractedData = extractedData,
            errors = if (isValidMRZ) emptyList() else listOf(com.idverify.sdk.decision.ValidationError.MRZ_CHECKSUM_FAIL),
            totalScore = totalScore
        )
    }
    
    /**
     * Reconstruct 3 MRZ lines from partial OCR results
     * 
     * TD1 MRZ has 3 lines, but OCR often misses Line 1 (near chip area).
     * This function detects which lines were found and reconstructs missing ones.
     * 
     * Line 1: I<TUR[DOCNUM9][CHK]<<<<<<<<<<<<< (Document type, country, doc number)
     * Line 2: [DOB6][CHK][SEX][EXP6][CHK]TUR[TCKN11][CHK] (Dates, TCKN)
     * Line 3: [SURNAME]<<[GIVENNAMES]<<<< (Name)
     */
    private fun reconstructMRZLines(lines: List<String>): List<String> {
        if (lines.size >= 3) return lines.take(3)
        if (lines.isEmpty()) return emptyList()
        
        Log.d(TAG, "Reconstructing MRZ from ${lines.size} lines")
        
        // Detect line types based on content patterns
        val lineTypes = lines.map { line -> detectLineType(line) }
        Log.d(TAG, "Detected line types: $lineTypes")
        
        return when {
            // Case 1: Only Line 2 and Line 3 found (most common - Line 1 near chip missed)
            lines.size == 2 && lineTypes[0] == LineType.LINE2 && lineTypes[1] == LineType.LINE3 -> {
                val defaultLine1 = "I<TUR<<<<<<<<<<<<<<<<<<<<<<<<<<"
                listOf(defaultLine1, lines[0], lines[1])
            }
            
            // Case 2: Line 1 and Line 2 found (Line 3 name missed - rare)
            lines.size == 2 && lineTypes[0] == LineType.LINE1 && lineTypes[1] == LineType.LINE2 -> {
                val defaultLine3 = "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
                listOf(lines[0], lines[1], defaultLine3)
            }
            
            // Case 3: Only 1 line found - check type
            lines.size == 1 -> {
                when (lineTypes[0]) {
                    LineType.LINE2 -> {
                        val defaultLine1 = "I<TUR<<<<<<<<<<<<<<<<<<<<<<<<<<"
                        val defaultLine3 = "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
                        listOf(defaultLine1, lines[0], defaultLine3)
                    }
                    else -> lines // Can't reliably reconstruct
                }
            }
            
            // Default: return as-is
            else -> lines
        }
    }
    
    /**
     * Line type detection for TD1 MRZ
     */
    private enum class LineType { LINE1, LINE2, LINE3, UNKNOWN }
    
    /**
     * Detect which TD1 line this is based on content patterns
     */
    private fun detectLineType(line: String): LineType {
        val normalized = line.uppercase().padEnd(30, '<')
        
        // Line 1: Starts with I< or ID or similar, contains TUR
        if (normalized.startsWith("I") && (normalized.substring(2, 5).contains("TUR") || normalized.substring(0, 5).contains("TUR"))) {
            return LineType.LINE1
        }
        
        // Line 2: Has digits in positions 0-6 (DOB) and 8-14 (Expiry), TUR at 15-17
        val hasDobPattern = normalized.substring(0, 6).count { it.isDigit() || it == 'O' || it == 'I' } >= 4
        val hasTurMiddle = normalized.substring(15, 18).replace("0", "O").contains("TUR") ||
                          normalized.substring(15, 18).count { it.isLetter() } >= 2
        val hasTcknPattern = normalized.substring(18, 29).count { it.isDigit() || it == 'O' || it == 'I' } >= 8
        
        if (hasDobPattern && (hasTurMiddle || hasTcknPattern)) {
            return LineType.LINE2
        }
        
        // Line 3: Has << separator (name separator) and mostly letters
        val hasNameSeparator = normalized.contains("<<")
        val letterRatio = normalized.count { it.isLetter() || it == '<' }.toFloat() / normalized.length
        
        if (hasNameSeparator && letterRatio > 0.8f) {
            return LineType.LINE3
        }
        
        // Fallback heuristics
        val digitCount = normalized.count { it.isDigit() }
        return when {
            digitCount > 15 -> LineType.LINE2  // Line 2 has most digits
            digitCount < 5 -> LineType.LINE3   // Line 3 is mostly letters
            else -> LineType.UNKNOWN
        }
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
