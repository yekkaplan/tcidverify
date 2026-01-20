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
    
    override fun startScanning(previewView: PreviewView, callback: ScanCallback) {
        if (!hasCameraPermission()) {
            callback.onError(ScanError.CameraPermissionDenied())
            return
        }
        
        this.callback = callback
        updateStatus(ScanStatus.DETECTING_FRONT, 0f, "Position front of ID card")
        
        startCamera(previewView)
    }
    
    override fun stopScanning() {
        cameraProvider?.unbindAll()
        updateStatus(ScanStatus.IDLE)
    }
    
    override fun reset() {
        stopScanning()
        frontImageBytes = null
        backImageBytes = null
        frontQualityScore = 0f
        currentStatus = ScanStatus.IDLE
    }
    
    override fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    override fun release() {
        coroutineScope.cancel()
        cameraExecutor.shutdown()
        textRecognizer.release()
        cameraProvider?.unbindAll()
    }
    
    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView)
            } catch (e: Exception) {
                callback?.onError(ScanError.CameraInitializationFailed(e.message))
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCameraUseCases(previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return
        
        // Preview
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image capture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        
        // Image analysis for real-time quality check
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeImage(imageProxy)
                }
            }
        
        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            
            var lifecycleOwner: LifecycleOwner? = null
            var context = previewView.context
            
            while (context is android.content.ContextWrapper) {
                if (context is LifecycleOwner) {
                    lifecycleOwner = context
                    break
                }
                context = context.baseContext
            }
            
            if (lifecycleOwner == null) {
                callback?.onError(ScanError.CameraInitializationFailed("Context is not a LifecycleOwner"))
                return
            }
            
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )
        } catch (e: Exception) {
            callback?.onError(ScanError.CameraInitializationFailed(e.message))
        }
    }
    
    private fun analyzeImage(imageProxy: ImageProxy) {
        // Quick quality check - implement full analysis in production
        val bitmap = imageProxy.toBitmap()
        val analysis = idAnalyzer.analyze(bitmap)
        
        imageProxy.close()
        
        // Auto-capture logic would go here
        // For now, this is a skeleton
    }
    
    private fun updateStatus(status: ScanStatus, progress: Float = 0f, message: String? = null) {
        currentStatus = status
        coroutineScope.launch(Dispatchers.Main) {
            callback?.onStatusChanged(status, progress, message)
        }
    }
}
