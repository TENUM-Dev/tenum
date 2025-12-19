@file:Suppress("REDUNDANT_ELSE_IN_WHEN")

package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.AlignHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.DoubleHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.FixedStringHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.FloatHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.PrefixedStringHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.SignedByteHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.SignedIntHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.SignedLongHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.SignedShortHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.SignedVariableIntHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.SizeTHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.SkipByteHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.UnsignedByteHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.UnsignedIntHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.UnsignedLongHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.UnsignedShortHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.UnsignedVariableIntHandler
import ai.tenum.lua.stdlib.string.unpack.handlers.ZeroTerminatedStringHandler

/**
 * Binary unpacking operations for string.unpack.
 */
object StringBinaryUnpack {
    private val handlers: Map<Char, UnpackHandler> =
        mapOf(
            'b' to SignedByteHandler(),
            'B' to UnsignedByteHandler(),
            'h' to SignedShortHandler(),
            'H' to UnsignedShortHandler(),
            'l' to SignedIntHandler(),
            'L' to UnsignedIntHandler(),
            'j' to SignedLongHandler(),
            'J' to UnsignedLongHandler(),
            'i' to SignedVariableIntHandler(),
            'I' to UnsignedVariableIntHandler(),
            'f' to FloatHandler(),
            'd' to DoubleHandler(),
            'n' to DoubleHandler(),
            'T' to SizeTHandler(),
            'c' to FixedStringHandler(),
            's' to PrefixedStringHandler(),
            'z' to ZeroTerminatedStringHandler(),
            'x' to SkipByteHandler(),
            'X' to AlignHandler(),
        )

    /**
     * Unpack values from a binary string according to format string.
     */
    fun unpackValues(
        format: String,
        data: String,
        startPos: Int = 0,
    ): List<LuaValue<*>> {
        val result = mutableListOf<LuaValue<*>>()
        val parser = FormatParser(format)
        val context = UnpackContext(data, parser, result, startPos)

        processFormatString(context)
        addFinalPosition(context, result)
        return result
    }

    private fun processFormatString(context: UnpackContext) {
        while (context.parser.hasMore()) {
            context.parser.skipWhitespace()
            if (!context.parser.hasMore()) break

            val c = context.parser.current()
            if (handleModifierOrAlignment(context, c)) {
                continue
            }

            context.parser.advance()
            dispatchHandler(c, context)
        }
    }

    private fun handleModifierOrAlignment(
        context: UnpackContext,
        c: Char,
    ): Boolean {
        if (FormatParserHelpers.processModifier(context.parser, c)) {
            return true
        }
        if (c == '!') {
            FormatParserHelpers.processAlignment(context.parser)
            return true
        }
        return false
    }

    private fun dispatchHandler(
        c: Char,
        context: UnpackContext,
    ) {
        val handler = handlers[c] ?: throw IllegalArgumentException("invalid format option '$c'")
        handler.unpack(context)
    }

    private fun addFinalPosition(
        context: UnpackContext,
        result: MutableList<LuaValue<*>>,
    ) {
        result.add(LuaNumber.of((context.pos + 1).toDouble()))
    }
}
