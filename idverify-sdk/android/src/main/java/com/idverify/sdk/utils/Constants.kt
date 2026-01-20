package com.idverify.sdk.utils

/**
 * SDK-wide constants
 * Based on TC_ID_SPEC.md and ICAO Doc 9303
 */
object Constants {
    
    /**
     * Image Quality Thresholds (TC_ID_SPEC.md based)
     */
    object Quality {
        // Blur detection (Laplacian Variance)
        const val MIN_LAPLACIAN_VARIANCE = 100.0    // TC_ID_SPEC: > 100 = sharp
        const val MIN_BLUR_SCORE = 0.5f             // Normalized score threshold
        
        // Glare detection
        const val MIN_GLARE_SCORE = 0.6f            // Normalized score threshold
        const val MAX_GLARE_AREA_PERCENT = 5.0f     // TC_ID_SPEC: < 5% of card area
        const val BRIGHT_PIXEL_THRESHOLD = 250      // Luminance > 250 = bright
        
        // Luminance (brightness)
        const val MAX_LUMINANCE = 240               // Above = overexposed
        const val MIN_LUMINANCE = 30                // Below = underexposed
        
        // OCR
        const val MIN_OCR_CONFIDENCE = 0.85f        // TC_ID_SPEC: 85% per character
        
        // Overall
        const val MIN_AUTHENTICITY_SCORE = 0.7f     // Minimum overall score
    }
    
    /**
     * ID-1 Card Dimensions (ISO/IEC 7810 - TC_ID_SPEC.md)
     */
    object CardDimensions {
        const val WIDTH_MM = 85.60f
        const val HEIGHT_MM = 53.98f
        const val ASPECT_RATIO = WIDTH_MM / HEIGHT_MM  // 1.5858 (ideal)
        
        // Strict tolerance for OCR gate (user requirement: 1.55-1.62)
        const val ASPECT_RATIO_MIN_STRICT = 1.55f
        const val ASPECT_RATIO_MAX_STRICT = 1.62f
        
        // Loose tolerance for initial detection (TC_ID_SPEC: 1.50-1.65)
        const val ASPECT_RATIO_MIN = 1.50f
        const val ASPECT_RATIO_MAX = 1.65f
        
        const val CORNER_RADIUS_MM = 3.18f
    }
    
    /**
     * MRZ Format Constants (ICAO Doc 9303 - TD1)
     */
    object MRZ {
        const val LINE_COUNT = 3                    // TD-1 format has 3 lines
        const val LINE_LENGTH = 30                  // Each line is 30 characters
        
        // Character sets
        const val ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"
        val ALLOWED_CHARSET: Set<Char> = ('A'..'Z').toSet() + ('0'..'9').toSet() + setOf('<')
        
        // Checksum weights (7-3-1 algorithm - TC_ID_SPEC)
        val CHECKSUM_WEIGHTS = intArrayOf(7, 3, 1)
        
        // MRZ extraction region (bottom portion of card)
        const val EXTRACTION_RATIO_MIN = 0.22f      // Minimum: 22%
        const val EXTRACTION_RATIO_MAX = 0.28f      // Maximum: 28%
        const val EXTRACTION_RATIO_DEFAULT = 0.25f  // Default: 25%
        
        // Filler (<) ratio constraints
        const val MIN_FILLER_RATIO = 0.15f          // Minimum 15% fillers
        const val MAX_FILLER_RATIO = 0.40f          // Maximum 40% fillers
    }
    
    /**
     * Scoring Thresholds
     */
    object Scoring {
        // Decision thresholds
        const val THRESHOLD_VALID = 80              // ≥80: Valid ID
        const val THRESHOLD_RETRY = 50              // 50-79: Retry
        // <50: Invalid
        
        // Max points per category
        const val MAX_ASPECT_RATIO_SCORE = 20
        const val MAX_FRONT_TEXT_SCORE = 20
        const val MAX_MRZ_STRUCTURE_SCORE = 20
        const val MAX_MRZ_CHECKSUM_SCORE = 30
        const val MAX_TCKN_ALGORITHM_SCORE = 10
        const val MAX_TOTAL_SCORE = 100
    }
    
    /**
     * Multi-Frame Analysis
     */
    object FrameBuffer {
        const val MIN_REQUIRED_FRAMES = 3           // Minimum frames for decision
        const val MAX_REQUIRED_FRAMES = 5           // Maximum frames to consider
        const val MAX_BUFFER_SIZE = 10              // Maximum buffer size
        const val STABILITY_VARIANCE_THRESHOLD = 400 // Score variance for stability
    }
    
    /**
     * Camera Settings
     */
    object Camera {
        const val IMAGE_CAPTURE_QUALITY = 95        // JPEG quality (0-100)
        const val PREVIEW_ASPECT_RATIO_NUMERATOR = 4
        const val PREVIEW_ASPECT_RATIO_DENOMINATOR = 3
    }
    
    /**
     * Timing
     */
    object Timing {
        const val FRAME_ANALYSIS_INTERVAL_MS = 200L // Analyze every 200ms
        const val QUALITY_CHECK_DEBOUNCE_MS = 300L  // Debounce quality checks
        const val AUTO_CAPTURE_DELAY_MS = 500L      // Delay before auto-capture
        const val MRZ_READ_CONFIRMATION_MS = 500L   // Confirm MRZ before parsing
    }
    
    /**
     * TCKN (T.C. Kimlik Numarası) Validation
     */
    object TCKN {
        const val LENGTH = 11                       // Exactly 11 digits
        const val MIN_FIRST_DIGIT = '1'             // First digit cannot be 0
    }
    
    /**
     * Front Side Detection Patterns
     */
    object FrontSide {
        // Text patterns to look for (Turkish ID)
        val TURKIYE_PATTERNS = listOf(
            "TÜRKİYE",
            "TURKIYE",
            "TÜRKİYE CUMHURİYETİ",
            "TURKIYE CUMHURIYETI",
            "T.C.",
            "TC"
        )
        
        // Minimum uppercase ratio for ID card text
        const val MIN_UPPERCASE_RATIO = 0.6f
        
        // Date pattern: DD.MM.YYYY or DD/MM/YYYY
        val DATE_PATTERN = Regex("\\d{2}[./]\\d{2}[./]\\d{4}")
    }
}
