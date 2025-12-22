package ai.tenum.lua.stdlib.string

/**
 * Represents a parsed format specifier from a Lua format string.
 *
 * Domain: String formatting
 * Responsibility: Hold all parsed components of a format specifier (%d, %s, %.2f, etc.)
 *
 * @property flags Format flags: '-' (left-justify), '0' (zero-pad), '+' (sign), ' ' (space), '#' (alternate)
 * @property width Minimum field width (null if not specified)
 * @property precision Number of decimal places for floats, max chars for strings (null if not specified)
 * @property formatChar The format type character ('d', 's', 'f', etc.)
 */
data class FormatSpecifier(
    val flags: Set<Char> = emptySet(),
    val width: Int? = null,
    val precision: Int? = null,
    val formatChar: Char,
) {
    // Flag helpers
    val leftJustify: Boolean get() = '-' in flags
    val zeroPad: Boolean get() = '0' in flags
    val forceSign: Boolean get() = '+' in flags
    val spaceForSign: Boolean get() = ' ' in flags
    val alternate: Boolean get() = '#' in flags

    /**
     * Validate that this format specifier is legal for its type.
     * Throws exception if illegal modifier combinations are found.
     */
    fun validate() {
        when (formatChar) {
            'c' -> {
                // %c: only width and '-' flag allowed
                val illegalFlags = flags - setOf('-')
                require(illegalFlags.isEmpty()) {
                    "invalid flags for %%$formatChar: ${illegalFlags.joinToString("")}"
                }
                require(precision == null) {
                    "invalid option '.$precision' to 'format'"
                }
            }
            's' -> {
                // %s: width, '-', and precision only
                val illegalFlags = flags - setOf('-')
                require(illegalFlags.isEmpty()) {
                    "invalid flags for %%$formatChar: ${illegalFlags.joinToString("")}"
                }
            }
            'q' -> {
                // %q: no modifiers at all
                require(flags.isEmpty() && width == null && precision == null) {
                    "%%q does not accept flags, width, or precision"
                }
            }
            'p' -> {
                // %p: width and '-' flag only
                val illegalFlags = flags - setOf('-')
                require(illegalFlags.isEmpty()) {
                    "invalid flags for %%$formatChar: ${illegalFlags.joinToString("")}"
                }
                require(precision == null) {
                    "invalid option '.$precision' to 'format'"
                }
            }
            'd', 'i', 'u' -> {
                // Integer formats: no '#' flag
                require('#' !in flags) {
                    "invalid flags for %%$formatChar: #"
                }
            }
        }
    }
}
