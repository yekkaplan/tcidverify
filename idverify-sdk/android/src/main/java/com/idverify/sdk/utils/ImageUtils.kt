package com.idverify.sdk.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Utility functions for image processing
 */
object ImageUtils {
    
    /**
     * Convert ImageProxy to Bitmap
     */
    fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    /**
     * Convert Bitmap to JPEG bytes
     */
    fun Bitmap.toJpegBytes(quality: Int = Constants.Camera.IMAGE_CAPTURE_QUALITY): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
    
    /**
     * Convert Bitmap to grayscale
     */
    fun Bitmap.toGrayscale(): Bitmap {
        val width = this.width
        val height = this.height
        val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val red = (pixel shr 16) and 0xff
            val green = (pixel shr 8) and 0xff
            val blue = pixel and 0xff
            
            val gray = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
            pixels[i] = (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        
        grayscale.setPixels(pixels, 0, width, 0, 0, width, height)
        return grayscale
    }
    
    /**
     * Calculate mean luminance of bitmap
     */
    fun Bitmap.meanLuminance(): Double {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        
        var sum = 0.0
        for (pixel in pixels) {
            val red = (pixel shr 16) and 0xff
            val green = (pixel shr 8) and 0xff
            val blue = pixel and 0xff
            // Calculate relative luminance
            sum += 0.299 * red + 0.587 * green + 0.114 * blue
        }
        
        return sum / pixels.size
    }
    
    /**
     * Preprocess bitmap for better OCR (MRZ reading)
     * Applies contrast enhancement
     */
    fun preprocessForOCR(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Increase contrast for better text recognition
        for (i in pixels.indices) {
            val pixel = pixels[i]
            var red = (pixel shr 16) and 0xff
            var green = (pixel shr 8) and 0xff
            var blue = pixel and 0xff
            
            // Increase contrast (1.5x) and adjust brightness
            red = ((red - 128) * 1.5 + 128 - 20).toInt().coerceIn(0, 255)
            green = ((green - 128) * 1.5 + 128 - 20).toInt().coerceIn(0, 255)
            blue = ((blue - 128) * 1.5 + 128 - 20).toInt().coerceIn(0, 255)
            
            pixels[i] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
        }
        
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
    
    /**
     * Extract bottom portion of bitmap (where MRZ typically is)
     */
    fun extractBottomPortion(bitmap: Bitmap, ratio: Float = 0.3f): Bitmap {
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val y = bitmap.height - height
        return Bitmap.createBitmap(bitmap, 0, y, bitmap.width, height)
    }
}
