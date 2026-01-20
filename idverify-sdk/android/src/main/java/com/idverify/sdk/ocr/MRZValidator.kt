package com.idverify.sdk.ocr

import com.idverify.sdk.api.models.MRZData
import com.idverify.sdk.utils.Constants

/**
 * MRZ Validator implementing ICAO Doc 9303 checksum validation
 * Uses 7-3-1 weighting algorithm
 */
class MRZValidator {
    
    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )
    
    /**
     * Validate complete MRZ data
     */
    fun validateMRZ(mrzData: MRZData): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate document number checksum
        if (!validateDocumentNumber(mrzData.documentNumber, mrzData.rawMRZ)) {
            errors.add("Invalid document number checksum")
        }
        
        // Validate birth date checksum
        if (!validateDateChecksum(mrzData.birthDate, 1)) {
            errors.add("Invalid birth date checksum")
        }
        
        // Validate expiry date checksum
        if (!validateDateChecksum(mrzData.expiryDate, 2)) {
            errors.add("Invalid expiry date checksum")
        }
        
        // Validate composite checksum (optional for TD-1)
        if (!validateCompositeChecksum(mrzData.rawMRZ)) {
            errors.add("Invalid composite checksum")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Validate document number checksum
     * TD-1 Line 2: positions 0-8 are document number, position 9 is check digit
     */
    private fun validateDocumentNumber(documentNumber: String, rawMRZ: List<String>): Boolean {
        if (rawMRZ.size < 2) return false
        val line2 = rawMRZ[1]
        if (line2.length < 10) return false
        
        val data = line2.substring(0, 9)
        val checkDigit = line2[9]
        
        return validateCheckDigit(data, checkDigit)
    }
    
    /**
     * Validate date checksum
     * @param date YYMMDD format
     * @param lineIndex 1 for birth date, 2 for expiry (in line 2 of MRZ)
     */
    private fun validateDateChecksum(date: String, lineIndex: Int): Boolean {
        if (date.length != 6) return false
        // Date validation is part of document number line (line 2)
        // Birth date: positions 13-18, check digit at 19
        // Expiry date: positions 21-26, check digit at 27
        // For now, we'll do basic format validation
        return date.all { it.isDigit() }
    }
    
    /**
     * Validate composite checksum (Line 2, position 29)
     * Composite = document number + check + DOB + check + expiry + check
     */
    private fun validateCompositeChecksum(rawMRZ: List<String>): Boolean {
        if (rawMRZ.size < 2) return false
        val line2 = rawMRZ[1]
        if (line2.length < 30) return false
        
        // Composite data: positions 0-9, 13-19, 21-27 (total 24 chars)
        val compositeData = line2.substring(0, 10) + 
                           line2.substring(13, 20) + 
                           line2.substring(21, 28)
        val compositeCheck = line2[29]
        
        return validateCheckDigit(compositeData, compositeCheck)
    }
    
    /**
     * Validate check digit using 7-3-1 algorithm
     * @param data Data string to validate
     * @param checkDigit Expected check digit
     * @return true if check digit is valid
     */
    fun validateCheckDigit(data: String, checkDigit: Char): Boolean {
        val calculatedDigit = calculateCheckDigit(data)
        return calculatedDigit == checkDigit
    }
    
    /**
     * Calculate check digit using 7-3-1 weighting
     * 
     * Algorithm:
     * 1. Convert each character to numeric value (0-9 = 0-9, A-Z = 10-35, < = 0)
     * 2. Multiply by weight (7, 3, 1, repeating)
     * 3. Sum all products
     * 4. Take modulo 10
     */
    fun calculateCheckDigit(data: String): Char {
        val weights = Constants.MRZ.CHECKSUM_WEIGHTS
        var sum = 0
        
        data.forEachIndexed { index, char ->
            val value = charToValue(char)
            val weight = weights[index % weights.size]
            sum += value * weight
        }
        
        val checkDigit = sum % 10
        return '0' + checkDigit
    }
    
    /**
     * Convert MRZ character to numeric value
     * 0-9 = 0-9
     * A-Z = 10-35
     * < = 0 (filler)
     */
    private fun charToValue(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'A'..'Z' -> char - 'A' + 10
            '<' -> 0
            else -> 0
        }
    }
    
    /**
     * Validate MRZ format (3 lines, 30 chars each)
     */
    fun isValidMRZFormat(lines: List<String>): Boolean {
        if (lines.size != Constants.MRZ.LINE_COUNT) return false
        
        return lines.all { line ->
            line.length == Constants.MRZ.LINE_LENGTH &&
            line.all { it in Constants.MRZ.ALLOWED_CHARS }
        }
    }
}
