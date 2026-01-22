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
        const val MIN_BLUR_SCORE = 0.35f            // Relaxed: was 0.5 (holograms reduce sharpness)
        
        // Glare detection - RELAXED for holographic IDs
        const val MIN_GLARE_SCORE = 0.40f           // Relaxed: was 0.6 (holograms reflect light)
        const val MAX_GLARE_AREA_PERCENT = 15.0f    // Relaxed: was 5.0 (chip + hologram reflections)
        const val BRIGHT_PIXEL_THRESHOLD = 250      // Luminance > 250 = bright
        
        // Luminance (brightness)
        const val MAX_LUMINANCE = 240               // Above = overexposed
        const val MIN_LUMINANCE = 30                // Below = underexposed
        
        // OCR - RELAXED for embossed text on holograms
        const val MIN_OCR_CONFIDENCE = 0.70f        // Relaxed: was 0.85 (hologram overlay lowers confidence)
        
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
        
        // Strict tolerance - WIDENED for angled captures
        const val ASPECT_RATIO_MIN_STRICT = 1.50f      // Relaxed: was 1.55 (accept slight angles)
        const val ASPECT_RATIO_MAX_STRICT = 1.68f      // Relaxed: was 1.62 (mobile distortion)
        
        // Loose tolerance for initial detection
        const val ASPECT_RATIO_MIN = 1.45f             // Relaxed: was 1.50
        const val ASPECT_RATIO_MAX = 1.70f             // Relaxed: was 1.65
        
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
        
        // MRZ extraction region - ADJUSTED to avoid chip area
        const val EXTRACTION_RATIO_MIN = 0.15f      // Changed: was 0.22 (skip chip area)
        const val EXTRACTION_RATIO_MAX = 0.22f      // Changed: was 0.28 (pure MRZ zone)
        const val EXTRACTION_RATIO_DEFAULT = 0.18f  // Changed: was 0.25 (optimal for Turkish IDs)
        const val CHIP_SKIP_RATIO = 0.05f           // NEW: Skip top 5% (chip area)
        
        // Filler (<) ratio constraints - RELAXED for OCR errors
        const val MIN_FILLER_RATIO = 0.10f          // Relaxed: was 0.15 (OCR may miss some)
        const val MAX_FILLER_RATIO = 0.45f          // Relaxed: was 0.40 (OCR may add extra)
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
        const val MAX_MRZ_CHECKSUM_SCORE = 60 // Increased for native validation (was 30)
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
        const val IMAGE_CAPTURE_QUALITY = 95        // JPEG quality for CAPTURE (high for OCR)
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
