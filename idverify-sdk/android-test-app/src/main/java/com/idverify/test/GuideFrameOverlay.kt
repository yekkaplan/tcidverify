package com.idverify.test

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Smart Guide Frame Overlay with Real-time Feedback
 * 
 * Features:
 * - Card detection visual feedback (green = detected, red = not detected)
 * - Glare warning indicator
 * - User guidance text
 * - Quality pulse animation
 */
class GuideFrameOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Frame paint
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    
    // Overlay (darkened area outside frame)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // 50% black
        style = Paint.Style.FILL
    }
    
    // Guidance text paint
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    
    // Sub-text paint
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        textSize = 32f
        textAlign = Paint.Align.CENTER
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    
    // Warning indicator paint
    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B")
        style = Paint.Style.FILL
    }
    
    // Corner paint (solid lines for corners)
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    
    // Frame color based on detection status
    var frameColor: Int = Color.parseColor("#4CAF50") // Green
        set(value) {
            field = value
            framePaint.color = value
            cornerPaint.color = value
            invalidate()
        }
    
    // Card detection status
    var cardDetected: Boolean = false
        set(value) {
            field = value
            frameColor = if (value) {
                Color.parseColor("#4CAF50") // Green
            } else {
                Color.parseColor("#F44336") // Red
            }
            invalidate()
        }
    
    // Glare warning
    var showGlareWarning: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    
    // Blur warning
    var showBlurWarning: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    
    // User guidance text
    var guidanceText: String = ""
        set(value) {
            field = value
            invalidate()
        }
    
    // Sub guidance text (smaller)
    var subGuidanceText: String = ""
        set(value) {
            field = value
            invalidate()
        }
    
    // Quality score (0-100)
    var qualityScore: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }
    
    // Scanning mode text
    var modeText: String = "ÖN YÜZ"
        set(value) {
            field = value
            invalidate()
        }
    
    private var frameRect = RectF()
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        // TC Kimlik Kartı dimensions: 85.60 mm × 53.98 mm
        // Aspect ratio: 1.5858 (ISO/IEC 7810 ID-1)
        val cardAspectRatio = 85.60f / 53.98f
        
        // Calculate frame dimensions (75% of screen, centered)
        val maxWidth = w * 0.75f
        val maxHeight = h * 0.60f
        
        val frameWidth: Float
        val frameHeight: Float
        
        if (maxWidth / maxHeight > cardAspectRatio) {
            frameHeight = maxHeight
            frameWidth = frameHeight * cardAspectRatio
        } else {
            frameWidth = maxWidth
            frameHeight = frameWidth / cardAspectRatio
        }
        
        // Center the frame (slightly higher for better UX)
        val frameLeft = (w - frameWidth) / 2f
        val frameTop = (h - frameHeight) / 2f - 60f
        val frameRight = frameLeft + frameWidth
        val frameBottom = frameTop + frameHeight
        
        frameRect.set(frameLeft, frameTop, frameRight, frameBottom)
        
        // Draw dark overlay (outside frame)
        val overlayPath = Path().apply {
            addRect(0f, 0f, w, h, Path.Direction.CW)
            addRoundRect(frameRect, 16f, 16f, Path.Direction.CCW)
            fillType = Path.FillType.EVEN_ODD
        }
        canvas.drawPath(overlayPath, overlayPaint)
        
        // Draw main frame with dashed border
        framePaint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        canvas.drawRoundRect(frameRect, 16f, 16f, framePaint)
        
        // Draw corner indicators (solid)
        drawCorners(canvas, frameRect)
        
        // Draw mode indicator at top
        drawModeIndicator(canvas, frameLeft, frameTop - 20f)
        
        // Draw warning indicators
        if (showGlareWarning) {
            drawWarning(canvas, "☀️ Parlama algılandı", frameRect.centerX(), frameTop + 80f)
        }
        
        if (showBlurWarning) {
            drawWarning(canvas, "📷 Bulanık - Sabit tutun", frameRect.centerX(), frameTop + 80f + if (showGlareWarning) 50f else 0f)
        }
        
        // Draw guidance text below frame
        if (guidanceText.isNotEmpty()) {
            canvas.drawText(guidanceText, frameRect.centerX(), frameBottom + 60f, textPaint)
        }
        
        if (subGuidanceText.isNotEmpty()) {
            canvas.drawText(subGuidanceText, frameRect.centerX(), frameBottom + 100f, subTextPaint)
        }
        
        // Draw quality bar at bottom of frame
        drawQualityBar(canvas, frameRect)
    }
    
    private fun drawCorners(canvas: Canvas, rect: RectF) {
        val cornerLength = 40f
        cornerPaint.pathEffect = null
        
        // Top-left corner
        canvas.drawLine(rect.left, rect.top + cornerLength, rect.left, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left + cornerLength, rect.top, cornerPaint)
        
        // Top-right corner
        canvas.drawLine(rect.right - cornerLength, rect.top, rect.right, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLength, cornerPaint)
        
        // Bottom-left corner
        canvas.drawLine(rect.left, rect.bottom - cornerLength, rect.left, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLength, rect.bottom, cornerPaint)
        
        // Bottom-right corner
        canvas.drawLine(rect.right - cornerLength, rect.bottom, rect.right, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom - cornerLength, rect.right, rect.bottom, cornerPaint)
    }
    
    private fun drawModeIndicator(canvas: Canvas, x: Float, y: Float) {
        val modePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = frameColor
            textSize = 28f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("📋 $modeText", x, y, modePaint)
    }
    
    private fun drawWarning(canvas: Canvas, text: String, x: Float, y: Float) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC000000")
            style = Paint.Style.FILL
        }
        
        val warnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFEB3B")
            textSize = 36f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val textWidth = warnTextPaint.measureText(text)
        val padding = 24f
        
        canvas.drawRoundRect(
            x - textWidth / 2 - padding,
            y - 36f,
            x + textWidth / 2 + padding,
            y + 12f,
            12f, 12f,
            bgPaint
        )
        
        canvas.drawText(text, x, y, warnTextPaint)
    }
    
    private fun drawQualityBar(canvas: Canvas, rect: RectF) {
        val barHeight = 8f
        val barY = rect.bottom + 16f
        val barWidth = rect.width()
        
        // Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FFFFFF")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            rect.left, barY,
            rect.right, barY + barHeight,
            4f, 4f, bgPaint
        )
        
        // Progress
        val progressWidth = (qualityScore / 100f) * barWidth
        val progressColor = when {
            qualityScore >= 70 -> Color.parseColor("#4CAF50") // Green
            qualityScore >= 40 -> Color.parseColor("#FF9800") // Orange
            else -> Color.parseColor("#F44336") // Red
        }
        
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = progressColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            rect.left, barY,
            rect.left + progressWidth, barY + barHeight,
            4f, 4f, progressPaint
        )
    }
    
    /**
     * Update all visual indicators at once
     */
    fun updateState(
        detected: Boolean,
        glareWarning: Boolean,
        blurWarning: Boolean,
        guidance: String,
        subGuidance: String = "",
        quality: Int,
        mode: String
    ) {
        cardDetected = detected
        showGlareWarning = glareWarning
        showBlurWarning = blurWarning
        guidanceText = guidance
        subGuidanceText = subGuidance
        qualityScore = quality
        modeText = mode
        invalidate()
    }
}
