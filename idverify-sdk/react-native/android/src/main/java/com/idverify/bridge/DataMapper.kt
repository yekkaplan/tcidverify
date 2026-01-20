package com.idverify.bridge

import android.graphics.Bitmap
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.idverify.sdk.api.models.MRZData
import com.idverify.sdk.api.models.ScanMetadata
import com.idverify.sdk.api.models.ScanResult
import com.idverify.sdk.api.models.ScanStatus
import java.io.ByteArrayOutputStream

/**
 * Maps SDK data models to React Native WritableMap
 * Handles serialization of native objects to JavaScript-compatible format
 */
object DataMapper {
    
    /**
     * Convert ScanResult to WritableMap for JavaScript
     * @param result Native scan result
     * @param useBase64 If true, encode images as Base64, otherwise return empty strings
     */
    fun scanResultToMap(result: ScanResult, useBase64: Boolean = true): WritableMap {
        return Arguments.createMap().apply {
            if (useBase64) {
                putString("frontImage", encodeImageToBase64(result.frontImage))
                putString("backImage", encodeImageToBase64(result.backImage))
            } else {
                // For file URI mode (future implementation)
                putString("frontImage", "")
                putString("backImage", "")
            }
            putMap("mrzData", mrzDataToMap(result.mrzData))
            putDouble("authenticityScore", result.authenticityScore.toDouble())
            putMap("metadata", metadataToMap(result.metadata))
        }
    }
    
    /**
     * Convert MRZData to WritableMap
     */
    fun mrzDataToMap(mrz: MRZData): WritableMap {
        return Arguments.createMap().apply {
            putString("documentType", mrz.documentType)
            putString("issuingCountry", mrz.issuingCountry)
            putString("documentNumber", mrz.documentNumber)
            putString("birthDate", mrz.birthDate)
            putString("sex", mrz.sex)
            putString("expiryDate", mrz.expiryDate)
            putString("nationality", mrz.nationality)
            putString("surname", mrz.surname)
            putString("givenNames", mrz.givenNames)
            putBoolean("checksumValid", mrz.checksumValid)
            
            // Raw MRZ lines
            val rawMrzArray = Arguments.createArray()
            mrz.rawMRZ.forEach { rawMrzArray.pushString(it) }
            putArray("rawMRZ", rawMrzArray)
        }
    }
    
    /**
     * Convert ScanMetadata to WritableMap
     */
    fun metadataToMap(metadata: ScanMetadata): WritableMap {
        return Arguments.createMap().apply {
            putDouble("scanDuration", metadata.scanDuration.toDouble())
            putDouble("frontCaptureTimestamp", metadata.frontCaptureTimestamp.toDouble())
            putDouble("backCaptureTimestamp", metadata.backCaptureTimestamp.toDouble())
            putDouble("blurScore", metadata.blurScore.toDouble())
            putDouble("glareScore", metadata.glareScore.toDouble())
        }
    }
    
    /**
     * Convert ScanStatus to WritableMap
     */
    fun scanStatusToMap(
        status: ScanStatus,
        progress: Float = 0f,
        message: String? = null
    ): WritableMap {
        return Arguments.createMap().apply {
            putString("status", status.name)
            putDouble("progress", progress.toDouble())
            if (message != null) {
                putString("message", message)
            }
        }
    }
    
    /**
     * Convert image bytes to WritableMap with base64 and quality score
     */
    fun imageToMap(imageBytes: ByteArray, qualityScore: Float): WritableMap {
        return Arguments.createMap().apply {
            putString("image", encodeImageToBase64(imageBytes))
            putDouble("qualityScore", qualityScore.toDouble())
        }
    }
    
    /**
     * Convert error to WritableMap
     */
    fun errorToMap(code: String, message: String, details: String? = null): WritableMap {
        return Arguments.createMap().apply {
            putString("code", code)
            putString("message", message)
            if (details != null) {
                putString("details", details)
            }
        }
    }
    
    /**
     * Encode byte array to Base64 string
     */
    private fun encodeImageToBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
