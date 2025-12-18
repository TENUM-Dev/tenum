package ai.tenum.lua.stdlib.string.unpack.handlers

import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.stdlib.string.BinaryOperations
import ai.tenum.lua.stdlib.string.NumericUnpackHelpers
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler

/**
 * Unpack fixed-size string (c)
 */
class FixedStringHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        val size = context.parser.readNumber() ?: throw IllegalArgumentException("missing size for 'c'")
        if (context.pos + size > context.data.length) {
            throw RuntimeException("data string too short")
        }
        val sb = StringBuilder()
        for (j in 0 until size) {
            sb.append(context.readByte().toChar())
        }
        context.result.add(LuaString(sb.toString()))
    }
}

/**
 * Unpack string with length prefix (s)
 */
class PrefixedStringHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        val size = context.parser.readNumber() ?: 8
        BinaryOperations.validateIntegerSize(size)
        val length =
            when (
                val lengthVal =
                    NumericUnpackHelpers.unpackInteger(
                        context::readByte,
                        size,
                        context.parser.littleEndian,
                        signed = false,
                    )
            ) {
                is LuaLong -> lengthVal.value
                is LuaDouble -> lengthVal.value.toLong()
                else -> 0L
            }
        if (length < 0 || length > Int.MAX_VALUE) {
            throw RuntimeException("unfinished string")
        }
        val sb = StringBuilder()
        for (j in 0 until length.toInt()) {
            sb.append(context.readByte().toChar())
        }
        context.result.add(LuaString(sb.toString()))
    }
}

/**
 * Unpack zero-terminated string (z)
 */
class ZeroTerminatedStringHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        val sb = StringBuilder()
        var loopCounter = 0
        while (true) {
            if (loopCounter++ > context.data.length) {
                throw RuntimeException("infinite loop detected in zero-terminated string")
            }
            if (context.pos >= context.data.length) {
                throw RuntimeException("unfinished string for format 'z'")
            }
            val b = context.readByte()
            if (b == 0) break
            sb.append(b.toChar())
        }
        context.result.add(LuaString(sb.toString()))
    }
}
