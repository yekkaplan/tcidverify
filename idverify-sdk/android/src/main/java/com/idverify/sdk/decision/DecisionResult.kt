package com.idverify.sdk.decision

/**
 * Final decision result for ID verification
 * 
 * Scoring Thresholds (100 points total):
 * - ≥80: VALID (Gerçek T.C. Kimlik Kartı)
 * - 50-79: RETRY (Tekrar Okut)  
 * - <50: INVALID (Geçersiz)
 */
data class DecisionResult(
    val decision: Decision,
    val totalScore: Int,
    val scoreBreakdown: ScoreBreakdown,
    val confidence: Float,
    val errors: List<ValidationError>,
    val rawData: RawExtractionData?
) {
    
    /**
     * Final decision categories
     */
    enum class Decision {
        /** Score ≥80: Verified as genuine T.C. ID Card */
        VALID,
        
        /** Score 50-79: Quality insufficient, retry capture */
        RETRY,
        
        /** Score <50: Not a valid T.C. ID Card */
        INVALID
    }
    
    /**
     * Detailed score breakdown (total 100 points)
     */
    data class ScoreBreakdown(
        /** Card aspect ratio validation: +20 points */
        val aspectRatioScore: Int = 0,
        
        /** Front side text structure: +20 points */
        val frontTextScore: Int = 0,
        
        /** MRZ regex and line structure: +20 points */
        val mrzStructureScore: Int = 0,
        
        /** MRZ ICAO checksum validation: +30-60 points
         *  - Native (3 lines): 60 pts
         *  - Java (3 lines): up to 30 pts
         *  - Java (2 lines): up to 30 pts
         */
        val mrzChecksumScore: Int = 0,
        
        /** T.C. Kimlik No algorithm validation: +10 points */
        val tcknAlgorithmScore: Int = 0
    ) {
        val total: Int get() = (aspectRatioScore + frontTextScore + mrzStructureScore + 
                              mrzChecksumScore + tcknAlgorithmScore).coerceAtMost(100)
    }
    
    /**
     * Raw extraction data for debugging/logging
     */
    data class RawExtractionData(
        val frontTextLines: List<String>,
        val mrzLines: List<String>,
        val detectedTCKN: String?,
        val detectedAspectRatio: Float,
        val mrzFillerRatio: Float
    )
    
    companion object {
        const val SCORE_THRESHOLD_VALID = 80
        const val SCORE_THRESHOLD_RETRY = 50
        
        /** Max possible scores */
        const val MAX_ASPECT_RATIO_SCORE = 20
        const val MAX_FRONT_TEXT_SCORE = 20
        const val MAX_MRZ_STRUCTURE_SCORE = 20
        const val MAX_MRZ_CHECKSUM_SCORE = 60 // Increased for native validation
        const val MAX_TCKN_ALGORITHM_SCORE = 10
        const val MAX_TOTAL_SCORE = 100
    }
}

/**
 * Validation error codes with Turkish descriptions
 * Maps to TC_ID_SPEC.md error codes
 */
enum class ValidationError(val code: String, val message: String, val messageTr: String) {
    // Quality Errors (ERR_01, ERR_02, ERR_04)
    CARD_NOT_FOUND("ERR_01", "No ID-1 card detected in frame", "Kadrajda kimlik kartı tespit edilemedi"),
    IMAGE_TOO_BLURRY("ERR_02", "Image blur detected, cannot read", "Görüntü bulanık, okunamıyor"),
    MOTION_DETECTED("ERR_02B", "Motion blur detected", "Hareket bulanıklığı tespit edildi"),
    LIGHTING_ISSUE("ERR_04", "Lighting problem (glare/dark)", "Aydınlatma sorunu (parlama/karanlık)"),
    PERSPECTIVE_DISTORTED("ERR_04B", "Card perspective too distorted", "Kart perspektifi bozuk"),
    
    // Aspect Ratio Errors
    ASPECT_RATIO_FAIL("ERR_AR01", "Card aspect ratio out of tolerance (1.55-1.62)", "Kart oran tolerans dışı"),
    
    // Front Side Errors
    FRONT_TURKIYE_NOT_FOUND("ERR_FR01", "TÜRKİYE/TÜRKİYE CUMHURİYETİ text not found", "TÜRKİYE yazısı bulunamadı"),
    FRONT_TCKN_NOT_FOUND("ERR_FR02", "11-digit TCKN candidate not found", "11 haneli TC No adayı bulunamadı"),
    FRONT_TCKN_ALGORITHM_FAIL("ERR_FR03", "TCKN failed algorithm validation", "TC No algoritma doğrulaması başarısız"),
    FRONT_NAME_PATTERN_FAIL("ERR_FR04", "Name pattern not detected", "İsim deseni tespit edilemedi"),
    FRONT_DATE_PATTERN_FAIL("ERR_FR05", "Birth date pattern not detected", "Doğum tarihi deseni tespit edilemedi"),
    
    // MRZ Errors (ERR_03)
    MRZ_NOT_FOUND("ERR_MZ01", "MRZ area not detected", "MRZ alanı tespit edilemedi"),
    MRZ_LINE_COUNT_INVALID("ERR_MZ02", "MRZ must have exactly 2 lines (TD1 bottom)", "MRZ 2 satırdan oluşmalı"),
    MRZ_LINE_LENGTH_INVALID("ERR_MZ03", "MRZ lines must be exactly 30 characters", "MRZ satırları 30 karakter olmalı"),
    MRZ_CHARSET_INVALID("ERR_MZ04", "MRZ contains invalid characters", "MRZ geçersiz karakter içeriyor"),
    MRZ_FILLER_RATIO_INVALID("ERR_MZ05", "MRZ filler (<) ratio out of range (15-40%)", "MRZ dolgu oranı hatalı"),
    MRZ_CHECKSUM_FAIL("ERR_03", "MRZ checksum validation failed", "MRZ checksum doğrulaması başarısız"),
    MRZ_DOC_NUMBER_CHECKSUM_FAIL("ERR_MZ06", "Document number checksum failed", "Belge no checksum hatalı"),
    MRZ_BIRTH_DATE_CHECKSUM_FAIL("ERR_MZ07", "Birth date checksum failed", "Doğum tarihi checksum hatalı"),
    MRZ_EXPIRY_DATE_CHECKSUM_FAIL("ERR_MZ08", "Expiry date checksum failed", "Geçerlilik tarihi checksum hatalı"),
    MRZ_COMPOSITE_CHECKSUM_FAIL("ERR_MZ09", "Composite checksum failed", "Bileşik checksum hatalı"),
    
    // Document Errors (ERR_05)
    DOCUMENT_EXPIRED("ERR_05", "Document has expired", "Belgenin geçerlilik süresi dolmuş"),
    
    // OCR Errors
    OCR_CONFIDENCE_LOW("ERR_OC01", "OCR confidence too low", "OCR güven skoru düşük"),
    OCR_FAILED("ERR_OC02", "OCR processing failed", "OCR işlemi başarısız"),
    
    // Generic
    UNKNOWN_ERROR("ERR_99", "Unknown validation error", "Bilinmeyen doğrulama hatası")
}
