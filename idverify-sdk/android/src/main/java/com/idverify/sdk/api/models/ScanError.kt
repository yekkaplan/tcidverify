package com.idverify.sdk.api.models

/**
 * Error types that can occur during scanning
 */
sealed class ScanError(
    open val code: String,
    open val message: String,
    open val details: Any? = null
) {
    class CameraPermissionDenied : ScanError(
        code = "CAMERA_PERMISSION_DENIED",
        message = "Camera permission is required for ID scanning"
    )
    
    class CameraInitializationFailed(override val details: Any? = null) : ScanError(
        code = "CAMERA_INIT_FAILED",
        message = "Failed to initialize camera",
        details = details
    )
    
    class ImageQualityTooLow(override val details: Any? = null) : ScanError(
        code = "IMAGE_QUALITY_LOW",
        message = "Image quality is too low. Please ensure good lighting and stable position",
        details = details
    )
    
    class MRZParsingFailed(override val details: Any? = null) : ScanError(
        code = "MRZ_PARSING_FAILED",
        message = "Failed to read MRZ data from ID card",
        details = details
    )
    
    class ChecksumValidationFailed : ScanError(
        code = "CHECKSUM_INVALID",
        message = "MRZ checksum validation failed. The ID card may be damaged or counterfeit"
    )
    
    class UnknownError(override val details: Any? = null) : ScanError(
        code = "UNKNOWN_ERROR",
        message = "An unexpected error occurred",
        details = details
    )
    
    class ProcessingFailed(
        override val message: String,
        override val details: Any? = null
    ) : ScanError(
        code = "PROCESSING_FAILED",
        message = message,
        details = details
    )
    
    class MRZReadFailed(
        override val message: String,
        override val details: Any? = null
    ) : ScanError(
        code = "MRZ_READ_FAILED",
        message = message,
        details = details
    )
}
