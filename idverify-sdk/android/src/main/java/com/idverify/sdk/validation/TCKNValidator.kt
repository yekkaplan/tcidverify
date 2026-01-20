package com.idverify.sdk.validation

/**
 * T.C. Kimlik Numarası Algorithm Validator
 * 
 * Turkish National ID numbers have a specific algorithm:
 * - 11 digits total
 * - First digit cannot be 0
 * - 10th digit: ((sum of odd positions * 7) - sum of even positions) mod 10
 * - 11th digit: (sum of first 10 digits) mod 10
 * 
 * This is a HARD RULE - no TCKN passes without algorithm validation
 */
object TCKNValidator {
    
    /**
     * Validation result with details
     */
    data class ValidationResult(
        val isValid: Boolean,
        val normalizedTCKN: String?,
        val reason: String? = null
    )
    
    /**
     * Validate a TCKN string
     * @param tckn String that might contain a TCKN (can have spaces/dashes)
     * @return ValidationResult with validation status
     */
    fun validate(tckn: String?): ValidationResult {
        if (tckn.isNullOrBlank()) {
            return ValidationResult(false, null, "TCKN is empty")
        }
        
        // Normalize: remove non-digits
        val normalized = tckn.filter { it.isDigit() }
        
        // Must be exactly 11 digits
        if (normalized.length != 11) {
            return ValidationResult(false, null, "TCKN must be 11 digits, got ${normalized.length}")
        }
        
        // First digit cannot be 0
        if (normalized[0] == '0') {
            return ValidationResult(false, null, "First digit cannot be 0")
        }
        
        // Convert to int array for calculations
        val digits = normalized.map { it.digitToInt() }
        
        // Calculate 10th digit check
        // Formula: ((d1 + d3 + d5 + d7 + d9) * 7 - (d2 + d4 + d6 + d8)) mod 10
        val oddSum = digits[0] + digits[2] + digits[4] + digits[6] + digits[8]
        val evenSum = digits[1] + digits[3] + digits[5] + digits[7]
        val expectedDigit10 = ((oddSum * 7) - evenSum).mod(10)
        
        if (digits[9] != expectedDigit10) {
            return ValidationResult(
                false, 
                normalized, 
                "10th digit check failed: expected $expectedDigit10, got ${digits[9]}"
            )
        }
        
        // Calculate 11th digit check
        // Formula: (d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10) mod 10
        val sumFirst10 = digits.take(10).sum()
        val expectedDigit11 = sumFirst10.mod(10)
        
        if (digits[10] != expectedDigit11) {
            return ValidationResult(
                false, 
                normalized, 
                "11th digit check failed: expected $expectedDigit11, got ${digits[10]}"
            )
        }
        
        return ValidationResult(true, normalized)
    }
    
    /**
     * Extract TCKN candidates from text
     * Returns all 11-digit sequences that PASS the algorithm
     * 
     * @param text OCR text to search
     * @return List of valid TCKN strings found
     */
    fun extractValidTCKNs(text: String): List<String> {
        // Remove all non-alphanumeric except spaces
        val cleaned = text.replace(Regex("[^0-9\\s]"), " ")
        
        // Find all 11-digit sequences
        val candidates = mutableListOf<String>()
        
        // Method 1: Direct 11-digit sequences
        val digitSequences = Regex("\\d{11}").findAll(cleaned)
        candidates.addAll(digitSequences.map { it.value })
        
        // Method 2: Sequences with spaces/separators (e.g., "123 456 789 01")
        val words = cleaned.split(Regex("\\s+"))
        val buffer = StringBuilder()
        
        for (word in words) {
            if (word.all { it.isDigit() }) {
                buffer.append(word)
                if (buffer.length >= 11) {
                    // Extract potential TCKN from buffer
                    val potential = buffer.toString().take(11)
                    candidates.add(potential)
                    buffer.clear()
                    buffer.append(word)
                }
            } else {
                buffer.clear()
            }
        }
        
        // Validate all candidates and return only valid ones
        return candidates
            .distinct()
            .filter { validate(it).isValid }
    }
    
    /**
     * Check if text contains any valid TCKN
     */
    fun containsValidTCKN(text: String): Boolean {
        return extractValidTCKNs(text).isNotEmpty()
    }
    
    /**
     * Get first valid TCKN from text, or null
     */
    fun extractFirstValidTCKN(text: String): String? {
        return extractValidTCKNs(text).firstOrNull()
    }
}
