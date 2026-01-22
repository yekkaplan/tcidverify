package com.idverify.sdk.api.models

/**
 * Complete scan result containing front/back images, MRZ data, and quality scores
 */
data class ScanResult(
    val frontImage: ByteArray,           // Front image as JPEG bytes
    val backImage: ByteArray,            // Back image as JPEG bytes
    val mrzData: MRZData,                // Parsed MRZ information
    val authenticityScore: Float,        // 0.0 - 1.0 (physical authenticity)
    val scoreDetails: ScoreDetails = ScoreDetails(), // Detailed score breakdown
    val metadata: ScanMetadata           // Scanning metadata
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScanResult

        if (!frontImage.contentEquals(other.frontImage)) return false
        if (!backImage.contentEquals(other.backImage)) return false
        if (mrzData != other.mrzData) return false
        if (authenticityScore != other.authenticityScore) return false
        if (scoreDetails != other.scoreDetails) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frontImage.contentHashCode()
        result = 31 * result + backImage.contentHashCode()
        result = 31 * result + mrzData.hashCode()
        result = 31 * result + authenticityScore.hashCode()
        result = 31 * result + scoreDetails.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Detailed score breakdown
 */
data class ScoreDetails(
    val totalScore: Int = 0,
    val checksumScore: Int = 0,
    val structureScore: Int = 0,
    val qualityScore: Int = 0
)
