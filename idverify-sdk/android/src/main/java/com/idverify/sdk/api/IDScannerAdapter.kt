package com.idverify.sdk.api

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.idverify.sdk.api.models.MRZData
import com.idverify.sdk.api.models.ScanError
import com.idverify.sdk.api.models.ScanMetadata
import com.idverify.sdk.api.models.ScanResult
import com.idverify.sdk.api.models.ScanStatus
import com.idverify.sdk.core.IDVerificationEngine
import com.idverify.sdk.decision.DecisionResult
import com.idverify.sdk.detection.QualityGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Adapter class that bridges the old IDScanner interface with the new IDVerificationEngine
 * This maintains backward compatibility while using the new result-oriented architecture
 */
class IDScannerAdapter(private val context: Context) : IDScanner {
    
    private val engine = IDVerificationEngine(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var callback: ScanCallback? = null
    private var currentMode = IDVerificationEngine.ScanMode.IDLE
    
    private val verificationCallback = object : IDVerificationEngine.VerificationCallback {
        override fun onModeChanged(mode: IDVerificationEngine.ScanMode, message: String) {
            currentMode = mode
            
            // Map new modes to old status
            val status = when (mode) {
                IDVerificationEngine.ScanMode.IDLE -> ScanStatus.IDLE
                IDVerificationEngine.ScanMode.SCANNING_FRONT -> ScanStatus.DETECTING_FRONT
                IDVerificationEngine.ScanMode.FRONT_CAPTURED -> ScanStatus.FRONT_CAPTURED
                IDVerificationEngine.ScanMode.SCANNING_BACK -> ScanStatus.DETECTING_BACK
                IDVerificationEngine.ScanMode.BACK_CAPTURED -> ScanStatus.BACK_CAPTURED
                IDVerificationEngine.ScanMode.PROCESSING -> ScanStatus.PROCESSING
                IDVerificationEngine.ScanMode.COMPLETED -> ScanStatus.COMPLETED
                IDVerificationEngine.ScanMode.ERROR -> ScanStatus.IDLE
            }
            
            callback?.onStatusChanged(status, 0f, message)
        }
        
        override fun onQualityUpdate(quality: QualityGate.QualityResult) {
            // Quality updates can be used for progress feedback
            val progress = if (quality.passed) 0.8f else 0.3f
            val status = when (currentMode) {
                IDVerificationEngine.ScanMode.SCANNING_FRONT -> ScanStatus.DETECTING_FRONT
                IDVerificationEngine.ScanMode.SCANNING_BACK -> ScanStatus.DETECTING_BACK
                else -> ScanStatus.IDLE
            }
            
            // Generate message from quality scores
            val message = when {
                !quality.passed && quality.blurScore < 0.5f -> "Görüntü bulanık - Kamerayı sabit tutun"
                !quality.passed && quality.glareScore < 0.5f -> "Parlama var - Işık açısını değiştirin"
                !quality.passed && quality.brightnessScore < 0.5f -> "Işık yetersiz - Daha aydınlık bir ortam"
                else -> "Kalite kontrol ediliyor..."
            }
            
            callback?.onStatusChanged(status, progress, message)
        }
        
        override fun onFrameAnalyzed(result: DecisionResult) {
            // Frame analysis provides real-time score updates
            val progress = result.totalScore / 100f
            val status = when (currentMode) {
                IDVerificationEngine.ScanMode.SCANNING_FRONT -> ScanStatus.DETECTING_FRONT
                IDVerificationEngine.ScanMode.SCANNING_BACK -> ScanStatus.DETECTING_BACK
                else -> ScanStatus.IDLE
            }
            
            val message = "Score: ${result.totalScore}/100"
            callback?.onStatusChanged(status, progress, message)
        }
        
        override fun onFrontCaptured(imageBytes: ByteArray, result: DecisionResult) {
            val qualityScore = result.totalScore / 100f
            callback?.onFrontCaptured(imageBytes, qualityScore)
        }
        
        override fun onBackCaptured(imageBytes: ByteArray, result: DecisionResult) {
            val qualityScore = result.totalScore / 100f
            callback?.onBackCaptured(imageBytes, qualityScore)
        }
        
        override fun onVerificationComplete(result: IDVerificationEngine.VerificationResult) {
            // Map new verification result to old ScanResult
            val mrzData = result.extractedData?.let { data ->
                MRZData(
                    documentType = "I",
                    issuingCountry = data.nationality ?: "TUR",
                    documentNumber = data.documentNumber ?: "",
                    birthDate = data.birthDate ?: "",
                    sex = data.sex ?: "",
                    expiryDate = data.expiryDate ?: "",
                    nationality = data.nationality ?: "TUR",
                    surname = data.surname ?: "",
                    givenNames = data.givenNames ?: "",
                    checksumValid = result.isValid,
                    rawMRZ = emptyList()
                )
            } ?: MRZData(
                documentType = "I",
                issuingCountry = "TUR",
                documentNumber = "",
                birthDate = "",
                sex = "",
                expiryDate = "",
                nationality = "TUR",
                surname = "",
                givenNames = "",
                checksumValid = false,
                rawMRZ = emptyList()
            )
            
            val scanResult = ScanResult(
                frontImage = result.frontImage,
                backImage = result.backImage,
                mrzData = mrzData,
                authenticityScore = result.totalScore / 100f,
                metadata = ScanMetadata(
                    scanDuration = 0L,
                    frontCaptureTimestamp = System.currentTimeMillis(),
                    backCaptureTimestamp = System.currentTimeMillis(),
                    blurScore = result.frontResult.scoreBreakdown.aspectRatioScore / 20f,
                    glareScore = result.backResult.scoreBreakdown.mrzStructureScore / 20f
                )
            )
            
            callback?.onCompleted(scanResult)
        }
        
        override fun onError(error: IDVerificationEngine.VerificationError) {
            val scanError = when (error.code) {
                "PERMISSION_DENIED" -> ScanError.CameraPermissionDenied()
                "MRZ_READ_FAILED", "MRZ_PARSING_FAILED" -> ScanError.MRZParsingFailed(error.messageTr)
                "IMAGE_QUALITY_LOW" -> ScanError.ImageQualityTooLow(error.messageTr)
                "CHECKSUM_INVALID" -> ScanError.ChecksumValidationFailed()
                else -> ScanError.UnknownError(error.messageTr)
            }
            callback?.onError(scanError)
        }
    }
    
    override fun startScanning(
        previewView: PreviewView,
        callback: ScanCallback,
        lifecycleOwner: LifecycleOwner?
    ) {
        this.callback = callback
        engine.startScanning(previewView, verificationCallback, lifecycleOwner)
    }
    
    override fun stopScanning() {
        engine.stopScanning()
    }
    
    override fun reset() {
        engine.reset()
    }
    
    override fun hasCameraPermission(): Boolean {
        return engine.hasCameraPermission()
    }
    
    override fun release() {
        engine.release()
        callback = null
    }
    
    override suspend fun captureFrontManually(): Boolean {
        return engine.captureFrontManually()
    }
    
    override suspend fun captureBackManually(): Boolean {
        return engine.captureBackManually()
    }
    
    override suspend fun completeScanManually(): Boolean {
        return engine.completeScanManually()
    }
}
