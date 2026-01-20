package com.idverify.sdk.api.models

/**
 * MRZ (Machine Readable Zone) data parsed from ID card
 * Following ICAO Doc 9303 TD-1 format
 */
data class MRZData(
    val documentType: String,           // "I" for ID card
    val issuingCountry: String,         // "TUR" for Turkish ID
    val documentNumber: String,         // 9 digits
    val birthDate: String,              // YYMMDD format
    val sex: String,                    // "M" or "F"
    val expiryDate: String,             // YYMMDD format
    val nationality: String,            // "TUR"
    val surname: String,                // From MRZ line 3
    val givenNames: String,             // From MRZ line 3
    val checksumValid: Boolean,         // Overall checksum validation result
    val rawMRZ: List<String>            // Original 3 lines for reference
)
