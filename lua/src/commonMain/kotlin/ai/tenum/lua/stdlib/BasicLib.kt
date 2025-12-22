package ai.tenum.lua.stdlib

import ai.tenum.lua.lexer.NumberParser
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.internal.BasicLibErrorHandling
import ai.tenum.lua.stdlib.internal.BasicLibIteration
import ai.tenum.lua.stdlib.internal.BasicLibLoading
import ai.tenum.lua.stdlib.internal.argError
import ai.tenum.lua.vm.debug.DebugTracer
import ai.tenum.lua.vm.library.CallFunctionCallback
import ai.tenum.lua.vm.library.GetMetamethodCallback
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext
import ai.tenum.lua.vm.library.RegisterGlobalCallback

/**
 * Basic Lua functions (global level)
 * Implements: type, tostring, tonumber, metatable operations, print, raw operations
 *
 * Delegates to specialized modules:
 * - BasicLibErrorHandling: assert, error, pcall, xpcall
 * - BasicLibIteration: pairs, ipairs, next, select
 * - BasicLibLoading: load, loadfile, dofile, require, package.*
 */
class BasicLib(
    val debugTracer: DebugTracer,
) : LuaLibrary {
    override val name: String? = null // Basic functions are registered globally

    private val numberParser = NumberParser()

    override fun register(context: LuaLibraryContext) {
        val registerGlobal = context.registerGlobal
        val getMetamethod = context.getMetamethod
        val callFunction = context.callFunction

        // Core type and conversion functions
        registerCoreTypeFunctions(registerGlobal, getMetamethod, callFunction)

        // Metatable operations
        registerMetatableFunctions(registerGlobal)

        // Print function
        registerGlobal(
            "print",
            LuaNativeFunction { args ->
                printImpl(args)
                emptyList()
            },
        )

        // Raw table operations
        registerRawOperations(registerGlobal)

        // Delegate to specialized modules
        BasicLibErrorHandling.registerFunctions(registerGlobal, callFunction, context.getCallStack, context.vm)
        BasicLibIteration.registerFunctions(registerGlobal, getMetamethod, callFunction)
        BasicLibLoading(debugTracer).registerFunctions(registerGlobal, context)
    }

    private fun registerCoreTypeFunctions(
        registerGlobal: RegisterGlobalCallback,
        getMetamethod: GetMetamethodCallback,
        callFunction: CallFunctionCallback,
    ) {
        // type(value) - returns the type of a value
        registerGlobal(
            "type",
            LuaNativeFunction { args ->
                if (args.isEmpty()) {
                    throw RuntimeException("bad argument #1 to 'type' (value expected)")
                }
                val value = args[0]
                val mtName = (value.metatable as? LuaTable)?.get(LuaString("__name")) as? LuaString
                val typeName = mtName?.value ?: value.type().name.lowercase()
                buildList { add(LuaString(typeName)) }
            },
        )

        // tostring(value) - converts a value to a string
        registerGlobal(
            "tostring",
            LuaNativeFunction { args ->
                if (args.isEmpty()) {
                    throw RuntimeException("bad argument #1 to 'tostring' (value expected)")
                }
                val value = args[0]
                // Check for __tostring metamethod
                val result = getMetamethod(value, "__tostring")
                if (result != null && result is LuaFunction) {
                    val metaResult = callFunction(result, buildList { add(value) })
                    if (metaResult.isNotEmpty() && metaResult[0] is LuaString) {
                        return@LuaNativeFunction buildList { add(metaResult[0]) }
                    } else {
                        // __tostring must return a string
                        throw RuntimeException("'__tostring' must return a string")
                    }
                }
                // If value is a table and has __name in metatable, use it in default tostring
                if (value is LuaTable) {
                    val name = (value.metatable as? LuaTable)?.get(LuaString("__name")) as? LuaString
                    if (name != null) {
                        return@LuaNativeFunction buildList { add(LuaString("${name.value}: ${value.hashCode()}")) }
                    }
                }

                buildList { add(LuaString(valueToString(value))) }
            },
        )

        // tonumber(e [, base]) - converts string/number to number
        registerGlobal(
            "tonumber",
            LuaNativeFunction { args ->
                if (args.isEmpty()) {
                    throw RuntimeException("bad argument #1 to 'tonumber' (value expected)")
                }
                val value = args[0]
                val base = args.getOrNull(1) as? LuaNumber

                val result =
                    if (base != null) {
                        convertToNumberWithBase(value, base.toDouble().toInt())
                    } else {
                        convertToNumber(value)
                    }

                buildList { add(result) }
            },
        )
    }

    private fun registerMetatableFunctions(registerGlobal: (String, LuaValue<*>) -> Unit) {
        // setmetatable(table, metatable) - sets the metatable of a table
        registerGlobal(
            "setmetatable",
            LuaNativeFunction { args ->
                if (args.isEmpty() || args[0] !is LuaTable) {
                    argError("setmetatable", 1, "table", args.getOrNull(0))
                }
                val table = args[0] as LuaTable
                val metatable = if (args.size > 1) args[1] else LuaNil

                if (metatable !is LuaNil && metatable !is LuaTable) {
                    argError("setmetatable", 2, "table or nil", metatable)
                }

                table.metatable = if (metatable is LuaTable) metatable else null
                buildList { add(table) }
            },
        )

        // getmetatable(table) - gets the metatable of a table
        registerGlobal(
            "getmetatable",
            LuaNativeFunction { args ->
                val value = args.firstOrNull() ?: LuaNil
                val mt = value.metatable
                buildList { add(mt ?: LuaNil) }
            },
        )
    }

    private fun registerRawOperations(registerGlobal: (String, LuaValue<*>) -> Unit) {
        // rawget(table, index) - gets value without __index metamethod
        registerGlobal(
            "rawget",
            LuaNativeFunction { args ->
                if (args.size < 2) {
                    throw RuntimeException("rawget expects 2 arguments")
                }
                val table = args[0]
                if (table !is LuaTable) {
                    throw RuntimeException("rawget expects a table as first argument")
                }
                val index = args[1]
                buildList { add(table.rawGet(index)) }
            },
        )

        // rawset(table, index, value) - sets value without __newindex metamethod
        registerGlobal(
            "rawset",
            LuaNativeFunction { args ->
                if (args.size < 3) {
                    throw RuntimeException("rawset expects 3 arguments")
                }
                val table = args[0]
                if (table !is LuaTable) {
                    throw RuntimeException("rawset expects a table as first argument")
                }
                val index = args[1]
                if (index is LuaNil) {
                    throw RuntimeException("table index is nil")
                }
                // Check for NaN in numeric indices (Lua 5.4 behavior)
                if (index is LuaDouble && index.value.isNaN()) {
                    throw RuntimeException("table index is NaN")
                }
                val value = args[2]
                table.rawSet(index, value)
                buildList { add(table) }
            },
        )

        // rawequal(v1, v2) - equality without __eq metamethod
        registerGlobal(
            "rawequal",
            LuaNativeFunction { args ->
                if (args.size < 2) {
                    throw RuntimeException("rawequal expects 2 arguments")
                }
                val v1 = args[0]
                val v2 = args[1]
                val isEqual = v1 == v2
                buildList { add(LuaBoolean.of(isEqual)) }
            },
        )

        // rawlen(v) - length without __len metamethod
        registerGlobal(
            "rawlen",
            LuaNativeFunction { args ->
                if (args.isEmpty()) {
                    throw RuntimeException("bad argument #1 to 'rawlen' (value expected)")
                }
                val value = args[0]
                val length =
                    when (value) {
                        is LuaTable -> {
                            var count = 0
                            for (i in 1..Int.MAX_VALUE) {
                                val v = value.rawGet(LuaNumber.of(i))
                                if (v is LuaNil) break
                                count++
                            }
                            count
                        }
                        is LuaString -> value.value.length
                        else -> throw RuntimeException("rawlen expects a table or string")
                    }
                buildList { add(LuaNumber.of(length)) }
            },
        )
    }

    private fun printImpl(args: List<LuaValue<*>>) {
        val parts = args.map { valueToString(it) }
        println(parts.joinToString("\t"))
    }

    /**
     * Convert a Lua value to a string representation
     */
    private fun valueToString(value: LuaValue<*>): String =
        ai.tenum.lua.vm.typeops.ValueFormatter
            .toString(value)

    /**
     * Convert a value to a number (no base)
     */
    private fun convertToNumber(value: LuaValue<*>): LuaValue<*> {
        return when (value) {
            is LuaNumber -> value
            is LuaString -> {
                val trimmed = value.value.trim()
                if (trimmed.isEmpty()) return LuaNil

                // Lua does not accept strings with null characters
                if (trimmed.contains('\u0000')) {
                    return LuaNil
                }

                // Handle hexadecimal literals with exponent (0x1.23p+4)
                // or without exponent (0xABC or 0xABC.DEF)
                if (ai.tenum.lua.lexer.HexParsingHelpers
                        .isHexLiteral(trimmed)
                ) {
                    val (sign, hexPart) =
                        ai.tenum.lua.lexer.HexParsingHelpers
                            .extractSignAndHexPart(trimmed)

                    // Validate there's at least one hex digit after "0x"
                    // Empty hex (e.g., "0x", "-0x ", etc.) should return nil
                    if (hexPart.length <= 2) {
                        return LuaNil
                    }

                    // Use NumberParser to handle hex floats with exponent (p notation)
                    val parsedValue = numberParser.parseHexFloat(hexPart)
                    if (parsedValue != null) {
                        return LuaNumber.of(sign * parsedValue)
                    }

                    // Fallback: try parsing as hex integer (no decimal point or exponent)
                    val hexMatch = Regex("^0x([0-9a-fA-F]+)$").matchEntire(hexPart)
                    if (hexMatch != null) {
                        val hexInt = hexMatch.groupValues[1]
                        try {
                            // Try direct Long parsing first
                            val intPart = hexInt.toLong(16).toDouble()
                            return LuaNumber.of(sign * intPart)
                        } catch (e: NumberFormatException) {
                            // Hex integer too large for Long - apply 64-bit wrapping
                            // Take only the last 16 hex digits (64 bits)
                            val truncated =
                                if (hexInt.length > 16) {
                                    hexInt.takeLast(16)
                                } else {
                                    hexInt
                                }
                            try {
                                val unsignedValue = truncated.toULong(16)
                                val intPart = unsignedValue.toLong().toDouble()
                                return LuaNumber.of(sign * intPart)
                            } catch (e2: NumberFormatException) {
                                // Even truncated value failed - return nil
                                return LuaNil
                            }
                        }
                    }

                    return LuaNil
                }

                // Lua does not accept "inf", "infinity", "nan", etc. as valid number strings
                // but Kotlin's toDouble() does, so we need to reject them explicitly
                val lowerTrimmed = trimmed.lowercase()
                val isInvalidNumberString =
                    lowerTrimmed in
                        setOf(
                            "inf",
                            "infinity",
                            "nan",
                            "-inf",
                            "-infinity",
                            "+inf",
                            "+infinity",
                        )
                if (isInvalidNumberString) {
                    return LuaNil
                }

                try {
                    if (!trimmed.contains('.') &&
                        !trimmed.contains('e', ignoreCase = true)
                    ) {
                        try {
                            return LuaNumber.of(trimmed.toLong())
                        } catch (e: NumberFormatException) {
                            // Integer overflow - parse as Double and force float type
                            return try {
                                LuaDouble(trimmed.toDouble())
                            } catch (e2: NumberFormatException) {
                                LuaNil
                            }
                        }
                    }
                    LuaNumber.of(trimmed.toDouble())
                } catch (e: NumberFormatException) {
                    LuaNil
                }
            }
            else -> LuaNil
        }
    }

    /**
     * Convert a value to a number with a specific base
     */
    private fun convertToNumberWithBase(
        value: LuaValue<*>,
        base: Int,
    ): LuaValue<*> {
        if (value !is LuaString) return LuaNil
        if (base < 2 || base > 36) {
            throw RuntimeException("bad argument #2 to 'tonumber' (base out of range)")
        }

        val str = value.value.trim()
        if (str.isEmpty()) return LuaNil

        return try {
            val negative = str.startsWith("-")
            val positive = str.startsWith("+")
            val numStr =
                if (negative || positive) {
                    str.substring(1)
                } else {
                    str
                }

            val result = numStr.toLong(base)
            LuaNumber.of(if (negative) -result else result)
        } catch (e: NumberFormatException) {
            LuaNil
        }
    }
}
