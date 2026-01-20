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
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.idverify.sdk.core.IDVerificationEngine
import com.idverify.sdk.decision.DecisionResult
import com.idverify.sdk.detection.QualityGate
import com.idverify.sdk.scoring.ScoringEngine
import kotlinx.coroutines.launch

/**
 * Test Application for ID Verification SDK
 * 
 * Demonstrates the new deterministic, scoring-based architecture:
 * - Quality Gate (blur, glare, brightness)
 * - Aspect Ratio validation
 * - Front/Back pipelines
 * - Multi-frame analysis
 * - 100-point scoring system
 */
class MainActivity : AppCompatActivity(), IDVerificationEngine.VerificationCallback {
    
    companion object {
        private const val TAG = "IDVerifyTest"
    }
    
    // Screens
    private lateinit var startScreen: LinearLayout
    private lateinit var cameraScreen: android.widget.FrameLayout
    private lateinit var resultScreen: ScrollView
    
    // Camera components
    private lateinit var previewView: PreviewView
    private lateinit var guideFrame: GuideFrameOverlay
    private lateinit var instructionCard: CardView
    private lateinit var instructionTitle: TextView
    private lateinit var instructionMessage: TextView
    private lateinit var btnCapture: Button
    private lateinit var statusCard: CardView
    private lateinit var statusMessage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var debugCard: CardView
    private lateinit var debugText: TextView
    private lateinit var btnToggleDebug: Button
    
    // Score display
    private lateinit var scoreCard: CardView
    private lateinit var scoreText: TextView
    private lateinit var qualityText: TextView
    
    // Result components
    private lateinit var resultTitle: TextView
    private lateinit var resultInfo: TextView
    private lateinit var btnNewProcess: Button
    
    // Control buttons
    private lateinit var btnStartProcess: Button
    
    // NEW: Use the new IDVerificationEngine
    private var engine: IDVerificationEngine? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Camera permission granted")
            showFrontCaptureScreen()
        } else {
            Log.e(TAG, "Camera permission denied")
            Toast.makeText(this, "Kamera izni gereklidir", Toast.LENGTH_LONG).show()
            showStartScreen()
        }
    }
    
    // ==================== Lifecycle ====================
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupListeners()
        createScoreCard()
        showStartScreen()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        engine?.release()
    }
    
    // ==================== UI Setup ====================
    
    private fun initializeViews() {
        // Screens
        startScreen = findViewById(R.id.startScreen)
        cameraScreen = findViewById(R.id.cameraScreen)
        resultScreen = findViewById(R.id.resultScreen)
        
        // Camera components
        previewView = findViewById(R.id.previewView)
        guideFrame = findViewById(R.id.guideFrame)
        instructionCard = findViewById(R.id.instructionCard)
        instructionTitle = findViewById(R.id.instructionTitle)
        instructionMessage = findViewById(R.id.instructionMessage)
        btnCapture = findViewById(R.id.btnCapture)
        statusCard = findViewById(R.id.statusCard)
        statusMessage = findViewById(R.id.statusMessage)
        progressBar = findViewById(R.id.progressBar)
        debugCard = findViewById(R.id.debugCard)
        debugText = findViewById(R.id.debugText)
        btnToggleDebug = findViewById(R.id.btnToggleDebug)
        
        // Result components
        resultTitle = findViewById(R.id.resultTitle)
        resultInfo = findViewById(R.id.resultInfo)
        btnNewProcess = findViewById(R.id.btnNewProcess)
        
        // Start button
        btnStartProcess = findViewById(R.id.btnStartProcess)
        
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
    
    /**
     * Create dynamic score card overlay (not in XML)
     */
    private fun createScoreCard() {
        // Create score card programmatically
        scoreCard = CardView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(48, 0, 48, 250)
                gravity = android.view.Gravity.BOTTOM
            }
            radius = 24f
            cardElevation = 8f
            setCardBackgroundColor(Color.parseColor("#E8F5E9"))
            visibility = View.GONE
        }
        
        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        
        scoreText = TextView(this).apply {
            text = "Skor: --/100"
            textSize = 20f
            setTextColor(Color.parseColor("#2E7D32"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        
        qualityText = TextView(this).apply {
            text = "Kalite: --"
            textSize = 12f
            setTextColor(Color.parseColor("#558B2F"))
            gravity = android.view.Gravity.CENTER
        }
        
        innerLayout.addView(scoreText)
        innerLayout.addView(qualityText)
        scoreCard.addView(innerLayout)
        cameraScreen.addView(scoreCard)
    }
    
    private fun setupListeners() {
        btnStartProcess.setOnClickListener {
            startProcess()
        }
        
        btnCapture.setOnClickListener {
            handleCaptureClick()
        }
        
        btnNewProcess.setOnClickListener {
            resetAndStartOver()
        }
        
        btnToggleDebug.setOnClickListener {
            debugCard.visibility = if (debugCard.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }
    
    // ==================== Screen Navigation ====================
    
    private fun showStartScreen() {
        startScreen.visibility = View.VISIBLE
        cameraScreen.visibility = View.GONE
        resultScreen.visibility = View.GONE
        scoreCard.visibility = View.GONE
        
        engine?.stopScanning()
    }
    
    private fun startProcess() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            showFrontCaptureScreen()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun showFrontCaptureScreen() {
        startScreen.visibility = View.GONE
        cameraScreen.visibility = View.VISIBLE
        resultScreen.visibility = View.GONE
        scoreCard.visibility = View.VISIBLE
        
        // Update UI for front side
        instructionTitle.text = "📷 Kimlik Ön Yüz Okuma"
        instructionMessage.text = "Lütfen kimliğinizin ön yüzünü çerçeveye yerleştirin.\nSkor 50+ olduğunda yakalayabilirsiniz."
        btnCapture.text = "Ön Yüzü Yakala"
        btnCapture.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_orange_dark)
        guideFrame.frameColor = Color.parseColor("#FF9800")
        statusCard.visibility = View.GONE
        
        // Initialize engine
        initializeEngine()
    }
    
    private fun showBackCaptureScreen() {
        // Update UI for back side (MRZ)
        instructionTitle.text = "📷 Kimlik Arka Yüz Okuma (MRZ)"
        instructionMessage.text = "Lütfen kimliğinizin arka yüzündeki MRZ alanını okutun.\nSkor 50+ olduğunda yakalayabilirsiniz."
        btnCapture.text = "Arka Yüzü Yakala (MRZ)"
        btnCapture.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_blue_dark)
        guideFrame.frameColor = Color.parseColor("#2196F3")
        statusCard.visibility = View.GONE
        btnCapture.isEnabled = true
        
        // Switch engine to back side mode
        engine?.switchToBackSide()
    }
    
    private fun showResultScreen(result: IDVerificationEngine.VerificationResult) {
        startScreen.visibility = View.GONE
        cameraScreen.visibility = View.GONE
        resultScreen.visibility = View.VISIBLE
        scoreCard.visibility = View.GONE
        
        engine?.stopScanning()
        
        displayResult(result)
    }
    
    // ==================== Engine Management ====================
    
    private fun initializeEngine() {
        if (engine == null) {
            engine = IDVerificationEngine(this)
        }
        
        previewView.post {
            engine?.startScanning(previewView, this, this)
        }
    }
    
    private fun handleCaptureClick() {
        val currentMode = engine?.getCurrentMode() ?: return
        btnCapture.isEnabled = false
        
        when (currentMode) {
            IDVerificationEngine.ScanMode.SCANNING_FRONT -> captureFront()
            IDVerificationEngine.ScanMode.FRONT_CAPTURED,
            IDVerificationEngine.ScanMode.SCANNING_BACK -> captureBack()
            else -> {
                btnCapture.isEnabled = true
            }
        }
    }
    
    private fun captureFront() {
        showStatusMessage("İşleniyor", "Ön yüz yakalanıyor...")
        
        lifecycleScope.launch {
            val success = engine?.captureFrontManually() ?: false
            
            runOnUiThread {
                if (success) {
                    Toast.makeText(this@MainActivity, "✓ Ön yüz yakalandı!", Toast.LENGTH_SHORT).show()
                    showBackCaptureScreen()
                } else {
                    btnCapture.isEnabled = true
                    statusCard.visibility = View.GONE
                    Toast.makeText(
                        this@MainActivity,
                        "Skor yetersiz. Kartı daha net konumlandırın.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun captureBack() {
        showStatusMessage("İşleniyor", "Arka yüz (MRZ) yakalanıyor...")
        addDebugLog("Arka yüz capture başladı")
        
        lifecycleScope.launch {
            try {
                val success = engine?.captureBackManually() ?: false
                addDebugLog("captureBackManually sonucu: $success")
                
                if (success) {
                    // Complete verification
                    addDebugLog("Doğrulama tamamlanıyor...")
                    val result = engine?.completeVerification()
                    
                    runOnUiThread {
                        if (result != null) {
                            showResultScreen(result)
                        } else {
                            btnCapture.isEnabled = true
                            statusCard.visibility = View.GONE
                            Toast.makeText(
                                this@MainActivity,
                                "Doğrulama tamamlanamadı",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        btnCapture.isEnabled = true
                        statusCard.visibility = View.GONE
                        addDebugLog("Arka yüz yakalama başarısız")
                        Toast.makeText(
                            this@MainActivity,
                            "MRZ okunamadı. Kartı düzgün hizalayın.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capture error", e)
                addDebugLog("HATA: ${e.message}")
                runOnUiThread {
                    btnCapture.isEnabled = true
                    Toast.makeText(this@MainActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun resetAndStartOver() {
        engine?.reset()
        engine?.release()
        engine = null
        showStartScreen()
    }
    
    // ==================== Callback Implementation ====================
    
    override fun onModeChanged(mode: IDVerificationEngine.ScanMode, message: String) {
        Log.d(TAG, "Mode changed: $mode - $message")
        runOnUiThread {
            statusMessage.text = message
        }
    }
    
    override fun onQualityUpdate(quality: QualityGate.QualityResult) {
        runOnUiThread {
            val blurPercent = (quality.blurScore * 100).toInt()
            val glarePercent = (quality.glareScore * 100).toInt()
            val brightnessPercent = (quality.brightnessScore * 100).toInt()
            
            qualityText.text = "Netlik: $blurPercent% | Işık: $glarePercent% | Parlaklık: $brightnessPercent%"
            
            // Color code quality
            val color = when {
                !quality.passed -> Color.parseColor("#F44336")  // Red
                quality.overallScore >= 0.8f -> Color.parseColor("#4CAF50")  // Green
                else -> Color.parseColor("#FF9800")  // Orange
            }
            qualityText.setTextColor(color)
        }
    }
    
    override fun onFrameAnalyzed(result: DecisionResult) {
        runOnUiThread {
            val score = result.totalScore
            scoreText.text = "Skor: $score/100"
            
            // Color based on score
            val (bgColor, textColor) = when {
                score >= 80 -> Pair("#E8F5E9", "#2E7D32")  // Green
                score >= 50 -> Pair("#FFF3E0", "#E65100")  // Orange
                else -> Pair("#FFEBEE", "#C62828")         // Red
            }
            
            scoreCard.setCardBackgroundColor(Color.parseColor(bgColor))
            scoreText.setTextColor(Color.parseColor(textColor))
            
            // Show decision hint
            val hint = when (result.decision) {
                DecisionResult.Decision.VALID -> "✓ Geçerli - Yakalayabilirsiniz"
                DecisionResult.Decision.RETRY -> "⚠ Yakalanabilir - Kaliteyi artırın"
                DecisionResult.Decision.INVALID -> "✗ Yetersiz - Kartı hizalayın"
            }
            
            addDebugLog("Frame: $score pts - ${result.decision}")
            
            // Update progress bar
            progressBar.progress = score
        }
    }
    
    override fun onFrontCaptured(imageBytes: ByteArray, result: DecisionResult) {
        Log.d(TAG, "Front captured: ${imageBytes.size} bytes, score: ${result.totalScore}")
        addDebugLog("Ön yüz yakalandı: ${result.totalScore} puan")
    }
    
    override fun onBackCaptured(imageBytes: ByteArray, result: DecisionResult) {
        Log.d(TAG, "Back captured: ${imageBytes.size} bytes, score: ${result.totalScore}")
        addDebugLog("Arka yüz yakalandı: ${result.totalScore} puan")
    }
    
    override fun onVerificationComplete(result: IDVerificationEngine.VerificationResult) {
        Log.d(TAG, "Verification complete: score=${result.totalScore}, valid=${result.isValid}")
        // Handled in captureBack()
    }
    
    override fun onError(error: IDVerificationEngine.VerificationError) {
        Log.e(TAG, "Verification error: ${error.code} - ${error.message}")
        addDebugLog("HATA: ${error.messageTr}")
        
        runOnUiThread {
            Toast.makeText(this, error.messageTr, Toast.LENGTH_LONG).show()
            
            if (error.code == "MISSING_IMAGES") {
                showStartScreen()
            }
        }
    }
    
    // ==================== UI Helpers ====================
    
    private fun showStatusMessage(title: String, message: String) {
        statusMessage.text = "$title: $message"
        statusCard.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
    }
    
    private fun addDebugLog(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(java.util.Date())
            val current = debugText.text.toString()
            val lines = current.split("\n").takeLast(15)
            debugText.text = (lines + "[$timestamp] $message").joinToString("\n")
        }
    }
    
    private fun displayResult(result: IDVerificationEngine.VerificationResult) {
        val data = result.extractedData
        val frontBreakdown = result.frontResult.scoreBreakdown
        val backBreakdown = result.backResult.scoreBreakdown
        
        // Determine result status
        val (statusIcon, statusText, statusColor) = when (result.decision) {
            DecisionResult.Decision.VALID -> Triple("✓", "Doğrulama Başarılı", "#4CAF50")
            DecisionResult.Decision.RETRY -> Triple("⚠", "Kısmen Doğrulandı", "#FF9800")
            DecisionResult.Decision.INVALID -> Triple("✗", "Doğrulama Başarısız", "#F44336")
        }
        
        resultTitle.text = "$statusIcon $statusText"
        resultTitle.setTextColor(Color.parseColor(statusColor))
        
        val info = buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📊 SKOR DETAYI (${result.totalScore}/100)")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("Ön Yüz Analizi:")
            appendLine("  ├ Kart Oranı: ${frontBreakdown.aspectRatioScore}/20")
            appendLine("  ├ Metin Yapısı: ${frontBreakdown.frontTextScore}/20")
            appendLine("  └ TC No Algoritması: ${frontBreakdown.tcknAlgorithmScore}/10")
            appendLine()
            appendLine("Arka Yüz Analizi (MRZ):")
            appendLine("  ├ MRZ Yapısı: ${backBreakdown.mrzStructureScore}/20")
            appendLine("  └ MRZ Checksum: ${backBreakdown.mrzChecksumScore}/30")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📋 OKUNAN VERİLER")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            
            if (data != null) {
                appendLine("🆔 TC Kimlik No: ${data.tcKimlikNo ?: "Bulunamadı"}")
                appendLine("📄 Belge No: ${data.documentNumber ?: "Bulunamadı"}")
                appendLine("👤 Ad: ${data.givenNames ?: "Bulunamadı"}")
                appendLine("👤 Soyad: ${data.surname ?: "Bulunamadı"}")
                appendLine("📅 Doğum Tarihi: ${formatMRZDate(data.birthDate)}")
                appendLine("📅 Geçerlilik: ${formatMRZDate(data.expiryDate)}")
                appendLine("⚧️ Cinsiyet: ${data.sex ?: "Belirtilmemiş"}")
                appendLine("🌍 Uyruk: ${data.nationality ?: "Bulunamadı"}")
            } else {
                appendLine("Veri çıkarılamadı")
            }
            
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📸 GÖRÜNTÜLER")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("  • Ön Yüz: ${result.frontImage.size / 1024} KB")
            appendLine("  • Arka Yüz: ${result.backImage.size / 1024} KB")
            
            // Show MRZ lines if available
            val mrzLines = result.backResult.rawData?.mrzLines
            if (!mrzLines.isNullOrEmpty()) {
                appendLine()
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📝 MRZ HAM VERİSİ")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine()
                mrzLines.forEach { line ->
                    appendLine("  $line")
                }
            }
            
            // Show validation errors if any
            val errors = (result.frontResult.errors + result.backResult.errors).distinct()
            if (errors.isNotEmpty()) {
                appendLine()
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("⚠️ UYARILAR")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine()
                errors.forEach { error ->
                    appendLine("  • ${error.messageTr}")
                }
            }
        }
        
        resultInfo.text = info
    }
    
    private fun formatMRZDate(dateStr: String?): String {
        if (dateStr == null || dateStr.length != 6) return dateStr ?: "Bulunamadı"
        
        return try {
            val yy = dateStr.substring(0, 2).toInt()
            val mm = dateStr.substring(2, 4)
            val dd = dateStr.substring(4, 6)
            val yyyy = if (yy <= 30) "20$yy" else "19$yy"
            "$dd.$mm.$yyyy"
        } catch (e: Exception) {
            dateStr
        }
    }
}
