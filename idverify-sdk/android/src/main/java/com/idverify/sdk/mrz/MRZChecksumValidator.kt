package com.idverify.sdk.mrz

import com.idverify.sdk.decision.ValidationError

/**
 * MRZ Checksum Validator - ICAO Doc 9303 Implementation
 * 
 * Uses 7-3-1 weighting algorithm as specified in TC_ID_SPEC.md
 * 
 * TD1 Format (Turkish ID Card):
 * Line 1: [DocType2][Country3][DocNo9][Check1][Filler15]
 * Line 2: [DOB6][Check1][Sex1][Expiry6][Check1][Nat3][TCKN11][Check1]
 * Line 3: [Surname<<GivenNames]
 * 
 * Check digits validated:
 * 1. Document Number (Line 1, pos 6-14, check at pos 15)
 * 2. Birth Date (Line 2, pos 1-6, check at pos 7)
 * 3. Expiry Date (Line 2, pos 9-14, check at pos 15)
 * 4. Composite (varies by implementation)
 */
object MRZChecksumValidator {
    
    /** ICAO 7-3-1 weights */
    private val WEIGHTS = intArrayOf(7, 3, 1)
    
    /**
     * Validation result with detailed breakdown
     */
    data class ValidationResult(
        val isValid: Boolean,
        val docNumberValid: Boolean,
        val birthDateValid: Boolean,
        val expiryDateValid: Boolean,
        val compositeValid: Boolean,
        val validCount: Int,
        val totalChecks: Int,
        val errors: List<ValidationError>,
        val score: Int  // 0-30 points
    )
    
    /**
     * Validate all checksums for TD1 MRZ
     * @param lines 3 MRZ lines (30 chars each)
     * @return ValidationResult with score (0-30 points)
     */
    fun validate(lines: List<String>): ValidationResult {
        if (lines.size < 2) {
            return ValidationResult(
                isValid = false,
                docNumberValid = false,
                birthDateValid = false,
                expiryDateValid = false,
                compositeValid = false,
                validCount = 0,
                totalChecks = 4,
                errors = listOf(ValidationError.MRZ_NOT_FOUND),
                score = 0
            )
        }
        
        val errors = mutableListOf<ValidationError>()
        var validCount = 0
        
        // Line indices (0-based)
        val line1 = lines.getOrElse(0) { "" }.padEnd(30, '<')
        val line2 = lines.getOrElse(1) { "" }.padEnd(30, '<')
        
        // 1. Document Number Check (Line 1: positions 5-13, check at 14)
        val docNumber = line1.substring(5, 14)
        val docNumberCheck = line1.getOrElse(14) { '<' }
        val docNumberValid = validateCheckDigit(docNumber, docNumberCheck)
        if (docNumberValid) validCount++ else errors.add(ValidationError.MRZ_DOC_NUMBER_CHECKSUM_FAIL)
        
        // 2. Birth Date Check (Line 2: positions 0-5, check at 6)
        val birthDate = line2.substring(0, 6)
        val birthDateCheck = line2.getOrElse(6) { '<' }
        val birthDateValid = validateCheckDigit(birthDate, birthDateCheck)
        if (birthDateValid) validCount++ else errors.add(ValidationError.MRZ_BIRTH_DATE_CHECKSUM_FAIL)
        
        // 3. Expiry Date Check (Line 2: positions 8-13, check at 14)
        val expiryDate = line2.substring(8, 14)
        val expiryDateCheck = line2.getOrElse(14) { '<' }
        val expiryDateValid = validateCheckDigit(expiryDate, expiryDateCheck)
        if (expiryDateValid) validCount++ else errors.add(ValidationError.MRZ_EXPIRY_DATE_CHECKSUM_FAIL)
        
        // 4. Composite Check (Line 2, position 29)
        // Composite = line1[5-29] + line2[0-6] + line2[8-14] + line2[18-28]
        val compositeData = buildString {
            append(line1.substring(5, 30))  // Doc number + filler
            append(line2.substring(0, 7))   // DOB + check
            append(line2.substring(8, 15))  // Expiry + check
            append(line2.substring(18, 29)) // Optional data
        }
        val compositeCheck = line2.getOrElse(29) { '<' }
        val compositeValid = validateCheckDigit(compositeData, compositeCheck)
        if (compositeValid) validCount++ else errors.add(ValidationError.MRZ_COMPOSITE_CHECKSUM_FAIL)
        
        // Calculate score (0-30 points)
        // Each valid checksum = 7.5 points (30 / 4)
        val score = (validCount * 7.5).toInt().coerceAtMost(30)
        
        // MRZ is valid if at least 2 checksums pass (birth date and doc number are critical)
        val isValid = validCount >= 2 && (docNumberValid || birthDateValid)
        
        return ValidationResult(
            isValid = isValid,
            docNumberValid = docNumberValid,
            birthDateValid = birthDateValid,
            expiryDateValid = expiryDateValid,
            compositeValid = compositeValid,
            validCount = validCount,
            totalChecks = 4,
            errors = errors,
            score = score
        )
    }
    
    /**
     * Validate single check digit
     * @param data Data to validate
     * @param expectedCheck Expected check digit character
     * @return true if check digit matches
     */
    fun validateCheckDigit(data: String, expectedCheck: Char): Boolean {
        val calculated = calculateCheckDigit(data)
        return calculated == expectedCheck
    }
    
    /**
     * Calculate check digit using ICAO 7-3-1 algorithm
     * 
     * @param data String to calculate check digit for
     * @return Check digit character ('0'-'9')
     */
    fun calculateCheckDigit(data: String): Char {
        var sum = 0
        
        data.forEachIndexed { index, char ->
            val value = charToValue(char)
            val weight = WEIGHTS[index % WEIGHTS.size]
            sum += value * weight
        }
        
        val checkDigit = sum % 10
        return ('0' + checkDigit)
    }
    
    /**
     * Convert MRZ character to numeric value
     * 
     * ICAO 9303 values:
     * - '0'-'9' → 0-9
     * - 'A'-'Z' → 10-35
     * - '<' → 0
     */
    private fun charToValue(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'A'..'Z' -> char - 'A' + 10
            '<' -> 0
            else -> 0  // Invalid char treated as 0
        }
    }
    
    /**
     * Parse birth date from MRZ (YYMMDD format)
     * @param yymmdd 6-character date string
     * @return Formatted date or null if invalid
     */
    fun parseMRZDate(yymmdd: String): String? {
        if (yymmdd.length != 6 || !yymmdd.all { it.isDigit() }) {
            return null
        }
        
        val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return null
        val mm = yymmdd.substring(2, 4).toIntOrNull() ?: return null
        val dd = yymmdd.substring(4, 6).toIntOrNull() ?: return null
        
        // Validate ranges
        if (mm !in 1..12 || dd !in 1..31) return null
        
        // Convert YY to YYYY (assume 00-30 = 2000-2030, 31-99 = 1931-1999)
        val yyyy = if (yy <= 30) 2000 + yy else 1900 + yy
        
        return "%02d.%02d.%04d".format(dd, mm, yyyy)
    }
    
    /**
     * Validate Line 2 checksums only (when Line 1 is reconstructed/missing)
     * 
     * This validates:
     * 1. Birth Date (pos 0-5, check at 6)
     * 2. Expiry Date (pos 8-13, check at 14)
     * 3. Composite is NOT validated (needs Line 1 doc number)
     * 
     * @param line2 TD1 Line 2 (30 chars)
     * @return ValidationResult with partial score (max 22 points)
     */
    fun validateLine2Only(line2: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        var validCount = 0
        
        val line = line2.padEnd(30, '<')
        
        // 1. Birth Date Check (Line 2: positions 0-5, check at 6)
        val birthDate = line.substring(0, 6)
        val birthDateCheck = line.getOrElse(6) { '<' }
        val birthDateValid = validateCheckDigit(birthDate, birthDateCheck)
        if (birthDateValid) validCount++ else errors.add(ValidationError.MRZ_BIRTH_DATE_CHECKSUM_FAIL)
        
        // 2. Expiry Date Check (Line 2: positions 8-13, check at 14)
        val expiryDate = line.substring(8, 14)
        val expiryDateCheck = line.getOrElse(14) { '<' }
        val expiryDateValid = validateCheckDigit(expiryDate, expiryDateCheck)
        if (expiryDateValid) validCount++ else errors.add(ValidationError.MRZ_EXPIRY_DATE_CHECKSUM_FAIL)
        
        // 3. TCKN Validation (optional - positions 18-28)
        val tckn = line.substring(18, 29).replace("<", "")
        val tcknValid = if (tckn.length == 11 && tckn.all { it.isDigit() }) {
            com.idverify.sdk.validation.TCKNValidator.validate(tckn).isValid
        } else false
        if (tcknValid) validCount++
        
        // Calculate score (0-30 points max for Line 2 only validation)
        // Birth date: 10 pts, Expiry: 10 pts, TCKN: 10 pts
        val score = validCount * 10
        
        // Valid if at least birth date and one other check pass
        val isValid = birthDateValid && (expiryDateValid || tcknValid)
        
        return ValidationResult(
            isValid = isValid,
            docNumberValid = false, // Not checked
            birthDateValid = birthDateValid,
            expiryDateValid = expiryDateValid,
            compositeValid = false, // Not checked
            validCount = validCount,
            totalChecks = 3,
            errors = errors,
            score = score
        )
    }
}
