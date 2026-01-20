package com.idverify.test

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Custom overlay view that shows a guide frame with darkened edges
 * Used to guide users to position ID card correctly
 */
class GuideFrameOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }
    
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // 50% black
        style = Paint.Style.FILL
    }
    
    var frameColor: Int = Color.parseColor("#4CAF50")
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // TC Kimlik Kartı boyutları: 85.60 mm × 53.98 mm
        // Aspect ratio: 1.5858 (ISO/IEC 7810 ID-1)
        val cardAspectRatio = 85.60f / 53.98f // 1.5858
        
        // Calculate frame dimensions to match card aspect ratio
        // Use 80% of screen width/height, centered
        val maxWidth = width * 0.8f
        val maxHeight = height * 0.8f
        
        // Calculate actual frame size maintaining aspect ratio
        val frameWidth: Float
        val frameHeight: Float
        
        if (maxWidth / maxHeight > cardAspectRatio) {
            // Height is limiting factor
            frameHeight = maxHeight
            frameWidth = frameHeight * cardAspectRatio
        } else {
            // Width is limiting factor
            frameWidth = maxWidth
            frameHeight = frameWidth / cardAspectRatio
        }
        
        // Center the frame
        val frameLeft = (width - frameWidth) / 2f
        val frameTop = (height - frameHeight) / 2f
        val frameRight = frameLeft + frameWidth
        val frameBottom = frameTop + frameHeight
        
        // Draw dark overlay (outside frame)
        val overlayPath = Path().apply {
            // Outer rectangle (full screen)
            addRect(0f, 0f, width, height, Path.Direction.CW)
            // Inner rectangle (frame area) - subtract this
            addRect(frameLeft, frameTop, frameRight, frameBottom, Path.Direction.CCW)
            fillType = Path.FillType.EVEN_ODD
        }
        canvas.drawPath(overlayPath, overlayPaint)
        
        // Draw guide frame border with rounded corners (3.18mm radius equivalent)
        paint.color = frameColor
        val cornerRadius = 12f // ~3.18mm equivalent at typical screen density
        val frameRect = RectF(frameLeft, frameTop, frameRight, frameBottom)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, paint)
        
        // Draw corner indicators for better alignment
        val cornerSize = 20f
        paint.strokeWidth = 4f
        paint.pathEffect = null // Solid lines for corners
        
        // Top-left corner
        canvas.drawLine(frameLeft, frameTop + cornerSize, frameLeft, frameTop, paint)
        canvas.drawLine(frameLeft, frameTop, frameLeft + cornerSize, frameTop, paint)
        
        // Top-right corner
        canvas.drawLine(frameRight - cornerSize, frameTop, frameRight, frameTop, paint)
        canvas.drawLine(frameRight, frameTop, frameRight, frameTop + cornerSize, paint)
        
        // Bottom-left corner
        canvas.drawLine(frameLeft, frameBottom - cornerSize, frameLeft, frameBottom, paint)
        canvas.drawLine(frameLeft, frameBottom, frameLeft + cornerSize, frameBottom, paint)
        
        // Bottom-right corner
        canvas.drawLine(frameRight - cornerSize, frameBottom, frameRight, frameBottom, paint)
        canvas.drawLine(frameRight, frameBottom - cornerSize, frameRight, frameBottom, paint)
        
        // Reset paint for next draw
        paint.strokeWidth = 8f
        paint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }
}
