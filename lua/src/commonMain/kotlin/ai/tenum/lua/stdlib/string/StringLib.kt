package ai.tenum.lua.stdlib.string

import ai.tenum.lua.compiler.io.ChunkWriter
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext

/**
 * Lua String Library
 * Implements string manipulation functions
 */
class StringLib : LuaLibrary {
    override val name: String = "string"

    override fun register(context: LuaLibraryContext) {
        val lib = LuaTable()

        // string.len(s) - returns the length of string s
        lib[LuaString("len")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getFirstArgAsString(args)
                buildList<LuaValue<*>> {
                    add(LuaNumber.Companion.of(str.length.toDouble()))
                }
            }

        // string.sub(s, i [, j]) - returns substring from i to j
        lib[LuaString("sub")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getArgAsStringStrict(args, 0, "sub")
                val i = ArgumentHelpers.getArgAsIntStrict(args, 1, 1, "sub")
                val j = ArgumentHelpers.getArgAsIntStrict(args, 2, str.length, "sub")

                buildList<LuaValue<*>> {
                    add(LuaString(StringOperations.substringLua(str, i, j)))
                }
            }

        // string.upper(s) - converts to uppercase
        lib[LuaString("upper")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getFirstArgAsString(args)
                buildList<LuaValue<*>> {
                    add(LuaString(str.uppercase()))
                }
            }

        // string.lower(s) - converts to lowercase
        lib[LuaString("lower")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getFirstArgAsString(args)
                buildList<LuaValue<*>> {
                    add(LuaString(str.lowercase()))
                }
            }

        // string.reverse(s) - reverses string
        lib[LuaString("reverse")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getFirstArgAsString(args)
                buildList<LuaValue<*>> {
                    add(LuaString(str.reversed()))
                }
            }

        // string.rep(s, n [, sep]) - repeats string n times with optional separator
        lib[LuaString("rep")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getArgAsString(args, 0)

                // Validate n before converting to Int
                val nValue = args.getOrNull(1)
                val nLong =
                    when (nValue) {
                        is LuaLong -> nValue.value
                        is LuaDouble -> {
                            // Validate integer representation
                            val doubleValue = nValue.value
                            if (!ArgumentHelpers.hasIntegerRepresentation(doubleValue)) {
                                throw RuntimeException("number has no integer representation")
                            }
                            doubleValue.toLong()
                        }

                        else -> 0L
                    }

                // Lua treats negative repetition counts as 0 (returns empty string)
                if (nLong < 0) {
                    return@LuaNativeFunction buildList<LuaValue<*>> {
                        add(LuaString(""))
                    }
                }

                val sep = ArgumentHelpers.getArgAsString(args, 2)

                // Check total result size BEFORE attempting allocation
                // Use practical memory limit to prevent OOM (not theoretical Int.MAX_VALUE)
                // Most JVMs can't actually allocate arrays anywhere near Int.MAX_VALUE
                // Set to Int.MAX_VALUE / 2 (~1GB) which catches truly massive allocations
                // while allowing the test suite to run (e.g., format tests that need large strings)
                val practicalMaxLength = (Int.MAX_VALUE / 2).toLong() // ~1GB (~1,073,741,823 chars)
                val strLen = str.length.toLong()
                val sepLen = sep.length.toLong()

                // Quick check: if n itself is unreasonably large, fail immediately
                if (nLong > practicalMaxLength) {
                    throw RuntimeException("resulting string too large")
                }

                // Calculate total result length: n * str.length + (n-1) * sep.length
                var totalLength = 0L

                // Add string contribution
                if (strLen > 0 && nLong > 0) {
                    if (nLong > practicalMaxLength / strLen) {
                        throw RuntimeException("resulting string too large")
                    }
                    totalLength = strLen * nLong
                }

                // Add separator contribution
                if (sepLen > 0 && nLong > 1) {
                    val sepRepetitions = nLong - 1
                    if (sepRepetitions > practicalMaxLength / sepLen) {
                        throw RuntimeException("resulting string too large")
                    }
                    val sepContribution = sepLen * sepRepetitions
                    if (totalLength > practicalMaxLength - sepContribution) {
                        throw RuntimeException("resulting string too large")
                    }
                    totalLength += sepContribution
                }

                // Final check
                if (totalLength > practicalMaxLength) {
                    throw RuntimeException("resulting string too large")
                }

                val n = nLong.toInt()

                buildList<LuaValue<*>> {
                    add(LuaString.Companion.of(StringOperations.repeatString(str, n, sep)))
                }
            }

        // string.byte(s [, i [, j]]) - returns byte codes
        lib[LuaString("byte")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getArgAsString(args, 0)
                val i = ArgumentHelpers.getArgAsInt(args, 1, 1)
                val j = ArgumentHelpers.getArgAsInt(args, 2, i)

                StringOperations.stringByte(str, i, j)
            }

        // string.char(...) - converts byte codes to string
        lib[LuaString("char")] =
            LuaNativeFunction { args ->
                StringOperations.charFromBytes(args)
            }

        // string.format(formatstring, ...) - formatted output
        lib[LuaString("format")] =
            LuaNativeFunction { args ->
                val format = ArgumentHelpers.getFirstArgAsString(args)
                val values = if (args.isNotEmpty()) args.drop(1) else emptyList()

                // Create a valueToString converter that handles metamethods
                val valueToString: (LuaValue<*>) -> String = { value ->
                    when (value) {
                        is LuaNil -> "nil"
                        is LuaBoolean -> value.value.toString()
                        is LuaString -> value.value
                        is LuaNumber -> ArgumentHelpers.numberToString(value.value)
                        else -> {
                            // Check for __tostring metamethod
                            val toStringMeta = context.getMetamethod(value, "__tostring")
                            if (toStringMeta != null && toStringMeta is LuaFunction) {
                                val result = context.callFunction(toStringMeta, listOf(value))
                                if (result.isNotEmpty() && result[0] is LuaString) {
                                    (result[0] as LuaString).value
                                } else {
                                    throw RuntimeException("'__tostring' must return a string")
                                }
                            } else {
                                // Check for __name in metatable
                                if (value is LuaTable) {
                                    val name = (value.metatable as? LuaTable)?.get(LuaString("__name")) as? LuaString
                                    if (name != null) {
                                        "${name.value}: "
                                    } else {
                                        value.toString()
                                    }
                                } else {
                                    value.toString()
                                }
                            }
                        }
                    }
                }

                buildList<LuaValue<*>> {
                    add(LuaString(StringFormatting.format(format, values, valueToString)))
                }
            }

        // string.find(s, pattern [, init [, plain]]) - finds substring
        lib[LuaString("find")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getArgAsStringChecked(args, 0, "find")
                val pattern = ArgumentHelpers.getArgAsStringChecked(args, 1, "find")
                val init = ArgumentHelpers.getArgAsInt(args, 2, 1)
                val plain = ArgumentHelpers.getArgAsBool(args, 3, false)

                StringPatternMatching.findString(str, pattern, init, plain)
            }

        // string.match(s, pattern [, init]) - matches pattern
        lib[LuaString("match")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getArgAsString(args, 0)
                val pattern = ArgumentHelpers.getArgAsString(args, 1)
                val init = ArgumentHelpers.getArgAsInt(args, 2, 1)

                StringPatternMatching.matchPattern(str, pattern, init)
            }

        // string.gmatch(s, pattern) - iterator for pattern matches
        lib[LuaString("gmatch")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getArgAsString(args, 0)
                val pattern = ArgumentHelpers.getArgAsString(args, 1)

                buildList<LuaValue<*>> {
                    add(StringPatternMatching.gmatchIterator(str, pattern))
                }
            }

        // string.gsub(s, pattern, repl [, n]) - global substitution
        lib[LuaString("gsub")] =
            LuaNativeFunction { args ->
                val str = ArgumentHelpers.getArgAsString(args, 0)
                val pattern = ArgumentHelpers.getArgAsString(args, 1)
                val repl = args.getOrNull(2) ?: LuaNil
                val n = ArgumentHelpers.getArgAsIntOrNull(args, 3)

                StringPatternMatching.gsubPattern(str, pattern, repl, n, context)
            }

        // string.dump(function [, strip]) - returns binary representation of function
        lib[LuaString("dump")] =
            LuaNativeFunction { args ->
                buildList<LuaValue<*>> {
                    add(dumpFunction(args, context))
                }
            }

        // string.packsize(format) - returns size (in bytes) of the given format
        lib[LuaString("packsize")] =
            LuaNativeFunction { args ->
                val fmt = ArgumentHelpers.getFirstArgAsString(args).trim()
                try {
                    val size = StringBinaryPack.computePackSize(fmt)
                    buildList<LuaValue<*>> { add(LuaNumber.Companion.of(size.toDouble())) }
                } catch (e: IllegalArgumentException) {
                    throw RuntimeException(e.message ?: "invalid format")
                }
            }

        // string.pack(format, v1, v2, ...) - returns binary string with values packed according to format
        lib[LuaString("pack")] =
            LuaNativeFunction { args ->
                if (args.isEmpty()) throw RuntimeException("format string required")
                val fmt = ArgumentHelpers.getFirstArgAsString(args)
                val values = args.drop(1)
                try {
                    val packed = StringBinaryPack.packValues(fmt, values)
                    buildList<LuaValue<*>> { add(LuaString(packed)) }
                } catch (e: IllegalArgumentException) {
                    throw RuntimeException(e.message ?: "invalid format")
                }
            }

        // string.unpack(format, s [, pos]) - returns values unpacked from binary string
        lib[LuaString("unpack")] =
            LuaNativeFunction { args ->
                if (args.size < 2) throw RuntimeException("format and string required")
                val fmt = ArgumentHelpers.getFirstArgAsString(args)
                val str = ArgumentHelpers.getArgAsString(args, 1)
                val pos =
                    if (args.size >= 3) {
                        val luaPos = (args[2] as? LuaNumber)?.toDouble()?.toInt() ?: 1
                        StringOperations.normalizePosition(luaPos, str.length)
                    } else {
                        0
                    }
                // Validate starting position
                if (pos < 0 || pos > str.length) {
                    throw RuntimeException("initial position out of string")
                }
                try {
                    StringBinaryUnpack.unpackValues(fmt, str, pos)
                } catch (e: IllegalArgumentException) {
                    throw RuntimeException(e.message ?: "invalid format")
                } catch (e: IndexOutOfBoundsException) {
                    throw RuntimeException("data string too short")
                }
            }

        context.registerGlobal("string", lib)

        // Set string metatable for method call syntax (s:upper(), etc.)
        setupStringMetatable(lib)
    }

    /**
     * string.dump() implementation
     * Returns binary representation of a function
     */
    private fun dumpFunction(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): LuaValue<*> {
        val func = args.getOrNull(0)

        // Must be a function
        if (func !is LuaFunction) {
            throw RuntimeException("unable to dump given function")
        }

        // For now, only support LuaCompiledFunction (functions with Proto)
        // Native functions cannot be dumped
        if (func is LuaNativeFunction) {
            throw RuntimeException("unable to dump native function")
        }

        // Get the Proto from the function
        val originalProto =
            when (func) {
                is LuaCompiledFunction -> func.proto
                else -> throw RuntimeException("unable to dump given function")
            }

        // Check if strip parameter is provided (second argument)
        val strip = args.getOrNull(1)?.let { it is LuaBoolean && it.value } ?: false

        // If stripping debug info, create a modified proto
        val proto =
            if (strip) {
                originalProto.copy(
                    source = "=?",
                    lineEvents = emptyList(),
                )
            } else {
                originalProto
            }

        // Serialize to binary chunk
        val bytes = ChunkWriter.dump(proto)

        val str = bytes.map { it.toInt() and 0xFF }.map { it.toChar() }.joinToString("")
        return LuaString(str)
    }

    /**
     * Set up string metatable to enable method call syntax: s:upper()
     *
     * In Lua, all strings share a metatable where __index points to the string library.
     * This allows method call syntax: "hello":upper() becomes string.upper("hello")
     */
    private fun setupStringMetatable(stringLib: LuaTable) {
        // Create metatable for strings
        val stringMetatable = LuaTable()

        // Set __index to the string library table
        stringMetatable[LuaString("__index")] = stringLib

        // Set this as the metatable for all string values
        // We need to set it through an instance because the companion setter is private
        val dummyString = LuaString("")
        dummyString.metatableStore = stringMetatable
    }
}
