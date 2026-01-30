package com.idverify.sdk.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.idverify.sdk.decision.DecisionEngine
import com.idverify.sdk.decision.DecisionResult
import com.idverify.sdk.decision.ValidationError
import com.idverify.sdk.detection.QualityGate
import com.idverify.sdk.utils.Constants
import com.idverify.sdk.utils.ImageUtils.toBitmap
import com.idverify.sdk.utils.ImageUtils.toScaledBitmap
import com.idverify.sdk.utils.ImageUtils.toJpegBytes
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * ID Verification Engine - Refactored Core Implementation
 * 
 * This is the main entry point for the ID verification SDK.
 * Uses the new deterministic, scoring-based architecture.
 * 
 * Architecture:
 * - Camera → Quality Gate → Aspect Ratio → Pipeline → Scoring → Decision
 * - Multi-frame analysis (3-5 frames)
 * - Separate pipelines for front/back
 * - Pure ML Kit + deterministic rules
 * 
 * Key Principles:
 * - Minimize false positives (reject non-ID images early)
 * - Quality gate before ANY OCR
 * - Strict aspect ratio validation (1.55-1.62)
 * - Multi-frame confirmation
 */
class IDVerificationEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "IDVerificationEngine"
    }
    
    // Decision engine (core logic)
    private val decisionEngine = DecisionEngine()
    
    // Camera components
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State
    private var currentMode = ScanMode.IDLE
    private var callback: VerificationCallback? = null
    
    // Frame storage for manual capture
    private var lastBitmap: Bitmap? = null
    private var lastQualityResult: QualityGate.QualityResult? = null
    
    // Captured images
    private var frontImageBytes: ByteArray? = null
    private var backImageBytes: ByteArray? = null
    private var frontDecisionResult: DecisionResult? = null
    private var backDecisionResult: DecisionResult? = null
    
    // Performance: frame timing
    private var lastAnalysisTime = 0L
    
    /**
     * Scan mode
     */
    enum class ScanMode {
        IDLE,
        SCANNING_FRONT,
        FRONT_CAPTURED,
        SCANNING_BACK,
        BACK_CAPTURED,
        PROCESSING,
        COMPLETED,
        ERROR
    }
    
    /**
     * Verification callback interface
     */
    interface VerificationCallback {
        fun onModeChanged(mode: ScanMode, message: String)
        fun onQualityUpdate(quality: QualityGate.QualityResult)
        fun onFrameAnalyzed(result: DecisionResult)
        fun onFrontCaptured(imageBytes: ByteArray, result: DecisionResult)
        fun onBackCaptured(imageBytes: ByteArray, result: DecisionResult)
        fun onVerificationComplete(result: VerificationResult)
        fun onError(error: VerificationError)
    }
    
    /**
     * Final verification result
     */
    data class VerificationResult(
        val isValid: Boolean,
        val totalScore: Int,
        val decision: DecisionResult.Decision,
        val frontImage: ByteArray,
        val backImage: ByteArray,
        val frontResult: DecisionResult,
        val backResult: DecisionResult,
        val extractedData: ExtractedIDData?
    )
    
    /**
     * Extracted ID data
     */
    data class ExtractedIDData(
        val tcKimlikNo: String?,
        val documentNumber: String?,
        val surname: String?,
        val givenNames: String?,
        val birthDate: String?,
        val expiryDate: String?,
        val sex: String?,
        val nationality: String?
    )
    
    /**
     * Verification error
     */
    data class VerificationError(
        val code: String,
        val message: String,
        val messageTr: String,
        val validationErrors: List<ValidationError> = emptyList()
    )
    
    // ==================== Public API ====================
    
    /**
     * Start camera preview and scanning
     */
    fun startScanning(
        previewView: PreviewView,
        callback: VerificationCallback,
        lifecycleOwner: LifecycleOwner? = null
    ) {
        if (!hasCameraPermission()) {
            callback.onError(VerificationError(
                code = "PERMISSION_DENIED",
                message = "Camera permission required",
                messageTr = "Kamera izni gerekli"
            ))
            return
        }
        
        this.callback = callback
        setMode(ScanMode.SCANNING_FRONT, "Kimlik kartının ön yüzünü yerleştirin")
        
        startCamera(previewView, lifecycleOwner)
    }
    
    /**
     * Stop scanning
     */
    fun stopScanning() {
        android.util.Log.d(TAG, "stopScanning called")
        cameraProvider?.unbindAll()
        setMode(ScanMode.IDLE, "Tarama durduruldu")
    }
    
    /**
     * Capture front side manually
     * @return true if capture successful
     */
    suspend fun captureFrontManually(): Boolean = withContext(Dispatchers.Default) {
        Log.d(TAG, "captureFrontManually başladı")
        val originalBitmap = lastBitmap ?: return@withContext false
        
        // CRITICAL: Create a copy to avoid recycle issues
        // The original bitmap might be recycled by analyzeFrame
        if (originalBitmap.isRecycled) {
            android.util.Log.e(TAG, "Original bitmap already recycled")
            return@withContext false
        }
        
        // Create immutable copy
        val bitmap = Bitmap.createBitmap(originalBitmap)
        val quality = lastQualityResult
        
        Log.d(TAG, "Bitmap size: ${bitmap.width}x${bitmap.height}, quality passed: ${quality?.passed}")
        
        // More lenient quality check - only reject if severely bad
        if (quality == null) {
            android.util.Log.w(TAG, "Quality check not available")
            // Continue anyway - let score decide
        } else if (!quality.passed) {
            // Check if quality is just slightly below threshold
            val blurOk = quality.blurScore >= 0.5f
            val glareOk = quality.glareScore >= 0.5f
            val brightnessOk = quality.brightnessScore >= 0.4f
            
            if (!blurOk && !glareOk && !brightnessOk) {
                android.util.Log.w(TAG, "Quality severely insufficient for front capture")
                return@withContext false
            }
            // If at least one quality metric is OK, continue
            Log.d(TAG, "Quality borderline but continuing: blur=$blurOk, glare=$glareOk, brightness=$brightnessOk")
        }
        
        // CRITICAL: For OCR, use optimal resolution (1080p max)
        // Full resolution (2992x2992) is too large for OCR and causes failures
        // Scale to 1080p for analysis, but keep full res for storage
        val analysisBitmap = if (bitmap.width > 1080 || bitmap.height > 1080) {
            val scale = 1080f / maxOf(bitmap.width, bitmap.height)
            val scaledWidth = (bitmap.width * scale).toInt()
            val scaledHeight = (bitmap.height * scale).toInt()
            Log.d(TAG, "Scaling for OCR: ${bitmap.width}x${bitmap.height} -> ${scaledWidth}x${scaledHeight}")
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            bitmap
        }
        
        Log.d(TAG, "analyzeFrontSide çağrılıyor (${analysisBitmap.width}x${analysisBitmap.height})")
        val result = decisionEngine.analyzeFrontSide(analysisBitmap)
        Log.d(TAG, "analyzeFrontSide tamamlandı: totalScore=${result.totalScore}")
        
        // Recycle scaled bitmap if created
        if (analysisBitmap != bitmap && !analysisBitmap.isRecycled) {
            analysisBitmap.recycle()
        }
        
        // Check if FRONT-SPECIFIC score is sufficient
        // Use frontTextScore + aspect ratio (not total which includes MRZ)
        val frontScore = result.scoreBreakdown.frontTextScore + 
                        result.scoreBreakdown.aspectRatioScore +
                        result.scoreBreakdown.tcknAlgorithmScore
        
        // For front capture, need at least 20 points (out of 50 possible for front-only)
        // But be more lenient if aspect ratio is good (10 points)
        val frontThreshold = if (result.scoreBreakdown.aspectRatioScore >= 10) {
            18  // If aspect ratio is good, lower threshold slightly
        } else {
            20  // Otherwise require full threshold
        }
        
        Log.d(TAG, "Front score breakdown: AR=${result.scoreBreakdown.aspectRatioScore}, FT=${result.scoreBreakdown.frontTextScore}, TC=${result.scoreBreakdown.tcknAlgorithmScore}")
        Log.d(TAG, "Front total: $frontScore (threshold: $frontThreshold)")
        
        if (frontScore < frontThreshold) {
            android.util.Log.w(TAG, "Front side score too low: $frontScore (need $frontThreshold)")
            return@withContext false
        }
        
        android.util.Log.d(TAG, "Front capture OK: score=$frontScore")
        
        // Store capture - bitmap is a copy, safe to compress
        frontImageBytes = bitmap.toJpegBytes()
        frontDecisionResult = result
        
        // Recycle the copy after compression
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
        
        withContext(Dispatchers.Main) {
            callback?.onFrontCaptured(frontImageBytes!!, result)
            setMode(ScanMode.FRONT_CAPTURED, "✓ Ön yüz yakalandı (Skor: ${result.totalScore})")
        }
        
        return@withContext true
    }
    
    /**
     * Capture back side (MRZ) manually
     * @return true if capture successful
     */
    suspend fun captureBackManually(): Boolean = withContext(Dispatchers.Default) {
        Log.d(TAG, "captureBackManually başladı")
        val originalBitmap = lastBitmap ?: return@withContext false
        
        // CRITICAL: Create a copy to avoid recycle issues
        // The original bitmap might be recycled by analyzeFrame
        if (originalBitmap.isRecycled) {
            android.util.Log.e(TAG, "Original bitmap already recycled")
            return@withContext false
        }
        
        // Create immutable copy
        val bitmap = Bitmap.createBitmap(originalBitmap)
        val quality = lastQualityResult
        
        Log.d(TAG, "Bitmap size: ${bitmap.width}x${bitmap.height}, quality passed: ${quality?.passed}")
        
        if (quality == null || !quality.passed) {
            android.util.Log.w(TAG, "Quality not sufficient for back capture")
            return@withContext false
        }
        
        // CRITICAL: For OCR, use optimal resolution (1080p max)
        // Full resolution (2992x2992) is too large for OCR and causes failures
        // Scale to 1080p for analysis, but keep full res for storage
        val analysisBitmap = if (bitmap.width > 1080 || bitmap.height > 1080) {
            val scale = 1080f / maxOf(bitmap.width, bitmap.height)
            val scaledWidth = (bitmap.width * scale).toInt()
            val scaledHeight = (bitmap.height * scale).toInt()
            Log.d(TAG, "Scaling for OCR: ${bitmap.width}x${bitmap.height} -> ${scaledWidth}x${scaledHeight}")
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            bitmap
        }
        
        Log.d(TAG, "analyzeBackSide çağrılıyor (${analysisBitmap.width}x${analysisBitmap.height})")
        val result = decisionEngine.analyzeBackSide(analysisBitmap)
        Log.d(TAG, "analyzeBackSide tamamlandı: totalScore=${result.totalScore}, MRZ=${result.scoreBreakdown.mrzStructureScore}+${result.scoreBreakdown.mrzChecksumScore}")
        
        // Recycle scaled bitmap if created
        if (analysisBitmap != bitmap && !analysisBitmap.isRecycled) {
            analysisBitmap.recycle()
        }
        
        // Check MRZ-SPECIFIC score (structure + checksum)
        val mrzScore = result.scoreBreakdown.mrzStructureScore + 
                      result.scoreBreakdown.mrzChecksumScore
        
        // For back capture, need at least 20 points (out of 80 possible for MRZ)
        // Structure(20) OR Structure(10)+Checksum(10) minimum
        val backThreshold = 20
        if (mrzScore < backThreshold) {
            android.util.Log.w(TAG, "Back side MRZ score too low: $mrzScore (need $backThreshold)")
            return@withContext false
        }
        
        android.util.Log.d(TAG, "Back capture OK: MRZ score=$mrzScore")
        
        // Store capture - bitmap is a copy, safe to compress
        backImageBytes = bitmap.toJpegBytes()
        backDecisionResult = result
        
        // Recycle the copy after compression
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
        
        withContext(Dispatchers.Main) {
            callback?.onBackCaptured(backImageBytes!!, result)
            setMode(ScanMode.BACK_CAPTURED, "✓ Arka yüz yakalandı (Skor: ${result.totalScore})")
        }
        
        return@withContext true
    }
    
    /**
     * Manually complete the scan (for backward compatibility with IDScanner interface)
     * Calls completeVerification internally
     * @return true if completion was successful
     */
    suspend fun completeScanManually(): Boolean = withContext(Dispatchers.Default) {
        val result = completeVerification()
        return@withContext result != null
    }
    
    /**
     * Complete verification with captured images
     */
    suspend fun completeVerification(): VerificationResult? = withContext(Dispatchers.Default) {
        Log.d(TAG, "completeVerification başladı")
        
        try {
            val front = frontImageBytes
            val back = backImageBytes
            val frontResult = frontDecisionResult
            val backResult = backDecisionResult
            
            Log.d(TAG, "front=${front?.size}, back=${back?.size}, frontResult=$frontResult, backResult=$backResult")
        
        if (front == null || back == null || frontResult == null || backResult == null) {
                Log.e(TAG, "Missing data: front=${front != null}, back=${back != null}, frontResult=${frontResult != null}, backResult=${backResult != null}")
            withContext(Dispatchers.Main) {
                callback?.onError(VerificationError(
                    code = "MISSING_IMAGES",
                    message = "Front and back images required",
                    messageTr = "Ön ve arka yüz görüntüleri gerekli"
                ))
            }
            return@withContext null
        }
        
            Log.d(TAG, "setMode PROCESSING")
            setMode(ScanMode.PROCESSING, "Doğrulama işleniyor...")
            
            // Calculate combined score
            Log.d(TAG, "Skor hesaplanıyor: front=${frontResult.totalScore}, back=${backResult.totalScore}")
            val combinedScore = (frontResult.totalScore + backResult.totalScore) / 2
            Log.d(TAG, "Combined score: $combinedScore")
            
            val finalDecision = when {
                combinedScore >= Constants.Scoring.THRESHOLD_VALID -> DecisionResult.Decision.VALID
                combinedScore >= Constants.Scoring.THRESHOLD_RETRY -> DecisionResult.Decision.RETRY
                else -> DecisionResult.Decision.INVALID
            }
            Log.d(TAG, "Final decision: $finalDecision")
            
            // Extract data from back result
            Log.d(TAG, "extractIDData çağrılıyor")
            val extractedData = extractIDData(backResult)
            Log.d(TAG, "extractedData: $extractedData")
            
            Log.d(TAG, "VerificationResult oluşturuluyor")
            val result = VerificationResult(
                isValid = finalDecision == DecisionResult.Decision.VALID,
                totalScore = combinedScore,
                decision = finalDecision,
                frontImage = front,
                backImage = back,
                frontResult = frontResult,
                backResult = backResult,
                extractedData = extractedData
            )
            Log.d(TAG, "VerificationResult oluşturuldu")
        
        withContext(Dispatchers.Main) {
                Log.d(TAG, "Main thread'e geçiliyor")
            if (result.isValid) {
                setMode(ScanMode.COMPLETED, "✓ Doğrulama başarılı (Skor: $combinedScore)")
            } else {
                setMode(ScanMode.ERROR, "✗ Doğrulama başarısız (Skor: $combinedScore)")
            }
                Log.d(TAG, "onVerificationComplete çağrılıyor")
            callback?.onVerificationComplete(result)
                Log.d(TAG, "onVerificationComplete tamamlandı")
        }
        
            Log.d(TAG, "completeVerification tamamlandı")
        return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "completeVerification HATA", e)
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * Reset to initial state
     */
    fun reset() {
        decisionEngine.reset()
        frontImageBytes = null
        backImageBytes = null
        frontDecisionResult = null
        backDecisionResult = null
        lastBitmap = null
        lastQualityResult = null
        setMode(ScanMode.IDLE, "Sıfırlandı")
    }
    
    /**
     * Release all resources
     */
    fun release() {
        stopScanning()
        decisionEngine.release()
        cameraExecutor.shutdown()
        coroutineScope.cancel()
    }
    
    /**
     * Check camera permission
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Switch to scanning back side
     */
    fun switchToBackSide() {
        setMode(ScanMode.SCANNING_BACK, "Kimlik kartının arka yüzünü yerleştirin")
    }
    
    /**
     * Get current quality status
     */
    fun getLastQuality(): QualityGate.QualityResult? = lastQualityResult
    
    /**
     * Get current quality status (alias for consistency)
     */
    fun getLastQualityResult(): QualityGate.QualityResult? = lastQualityResult
    
    /**
     * Get current mode
     */
    fun getCurrentMode(): ScanMode = currentMode
    
    /**
     * Get best front result from decision engine
     */
    fun getBestFrontResult(): DecisionResult? {
        return decisionEngine.getBestFrontResult()
    }
    
    /**
     * Get best back result from decision engine
     */
    fun getBestBackResult(): DecisionResult? {
        return decisionEngine.getBestBackResult()
    }
    
    // ==================== Private Implementation ====================
    
    private fun startCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner?) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera(previewView, lifecycleOwner)
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCamera(previewView: PreviewView, explicitLifecycleOwner: LifecycleOwner?) {
        val cameraProvider = cameraProvider ?: return
        
        // Ensure view is ready
        if (previewView.display == null || previewView.width == 0) {
            previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                bindCamera(previewView, explicitLifecycleOwner)
            }
            return
        }
        
        cameraProvider.unbindAll()
        
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        
        // Preview
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        
        // Image Analysis - Use 720p for balance between OCR quality and memory
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setTargetResolution(android.util.Size(1280, 720)) // 720p - good balance
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeFrame(imageProxy)
                }
            }
        
        // Find lifecycle owner
        var lifecycleOwner = explicitLifecycleOwner
        if (lifecycleOwner == null) {
            var ctx = previewView.context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is LifecycleOwner) {
                    lifecycleOwner = ctx
                    break
                }
                ctx = ctx.baseContext
            }
        }
        
        if (lifecycleOwner == null) {
            callback?.onError(VerificationError(
                code = "NO_LIFECYCLE",
                message = "LifecycleOwner not found",
                messageTr = "LifecycleOwner bulunamadı"
            ))
            return
        }
        
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis
        )
    }
    
    private fun analyzeFrame(imageProxy: ImageProxy) {
        // Frame rate limiting
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < Constants.Timing.FRAME_ANALYSIS_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        
        // Skip if not in scanning mode
        if (currentMode != ScanMode.SCANNING_FRONT && currentMode != ScanMode.SCANNING_BACK) {
            imageProxy.close()
            return
        }
        
        try {
            // BEST PRACTICE: Two-bitmap strategy
            // 1. FULL RESOLUTION for capture (stored, not processed)
            // 2. SCALED for live analysis (processed, discarded)
            
            val fullBitmap = imageProxy.toBitmap()  // Full resolution for capture
            val scaledBitmap = Bitmap.createScaledBitmap(
                fullBitmap, 
                720,  // Fixed 720p width for analysis
                (fullBitmap.height * 720f / fullBitmap.width).toInt(),
                true
            )
            
            imageProxy.close()
            lastAnalysisTime = now
            
            // MEMORY OPTIMIZATION: Recycle old full bitmap
            val oldBitmap = lastBitmap
            lastBitmap = fullBitmap  // Store FULL res for capture
            if (oldBitmap != null && oldBitmap != fullBitmap && !oldBitmap.isRecycled) {
                oldBitmap.recycle()
            }
            
            // Run analysis on SCALED bitmap (memory efficient)
            // DON'T manually recycle - let GC handle it (safer with coroutines)
            coroutineScope.launch(Dispatchers.Default) {
                processFrame(scaledBitmap)
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Frame analysis error", e)
            imageProxy.close()
        }
    }
    
    private suspend fun processFrame(bitmap: Bitmap) {
        // Step 1: Quality gate
        val quality = QualityGate.assess(bitmap)
        lastQualityResult = quality
        
        withContext(Dispatchers.Main) {
            callback?.onQualityUpdate(quality)
        }
        
        if (!quality.passed) {
            // Don't proceed with OCR
            return
        }
        
        // Step 2: Analyze based on current mode
        val mode = when (currentMode) {
            ScanMode.SCANNING_FRONT -> DecisionEngine.DetectionMode.FRONT_ONLY
            ScanMode.SCANNING_BACK -> DecisionEngine.DetectionMode.BACK_ONLY
            else -> return
        }
        
        val result = decisionEngine.analyzeFrame(bitmap, mode)
        
        withContext(Dispatchers.Main) {
            callback?.onFrameAnalyzed(result)
        }
    }
    
    private fun setMode(mode: ScanMode, message: String) {
        currentMode = mode
        callback?.onModeChanged(mode, message)
    }
    
    private fun extractIDData(result: DecisionResult): ExtractedIDData? {
        val rawData = result.rawData ?: return null
        
        // Parse MRZ lines if available
        if (rawData.mrzLines.size >= 2) {
            val line1 = rawData.mrzLines.getOrElse(0) { "" }.padEnd(30, '<')
            val line2 = rawData.mrzLines.getOrElse(1) { "" }.padEnd(30, '<')
            val line3 = rawData.mrzLines.getOrElse(2) { "" }.padEnd(30, '<')
            
            return ExtractedIDData(
                tcKimlikNo = line2.substring(18, 29).replace("<", "").takeIf { it.isNotEmpty() },
                documentNumber = line1.substring(5, 14).replace("<", "").takeIf { it.isNotEmpty() },
                surname = line3.split("<<").getOrNull(0)?.replace("<", " ")?.trim(),
                givenNames = line3.split("<<").getOrNull(1)?.replace("<", " ")?.trim(),
                birthDate = line2.substring(0, 6).takeIf { it.all { c -> c.isDigit() } },
                expiryDate = line2.substring(8, 14).takeIf { it.all { c -> c.isDigit() } },
                sex = when (line2.getOrElse(7) { '<' }) {
                    'M' -> "Erkek"
                    'F' -> "Kadın"
                    else -> null
                },
                nationality = line2.substring(15, 18).replace("<", "").takeIf { it.isNotEmpty() }
            )
        }
        
        return ExtractedIDData(
            tcKimlikNo = rawData.detectedTCKN,
            documentNumber = null,
            surname = null,
            givenNames = null,
            birthDate = null,
            expiryDate = null,
            sex = null,
            nationality = null
        )
    }
}
