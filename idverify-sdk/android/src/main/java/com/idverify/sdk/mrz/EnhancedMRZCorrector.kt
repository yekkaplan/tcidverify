package com.idverify.sdk.mrz

import android.util.Log

/**
 * Enhanced MRZ OCR Corrector with Context Awareness
 * 
 * Based on analysis of real Turkish ID cards:
 * - MRZ is heavily affected by hologram overlay
 * - OCR consistently confuses: 0↔O, 1↔I, 6↔G, 4↔A, 5↔S
 * - Some positions have KNOWN values (country, doc type)
 * 
 * This corrector uses:
 * 1. Positional constraints (e.g., position 0 MUST be "I")
 * 2. Known values (TUR, TCKN from front if available)
 * 3. Aggressive character substitution in digit/letter contexts
 */
object EnhancedMRZCorrector {
    
    private const val TAG = "EnhancedMRZCorrector"
    
    /**
     * Apply ultra-aggressive correction with known constraints
     * 
     * @param lines Raw MRZ lines from OCR (expecting 3)
     * @param knownTCKN TCKN from front side (if available) - 11 digits
     * @param knownDocNumber Document number from front (if available)
     * @return Corrected MRZ lines
     */
    fun correctWithContext(
        lines: List<String>,
        knownTCKN: String? = null,
        knownDocNumber: String? = null
    ): List<String> {
        if (lines.size != 3) return lines
        
        // Enforce STRICT 30 character length (truncate or pad)
        // OCR often adds spaces or noise at the end, or misses characters
        val line1 = enforceLength(lines[0])
        val line2 = enforceLength(lines[1])
        val line3 = enforceLength(lines[2])
        
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "Enhanced MRZ Correction (Context-Aware)")
        Log.d(TAG, "Input:")
        Log.d(TAG, "  Line 1: ${String(line1)}")
        Log.d(TAG, "  Line 2: ${String(line2)}")
        Log.d(TAG, "  Line 3: ${String(line3)}")
        Log.d(TAG, "Context: TCKN=${knownTCKN}, DocNum=${knownDocNumber}")
        
        // === LINE 1 CORRECTION ===
        // Format: I<TUR[DOCNUM 9][CHK]<<<<<<<<<<<<
        
        // Position 0: ALWAYS "I" (document type)
        line1[0] = 'I'
        
        // Position 1: ALWAYS "<"
        line1[1] = '<'
        
        // Positions 2-4: ALWAYS "TUR" (country code)
        line1[2] = 'T'
        line1[3] = 'U'
        line1[4] = 'R'
        
        // Positions 5-13: Document number (alphanum, but mostly digits for Turkish IDs)
        // If we know it from front, inject it
        if (knownDocNumber != null && knownDocNumber.length >= 7) {
            val docNum = knownDocNumber.take(9).padEnd(9, '<')
            for (i in 0 until minOf(9, docNum.length)) {
                if (docNum[i] != '<') {
                    line1[5 + i] = docNum[i]
                }
            }
        } else {
            // Aggressive correction for document number field
            for (i in 5..13) {
                line1[i] = smartCorrect(line1[i], preferDigits = false) // Can be alphanumeric
            }
        }
        
        // Position 14: Checksum (digit)
        line1[14] = forceDigit(line1[14])
        
        // Positions 15-29: Fillers
        for (i in 15..29) {
            if (line1[i] !in listOf('<', ' ')) {
                line1[i] = '<'
            }
        }
        
        // === LINE 2 CORRECTION ===
        // Format: [DOB 6][CHK][SEX][EXP 6][CHK]TUR[TCKN 11][CHK]
        
        // Positions 0-5: Birth date (YYMMDD) - digits
        for (i in 0..5) {
            line2[i] = forceDigit(line2[i])
        }
        
        // Position 6: Birth date checksum - digit
        line2[6] = forceDigit(line2[6])
        
        // Position 7: Sex (M/F/<) - keep as is but ensure it's valid
        if (line2[7] !in listOf('M', 'F', '<')) {
            line2[7] = when (line2[7].uppercaseChar()) {
                'W', 'K' -> 'M'  // Common OCR errors
                else -> '<'
            }
        }
        
        // Positions 8-13: Expiry date (YYMMDD) - digits  
        for (i in 8..13) {
            line2[i] = forceDigit(line2[i])
        }
        
        // Position 14: Expiry checksum - digit
        line2[14] = forceDigit(line2[14])
        
        // Positions 15-17: Nationality - FORCE "TUR"
        line2[15] = 'T'
        line2[16] = 'U'
        line2[17] = 'R'
        
        // Positions 18-28: TC Kimlik No (11 digits)
        if (knownTCKN != null && knownTCKN.length == 11) {
            // INJECT known TCKN from front side
            for (i in 0 until 11) {
                line2[18 + i] = knownTCKN[i]
            }
            Log.d(TAG, "✓ Injected TCKN from front: $knownTCKN")
        } else {
            // Aggressive digit correction
            for (i in 18..28) {
                line2[i] = forceDigit(line2[i])
            }
        }
        
        // Position 29: Final checksum - digit
        line2[29] = forceDigit(line2[29])
        
        // === LINE 3 CORRECTION ===
        // Format: [SURNAME]<<[GIVENNAMES]<<<
        // Mostly letters, light correction
        for (i in 0..29) {
            line3[i] = smartCorrect(line3[i], preferDigits = false)
        }
        
        val corrected1 = String(line1)
        val corrected2 = String(line2)
        val corrected3 = String(line3)
        
        Log.d(TAG, "Output:")
        Log.d(TAG, "  Line 1: $corrected1")
        Log.d(TAG, "  Line 2: $corrected2")
        Log.d(TAG, "  Line 3: $corrected3")
        Log.d(TAG, "═══════════════════════════════════════")
        
        return listOf(corrected1, corrected2, corrected3)
    }
    
    /**
     * Force character to digit with aggressive mapping
     */
    private fun forceDigit(char: Char): Char {
        return when (char.uppercaseChar()) {
            'O', 'Q' -> '0'
            'I', 'L', '|' -> '1'
            'Z' -> '2'
            'E' -> '3'
            'A' -> '4'
            'S' -> '5'
            'G', 'b' -> '6'
            'T' -> '7'
            'B' -> '8'
            'g', 'q' -> '9'
            '<', ' ' -> '<'
            in '0'..'9' -> char
            else -> '0'  // Unknown → 0
        }
    }
    
    /**
     * Smart correction based on context
     */
    private fun smartCorrect(char: Char, preferDigits: Boolean): Char {
        if (char == '<' || char == ' ') return '<'
        
        if (preferDigits) {
            return forceDigit(char)
        } else {
            // In alphanumeric context, keep as is if valid
            return if (char.isLetterOrDigit() || char == '<') char else '<'
        }
    }
    
    /**
     * Ensure line is exactly 30 characters
     */
    private fun enforceLength(line: String): CharArray {
        var processed = line.trim().replace("\\s+".toRegex(), "") // Remove whitespace
        if (processed.length > 30) {
            processed = processed.substring(0, 30)
        } else {
            processed = processed.padEnd(30, '<')
        }
        return processed.toCharArray()
    }
    
    /**
     * Correct 2-line MRZ (when Line 1 is missed by OCR)
     * 
     * This handles the common case where OCR only captures:
     * - Line 2 (DOB, Expiry, TCKN)
     * - Line 3 (Surname, Given Names)
     * 
     * We create a default Line 1 and return corrected 3 lines.
     * 
     * @param lines 2 lines: [Line2, Line3] from OCR
     * @return 3 corrected lines: [Line1 (default), Line2, Line3]
     */
    fun correctTwoLines(lines: List<String>): List<String> {
        if (lines.size != 2) return lines
        
        Log.d(TAG, "═══════════════════════════════════════")
        Log.d(TAG, "Enhanced MRZ Correction (2-Line Mode)")
        Log.d(TAG, "Input:")
        Log.d(TAG, "  Line 2 (data): ${lines[0]}")
        Log.d(TAG, "  Line 3 (name): ${lines[1]}")
        
        // Create default Line 1
        val defaultLine1 = "I<TUR<<<<<<<<<<<<<<<<<<<<<<<<<<"
        
        // Apply full 3-line correction
        val fullLines = listOf(defaultLine1, lines[0], lines[1])
        val corrected = correctWithContext(fullLines, null, null)
        
        Log.d(TAG, "Output (2-Line → 3-Line):")
        corrected.forEachIndexed { i, line -> Log.d(TAG, "  Line ${i+1}: $line") }
        Log.d(TAG, "═══════════════════════════════════════")
        
        return corrected
    }
    
    /**
     * Aggressively correct Line 2 only (most critical for validation)
     * 
     * Line 2 format: [DOB6][CHK][SEX][EXP6][CHK]TUR[TCKN11][CHK]
     * 
     * @param line2 Raw OCR of Line 2
     * @return Corrected Line 2
     */
    fun correctLine2Only(line2: String): String {
        val line = enforceLength(line2)
        
        // Positions 0-5: Birth date (YYMMDD) - digits
        for (i in 0..5) {
            line[i] = forceDigit(line[i])
        }
        
        // Position 6: Birth date checksum - digit
        line[6] = forceDigit(line[6])
        
        // Position 7: Sex (M/F/<)
        if (line[7] !in listOf('M', 'F', '<')) {
            line[7] = when (line[7].uppercaseChar()) {
                'W', 'K' -> 'M'
                else -> '<'
            }
        }
        
        // Positions 8-13: Expiry date (YYMMDD) - digits  
        for (i in 8..13) {
            line[i] = forceDigit(line[i])
        }
        
        // Position 14: Expiry checksum - digit
        line[14] = forceDigit(line[14])
        
        // Positions 15-17: Nationality - FORCE "TUR"
        line[15] = 'T'
        line[16] = 'U'
        line[17] = 'R'
        
        // Positions 18-28: TC Kimlik No (11 digits)
        for (i in 18..28) {
            line[i] = forceDigit(line[i])
        }
        
        // Position 29: Final checksum - digit
        line[29] = forceDigit(line[29])
        
        return String(line)
    }
}
