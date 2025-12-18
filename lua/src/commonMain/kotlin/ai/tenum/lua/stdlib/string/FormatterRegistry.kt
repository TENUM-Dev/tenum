package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.formatters.CharacterFormatter
import ai.tenum.lua.stdlib.string.formatters.ExponentialFormatter
import ai.tenum.lua.stdlib.string.formatters.FloatFormatter
import ai.tenum.lua.stdlib.string.formatters.HexFloatFormatter
import ai.tenum.lua.stdlib.string.formatters.IntegerFormatter
import ai.tenum.lua.stdlib.string.formatters.PointerFormatter
import ai.tenum.lua.stdlib.string.formatters.QuotedFormatter
import ai.tenum.lua.stdlib.string.formatters.StringFormatter

/**
 * Registry of format specifier handlers using Strategy Pattern.
 *
 * Domain: String formatting
 * Responsibility: Dispatch format requests to appropriate formatter implementations
 */
class FormatterRegistry(
    valueToString: (LuaValue<*>) -> String,
) {
    private val formatters: List<ValueFormatter> =
        listOf(
            StringFormatter(valueToString),
            IntegerFormatter(),
            FloatFormatter(),
            ExponentialFormatter(),
            CharacterFormatter(),
            PointerFormatter(),
            QuotedFormatter(),
            HexFloatFormatter(),
        )

    /**
     * Find and execute the appropriate formatter for the given format character.
     * Returns null if no formatter handles this character.
     */
    fun format(
        formatChar: Char,
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String? {
        val formatter = formatters.find { it.handles(formatChar) } ?: return null
        return formatter.format(value, spec)
    }

    /**
     * Check if a format character is supported by any formatter.
     */
    fun supports(formatChar: Char): Boolean = formatters.any { it.handles(formatChar) }
}
