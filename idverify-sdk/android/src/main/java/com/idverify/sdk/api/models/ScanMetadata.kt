package com.idverify.sdk.api.models

/**
 * Metadata captured during the scanning process
 */
data class ScanMetadata(
    val scanDuration: Long,              // Total scan time in milliseconds
    val frontCaptureTimestamp: Long,     // Front capture time (epoch millis)
    val backCaptureTimestamp: Long,      // Back capture time (epoch millis)
    val blurScore: Float,                // 0.0 (very blurry) - 1.0 (sharp)
    val glareScore: Float                // 0.0 (heavy glare) - 1.0 (no glare)
)
