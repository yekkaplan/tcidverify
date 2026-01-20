package com.idverify.sdk.ocr

import com.idverify.sdk.api.models.MRZData

/**
 * MRZ Parser for Turkish ID Cards (TD-1 format)
 * 
 * TD-1 Format (3 lines, 30 characters each):
 * Line 1: I<TUR<<<<<<<<<<<<<<<<<<<<<<<<<<
 * Line 2: [Doc#9][C][Nat3][DOB6][C][Sex1][Expiry6][C]<<<[C]
 * Line 3: [Surname]<<[GivenNames]<<<<<<<<<
 */
class MRZParser {
    
    private val validator = MRZValidator()
    
    /**
     * Parse result
     */
    sealed class ParseResult {
        data class Success(val mrzData: MRZData) : ParseResult()
        data class Failure(val error: String) : ParseResult()
    }
    
    /**
     * Parse MRZ lines into structured data
     * @param lines 3 lines of MRZ text (30 chars each)
     * @return ParseResult with MRZData or error message
     */
    fun parse(lines: List<String>): ParseResult {
        // Validate format
        if (!validator.isValidMRZFormat(lines)) {
            return ParseResult.Failure("Invalid MRZ format: Expected 3 lines of 30 characters")
        }
        
        return try {
            val mrzData = parseTD1Format(lines)
            ParseResult.Success(mrzData)
        } catch (e: Exception) {
            ParseResult.Failure("Failed to parse MRZ: ${e.message}")
        }
    }
    
    /**
     * Parse TD-1 format (Turkish ID Card) - TC_ID_SPEC.md compliant
     * 
     * Line 1: I<TUR<<<<<<<<<<<<<<<<<<<<<<<<<<
     *   - Pos 1-2: I< or ID (Document type)
     *   - Pos 3-5: TUR (Issuing country)
     *   - Pos 6-14: A12B34567 (Document number, 9 chars)
     *   - Pos 15: Check digit
     *   - Pos 16-30: Filler
     * 
     * Line 2: [DOB6][C][Sex1][Expiry6][C][Nat3][TC11][C]
     *   - Pos 1-6: YYMMDD (Birth date)
     *   - Pos 7: Check digit
     *   - Pos 8: M/F/< (Sex)
     *   - Pos 9-14: YYMMDD (Expiry date)
     *   - Pos 15: Check digit
     *   - Pos 16-18: TUR (Nationality)
     *   - Pos 19-29: TC Kimlik No (11 chars, optional)
     *   - Pos 30: Composite check digit
     * 
     * Line 3: [Surname]<<[GivenNames]<<<<<<<<<
     *   - Up to pos 30: Surname<<GivenNames
     */
    private fun parseTD1Format(lines: List<String>): MRZData {
        val line1 = lines[0]
        val line2 = lines[1]
        val line3 = lines[2]
        
        // Line 1: Document type and issuing country (TC_ID_SPEC.md)
        val documentType = if (line1.length > 0) line1[0].toString() else "I"
        val issuingCountry = if (line1.length >= 5) line1.substring(2, 5) else "TUR"
        
        // Extract document number from line 1 (positions 6-14, 9 chars)
        val documentNumberRaw = if (line1.length >= 15) {
            line1.substring(5, 14).replace("<", "").trim()
        } else {
            // Fallback: try line 2 if line 1 format is different
            if (line2.length >= 10) line2.substring(0, 9).replace("<", "") else ""
        }
        
        // Line 2: Critical data (TC_ID_SPEC.md positions)
        val birthDate = if (line2.length >= 7) line2.substring(0, 6) else ""  // Pos 1-6: YYMMDD
        val sex = if (line2.length >= 8) line2[7].toString() else "<"  // Pos 8: M/F/<
        val expiryDate = if (line2.length >= 15) line2.substring(8, 14) else ""  // Pos 9-14: YYMMDD
        val nationality = if (line2.length >= 18) line2.substring(15, 18) else "TUR"  // Pos 16-18: TUR
        
        // Extract TC Kimlik No (Pos 19-29, 11 chars) - TC_ID_SPEC.md
        val tcKimlikNo = if (line2.length >= 29) {
            line2.substring(18, 29).replace("<", "").trim()  // Pos 19-29
        } else {
            ""
        }
        
        android.util.Log.d("MRZParser", "Extracted TC Kimlik No: $tcKimlikNo (length: ${tcKimlikNo.length})")
        
        // Use document number from line 1 if available, otherwise from line 2
        val documentNumber = if (documentNumberRaw.isNotBlank()) {
            documentNumberRaw
        } else if (line2.length >= 10) {
            line2.substring(0, 9).replace("<", "").trim()
        } else {
            ""
        }
        
        // Line 3: Name fields
        val nameParts = line3.split("<<")
        val surname = nameParts[0].replace("<", " ").trim()
        val givenNames = if (nameParts.size > 1) {
            nameParts[1].replace("<", " ").trim()
        } else {
            ""
        }
        
        // Validate checksums
        val validationResult = validator.validateMRZ(
            MRZData(
                documentType = documentType,
                issuingCountry = issuingCountry,
                documentNumber = documentNumber,
                birthDate = birthDate,
                sex = sex,
                expiryDate = expiryDate,
                nationality = nationality,
                surname = surname,
                givenNames = givenNames,
                checksumValid = false,  // Will be updated
                rawMRZ = lines
            )
        )
        
        return MRZData(
            documentType = documentType,
            issuingCountry = issuingCountry,
            documentNumber = documentNumber,
            birthDate = birthDate,
            sex = sex,
            expiryDate = expiryDate,
            nationality = nationality,
            surname = surname,
            givenNames = givenNames,
            checksumValid = validationResult.isValid,
            rawMRZ = lines
        )
    }
    
    /**
     * Clean and normalize recognized text with context-aware replacements
     * Replaces common OCR errors based on MRZ position context
     */
    fun cleanMRZText(text: String): String {
        var cleaned = text
            .uppercase()
            .replace(" ", "<")  // Space -> filler first
            .replace("-", "<")  // Dash -> filler
            .replace("_", "<")  // Underscore -> filler
            .filter { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<" }
        
        // Context-aware replacements (only in numeric contexts)
        // Don't replace globally - be more selective
        cleaned = cleaned.mapIndexed { index, char ->
            when (char) {
                'O' -> {
                    // In numeric positions (document number, dates), O might be 0
                    if (isNumericContext(index, cleaned)) '0' else char
                }
                'Q' -> {
                    // Q is rarely in MRZ, likely 0
                    if (isNumericContext(index, cleaned)) '0' else char
                }
                'I' -> {
                    // I in numeric context might be 1
                    if (isNumericContext(index, cleaned)) '1' else char
                }
                'S' -> {
                    // S in numeric context might be 5
                    if (isNumericContext(index, cleaned)) '5' else char
                }
                'Z' -> {
                    // Z in numeric context might be 2
                    if (isNumericContext(index, cleaned)) '2' else char
                }
                else -> char
            }
        }.joinToString("")
        
        return cleaned
    }
    
    /**
     * Check if character is in a numeric context (document number, dates, etc.)
     */
    private fun isNumericContext(index: Int, text: String): Boolean {
        // Document number positions: 0-9 (line 2)
        // Date positions: 13-18 (DOB), 21-26 (expiry) in line 2
        // TC No positions: 19-29 (line 2)
        return when {
            index in 0..9 -> true  // Document number area
            index in 13..18 -> true  // DOB area
            index in 19..29 -> true  // TC No / expiry area
            else -> false
        }
    }
}
