package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.stdlib.LuaPattern
import ai.tenum.lua.vm.library.LuaLibraryContext

/**
 * Pattern matching operations for string library.
 */
object StringPatternMatching {
    /**
     * Converts a match result to a list of Lua values.
     * Returns captures if present, otherwise the whole match.
     */
    private fun matchResultToValues(
        result: LuaPattern.MatchResult?,
        str: String,
    ): List<LuaValue<*>> =
        if (result != null) {
            if (result.captures.isNotEmpty()) {
                // Return captures
                result.captures.map { LuaString(it.text) }
            } else {
                // No captures, return whole match
                buildList {
                    add(LuaString(str.substring(result.start, result.end)))
                }
            }
        } else {
            buildList { add(LuaNil) }
        }

    /**
     * string.match(s, pattern, init) - Find first match and return captures
     */
    fun matchPattern(
        str: String,
        patternStr: String,
        init: Int,
    ): List<LuaValue<*>> {
        val startPos = (init - 1).coerceIn(0, str.length)
        val pattern = LuaPattern(patternStr)
        val result = pattern.find(str, startPos)
        return matchResultToValues(result, str)
    }

    /**
     * string.gmatch(s, pattern) - Iterator function for all matches
     */
    fun gmatchIterator(
        str: String,
        patternStr: String,
    ): LuaFunction {
        val pattern = LuaPattern(patternStr)
        val iterator = pattern.findAll(str).iterator()

        // Create upvalues to hold the string and pattern (similar to Lua 5.4 C closure)
        // This allows debug.upvalueid to inspect the iterator's state
        val strUpvalue =
            Upvalue(closedValue = LuaString(str)).apply {
                isClosed = true
            }
        val patternUpvalue =
            Upvalue(closedValue = LuaString(patternStr)).apply {
                isClosed = true
            }

        return LuaNativeFunction(
            name = "gmatch iterator",
            value = { _ ->
                if (iterator.hasNext()) {
                    val result = iterator.next()
                    matchResultToValues(result, str)
                } else {
                    buildList { add(LuaNil) }
                }
            },
            upvalues = listOf(strUpvalue, patternUpvalue),
        )
    }

    /**
     * string.gsub(s, pattern, repl, n) - Global substitution with patterns
     */
    fun gsubPattern(
        str: String,
        patternStr: String,
        repl: LuaValue<*>,
        maxReplacements: Int?,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val pattern = LuaPattern(patternStr)
        val matches = pattern.findAll(str).toList()

        val limit = maxReplacements ?: Int.MAX_VALUE
        var count = 0
        val result = StringBuilder()
        var lastPos = 0

        for (match in matches) {
            if (count >= limit) break

            // Append text before match
            result.append(str.substring(lastPos, match.start))

            // Get replacement text
            val replacement =
                when (repl) {
                    is LuaString -> {
                        // String replacement with %N capture references
                        var replText = repl.value
                        for (i in match.captures.indices) {
                            replText = replText.replace("%${i + 1}", match.captures[i].text)
                        }
                        // %0 is whole match
                        replText = replText.replace("%0", str.substring(match.start, match.end))
                        // %% is literal %
                        replText = replText.replace("%%", "%")
                        replText
                    }
                    is LuaTable -> {
                        // Table lookup
                        val key =
                            if (match.captures.isNotEmpty()) {
                                LuaString(match.captures[0].text)
                            } else {
                                LuaString(str.substring(match.start, match.end))
                            }
                        val value = repl.get(key)
                        when (value) {
                            is LuaString -> value.value
                            is LuaNumber -> value.value.toString()
                            else -> str.substring(match.start, match.end) // Keep original if nil/false
                        }
                    }
                    is LuaFunction -> {
                        // Function call
                        val args =
                            if (match.captures.isNotEmpty()) {
                                match.captures.map { LuaString(it.text) }
                            } else {
                                listOf(LuaString(str.substring(match.start, match.end)))
                            }
                        val result = context.callFunction(repl, args)
                        val value = result.firstOrNull()
                        when (value) {
                            is LuaString -> value.value
                            is LuaNumber -> value.value.toString()
                            else -> str.substring(match.start, match.end) // Keep original if nil/false
                        }
                    }
                    else -> str.substring(match.start, match.end)
                }

            result.append(replacement)
            lastPos = match.end
            count++
        }

        // Append remaining text
        result.append(str.substring(lastPos))

        return buildList {
            add(LuaString(result.toString()))
            add(LuaNumber.of(count.toDouble()))
        }
    }

    /**
     * Find substring (supports both plain search and pattern matching)
     */
    fun findString(
        str: String,
        pattern: String,
        init: Int,
        plain: Boolean,
    ): List<LuaValue<*>> {
        // Handle empty pattern special case
        if (pattern.isEmpty()) {
            // Empty pattern matches at start position if it's valid
            val startPos1Based =
                when {
                    init < 0 -> str.length + init + 1
                    init == 0 -> 1
                    else -> init
                }

            if (startPos1Based >= 1 && startPos1Based <= str.length + 1) {
                return buildList {
                    add(LuaNumber.of(startPos1Based.toDouble()))
                    add(LuaNumber.of((startPos1Based - 1).toDouble())) // endPos is before startPos
                }
            }
            return buildList { add(LuaNil) }
        }

        // Convert to 0-based index
        val startPos =
            when {
                init < 0 -> str.length + init + 1
                init == 0 -> 1
                else -> init
            } - 1

        if (startPos < 0 || startPos > str.length) {
            return buildList {
                add(LuaNil)
            }
        }

        // For plain text search, just use indexOf
        if (plain) {
            val index = str.indexOf(pattern, startPos)
            if (index >= 0) {
                return buildList {
                    add(LuaNumber.of((index + 1).toDouble()))
                    add(LuaNumber.of((index + pattern.length).toDouble()))
                }
            }
            return buildList { add(LuaNil) }
        }

        // Pattern matching using LuaPattern
        val luaPattern = LuaPattern(pattern)
        val result = luaPattern.find(str, startPos)

        return if (result != null) {
            buildList {
                add(LuaNumber.of((result.start + 1).toDouble()))
                add(LuaNumber.of(result.end.toDouble()))
                // Add captures if any
                for (capture in result.captures) {
                    add(LuaString(capture.text))
                }
            }
        } else {
            buildList { add(LuaNil) }
        }
    }
}
