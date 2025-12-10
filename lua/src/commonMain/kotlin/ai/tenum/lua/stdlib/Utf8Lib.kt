package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext

/**
 * UTF-8 Library (utf8.*)
 *
 * Provides functions for UTF-8 string manipulation.
 * Based on Lua 5.4 utf8 library specification.
 */
class Utf8Lib : LuaLibrary {
    override val name: String = "utf8"

    override fun register(context: LuaLibraryContext) {
        val lib = LuaTable()

        // utf8.char(...) - Convert codepoints to UTF-8 string
        lib[LuaString("char")] =
            LuaNativeFunction { args ->
                buildList {
                    add(utf8Char(args))
                }
            }

        // utf8.codes(s) - Iterator for UTF-8 characters
        lib[LuaString("codes")] =
            LuaNativeFunction { args ->
                buildList {
                    add(utf8Codes(args, context))
                }
            }

        // utf8.codepoint(s [, i [, j]]) - Extract codepoints from string
        lib[LuaString("codepoint")] =
            LuaNativeFunction { args ->
                utf8Codepoint(args)
            }

        // utf8.len(s [, i [, j]]) - Count UTF-8 characters
        lib[LuaString("len")] =
            LuaNativeFunction { args ->
                buildList {
                    add(utf8Len(args))
                }
            }

        // utf8.offset(s, n [, i]) - Find byte offset of nth character
        lib[LuaString("offset")] =
            LuaNativeFunction { args ->
                buildList {
                    add(utf8Offset(args))
                }
            }

        // utf8.charpattern - Pattern that matches exactly one UTF-8 character
        lib[LuaString("charpattern")] = LuaString("[\u0000-\u007F\u0080-\u00BF\u00C0-\u00DF][\u0080-\u00BF]*")

        context.registerGlobal("utf8", lib)
    }

    /**
     * utf8.char(...) - Convert codepoints to UTF-8 string
     */
    private fun utf8Char(args: List<LuaValue<*>>): LuaValue<*> {
        if (args.isEmpty()) {
            return LuaString("")
        }

        val result = StringBuilder()
        for (arg in args) {
            val codepoint =
                when (arg) {
                    is LuaNumber -> arg.toDouble().toInt()
                    else -> return LuaNil
                }

            // Validate codepoint range (0 to 0x10FFFF)
            if (codepoint < 0 || codepoint > 0x10FFFF) {
                return LuaNil
            }

            // Convert codepoint to UTF-8 character
            try {
                result.append(Char(codepoint))
            } catch (e: Exception) {
                // Invalid codepoint
                return LuaNil
            }
        }

        return LuaString(result.toString())
    }

    /**
     * utf8.codes(s) - Returns iterator function for UTF-8 characters
     */
    private fun utf8Codes(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): LuaValue<*> {
        val str = args.getOrNull(0) as? LuaString ?: return LuaNil
        val text = str.value

        // Return an iterator function
        return LuaNativeFunction { iterArgs ->
            // iterArgs[0] is the string, iterArgs[1] is the current position
            val pos = (iterArgs.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt() ?: 0

            if (pos >= text.length) {
                buildList { add(LuaNil) }
            } else {
                val char = text[pos]
                val codepoint = char.code
                val nextPos = pos + 1

                buildList {
                    add(LuaNumber.of(nextPos.toLong()))
                    add(LuaNumber.of(codepoint.toLong()))
                }
            }
        }
    }

    /**
     * utf8.codepoint(s [, i [, j]]) - Extract codepoints from string
     */
    private fun utf8Codepoint(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val str = args.getOrNull(0) as? LuaString ?: return listOf(LuaNil)
        // CPD-OFF: index parsing (similar but different default for j parameter)
        val text = str.value

        val i = (args.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt() ?: 1
        val j = (args.getOrNull(2) as? LuaNumber)?.toDouble()?.toInt() ?: i

        // Convert to 0-based indices
        val startIdx = adjustIndex(i, text.length)
        val endIdx = adjustIndex(j, text.length)
        // CPD-ON

        if (startIdx < 0 || startIdx >= text.length || endIdx < startIdx) {
            return listOf(LuaNil)
        }

        val result = mutableListOf<LuaValue<*>>()
        for (idx in startIdx..minOf(endIdx, text.length - 1)) {
            val codepoint = text[idx].code
            result.add(LuaNumber.of(codepoint.toLong()))
        }

        return result
    }

    /**
     * utf8.len(s [, i [, j]]) - Count UTF-8 characters in string
     */
    private fun utf8Len(args: List<LuaValue<*>>): LuaValue<*> {
        val str = args.getOrNull(0) as? LuaString ?: return LuaNil
        val text = str.value

        val i = (args.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt() ?: 1
        val j = (args.getOrNull(2) as? LuaNumber)?.toDouble()?.toInt() ?: text.length

        // Convert to 0-based indices
        val startIdx = adjustIndex(i, text.length)
        val endIdx = adjustIndex(j, text.length)

        if (startIdx < 0 || startIdx > text.length) {
            return LuaNil
        }

        val actualEnd = minOf(endIdx, text.length - 1)
        if (actualEnd < startIdx) {
            return LuaNumber.of(0)
        }

        val length = actualEnd - startIdx + 1
        return LuaNumber.of(length.toLong())
    }

    /**
     * utf8.offset(s, n [, i]) - Find byte offset of nth character
     */
    private fun utf8Offset(args: List<LuaValue<*>>): LuaValue<*> {
        val str = args.getOrNull(0) as? LuaString ?: return LuaNil
        val text = str.value

        val n = (args.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt() ?: return LuaNil
        val i = (args.getOrNull(2) as? LuaNumber)?.toDouble()?.toInt() ?: 1

        // Convert to 0-based index
        val startIdx = adjustIndex(i, text.length)

        if (startIdx < 0 || startIdx > text.length) {
            return LuaNil
        }

        // Calculate target position
        val targetIdx =
            if (n >= 0) {
                startIdx + n
            } else {
                startIdx + n + 1
            }

        // Validate result
        if (targetIdx < 0 || targetIdx > text.length) {
            return LuaNil
        }

        // Return 1-based byte position
        return LuaNumber.of((targetIdx + 1).toLong())
    }

    /**
     * Convert Lua 1-based index to 0-based, handling negative indices
     */
    private fun adjustIndex(
        luaIndex: Int,
        length: Int,
    ): Int =
        if (luaIndex > 0) {
            luaIndex - 1 // Convert 1-based to 0-based
        } else if (luaIndex < 0) {
            length + luaIndex // Negative index from end
        } else {
            0 // Lua index 0 is treated as position before first character
        }
}
