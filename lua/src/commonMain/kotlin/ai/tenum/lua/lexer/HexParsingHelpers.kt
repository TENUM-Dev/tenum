package ai.tenum.lua.lexer

/**
 * Shared utilities for parsing hexadecimal number literals.
 * Centralizes the logic for extracting sign and hex part from strings.
 */
internal object HexParsingHelpers {
    /**
     * Extracts the sign and hexadecimal part from a trimmed string.
     * Returns Pair(sign, hexPart) where sign is -1.0 or 1.0.
     */
    fun extractSignAndHexPart(trimmed: String): Pair<Double, String> {
        val sign =
            when {
                trimmed.startsWith("-") -> -1.0
                else -> 1.0
            }
        val hexPart =
            if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
                trimmed.substring(1)
            } else {
                trimmed
            }
        return Pair(sign, hexPart)
    }

    /**
     * Checks if a string starts with a hexadecimal prefix (0x, +0x, or -0x).
     */
    fun isHexLiteral(trimmed: String): Boolean =
        trimmed.startsWith("0x", ignoreCase = true) ||
            trimmed.startsWith("+0x", ignoreCase = true) ||
            trimmed.startsWith("-0x", ignoreCase = true)
}
