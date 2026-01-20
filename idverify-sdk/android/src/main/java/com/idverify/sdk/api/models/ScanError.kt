package com.idverify.sdk.api.models

/**
 * Error types that can occur during scanning
 */
sealed class ScanError(
    val code: String,
    val message: String,
    val details: String? = null
) {
    class CameraPermissionDenied : ScanError(
        code = "CAMERA_PERMISSION_DENIED",
        message = "Camera permission is required for ID scanning"
    )
    
    class CameraInitializationFailed(details: String?) : ScanError(
        code = "CAMERA_INIT_FAILED",
        message = "Failed to initialize camera",
        details = details
    )
    
    class ImageQualityTooLow(details: String?) : ScanError(
        code = "IMAGE_QUALITY_LOW",
        message = "Image quality is too low. Please ensure good lighting and stable position",
        details = details
    )
    
    class MRZParsingFailed(details: String?) : ScanError(
        code = "MRZ_PARSING_FAILED",
        message = "Failed to read MRZ data from ID card",
        details = details
    )
    
    class ChecksumValidationFailed : ScanError(
        code = "CHECKSUM_INVALID",
        message = "MRZ checksum validation failed. The ID card may be damaged or counterfeit"
    )
    
    class UnknownError(details: String?) : ScanError(
        code = "UNKNOWN_ERROR",
        message = "An unexpected error occurred",
        details = details
    )
}
