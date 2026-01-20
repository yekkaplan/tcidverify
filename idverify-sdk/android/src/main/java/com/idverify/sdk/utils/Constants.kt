package com.idverify.sdk.utils

/**
 * SDK-wide constants
 */
object Constants {
    
    // Image Quality Thresholds
    object Quality {
        const val MIN_BLUR_SCORE = 0.4f              // Minimum acceptable blur score
        const val MIN_GLARE_SCORE = 0.5f             // Minimum acceptable glare score
        const val MIN_AUTHENTICITY_SCORE = 0.6f      // Minimum overall authenticity
        
        // Laplacian variance threshold for blur detection
        const val LAPLACIAN_THRESHOLD = 100.0        // Values > 100 = sharp
        
        // Luminance thresholds for glare detection
        const val MAX_MEAN_LUMINANCE = 240           // Above this = overexposed
        const val MIN_MEAN_LUMINANCE = 30            // Below this = underexposed
    }
    
    // ID-1 Card Dimensions (ISO/IEC 7810)
    object CardDimensions {
        const val WIDTH_MM = 85.6f
        const val HEIGHT_MM = 53.98f
        const val ASPECT_RATIO = WIDTH_MM / HEIGHT_MM  // ~1.586
        const val ASPECT_RATIO_TOLERANCE = 0.15f       // ±15% tolerance
    }
    
    // MRZ Format Constants (ICAO Doc 9303 - TD1)
    object MRZ {
        const val LINE_COUNT = 3                     // TD-1 format has 3 lines
        const val LINE_LENGTH = 30                   // Each line is 30 characters
        
        // Character sets
        const val ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"
        
        // Checksum weights (7-3-1 algorithm)
        val CHECKSUM_WEIGHTS = intArrayOf(7, 3, 1)
    }
    
    // Camera Settings
    object Camera {
        const val IMAGE_CAPTURE_QUALITY = 95         // JPEG quality (0-100)
        const val PREVIEW_ASPECT_RATIO_NUMERATOR = 4
        const val PREVIEW_ASPECT_RATIO_DENOMINATOR = 3
    }
    
    // Timing
    object Timing {
        const val AUTO_CAPTURE_DELAY_MS = 500L       // Delay before auto-capture
        const val FRAME_ANALYSIS_INTERVAL_MS = 100L  // Analyze every 100ms
    }
}
