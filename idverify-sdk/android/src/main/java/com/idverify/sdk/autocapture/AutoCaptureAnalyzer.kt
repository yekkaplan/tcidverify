package com.idverify.sdk.autocapture

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.idverify.sdk.core.NativeProcessor
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AutoCaptureAnalyzer(
    private val isBackSide: Boolean = false,
    private val onStateChange: (CaptureState, String) -> Unit,
    private val onQualityUpdate: (QualityMetrics) -> Unit,
    private val onCaptured: (CaptureResult) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "AutoCaptureAnalyzer"
        
        // Quality Thresholds (Balanced for real-world use)
        const val MIN_CARD_CONFIDENCE = 15
        const val MIN_BLUR_SCORE = 40f
        const val MIN_STABILITY = 0.15f
        const val MAX_GLARE = 50
        
        // Stricter thresholds for back-side MRZ (requires higher stability)
        const val MIN_STABILITY_BACK = 0.20f  // 20% stability for MRZ (Lowered for usability)
        const val STABILITY_FRAMES_BACK = 3   // 3 stable frames
        
        const val STABILITY_FRAMES = 2
        const val MAX_VERIFY_ATTEMPTS = 5
        
        /**
         * Correct common OCR errors for Turkish ID cards
         * - For TCKN: Only digits (I→1, O→0, S→5, etc.)
         * - For MRZ: Specific character set
         */
        fun correctOCRErrors(text: String, forTCKN: Boolean = false): String {
            var corrected = text.uppercase()
            
            if (forTCKN) {
                // TCKN should only contain digits
                corrected = corrected
                    .replace('I', '1')
                    .replace('L', '1')
                    .replace('O', '0')
                    .replace('D', '0')
                    .replace('Q', '0')
                    .replace('S', '5')
                    .replace('Z', '2')
                    .replace('G', '6')
                    .replace('B', '8')
                    .filter { it.isDigit() }
            } else {
                // MRZ character corrections
                corrected = corrected
                    .replace('0', 'O') // In MRZ, O is more common than 0 in names
                    .replace('.', '<')
                    .replace(' ', '<')
                    .replace('«', '<')  // OCR often reads < as «
            }
            
            return corrected
        }
        
        /**
         * Normalizes an MRZ line to exactly 30 characters (ICAO TD1 standard).
         * Handles OCR errors that produce 28-32 character lines.
         * @param line Raw MRZ line from OCR (after correction)
         * @return Normalized 30-character line, or null if not a valid MRZ candidate
         */
        fun normalizeMRZLine(line: String): String? {
            // Only process lines that are close to 30 characters (allow ±2 for OCR errors)
            if (line.length !in 28..32) return null
            
            // Must contain only valid MRZ characters (A-Z, 0-9, <)
            // Note: Turkish characters (İ, Ç, etc.) should NOT be in MRZ, they get transliterated
            if (!line.matches(Regex("^[A-Z0-9<]+$"))) return null
            
            return when {
                line.length == 30 -> line  // Perfect, no change needed
                line.length < 30 -> line.padEnd(30, '<')  // Pad with '<' to reach 30
                else -> line.take(30)  // Trim to 30 (remove excess '<' at end)
            }
        }
        
        /**
         * Calculates MRZ checksum using 7-3-1 weighting
         */
        fun calculateMRZChecksum(data: String): Int {
            val weights = intArrayOf(7, 3, 1)
            var sum = 0
            for (i in data.indices) {
                val c = data[i]
                val value = when {
                    c.isDigit() -> c - '0'
                    c in 'A'..'Z' -> c - 'A' + 10
                    c == '<' -> 0
                    else -> 0
                }
                sum += value * weights[i % 3]
            }
            return sum % 10
        }

        /**
         * Tries to correct a string to match the given check digit by swapping common OCR error characters
         */
        fun tryCorrectWithChecksum(raw: String, checkDigit: Int): String? {
            if (calculateMRZChecksum(raw) == checkDigit) return raw
            
            // Try correcting common OCR errors one by one
            val replacements = mapOf(
                'O' to '0', '0' to 'O',
                'I' to '1', '1' to 'I',
                'L' to '1',
                'S' to '5', '5' to 'S',
                'Z' to '2', '2' to 'Z',
                'B' to '8', '8' to 'B',
                'D' to '0', 
                'Q' to '0',
                'G' to '6', '6' to 'G'
            )
            
            val chars = raw.toCharArray()
            for (i in chars.indices) {
                val original = chars[i]
                if (replacements.containsKey(original)) {
                    chars[i] = replacements[original]!!
                    if (calculateMRZChecksum(String(chars)) == checkDigit) {
                        Log.d(TAG, "Checksum correction success: $raw -> ${String(chars)}")
                        return String(chars)
                    }
                    chars[i] = original // Backtrack
                }
            }
            return null
        }
    }

    enum class CaptureState { SEARCHING, ALIGNING, VERIFYING, CAPTURED, ERROR }

    data class QualityMetrics(
        val cardConfidence: Int = 0,
        val blurScore: Float = 0f,
        val stability: Float = 0f,
        val glareScore: Int = 0,
        val state: CaptureState = CaptureState.SEARCHING,
        val message: String = ""
    )

    data class CaptureResult(
        val warpedImage: Bitmap,
        val extractedData: Map<String, String>,
        val mrzScore: Int,
        val isValid: Boolean
    )

    /**
     * Represents a single MRZ reading from one frame
     * Used for multi-frame validation to select the best result
     */
    data class MRZCandidate(
        val lines: List<String>,           // 3 MRZ lines
        val score: Int,                    // Native checksum score
        val tckn: String,                  // Extracted TCKN for matching
        val extractedData: Map<String, String>,  // All parsed fields
        val timestamp: Long = System.currentTimeMillis()
    )

    private var currentState: CaptureState? = null
    private var stableFrameCount = 0
    private var verifyAttempts = 0
    private var previousFrame: Bitmap? = null
    private val isProcessing = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private var frameCount = 0L
    
    // Multi-frame MRZ validation
    private val mrzCandidates = mutableListOf<MRZCandidate>()
    private val MIN_MRZ_FRAMES = 2  // Require 2 matching frames (Reduced from 3 for speed)
    private val MRZ_CANDIDATE_TIMEOUT_MS = 10000L  // Keep candidates for 10s (Increased from 2s to allow slower scans)

    override fun analyze(image: ImageProxy) {
        if (isProcessing.get()) {
            image.close()
            return
        }
        if (currentState == CaptureState.CAPTURED) {
            image.close()
            return
        }

        isProcessing.set(true)
        frameCount++

        scope.launch {
            try {
                if (!NativeProcessor.isAvailable()) {
                    updateState(CaptureState.ERROR, "Native Lib Error")
                    return@launch
                }
                
                val bitmap = image.toBitmap()
                processFrame(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
            } finally {
                isProcessing.set(false)
                image.close()
            }
        }
    }

    private suspend fun processFrame(bitmap: Bitmap) {
        // Step 1: Detect
        val cardConfidence = NativeProcessor.getCardConfidence(bitmap)
        
        if (cardConfidence < MIN_CARD_CONFIDENCE) {
            resetToSearching()
            emitQuality(cardConfidence, 0f, 0f, 0, "Aranıyor %$cardConfidence")
            return
        }

        // Step 2: Warp
        val warped = NativeProcessor.warpToID1(bitmap) ?: return
        Log.d(TAG, "Warped Bitmap: ${warped.width}x${warped.height}, Config: ${warped.config}, Bytes: ${warped.byteCount}")

        // Step 3: Quality
        val blurScore = NativeProcessor.calculateBlurScore(warped)
        val glareScore = NativeProcessor.detectGlare(warped)
        val stability = previousFrame?.let { NativeProcessor.calculateStability(warped, it) } ?: 1.0f

        previousFrame?.recycle()
        previousFrame = warped.config?.let { warped.copy(it, true) }

        // Step 4: Validate Quality
        if (blurScore < MIN_BLUR_SCORE) {
            updateState(CaptureState.ALIGNING, "Netleştirin ($blurScore)")
            emitQuality(cardConfidence, blurScore, stability, glareScore, "Bulanık")
            stableFrameCount = 0
            return
        }
        
        // Use stricter stability for back side (MRZ is sensitive to blur)
        val minStability = if (isBackSide) MIN_STABILITY_BACK else MIN_STABILITY
        val requiredFrames = if (isBackSide) STABILITY_FRAMES_BACK else STABILITY_FRAMES

        if (stability < minStability) {
            updateState(CaptureState.ALIGNING, "Sabit Tutun")
            emitQuality(cardConfidence, blurScore, stability, glareScore, "Sabitle")
            stableFrameCount = 0
            return
        }

        stableFrameCount++
        if (stableFrameCount < requiredFrames) {
            updateState(CaptureState.ALIGNING, "Bekleyin...")
            emitQuality(cardConfidence, blurScore, stability, glareScore, "Hazır...")
            return
        }

        // Step 5: Verify
        updateState(CaptureState.VERIFYING, "Okunuyor...")
        emitQuality(cardConfidence, blurScore, stability, glareScore, "Okunuyor...")

        // Debug: Log verifying attempt
        Log.i(TAG, "VERIFYING: Attempt $verifyAttempts. BackSide=$isBackSide")

        val result = if (isBackSide) verifyBackSide(warped) else verifyFrontSide(warped)

        if (result != null) {
            Log.i(TAG, "CAPTURED! Valid TCKN/MRZ found.")
            updateState(CaptureState.CAPTURED, "Başarılı!")
            onCaptured(result)
        } else {
            verifyAttempts++
            // Force feedback to user about what is being read
            val msg = if (verifyAttempts % 3 == 0) "Kodu Okuyamadım" else "Okunuyor..."
            updateState(CaptureState.VERIFYING, msg)
            
            if (verifyAttempts > MAX_VERIFY_ATTEMPTS + 5) {
                // Reset to try focusing again
                stableFrameCount = 0
                verifyAttempts = 0
            }
        }
    }

    private suspend fun verifyFrontSide(warped: Bitmap): CaptureResult? = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(warped, 0)
            val result = textRecognizer.process(inputImage).await()
            val text = result.text.replace("\n", " ")
            
            Log.d(TAG, "OCR Raw Text: $text")
            
            // Apply OCR error correction for TCKN
            val correctedText = correctOCRErrors(text, forTCKN = true)
            Log.d(TAG, "OCR Corrected Text: $correctedText")
            
            // Find all potential 11-digit numbers
            val candidates = Regex("[0-9]{11}").findAll(correctedText).map { it.value }.toList()
            Log.d(TAG, "OCR Candidates: $candidates")
            
            for (tckn in candidates) {
                val isValid = NativeProcessor.validateTCKNNative(tckn)
                if (isValid) {
                    Log.d(TAG, "Front VALID TCKN: $tckn")
                    
                    // Extract all other fields from front face
                    val extractedData = mutableMapOf<String, String>()
                    extractedData["tckn"] = tckn
                    
                    // Extract Surname
                    try {
                        val surnameRoi = NativeProcessor.extractROI(warped, NativeProcessor.ROIType.SURNAME, false)
                        if (surnameRoi != null) {
                            val surnameResult = textRecognizer.process(InputImage.fromBitmap(surnameRoi, 0)).await()
                            val surname = surnameResult.text.lines()
                                .map { it.trim().uppercase() }
                                .filter { it.isNotEmpty() && it.length > 2 }
                                .firstOrNull() ?: ""
                            Log.d(TAG, "Extracted Surname: $surname")
                            if (surname.isNotEmpty()) extractedData["surname"] = surname
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Surname extraction failed", e)
                    }
                    
                    // Extract Name
                    try {
                        val nameRoi = NativeProcessor.extractROI(warped, NativeProcessor.ROIType.NAME, false)
                        if (nameRoi != null) {
                            val nameResult = textRecognizer.process(InputImage.fromBitmap(nameRoi, 0)).await()
                            val name = nameResult.text.lines()
                                .map { it.trim().uppercase() }
                                .filter { it.isNotEmpty() && it.length > 2 }
                                .firstOrNull() ?: ""
                            Log.d(TAG, "Extracted Name: $name")
                            if (name.isNotEmpty()) extractedData["name"] = name
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Name extraction failed", e)
                    }
                    
                    // Extract Birth Date
                    try {
                        val birthdateRoi = NativeProcessor.extractROI(warped, NativeProcessor.ROIType.BIRTHDATE, false)
                        if (birthdateRoi != null) {
                            val birthdateResult = textRecognizer.process(InputImage.fromBitmap(birthdateRoi, 0)).await()
                            val birthdate = birthdateResult.text.replace("\n", " ").trim()
                            Log.d(TAG, "Extracted Birthdate: $birthdate")
                            if (birthdate.isNotEmpty()) extractedData["birthdate"] = birthdate
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Birthdate extraction failed", e)
                    }
                    
                    // Extract Serial Number
                    try {
                        val serialRoi = NativeProcessor.extractROI(warped, NativeProcessor.ROIType.SERIAL, false)
                        if (serialRoi != null) {
                            val serialResult = textRecognizer.process(InputImage.fromBitmap(serialRoi, 0)).await()
                            val serial = serialResult.text.replace("\n", " ").trim()
                            Log.d(TAG, "Extracted Serial: $serial")
                            if (serial.isNotEmpty()) extractedData["serial"] = serial
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Serial extraction failed", e)
                    }
                    
                    Log.d(TAG, "All extracted data: $extractedData")
                    return@withContext CaptureResult(warped, extractedData, 80, true)
                } else {
                    Log.w(TAG, "TCKN Check Fail: $tckn")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "OCR Error", e)
            null
        }
    }

    private suspend fun verifyBackSide(warped: Bitmap): CaptureResult? = withContext(Dispatchers.IO) {
        try {
            val roi = NativeProcessor.extractROI(warped, NativeProcessor.ROIType.MRZ, true) ?: warped
            val inputImage = InputImage.fromBitmap(roi, 0)
            val result = textRecognizer.process(inputImage).await()
            val rawText = result.text.replace("\n", " ")
            Log.d(TAG, "Back Raw Text (ROI): $rawText")

            var lines = result.text.lines().map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            Log.d(TAG, "Back Lines (ROI): $lines")
            
            // Initial normalization attempt on ROI result
            var correctedLines = lines.map { correctOCRErrors(it, forTCKN = false) }
            var normalizedMRZLines = correctedLines.mapNotNull { normalizeMRZLine(it) }

            // Fallback: If ROI normalization yielded < 3 valid lines, try full-frame OCR
            // This fixes the issue where ROI returns garbage text (but enough lines) and we skip Full Frame
            if (normalizedMRZLines.size < 3) {
                Log.d(TAG, "ROI Yielded only ${normalizedMRZLines.size} valid lines. Falling back to Full Frame...")
                val fullResult = textRecognizer.process(InputImage.fromBitmap(warped, 0)).await()
                val fullRaw = fullResult.text.replace("\n", " ")
                Log.d(TAG, "Back Raw Text (Full): $fullRaw")
                
                lines = fullResult.text.lines().map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                Log.d(TAG, "Back Lines (Full): $lines")
                
                // Re-process full frame lines
                correctedLines = lines.map { correctOCRErrors(it, forTCKN = false) }
                normalizedMRZLines = correctedLines.mapNotNull { normalizeMRZLine(it) }
            }
            
            Log.d(TAG, "Final Normalized MRZ Lines (30 char each): $normalizedMRZLines")

            // CRITICAL: Turkish ID cards use TD1 format - MUST have exactly 3 lines
            if (normalizedMRZLines.size >= 3) {
                // Take first 3 valid lines
                val line1 = normalizedMRZLines[0]
                val line2 = normalizedMRZLines[1]
                var line3 = normalizedMRZLines[2]
                
                // Fix common OCR error in Line 3: Separator << read as <C, C<, K<, etc.
                line3 = line3.replace("<C", "<<")
                             .replace("C<", "<<")
                             .replace("<K", "<<")
                             .replace("K<", "<<")
                
                // Validate TD1 format structure
                val isValidTD1 = line1.startsWith("I<TUR") && 
                                 line2.length == 30 && 
                                 line3.length == 30
                
                if (!isValidTD1) {
                    Log.d(TAG, "Invalid TD1 format: Line1 doesn't start with I<TUR or wrong lengths")
                    return@withContext null
                }
                
                Log.d(TAG, "Valid TD1 MRZ found!")
                Log.d(TAG, "Validating MRZ: L1='$line1', L2='$line2', L3='$line3'")
                
                // Use native MRZ validator (expects List<String>)
                val mrzScore = NativeProcessor.validateMRZ(listOf(line1, line2, line3))
                Log.d(TAG, "MRZ Score from native: $mrzScore")
                
                // Accept if score is reasonable (balanced threshold)
                if (mrzScore >= 40) { // Lowered from 45: at least 2-3 checksums valid
                    // Parse MRZ data
                    val extractedData = mutableMapOf<String, String>()
                    extractedData["mrzScore"] = mrzScore.toString()
                    extractedData["mrzValid"] = "true"
                    
                    // Parse Line 1: I<TUR[DOCNUM9][CHK]...
                    // Parse Line 1: I<TUR[DOCNUM9][CHK]...
                    if (line1.length >= 15) {
                        // Document number is at positions 5-14 (9 chars + 1 check digit)
                        val docNumRaw = line1.substring(5, 14)
                        val checkDigitChar = line1[14]
                        
                        var processedDocNum = docNumRaw
                        
                        // Try to correct using checksum if check digit is valid
                        if (checkDigitChar.isDigit()) {
                            val checkDigit = checkDigitChar - '0'
                            val corrected = tryCorrectWithChecksum(docNumRaw, checkDigit)
                            if (corrected != null) {
                                processedDocNum = corrected
                            } else {
                                Log.w(TAG, "DocNum Checksum Failed. Raw: $docNumRaw, Check: $checkDigit, Calc: ${calculateMRZChecksum(docNumRaw)}")
                            }
                        }
                        
                        // Clean up filler chars for result
                        val docNum = processedDocNum.replace("<", "").trim()
                        if (docNum.isNotEmpty()) {
                            extractedData["documentNumber"] = docNum
                            Log.d(TAG, "Parsed Document Number: $docNum")
                        }
                    }
                    
                    // Extract TCKN from Line 1 (Optional Data field: positions 15-29)
                    // Format: [Optional Data (15 chars)]
                    // Turkey uses this for TCKN, but sometimes there is padding << before it
                    if (line1.length >= 26) {
                        val optionalData = line1.substring(15, 30.coerceAtMost(line1.length))
                        // Find first alignment of 11 digits
                        // Expand O->0 correction to whole optional field
                        val correctedOptional = optionalData.replace('O', '0')
                        val tcknMatch = Regex("[0-9]{11}").find(correctedOptional)
                        
                        if (tcknMatch != null) {
                            val tckn = tcknMatch.value
                            if (NativeProcessor.validateTCKNNative(tckn)) {
                                extractedData["tcknFromMRZ"] = tckn
                                Log.d(TAG, "Parsed TCKN from MRZ Line 1: $tckn")
                            }
                        }
                    }
                    
                    // Parse Line 2: [DOB6][CHK][SEX][EXP6][CHK]TUR...
                    if (line2.length >= 15) {
                        // Birth Date (positions 0-5) - MUST be all digits, so O→0
                        val dobRaw = line2.substring(0, 6).replace('O', '0')
                        val dobStr = dobRaw.replace(Regex("[^0-9]"), "")
                        if (dobStr.length == 6) {
                            try {
                                val dob = "${dobStr.substring(4, 6)}.${dobStr.substring(2, 4)}.19${dobStr.substring(0, 2)}"
                                extractedData["birthDate"] = dob
                                Log.d(TAG, "Parsed Birth Date: $dob")
                            } catch (e: Exception) {
                                Log.e(TAG, "Birth date parse error", e)
                            }
                        }
                        
                        // Sex (position 7) - should be M, F, or < (unknown)
                        if (line2.length > 7) {
                            val sexChar = line2[7]
                            val sex = when (sexChar) {
                                'M' -> "Erkek"
                                'F', '0' -> "Kadın"  // 0 in MRZ means Female in some formats
                                else -> sexChar.toString()
                            }
                            extractedData["sex"] = sex
                            Log.d(TAG, "Parsed Sex: $sex")
                        }
                        
                        // Expiry Date (positions 8-13) - MUST be all digits, so O→0
                        if (line2.length >= 14) {
                            val expRaw = line2.substring(8, 14).replace('O', '0')
                            val expStr = expRaw.replace(Regex("[^0-9]"), "")
                            if (expStr.length == 6) {
                                try {
                                    val exp = "${expStr.substring(4, 6)}.${expStr.substring(2, 4)}.20${expStr.substring(0, 2)}"
                                    extractedData["expiryDate"] = exp
                                    Log.d(TAG, "Parsed Expiry Date: $exp")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Expiry date parse error", e)
                                }
                            }
                        }
                        
                        // TCKN from MRZ (positions 18-28, 11 digits) in Line 2
                        // Search in the valid range for TCKN
                        if (line2.length >= 18) {
                            // Look in the second half (after optional data start)
                            val searchRegion = line2.substring(15.coerceAtMost(line2.length))
                            val correctedRegion = searchRegion.replace('O', '0')
                            
                            val tcknMatch = Regex("[0-9]{11}").find(correctedRegion)
                            if (tcknMatch != null) {
                                val tckn = tcknMatch.value
                                if (NativeProcessor.validateTCKNNative(tckn)) {
                                    extractedData["tcknFromMRZ"] = tckn
                                    Log.d(TAG, "Parsed TCKN from MRZ Line 2: $tckn")
                                }
                            }
                        }
                    }
                    
                    // Parse Line 3: SURNAME<<FIRSTNAME
                    if (line3.isNotEmpty()) {
                        val nameParts = line3.split("<<").filter { it.isNotEmpty() }
                        if (nameParts.isNotEmpty()) {
                            extractedData["surnameFromMRZ"] = nameParts[0].replace("<", " ").trim()
                            Log.d(TAG, "Parsed Surname from MRZ: ${nameParts[0]}")
                        }
                        if (nameParts.size > 1) {
                            val nameRaw = nameParts.drop(1).joinToString(" ")
                            extractedData["nameFromMRZ"] = nameRaw.replace("<", " ").trim()
                            Log.d(TAG, "Parsed Name from MRZ: $nameRaw")
                        }
                    }
                    
                    Log.d(TAG, "All MRZ extracted data: $extractedData")
                    
                    // Multi-frame validation: Don't accept immediately
                    // Instead, add to candidates and check if we have 3 matching frames
                    val tckn = extractedData["tcknFromMRZ"] ?: ""
                    
                    if (tckn.isNotEmpty() && tckn.length == 11) {
                        // Add this candidate to the list
                        val candidate = MRZCandidate(
                            lines = listOf(line1, line2, line3),
                            score = mrzScore,
                            tckn = tckn,
                            extractedData = extractedData
                        )
                        
                        // Remove old candidates (older than 2 seconds)
                        val now = System.currentTimeMillis()
                        mrzCandidates.removeAll { now - it.timestamp > MRZ_CANDIDATE_TIMEOUT_MS }
                        
                        // Add new candidate
                        mrzCandidates.add(candidate)
                        Log.d(TAG, "Added MRZ candidate. Total candidates: ${mrzCandidates.size}")
                        
                        // Check if we have at least MIN_MRZ_FRAMES with the same TCKN
                        val matchingCandidates = mrzCandidates.filter { it.tckn == tckn }
                        Log.d(TAG, "Matching TCKN candidates: ${matchingCandidates.size} (need $MIN_MRZ_FRAMES)")
                        
                        if (matchingCandidates.size >= MIN_MRZ_FRAMES) {
                            // Select the best candidate (highest score)
                            val bestCandidate = matchingCandidates.maxByOrNull { it.score }!!
                            Log.d(TAG, "✅ Multi-frame validation passed! Best score: ${bestCandidate.score}")
                            
                            // Clear candidates for next capture
                            mrzCandidates.clear()
                            
                            return@withContext CaptureResult(
                                warped,
                                bestCandidate.extractedData,
                                bestCandidate.score,
                                true
                            )
                        } else {
                            Log.d(TAG, "⏳ Waiting for more matching frames...")
                            return@withContext null  // Continue capturing
                        }
                    } else {
                        Log.d(TAG, "Invalid TCKN extracted: $tckn")
                    }
                } else {
                    Log.d(TAG, "MRZ validation failed. Score too low: $mrzScore")
                }
            } else {
                Log.d(TAG, "Not enough MRZ candidates found: ${normalizedMRZLines.size}")
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Back Error", e)
            null
        }
    }

    private fun resetToSearching() {
        if (currentState != CaptureState.SEARCHING) {
            currentState = CaptureState.SEARCHING
            stableFrameCount = 0
            onStateChange(CaptureState.SEARCHING, "Kartı Gösterin")
        }
    }

    private fun updateState(state: CaptureState, message: String) {
        currentState = state
        onStateChange(state, message)
    }

    private fun emitQuality(c: Int, b: Float, s: Float, g: Int, m: String) {
        onQualityUpdate(QualityMetrics(c, b, s, g, currentState ?: CaptureState.SEARCHING, m))
    }

    fun release() {
        scope.cancel()
        previousFrame?.recycle()
    }
    
    fun reset() {
        currentState = CaptureState.SEARCHING
        stableFrameCount = 0
        verifyAttempts = 0
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = 
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) {} }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
