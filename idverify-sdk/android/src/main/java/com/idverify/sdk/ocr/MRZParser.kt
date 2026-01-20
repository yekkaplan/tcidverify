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
     * Parse TD-1 format (Turkish ID Card)
     */
    private fun parseTD1Format(lines: List<String>): MRZData {
        val line1 = lines[0]
        val line2 = lines[1]
        val line3 = lines[2]
        
        // Line 1: Document type and issuing country
        val documentType = line1[0].toString()  // "I"
        val issuingCountry = line1.substring(2, 5)  // "TUR"
        
        // Line 2: Critical data
        val documentNumber = line2.substring(0, 9).replace("<", "")
        val nationality = line2.substring(10, 13)  // TUR
        val birthDate = line2.substring(13, 19)  // YYMMDD
        val sex = line2[20].toString()  // M/F
        val expiryDate = line2.substring(21, 27)  // YYMMDD
        
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
     * Clean and normalize recognized text
     * Replaces common OCR errors
     */
    fun cleanMRZText(text: String): String {
        return text
            .uppercase()
            .replace('O', '0')  // O -> 0
            .replace('Q', '0')  // Q -> 0
            .replace('D', '0')  // D -> 0 (sometimes)
            .replace('I', '1')  // I -> 1 (in numeric context)
            .replace('S', '5')  // S -> 5 (sometimes)
            .replace('Z', '2')  // Z -> 2 (sometimes)
            .replace(' ', '<')  // Space -> filler
            .filter { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<" }
    }
}
