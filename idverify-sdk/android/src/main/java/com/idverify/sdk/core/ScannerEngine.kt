package com.idverify.sdk.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.idverify.sdk.api.IDScanner
import com.idverify.sdk.api.ScanCallback
import com.idverify.sdk.api.models.*
import com.idverify.sdk.heuristics.IDAnalyzer
import com.idverify.sdk.ocr.MLKitTextRecognizer
import com.idverify.sdk.ocr.MRZParser
import com.idverify.sdk.utils.ImageUtils
import com.idverify.sdk.utils.ImageUtils.toBitmap
import com.idverify.sdk.utils.ImageUtils.toJpegBytes
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Core scanner engine implementation
 * Orchestrates camera, image analysis, MRZ parsing, and validation
 */
class ScannerEngine(private val context: Context) : IDScanner {
    
    private val idAnalyzer = IDAnalyzer()
    private val textRecognizer = MLKitTextRecognizer()
    private val mrzParser = MRZParser()
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State
    private var currentStatus = ScanStatus.IDLE
    private var callback: ScanCallback? = null
    private var frontImageBytes: ByteArray? = null
    private var backImageBytes: ByteArray? = null
    private var frontQualityScore: Float = 0f
    
    // Manual capture: store last analyzed bitmap
    private var lastAnalyzedBitmap: android.graphics.Bitmap? = null
    private var lastAnalysisResult: IDAnalyzer.AnalysisResult? = null
    
    // Performance optimization: frame skipping and debouncing
    private var lastAnalysisTime = 0L
    private var consecutiveGoodFrames = 0
    private var lastMRZAttemptTime = 0L
    private var mrzReadAttempts = 0
    private val maxMRZAttempts = 5  // Max attempts before giving up
    
    override fun startScanning(previewView: PreviewView, callback: ScanCallback, lifecycleOwner: LifecycleOwner?) {
        ContextCompat.getMainExecutor(context).execute {
            android.util.Log.d("ScannerEngine", ">>> startScanning called on Main Thread")
            if (!hasCameraPermission()) {
                android.util.Log.e("ScannerEngine", "Permission denied")
                callback.onError(ScanError.CameraPermissionDenied())
                return@execute
            }
            
            this.callback = callback
            updateStatus(ScanStatus.DETECTING_FRONT, 0f, "Kimlik kartının ön yüzünü çerçeveye yerleştirin")
            
            android.util.Log.d("ScannerEngine", "Calling startCamera...")
            startCamera(previewView, lifecycleOwner)
        }
    }
    
    override fun stopScanning() {
        android.util.Log.d("ScannerEngine", "stopScanning called")
        ContextCompat.getMainExecutor(context).execute {
            try {
                if (cameraProvider != null) {
                    cameraProvider?.unbindAll()
                    android.util.Log.d("ScannerEngine", "Camera unbindAll success")
                }
                updateStatus(ScanStatus.IDLE)
            } catch (e: Exception) {
                android.util.Log.e("ScannerEngine", "Failed to stop scanning", e)
            }
        }
    }

    override fun reset() {
        android.util.Log.d("ScannerEngine", "reset called")
        frontImageBytes = null
        backImageBytes = null
        frontQualityScore = 0f
        consecutiveGoodFrames = 0
        lastMRZAttemptTime = 0L
        mrzReadAttempts = 0
        lastAnalysisTime = 0L
        updateStatus(ScanStatus.IDLE)
    }

    override fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun release() {
        android.util.Log.d("ScannerEngine", "release called")
        ContextCompat.getMainExecutor(context).execute {
            try {
                cameraExecutor.shutdown()
                cameraProvider?.unbindAll()
                cameraProvider = null
                camera = null
            } catch (e: Exception) {
                android.util.Log.e("ScannerEngine", "Failed to release resources", e)
            }
        }
    }
    
    override suspend fun captureFrontManually(): Boolean = withContext(Dispatchers.Default) {
        val bitmap = lastAnalyzedBitmap
        val analysis = lastAnalysisResult
        
        if (bitmap == null || analysis == null) {
            android.util.Log.w("ScannerEngine", "No bitmap available for manual front capture")
            return@withContext false
        }
        
        // RELAXED quality threshold for manual mode (user decides when to capture)
        if (analysis.overallScore < 0.4f) {
            android.util.Log.w("ScannerEngine", "Quality too low for manual capture: ${analysis.overallScore} (blur=${analysis.blurScore}, glare=${analysis.glareScore})")
            return@withContext false
        }
        
        android.util.Log.d("ScannerEngine", "Manual front capture: quality=${analysis.overallScore}, blur=${analysis.blurScore}, glare=${analysis.glareScore}")
        captureFrontSide(bitmap, analysis.overallScore)
        updateStatus(ScanStatus.FRONT_CAPTURED, 0.4f, "✓ Ön yüz yakalandı")
        true
    }
    
    override suspend fun captureBackManually(): Boolean = withContext(Dispatchers.Default) {
        val bitmap = lastAnalyzedBitmap
        val analysis = lastAnalysisResult
        
        if (bitmap == null || analysis == null) {
            android.util.Log.w("ScannerEngine", "No bitmap available for manual back capture")
            return@withContext false
        }
        
        // RELAXED quality threshold for manual mode (user decides when to capture)
        if (analysis.overallScore < 0.4f) {
            android.util.Log.w("ScannerEngine", "Quality too low for manual capture: ${analysis.overallScore} (blur=${analysis.blurScore}, glare=${analysis.glareScore})")
            return@withContext false
        }
        
        android.util.Log.d("ScannerEngine", "Manual back capture: quality=${analysis.overallScore}, blur=${analysis.blurScore}, glare=${analysis.glareScore}")
        captureBackSide(bitmap, analysis.overallScore)
        updateStatus(ScanStatus.BACK_CAPTURED, 0.7f, "✓ Arka yüz yakalandı")
        true
    }
    
    override suspend fun completeScanManually(): Boolean = withContext(Dispatchers.Default) {
        android.util.Log.d("ScannerEngine", "completeScanManually called")
        
        if (frontImageBytes == null || backImageBytes == null) {
            android.util.Log.w("ScannerEngine", "Cannot complete scan: missing images (front=${frontImageBytes != null}, back=${backImageBytes != null})")
            withContext(Dispatchers.Main) {
                callback?.onError(ScanError.ProcessingFailed("Ön ve arka yüz görüntüleri gerekli"))
            }
            return@withContext false
        }
        
        // Try to extract MRZ from back image
        val bitmap = lastAnalyzedBitmap ?: run {
            android.util.Log.w("ScannerEngine", "No bitmap available for MRZ extraction")
            withContext(Dispatchers.Main) {
                callback?.onError(ScanError.ProcessingFailed("Görüntü bulunamadı"))
            }
            return@withContext false
        }
        
        updateStatus(ScanStatus.PROCESSING, 0.8f, "MRZ verileri işleniyor...")
        
        try {
            android.util.Log.d("ScannerEngine", "Starting MRZ recognition...")
            
            // Try multiple preprocessing strategies for better OCR
            var mrzLines = emptyList<String>()
            var bestAttemptLog = ""
            
            // Attempt 1: Full image
            android.util.Log.d("ScannerEngine", "Attempt 1: Full image OCR")
            var recognitionResult = textRecognizer.recognizeText(bitmap)
            android.util.Log.d("ScannerEngine", "Full image OCR: ${recognitionResult.lines.size} lines")
            mrzLines = textRecognizer.extractMRZLines(recognitionResult)
            bestAttemptLog = "Full image: ${mrzLines.size} MRZ lines"
            
            // Attempt 2: Bottom portion (where MRZ typically is)
            if (mrzLines.isEmpty()) {
                android.util.Log.d("ScannerEngine", "Attempt 2: Bottom portion OCR")
                val bottomBitmap = ImageUtils.extractBottomPortion(bitmap, 0.35f)
                recognitionResult = textRecognizer.recognizeText(bottomBitmap)
                android.util.Log.d("ScannerEngine", "Bottom OCR: ${recognitionResult.lines.size} lines")
                val bottomMRZ = textRecognizer.extractMRZLines(recognitionResult)
                if (bottomMRZ.size > mrzLines.size) {
                    mrzLines = bottomMRZ
                    bestAttemptLog = "Bottom 35%: ${mrzLines.size} MRZ lines"
                }
                bottomBitmap.recycle()
            }
            
            // Attempt 3: Preprocessed image (enhanced contrast)
            if (mrzLines.isEmpty() || mrzLines.size < 3) {
                android.util.Log.d("ScannerEngine", "Attempt 3: Preprocessed image OCR")
                val processedBitmap = ImageUtils.preprocessForOCR(bitmap)
                val processedBottom = ImageUtils.extractBottomPortion(processedBitmap, 0.35f)
                recognitionResult = textRecognizer.recognizeText(processedBottom)
                android.util.Log.d("ScannerEngine", "Preprocessed OCR: ${recognitionResult.lines.size} lines")
                val processedMRZ = textRecognizer.extractMRZLines(recognitionResult)
                if (processedMRZ.size > mrzLines.size) {
                    mrzLines = processedMRZ
                    bestAttemptLog = "Preprocessed bottom: ${mrzLines.size} MRZ lines"
                }
                processedBitmap.recycle()
                processedBottom.recycle()
            }
            
            android.util.Log.d("ScannerEngine", "Best result: $bestAttemptLog")
            
            // Log ALL OCR text for maximum debugging
            android.util.Log.d("ScannerEngine", "════════════════════════════════════")
            android.util.Log.d("ScannerEngine", "ALL OCR TEXT (${recognitionResult.lines.size} lines):")
            recognitionResult.lines.forEachIndexed { idx, line ->
                android.util.Log.d("ScannerEngine", "  [$idx] '$line' (len=${line.length})")
            }
            android.util.Log.d("ScannerEngine", "════════════════════════════════════")
            android.util.Log.d("ScannerEngine", "FINAL MRZ LINES: ${mrzLines.size}")
            mrzLines.forEachIndexed { idx, line ->
                android.util.Log.d("ScannerEngine", "  MRZ[$idx] '$line' (len=${line.length})")
            }
            android.util.Log.d("ScannerEngine", "════════════════════════════════════")
                
            if (mrzLines.isEmpty()) {
                android.util.Log.w("ScannerEngine", "No MRZ found in back image - OCR failed to extract valid MRZ lines")
                updateStatus(ScanStatus.ERROR, 0f, "MRZ bulunamadı")
                withContext(Dispatchers.Main) {
                    callback?.onError(ScanError.MRZReadFailed("MRZ satırları bulunamadı. Lütfen kartın arka yüzündeki MRZ alanını (3 satır) net bir şekilde gösterin ve tekrar deneyin."))
                }
                return@withContext false
            }
            
            android.util.Log.d("ScannerEngine", "Found ${mrzLines.size} MRZ lines, parsing...")
            mrzLines.forEachIndexed { idx, line ->
                android.util.Log.d("ScannerEngine", "MRZ[$idx]: '$line' (len=${line.length})")
            }
            
            // Parse MRZ
            val parseResult = mrzParser.parse(mrzLines)
            
            when (parseResult) {
                is MRZParser.ParseResult.Success -> {
                    android.util.Log.d("ScannerEngine", "MRZ parsed successfully!")
                    android.util.Log.d("ScannerEngine", "  Document: ${parseResult.mrzData.documentNumber}")
                    android.util.Log.d("ScannerEngine", "  Name: ${parseResult.mrzData.givenNames} ${parseResult.mrzData.surname}")
                    android.util.Log.d("ScannerEngine", "  Birth: ${parseResult.mrzData.birthDate}")
                    
                    val scanResult = ScanResult(
                        frontImage = frontImageBytes!!,
                        backImage = backImageBytes!!,
                        mrzData = parseResult.mrzData,
                        authenticityScore = (frontQualityScore + (lastAnalysisResult?.overallScore ?: 0f)) / 2f,
                        metadata = ScanMetadata(
                            scanDuration = 0L,
                            frontCaptureTimestamp = System.currentTimeMillis(),
                            backCaptureTimestamp = System.currentTimeMillis(),
                            blurScore = lastAnalysisResult?.blurScore ?: 0f,
                            glareScore = lastAnalysisResult?.glareScore ?: 0f
                        )
                    )
                    
                    updateStatus(ScanStatus.COMPLETED, 1.0f, "Tarama başarıyla tamamlandı!")
                    withContext(Dispatchers.Main) {
                        callback?.onCompleted(scanResult)
                    }
                    return@withContext true
                }
                is MRZParser.ParseResult.Failure -> {
                    android.util.Log.e("ScannerEngine", "MRZ parse failed: ${parseResult.error}")
                    updateStatus(ScanStatus.ERROR, 0f, "MRZ parse hatası: ${parseResult.error}")
                    withContext(Dispatchers.Main) {
                        callback?.onError(ScanError.MRZReadFailed("MRZ parse başarısız: ${parseResult.error}. Lütfen kartın arka yüzünü daha net gösterin ve tekrar deneyin."))
                    }
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScannerEngine", "Failed to complete scan manually", e)
            updateStatus(ScanStatus.ERROR, 0f, "Hata: ${e.message}")
            return@withContext false
        }
    }

    private fun startCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner?) {
        android.util.Log.d("ScannerEngine", ">>> startCamera called")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                android.util.Log.d("ScannerEngine", "Camera provider future finished")
                cameraProvider = cameraProviderFuture.get()
                android.util.Log.d("ScannerEngine", "CameraProvider retrieved: $cameraProvider")
                bindCameraUseCases(previewView, lifecycleOwner)
            } catch (e: Exception) {
                android.util.Log.e("ScannerEngine", "Camera provider failed", e)
                callback?.onError(ScanError.CameraInitializationFailed(e.message))
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCameraUseCases(previewView: PreviewView, explicitLifecycleOwner: LifecycleOwner?) {
        android.util.Log.d("ScannerEngine", ">>> bindCameraUseCases called")
        
        if (currentStatus == ScanStatus.IDLE) {
            android.util.Log.w("ScannerEngine", "Status is IDLE, aborting bind (race condition avoidance).")
            return
        }
        
        if (previewView.display == null || (previewView.width == 0 && previewView.height == 0)) {
            android.util.Log.d("ScannerEngine", "View not ready yet (W=${previewView.width}, H=${previewView.height}, Display=${previewView.display}). Waiting for layout...")
            previewView.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                override fun onLayoutChange(v: android.view.View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    previewView.removeOnLayoutChangeListener(this)
                    android.util.Log.d("ScannerEngine", "View layout changed, ready now. Retrying bind...")
                    bindCameraUseCases(previewView, explicitLifecycleOwner)
                }
            })
            return
        }

        val cameraProvider = cameraProvider ?: return
        
        // Unbind everything first to ensure clean state
        try {
            android.util.Log.d("ScannerEngine", "Unbinding all use cases...")
            cameraProvider.unbindAll()
            android.util.Log.d("ScannerEngine", "Unbound all previous use cases")
        } catch (e: Exception) {
            android.util.Log.e("ScannerEngine", "Failed to unbind use cases", e)
        }
        
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        android.util.Log.d("ScannerEngine", "PreviewView rotation: $rotation")
        
        // Preview
        android.util.Log.d("ScannerEngine", "Building Preview use case...")
        android.util.Log.d("ScannerEngine", "PreviewView details: W=${previewView.width}, H=${previewView.height}, Display=${previewView.display}, Visibility=${previewView.visibility}, Attached=${previewView.isAttachedToWindow}")
        android.util.Log.d("ScannerEngine", "PreviewView implementationMode: ${previewView.implementationMode}")
        
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also {
                android.util.Log.d("ScannerEngine", "Setting surface provider from previewView: $previewView")
                try {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                    android.util.Log.d("ScannerEngine", "Surface provider set successfully")
                } catch (e: Exception) {
                    android.util.Log.e("ScannerEngine", "Failed to set surface provider", e)
                    throw e
                }
            }
        
        // Image capture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        
        // Image analysis - Optimized for MRZ reading
        android.util.Log.d("ScannerEngine", "Building ImageAnalysis use case...")
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            // YUV_420_888 is default and works well with our ImageUtils converter
            .build()
            .also {
                android.util.Log.d("ScannerEngine", "Setting analyzer on ImageAnalysis use case")
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeImage(imageProxy)
                }
                android.util.Log.d("ScannerEngine", "ImageAnalysis analyzer set successfully")
            }
        
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            var lifecycleOwner: LifecycleOwner? = explicitLifecycleOwner
            
            if (lifecycleOwner == null) {
                android.util.Log.w("ScannerEngine", "Explicit lifecycle owner is null, searching context...")
                var ctx = previewView.context
                
                android.util.Log.d("ScannerEngine", "Looking for lifecycle owner from context: $ctx")
                
                while (ctx is android.content.ContextWrapper) {
                    if (ctx is LifecycleOwner) {
                        lifecycleOwner = ctx
                        android.util.Log.d("ScannerEngine", "Found lifecycle owner: $ctx")
                        break
                    }
                    ctx = ctx.baseContext
                }
            } else {
                 android.util.Log.d("ScannerEngine", "Using provided explicit lifecycle owner: $lifecycleOwner")
            }
            
            if (lifecycleOwner == null) {
                android.util.Log.e("ScannerEngine", "LifecycleOwner NOT FOUND")
                callback?.onError(ScanError.CameraInitializationFailed("Context is not a LifecycleOwner"))
                return
            }
            
            android.util.Log.d("ScannerEngine", "LifecycleOwner State: ${lifecycleOwner.lifecycle.currentState}")
            
            android.util.Log.d("ScannerEngine", "Binding to lifecycle...")
            android.util.Log.d("ScannerEngine", "Use cases to bind: Preview=${preview != null}, ImageAnalysis=${imageAnalyzer != null}")

            // Bind only Preview for now to isolate timeout issue
            val useCases = mutableListOf<UseCase>(preview)
            val analyzer = imageAnalyzer
            if (analyzer != null) {
                useCases.add(analyzer)
            }
            
            android.util.Log.d("ScannerEngine", "Binding ${useCases.size} use case(s)...")
            
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )
            android.util.Log.d("ScannerEngine", "Camera bound successfully. Camera: $camera")
            android.util.Log.d("ScannerEngine", "Camera info: ${camera?.cameraInfo}, Control: ${camera?.cameraControl}")
            
            // Verify active state with detailed logging
            camera?.cameraInfo?.cameraState?.observe(lifecycleOwner) { state ->
                android.util.Log.d("ScannerEngine", "Camera State: ${state.type} | Error: ${state.error}")
                val error = state.error
                if (error != null) {
                    android.util.Log.e("ScannerEngine", "Camera State Error Details: ${error.cause}")
                }
            }
            
            // Log preview surface state
            previewView.post {
                android.util.Log.d("ScannerEngine", "PreviewView post-bind check: W=${previewView.width}, H=${previewView.height}, Surface=${previewView.surfaceProvider}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ScannerEngine", "Bind failed", e)
            callback?.onError(ScanError.CameraInitializationFailed(e.message))
        }
    }
    
    private fun analyzeImage(imageProxy: ImageProxy) {
        // CRITICAL: ImageProxy MUST be closed quickly to avoid CameraX timeout.
        // Frame skipping for performance optimization
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAnalysis = currentTime - lastAnalysisTime
        
        // Skip frames if analyzing too frequently (debouncing)
        if (timeSinceLastAnalysis < com.idverify.sdk.utils.Constants.Timing.FRAME_ANALYSIS_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        
        try {
            val width = imageProxy.width
            val height = imageProxy.height
            
            // Quick aspect ratio check before expensive bitmap conversion
            val aspectRatio = width.toFloat() / height.toFloat()
            val cardAspectRatio = com.idverify.sdk.utils.Constants.CardDimensions.ASPECT_RATIO
            val minRatio = com.idverify.sdk.utils.Constants.CardDimensions.ASPECT_RATIO_MIN
            val maxRatio = com.idverify.sdk.utils.Constants.CardDimensions.ASPECT_RATIO_MAX
            
            // Skip if aspect ratio is way off (not a card)
            if (aspectRatio < minRatio * 0.8f || aspectRatio > maxRatio * 1.2f) {
                android.util.Log.v("ScannerEngine", "Skipping frame: aspect ratio too far off ($aspectRatio)")
                imageProxy.close()
                return
            }
            
            // Convert to bitmap BEFORE closing ImageProxy
            val bitmap = try {
                imageProxy.toBitmap()
            } catch (e: Exception) {
                android.util.Log.e("ScannerEngine", "Failed to convert ImageProxy to bitmap", e)
                imageProxy.close()
                return
            }
            
            // Close ImageProxy immediately to prevent timeout
            imageProxy.close()
            lastAnalysisTime = currentTime
            
            // Store last bitmap for manual capture
            lastAnalyzedBitmap = bitmap
            
            // Process bitmap asynchronously on background thread
            coroutineScope.launch(Dispatchers.Default) {
                processImageFrame(bitmap)
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ScannerEngine", "Error in analyzeImage", e)
            try {
                imageProxy.close()
            } catch (_: Exception) {
                // Ignore close errors
            }
        }
    }
    
    private suspend fun processImageFrame(bitmap: android.graphics.Bitmap) {
        try {
            // Skip if already completed or in wrong state
            if (currentStatus == ScanStatus.COMPLETED || currentStatus == ScanStatus.ERROR) {
                return
            }
            
            // Analyze image quality
            val analysis = idAnalyzer.analyze(bitmap)
            android.util.Log.d("ScannerEngine", "Quality analysis: blur=${analysis.blurScore}, glare=${analysis.glareScore}, aspect=${analysis.aspectRatioScore}, overall=${analysis.overallScore}, acceptable=${analysis.isAcceptable}")
            
            // Store last analysis for manual capture
            lastAnalysisResult = analysis
            
            // Quality check: blur and glare are critical, aspect ratio is optional (camera frame != card)
            // Aspect ratio validation would require card detection/segmentation first
            val qualityAcceptable = analysis.isAcceptable
            
            if (!qualityAcceptable) {
                consecutiveGoodFrames = 0
                val qualityMessage = when {
                    analysis.blurScore < com.idverify.sdk.utils.Constants.Quality.MIN_BLUR_SCORE -> 
                        when (currentStatus) {
                            ScanStatus.DETECTING_FRONT -> "Görüntü bulanık - kartı sabit tutun"
                            ScanStatus.DETECTING_BACK -> "Görüntü bulanık - kartı sabit tutun"
                            else -> "Görüntü bulanık"
                        }
                    analysis.glareScore < com.idverify.sdk.utils.Constants.Quality.MIN_GLARE_SCORE -> 
                        when (currentStatus) {
                            ScanStatus.DETECTING_FRONT -> "Işık yansıması var - açıyı değiştirin"
                            ScanStatus.DETECTING_BACK -> "Işık yansıması var - açıyı değiştirin"
                            else -> "Işık yansıması var"
                        }
                    else -> "Kalite düşük - kartı daha iyi konumlandırın"
                }
                updateStatus(
                    currentStatus,
                    progress = 0.1f,
                    message = qualityMessage
                )
                return
            }
            
            // Increment consecutive good frames counter (blur/glare acceptable)
            consecutiveGoodFrames++
            android.util.Log.d("ScannerEngine", "Consecutive good frames: $consecutiveGoodFrames")
            
            // Perform OCR to find MRZ (only if quality is good enough)
            // Skip OCR if quality is too low to save CPU
            if (analysis.overallScore < 0.6f) {
                android.util.Log.v("ScannerEngine", "Skipping OCR - quality too low: ${analysis.overallScore}")
                return
            }
            
            val recognitionResult = try {
                textRecognizer.recognizeText(bitmap)
            } catch (e: Exception) {
                android.util.Log.e("ScannerEngine", "OCR failed", e)
                return
            }
            
            android.util.Log.d("ScannerEngine", "OCR result: ${recognitionResult.lines.size} lines, confidence=${recognitionResult.confidence}, text length=${recognitionResult.fullText.length}")
            
            // Log all recognized lines for debugging
            recognitionResult.lines.forEachIndexed { index, line ->
                android.util.Log.v("ScannerEngine", "OCR Line $index: $line")
            }
            
            // Extract MRZ lines with improved filtering
            val mrzLines = textRecognizer.extractMRZLines(recognitionResult)
            
            // DISABLED: Auto-detection - user will manually control capture
            // MRZ is on the BACK of Turkish ID cards
            if (mrzLines.isEmpty()) {
                // No MRZ found - this is likely the FRONT side
                // Auto-capture disabled - user controls via buttons
                if (false && currentStatus == ScanStatus.DETECTING_FRONT && frontImageBytes == null) {
                    // Require multiple consecutive good frames before capturing (debouncing)
                    // Focus on blur/glare quality, aspect ratio is optional (camera frame != card)
                    val minQualityForCapture = 0.75f  // Lower threshold for better UX
                    val requiredFrames = 3
                    
                    if (analysis.overallScore >= minQualityForCapture && 
                        consecutiveGoodFrames >= requiredFrames) {
                        android.util.Log.d("ScannerEngine", "Capturing front side (quality: ${analysis.overallScore}, blur: ${analysis.blurScore}, glare: ${analysis.glareScore}, frames: $consecutiveGoodFrames)")
                        captureFrontSide(bitmap, analysis.overallScore)
                        consecutiveGoodFrames = 0  // Reset counter
                        updateStatus(ScanStatus.FRONT_CAPTURED, 0.4f, "✓ Ön yüz yakalandı")
                        
                        // Show front image info briefly, then transition to back
                        coroutineScope.launch {
                            delay(2000)  // Show success message for 2 seconds
                            if (currentStatus == ScanStatus.FRONT_CAPTURED) {
                                updateStatus(ScanStatus.DETECTING_BACK, 0.5f, "Kimlik kartının arka yüzünü çerçeveye yerleştirin")
                            }
                        }
                    } else {
                        // Not ready yet - show progress
                        val progress = (consecutiveGoodFrames.toFloat() / requiredFrames * 0.3f).coerceAtMost(0.3f)
                        val qualityPercent = (analysis.overallScore * 100).toInt()
                        updateStatus(
                            ScanStatus.DETECTING_FRONT,
                            progress = progress,
                            message = "Ön yüz taranıyor... (${consecutiveGoodFrames}/$requiredFrames, kalite: $qualityPercent%)"
                        )
                    }
                }
                return
            }
            
            // MRZ found - this is the BACK side
            android.util.Log.d("ScannerEngine", "MRZ lines found: ${mrzLines.size}")
            mrzLines.forEachIndexed { index, line ->
                android.util.Log.d("ScannerEngine", "MRZ Line $index: $line")
            }
            
            // Debounce MRZ reading - require multiple successful reads
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastMRZAttemptTime < com.idverify.sdk.utils.Constants.Timing.MRZ_READ_CONFIRMATION_MS) {
                // Too soon after last attempt
                return
            }
            lastMRZAttemptTime = currentTime
            
            // DISABLED: Auto-detection - user will manually control capture
            if (false && (currentStatus == ScanStatus.DETECTING_BACK || currentStatus == ScanStatus.FRONT_CAPTURED)) {
                // Parse MRZ
                val parseResult = mrzParser.parse(mrzLines)
                
                when (parseResult) {
                    is MRZParser.ParseResult.Success -> {
                        android.util.Log.d("ScannerEngine", "MRZ parsed successfully: ${parseResult.mrzData.documentNumber}")
                        mrzReadAttempts = 0  // Reset on success
                        
                        // Require good quality for back capture (blur/glare focus)
                        if (analysis.overallScore >= 0.75f && consecutiveGoodFrames >= 2) {
                            // Back side captured - MRZ found and validated
                            captureBackSide(bitmap, analysis.overallScore)
                            consecutiveGoodFrames = 0
                            
                            // Ensure we have front image
                            if (frontImageBytes == null) {
                                android.util.Log.w("ScannerEngine", "Back captured but front missing - using current frame as front")
                                captureFrontSide(bitmap, analysis.overallScore)
                            }
                            
                            // Show processing status
                            updateStatus(ScanStatus.PROCESSING, 0.9f, "MRZ verileri doğrulanıyor...")
                            
                            // Complete scan after brief processing delay
                            coroutineScope.launch {
                                delay(500)  // Brief delay to show processing
                                
                                val scanResult = ScanResult(
                                    frontImage = frontImageBytes!!,
                                    backImage = backImageBytes!!,
                                    mrzData = parseResult.mrzData,
                                    authenticityScore = (frontQualityScore + analysis.overallScore) / 2f,
                                    metadata = ScanMetadata(
                                        scanDuration = 0L, // TODO: Track duration
                                        frontCaptureTimestamp = System.currentTimeMillis(),
                                        backCaptureTimestamp = System.currentTimeMillis(),
                                        blurScore = analysis.blurScore,
                                        glareScore = analysis.glareScore
                                    )
                                )
                                
                                updateStatus(ScanStatus.COMPLETED, 1.0f, "✓ Tarama başarıyla tamamlandı!")
                                
                                launch(Dispatchers.Main) {
                                    callback?.onCompleted(scanResult)
                                }
                            }
                        } else {
                            // MRZ found but quality not good enough yet
                            val progress = 0.5f + (consecutiveGoodFrames.toFloat() / 2f * 0.2f).coerceAtMost(0.2f)
                            updateStatus(
                                ScanStatus.DETECTING_BACK,
                                progress = progress,
                                message = "MRZ bulundu - kaliteyi artırın (${consecutiveGoodFrames}/2)"
                            )
                        }
                    }
                    is MRZParser.ParseResult.Failure -> {
                        android.util.Log.w("ScannerEngine", "MRZ parse failed: ${parseResult.error}")
                        mrzReadAttempts++
                        
                        if (mrzReadAttempts >= maxMRZAttempts) {
                            updateStatus(
                                ScanStatus.DETECTING_BACK,
                                progress = 0.3f,
                                message = "MRZ okunamadı - kartı daha net konumlandırın"
                            )
                            mrzReadAttempts = 0  // Reset after max attempts
                        } else {
                            updateStatus(
                                ScanStatus.DETECTING_BACK,
                                progress = 0.3f,
                                message = "MRZ parse hatası (${mrzReadAttempts}/$maxMRZAttempts) - tekrar deneyin"
                            )
                        }
                    }
                }
            } else if (currentStatus == ScanStatus.DETECTING_FRONT) {
                // MRZ found on front - this shouldn't happen for Turkish ID, but handle it
                android.util.Log.w("ScannerEngine", "MRZ found on front side - unexpected for Turkish ID")
                updateStatus(
                    ScanStatus.DETECTING_BACK,
                    progress = 0.5f,
                    message = "Kimlik kartının arka yüzünü çerçeveye yerleştirin"
                )
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ScannerEngine", "Error processing image frame", e)
        }
    }
    
    private fun captureFrontSide(bitmap: android.graphics.Bitmap, qualityScore: Float) {
        try {
            frontImageBytes = bitmap.toJpegBytes()
            frontQualityScore = qualityScore
            android.util.Log.d("ScannerEngine", "Front side captured: ${frontImageBytes!!.size} bytes, quality: $qualityScore")
            
            coroutineScope.launch(Dispatchers.Main) {
                callback?.onFrontCaptured(frontImageBytes!!, qualityScore)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScannerEngine", "Failed to capture front side", e)
        }
    }
    
    private fun captureBackSide(bitmap: android.graphics.Bitmap, qualityScore: Float) {
        try {
            backImageBytes = bitmap.toJpegBytes()
            android.util.Log.d("ScannerEngine", "Back side captured: ${backImageBytes!!.size} bytes, quality: $qualityScore")
            
            coroutineScope.launch(Dispatchers.Main) {
                callback?.onBackCaptured(backImageBytes!!, qualityScore)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScannerEngine", "Failed to capture back side", e)
        }
    }
    
    private fun updateStatus(status: ScanStatus, progress: Float = 0f, message: String? = null) {
        currentStatus = status
        coroutineScope.launch(Dispatchers.Main) {
            callback?.onStatusChanged(status, progress, message)
        }
    }
}
