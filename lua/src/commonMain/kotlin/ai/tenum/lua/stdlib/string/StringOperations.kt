package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue

/**
 * Basic string operations for string library.
 */
object StringOperations {
    /**
     * Normalize Lua index to 1-based position.
     * - Negative indices wrap from end: -1 is last position
     * - Zero means position 1 for start indices, stays 0 for end indices
     * - Positive indices are kept as-is
     */
    fun normalizeLuaIndex(
        index: Int,
        length: Int,
        isStartIndex: Boolean,
    ): Int =
        when {
            index < 0 -> length + index + 1
            index == 0 && isStartIndex -> 1
            else -> index
        }

    /**
     * Normalize a Lua position index to 0-based Java index.
     * Handles:
     * - Negative indices (count from end): -1 is last position
     * - 1-based to 0-based conversion
     *
     * @param luaPos Lua position (1-based, negative counts from end)
     * @param length String length
     * @return 0-based position
     */
    fun normalizePosition(
        luaPos: Int,
        length: Int,
    ): Int =
        if (luaPos < 0) {
            length + luaPos // -1 becomes length-1, -2 becomes length-2, etc.
        } else {
            luaPos - 1 // Convert 1-based to 0-based
        }

    /**
     * Lua-style substring with 1-based indexing and negative indices
     */
    fun substringLua(
        str: String,
        i: Int,
        j: Int,
    ): String {
        val len = str.length
        val start = normalizeLuaIndex(i, len, isStartIndex = true)
        val end = normalizeLuaIndex(j, len, isStartIndex = false)

        // If end < start, return empty string
        if (end < start) return ""

        // Clamp and convert to 0-based indices
        val startIdx = (start - 1).coerceIn(0, len)
        val endIdx = end.coerceIn(0, len)

        return str.substring(startIdx, endIdx)
    }

    /**
     * Repeat string n times with separator
     */
    fun repeatString(
        str: String,
        n: Int,
        sep: String,
    ): String {
        if (n <= 0) return ""
        if (n == 1) return str

        // Validate that the resulting string won't be too large BEFORE attempting to build it
        // Max string length is Int.MAX_VALUE characters
        val maxLength = Int.MAX_VALUE.toLong()
        val strLen = str.length.toLong()
        val sepLen = sep.length.toLong()
        val nLong = n.toLong()

        // Guard against huge n values that would overflow even Long arithmetic
        // If n * strLen would overflow Long, it's definitely too large
        if (nLong > 0 && strLen > 0 && nLong > maxLength / strLen) {
            throw RuntimeException("resulting string too large")
        }

        // Calculate resulting length: n * str.length + (n-1) * sep.length
        val resultLength =
            if (sep.isEmpty()) {
                strLen * nLong
            } else {
                // Check if separator calculation would overflow
                val sepContribution = sepLen * (nLong - 1)
                if (nLong > 1 && sepLen > 0 && (nLong - 1) > maxLength / sepLen) {
                    throw RuntimeException("resulting string too large")
                }
                val baseLength = strLen * nLong
                if (baseLength > maxLength - sepContribution) {
                    throw RuntimeException("resulting string too large")
                }
                baseLength + sepContribution
            }

        // Throw error BEFORE any allocation attempts
        if (resultLength > maxLength || resultLength < 0) {
            throw RuntimeException("resulting string too large")
        }

        // Now safe to build the string
        if (sep.isEmpty()) {
            return str.repeat(n)
        }

        return buildString(resultLength.toInt()) {
            repeat(n) { i ->
                if (i > 0) append(sep)
                append(str)
            }
        }
    }

    /**
     * Get byte codes from string
     */
    fun stringByte(
        str: String,
        i: Int,
        j: Int,
    ): List<LuaValue<*>> {
        val len = str.length

        // Convert to 0-based indices, with clamping
        val start =
            when {
                i < 0 -> (len + i + 1).coerceAtLeast(1) // Clamp negative index to 1 if out of bounds
                i == 0 -> 1
                else -> i
            }

        val end =
            when {
                j < 0 -> len + j + 1
                else -> j
            }

        // Empty range check: if end < start, return empty list (not nil)
        if (end < start) {
            return emptyList()
        }

        // Bounds check: if start is after string end, return empty
        if (start > len) {
            return emptyList()
        }

        val result = mutableListOf<LuaValue<*>>()
        for (pos in start..end.coerceAtMost(len)) {
            val char = str[pos - 1]
            val byteValue: LuaValue<*> = LuaNumber.of(char.code.toDouble())
            result.add(byteValue)
        }

        return result
    }

    /**
     * Convert byte codes to string (string.char)
     */
    fun charFromBytes(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val chars = mutableListOf<Int>()
        for (arg in args) {
            val num = arg as? LuaNumber ?: continue
            val doubleValue = num.toDouble()

            // Validate range: must be 0-255 for valid byte
            // Check as double first to catch values outside Int range
            if (doubleValue < 0.0 || doubleValue > 255.0) {
                throw RuntimeException("value out of range")
            }

            val value = doubleValue.toInt()
            chars.add(value)
        }

        val result = chars.map { it.toChar() }.joinToString("")
        return buildList {
            add(LuaString(result))
        }
    }
}
