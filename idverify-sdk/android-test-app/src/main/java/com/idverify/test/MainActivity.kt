package com.idverify.test

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.idverify.sdk.autocapture.AutoCaptureAnalyzer
import com.idverify.sdk.core.NativeProcessor
import java.util.concurrent.Executors

/**
 * Simplified Test Application for ID Verification SDK
 * Focuses on the "Vision-First" Auto-Capture Workflow.
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "IDVerifyTest"
    }
    
    // UI Screens
    private lateinit var startScreen: LinearLayout
    private lateinit var cameraScreen: android.widget.FrameLayout
    private lateinit var resultScreen: ScrollView
    
    // UI Components (Camera Screen)
    private lateinit var previewView: PreviewView
    private lateinit var guideFrame: GuideFrameOverlay
    private lateinit var scoreText: TextView
    private lateinit var qualityText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var instructionTitle: TextView
    private lateinit var instructionMessage: TextView
    
    // UI Components (Result Screen)
    private lateinit var resultTitle: TextView
    private lateinit var resultInfo: TextView
    private lateinit var btnNewProcess: Button
    private lateinit var btnStartProcess: Button
    
    // Camera & Logic
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeAnalyzer: AutoCaptureAnalyzer? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    
    private var frontResult: AutoCaptureAnalyzer.CaptureResult? = null
    private var backResult: AutoCaptureAnalyzer.CaptureResult? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) showFrontCaptureScreen()
        else Toast.makeText(this, "Kamera izni gereklidir", Toast.LENGTH_LONG).show()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeViews()
        setupListeners()
        showStartScreen()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        activeAnalyzer?.release()
    }
    
    private fun initializeViews() {
        // Screens
        startScreen = findViewById(R.id.startScreen)
        cameraScreen = findViewById(R.id.cameraScreen)
        resultScreen = findViewById(R.id.resultScreen)
        
        // Camera View & Overlay
        previewView = findViewById(R.id.previewView)
        guideFrame = findViewById(R.id.guideFrame)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        
        // Dynamic UI from XML
        scoreText = findViewById(R.id.scoreText)
        qualityText = findViewById(R.id.qualityText)
        progressBar = findViewById(R.id.progressBar)
        instructionTitle = findViewById(R.id.instructionTitle)
        instructionMessage = findViewById(R.id.instructionMessage)
        
        // Buttons
        btnStartProcess = findViewById(R.id.btnStartProcess)
        btnNewProcess = findViewById(R.id.btnNewProcess)
        
        // Result
        resultTitle = findViewById(R.id.resultTitle)
        resultInfo = findViewById(R.id.resultInfo)
    }
    
    private fun setupListeners() {
        btnStartProcess.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
                showFrontCaptureScreen()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        btnNewProcess.setOnClickListener {
            frontResult = null
            backResult = null
            showStartScreen()
        }
    }
    
    // ==================== Navigation ====================
    
    private fun showStartScreen() {
        startScreen.visibility = View.VISIBLE
        cameraScreen.visibility = View.GONE
        resultScreen.visibility = View.GONE
        stopCamera()
    }
    
    private fun showFrontCaptureScreen() {
        startScreen.visibility = View.GONE
        cameraScreen.visibility = View.VISIBLE
        resultScreen.visibility = View.GONE
        
        instructionTitle.text = "Kimlik Ön Yüz"
        instructionMessage.text = "Kimliğin ön yüzünü çerçeveye yeşil yanana kadar hizalayın"
        scoreText.text = "Hazırlanıyor..."
        
        startCamera(isBackSide = false)
    }
    
    private fun showBackCaptureScreen() {
        instructionTitle.text = "Kimlik Arka Yüz (MRZ)"
        instructionMessage.text = "Arka yüzdeki MRZ kodlarını çerçeveye hizalayın"
        scoreText.text = "Arka yüz bekleniyor..."
        
        startCamera(isBackSide = true)
    }
    
    // ==================== Camera Workflow ====================
    
    private fun startCamera(isBackSide: Boolean) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            
            activeAnalyzer?.release()
            activeAnalyzer = AutoCaptureAnalyzer(
                isBackSide = isBackSide,
                onStateChange = { state, msg -> runOnUiThread { handleStateChange(state, msg) } },
                onQualityUpdate = { m -> runOnUiThread { updateQualityUI(m, isBackSide) } },
                onCaptured = { res -> runOnUiThread { handleCaptureSuccess(res, isBackSide) } }
            )
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(cameraExecutor, activeAnalyzer!!) }
            
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun stopCamera() {
        cameraProvider?.unbindAll()
        activeAnalyzer?.release()
        activeAnalyzer = null
    }
    
    // ==================== UI Feedback ====================
    
    private fun handleStateChange(state: AutoCaptureAnalyzer.CaptureState, message: String) {
        scoreText.text = message
        val color = when (state) {
            AutoCaptureAnalyzer.CaptureState.SEARCHING -> Color.GRAY
            AutoCaptureAnalyzer.CaptureState.ALIGNING -> Color.parseColor("#FF9800")
            AutoCaptureAnalyzer.CaptureState.VERIFYING -> Color.parseColor("#2196F3")
            AutoCaptureAnalyzer.CaptureState.CAPTURED -> Color.parseColor("#4CAF50")
            AutoCaptureAnalyzer.CaptureState.ERROR -> Color.RED
        }
        scoreText.setTextColor(color)
        guideFrame.frameColor = color
    }
    
    private fun updateQualityUI(metrics: AutoCaptureAnalyzer.QualityMetrics, isBackSide: Boolean) {
        val stabilityPct = (metrics.stability * 100).toInt()
        qualityText.text = "Netlik: ${metrics.blurScore.toInt()} | Stabilite: $stabilityPct% | Işık: ${100 - metrics.glareScore}%"
        progressBar.progress = stabilityPct
        
        guideFrame.updateState(
            detected = metrics.cardConfidence > 40,
            glareWarning = metrics.glareScore > 30,
            blurWarning = metrics.blurScore < 75,
            guidance = metrics.message,
            subGuidance = if (isBackSide) "MRZ Analiz Ediliyor" else "Otomatik Yakalama Aktif",
            quality = stabilityPct,
            mode = if (isBackSide) "ARKA YÜZ" else "ÖN YÜZ"
        )
    }
    
    private fun handleCaptureSuccess(result: AutoCaptureAnalyzer.CaptureResult, isBackSide: Boolean) {
        stopCamera()
        if (!isBackSide) {
            frontResult = result
            previewView.postDelayed({ showBackCaptureScreen() }, 800)
        } else {
            backResult = result
            showResultScreen()
        }
    }
    
    private fun showResultScreen() {
        cameraScreen.visibility = View.GONE
        resultScreen.visibility = View.VISIBLE
        
        val sb = StringBuilder()
        sb.append("✅ DOĞRULAMA TAMAMLANDI\n\n")
        
        frontResult?.let {
            sb.append("━━━ ÖN YÜZ VERİLERİ ━━━\n")
            sb.append("TCKN: ${it.extractedData["tckn"] ?: "---"}\n")
            sb.append("Soyadı: ${it.extractedData["surname"] ?: "---"}\n")
            sb.append("Adı: ${it.extractedData["name"] ?: "---"}\n")
            sb.append("Doğum Tarihi: ${it.extractedData["birthdate"] ?: "---"}\n")
            sb.append("Seri No: ${it.extractedData["serial"] ?: "---"}\n")
            sb.append("\n")
        }
        
        backResult?.let {
            sb.append("━━━ ARKA YÜZ VERİLERİ (MRZ) ━━━\n")
            sb.append("MRZ Skoru: ${it.mrzScore}/60 ${if (it.extractedData["mrzValid"] == "true") "✓" else ""}\n")
            sb.append("Belge No: ${it.extractedData["documentNumber"] ?: "---"}\n")
            sb.append("Doğum Tarihi: ${it.extractedData["birthDate"] ?: "---"}\n")
            sb.append("Son Geçerlilik: ${it.extractedData["expiryDate"] ?: "---"}\n")
            sb.append("Cinsiyet: ${it.extractedData["sex"] ?: "---"}\n")
            sb.append("Soyadı (MRZ): ${it.extractedData["surnameFromMRZ"] ?: "---"}\n")
            sb.append("Adı (MRZ): ${it.extractedData["nameFromMRZ"] ?: "---"}\n")
            
            val tcknFromMRZ = it.extractedData["tcknFromMRZ"]
            if (!tcknFromMRZ.isNullOrEmpty()) {
                sb.append("TCKN (MRZ): $tcknFromMRZ\n")
            }
            sb.append("\n")
        }
        
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n")
        val totalFields = (frontResult?.extractedData?.size ?: 0) + (backResult?.extractedData?.size ?: 0)
        sb.append("Toplam $totalFields alan başarıyla okundu.\n")
        
        resultInfo.text = sb.toString()
        resultTitle.setTextColor(Color.parseColor("#4CAF50"))
    }
}
