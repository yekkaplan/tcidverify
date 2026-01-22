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
        
        const val STABILITY_FRAMES = 2
        const val MAX_VERIFY_ATTEMPTS = 5
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
        
        if (stability < MIN_STABILITY) {
            updateState(CaptureState.ALIGNING, "Sabit Tutun")
            emitQuality(cardConfidence, blurScore, stability, glareScore, "Sabitle")
            stableFrameCount = 0
            return
        }

        stableFrameCount++
        if (stableFrameCount < STABILITY_FRAMES) {
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
            
            // Find all potential 11-digit numbers
            val candidates = Regex("[0-9]{11}").findAll(text).map { it.value }.toList()
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

            var lines = result.text.lines().map { it.trim().uppercase() }.filter { it.length > 10 }
            Log.d(TAG, "Back Lines (ROI): $lines")
            
            // Fallback: If ROI failed or returned nothing, try full-frame OCR
            if (lines.size < 2) {
                Log.d(TAG, "ROI failed, trying full frame OCR for Back side...")
                val fullResult = textRecognizer.process(InputImage.fromBitmap(warped, 0)).await()
                val fullRaw = fullResult.text.replace("\n", " ")
                Log.d(TAG, "Back Raw Text (Full): $fullRaw")
                lines = fullResult.text.lines().map { it.trim().uppercase() }.filter { it.length > 15 }
                Log.d(TAG, "Back Lines (Full): $lines")
            }

            // Filter for MRZ-like patterns (lines starting with I<TUR or containing date patterns)
            val mrzCandidates = lines.filter { line ->
                line.contains("I<TUR") || 
                line.contains(Regex("[0-9]{6}")) || // Date pattern YYMMDD
                line.contains("<<") || // MRZ filler
                (line.length >= 25 && line.count { it == '<' } >= 3) // Typical MRZ characteristics
            }
            
            Log.d(TAG, "MRZ Candidates: $mrzCandidates")

            // Need at least 2 valid MRZ lines
            if (mrzCandidates.size >= 2) {
                // Prepare lines for native validation (pad to ensure we have 3 lines)
                val line1 = mrzCandidates.getOrNull(0) ?: ""
                val line2 = mrzCandidates.getOrNull(1) ?: ""
                val line3 = mrzCandidates.getOrNull(2) ?: ""
                
                Log.d(TAG, "Validating MRZ: L1='$line1', L2='$line2', L3='$line3'")
                
                // Use native MRZ validator (expects List<String>)
                val mrzScore = NativeProcessor.validateMRZ(listOf(line1, line2, line3))
                Log.d(TAG, "MRZ Score from native: $mrzScore")
                
                // Accept if score is reasonable (at least 3 checksums valid)
                if (mrzScore >= 45) { // Stricter: at least 3 checksums (15+15+15)
                    // Parse MRZ data
                    val extractedData = mutableMapOf<String, String>()
                    extractedData["mrzScore"] = mrzScore.toString()
                    extractedData["mrzValid"] = "true"
                    
                    // Parse Line 1: I<TUR[DOCNUM9][CHK]...
                    if (line1.length >= 15) {
                        val docNum = line1.substring(5, 14).replace("<", "").trim()
                        if (docNum.isNotEmpty()) {
                            extractedData["documentNumber"] = docNum
                            Log.d(TAG, "Parsed Document Number: $docNum")
                        }
                    }
                    
                    // Parse Line 2: [DOB6][CHK][SEX][EXP6][CHK]TUR...
                    if (line2.length >= 15) {
                        // Birth Date (positions 0-5)
                        val dobStr = line2.substring(0, 6).replace(Regex("[^0-9]"), "")
                        if (dobStr.length == 6) {
                            try {
                                val dob = "${dobStr.substring(4, 6)}.${dobStr.substring(2, 4)}.19${dobStr.substring(0, 2)}"
                                extractedData["birthDate"] = dob
                                Log.d(TAG, "Parsed Birth Date: $dob")
                            } catch (e: Exception) {
                                Log.e(TAG, "Birth date parse error", e)
                            }
                        }
                        
                        // Sex (position 7)
                        if (line2.length > 7) {
                            val sex = when (line2[7]) {
                                'M' -> "Erkek"
                                'F' -> "Kadın"
                                else -> line2[7].toString()
                            }
                            extractedData["sex"] = sex
                            Log.d(TAG, "Parsed Sex: $sex")
                        }
                        
                        // Expiry Date (positions 8-13)
                        if (line2.length >= 14) {
                            val expStr = line2.substring(8, 14).replace(Regex("[^0-9]"), "")
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
                        
                        // TCKN from MRZ (positions 18-28, 11 digits)
                        if (line2.length >= 29) {
                            val tckn = line2.substring(18, 29).replace(Regex("[^0-9]"), "")
                            if (tckn.length == 11) {
                                extractedData["tcknFromMRZ"] = tckn
                                Log.d(TAG, "Parsed TCKN from MRZ: $tckn")
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
                            extractedData["nameFromMRZ"] = nameParts[1].replace("<", " ").trim()
                            Log.d(TAG, "Parsed Name from MRZ: ${nameParts[1]}")
                        }
                    }
                    
                    Log.d(TAG, "All MRZ extracted data: $extractedData")
                    
                    return@withContext CaptureResult(
                        warped, 
                        extractedData, 
                        mrzScore, 
                        true
                    )
                } else {
                    Log.d(TAG, "MRZ validation failed. Score too low: $mrzScore")
                }
            } else {
                Log.d(TAG, "Not enough MRZ candidates found: ${mrzCandidates.size}")
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
