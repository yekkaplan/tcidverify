package com.idverify.sdk.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * ML Kit Text Recognition wrapper for MRZ detection
 */
class MLKitTextRecognizer {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Recognition result
     */
    data class RecognitionResult(
        val fullText: String,
        val lines: List<String>,
        val confidence: Float  // 0.0 - 1.0
    )
    
    /**
     * Recognize text from bitmap
     * @param bitmap Image containing MRZ
     * @return RecognitionResult with detected text
     */
    suspend fun recognizeText(bitmap: Bitmap): RecognitionResult = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks
                    .flatMap { it.lines }
                    .map { it.text }
                    .filter { it.isNotBlank() }
                
                val fullText = visionText.text
                
                // Calculate average confidence (if available)
                val confidence = if (lines.isNotEmpty()) 1.0f else 0.0f
                
                val result = RecognitionResult(
                    fullText = fullText,
                    lines = lines,
                    confidence = confidence
                )
                
                continuation.resume(result)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }
    
    /**
     * Extract MRZ lines from recognized text
     * Filters for lines that look like MRZ (30 characters, mostly uppercase/digits)
     */
    fun extractMRZLines(recognitionResult: RecognitionResult): List<String> {
        val parser = MRZParser()
        
        return recognitionResult.lines
            .map { parser.cleanMRZText(it) }
            .filter { it.length == com.idverify.sdk.utils.Constants.MRZ.LINE_LENGTH }
            .take(com.idverify.sdk.utils.Constants.MRZ.LINE_COUNT)
    }
    
    /**
     * Release resources
     */
    fun release() {
        recognizer.close()
    }
}
