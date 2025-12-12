@file:Suppress("NOTHING_TO_INLINE")

package ai.tenum.lua.stdlib.string

/**
 * Shared utilities for parsing pack/unpack format strings.
 * Centralizes the common endianness parsing logic.
 */
object FormatParserHelpers {
    /**
     * Processes endianness modifiers in a format string.
     * Returns true if the current character was consumed as a modifier.
     * Note: Does NOT handle '!' (alignment) - that must be handled separately.
     */
    inline fun processModifier(
        parser: FormatParser,
        c: Char,
    ): Boolean =
        when (c) {
            '<', '=' -> {
                parser.littleEndian = true
                parser.advance()
                true
            }
            '>' -> {
                parser.littleEndian = false
                parser.advance()
                true
            }
            else -> false
        }

    /**
     * Processes the alignment modifier '!' in a format string.
     * Updates the parser's maxAlign value.
     * Returns true to indicate the character was consumed.
     */
    inline fun processAlignment(parser: FormatParser): Boolean {
        parser.advance()
        val alignValue = parser.readNumber()
        parser.maxAlign = alignValue ?: 8
        return true
    }

    /**
     * Executes the main parsing loop, handling whitespace, modifiers, and alignment.
     * Delegates format-specific operations to the provided block.
     * Returns true to continue the main loop, false to break.
     */
    inline fun parseFormatLoop(
        parser: FormatParser,
        processFormat: (Char) -> Unit,
    ) {
        while (parser.hasMore()) {
            parser.skipWhitespace()
            if (!parser.hasMore()) break

            val c = parser.current()
            if (processModifier(parser, c)) {
                continue
            }

            when (c) {
                '!' -> {
                    processAlignment(parser)
                    continue
                }
            }

            processFormat(c)
        }
    }
}
