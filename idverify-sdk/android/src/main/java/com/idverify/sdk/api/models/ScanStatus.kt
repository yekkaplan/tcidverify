package com.idverify.sdk.api.models

/**
 * Scan status representing the current state of the scanning process
 */
enum class ScanStatus {
    IDLE,                   // Not started
    DETECTING_FRONT,        // Detecting front of ID card
    FRONT_CAPTURED,         // Front captured, waiting for back
    DETECTING_BACK,         // Detecting back of ID card
    BACK_CAPTURED,          // Back captured, processing
    PROCESSING,             // Processing MRZ and validating
    COMPLETED,              // Successfully completed
    ERROR                   // Error occurred
}
