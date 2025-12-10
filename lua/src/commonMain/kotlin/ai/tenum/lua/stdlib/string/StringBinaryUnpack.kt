package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue

/**
 * Binary unpacking operations for string.unpack.
 */
object StringBinaryUnpack {
    /**
     * Unpack values from a binary string according to format string.
     */
    fun unpackValues(
        format: String,
        data: String,
        startPos: Int = 0,
    ): List<LuaValue<*>> {
        val result = mutableListOf<LuaValue<*>>()
        var pos = startPos
        val parser = FormatParser(format)

        fun readByte(): Int {
            if (pos >= data.length) throw RuntimeException("data string too short")
            return data[pos++].code.and(0xFF)
        }

        // Auto-align current position to given size (when ! is active)
        fun autoAlign(size: Int) {
            val padding = BinaryOperations.autoAlign(pos, size, parser.maxAlign)
            repeat(padding) { readByte() }
        }

        // Unpack variable-size integer with size validation and alignment
        fun unpackVariableInteger(signed: Boolean) {
            val size = parser.readNumber() ?: 4
            BinaryOperations.validateIntegerSize(size)
            val actualSize = if (parser.maxAlign != null) minOf(size, parser.maxAlign!!) else size
            autoAlign(actualSize)
            result.add(NumericUnpackHelpers.unpackInteger(::readByte, actualSize, parser.littleEndian, signed))
        }

        // CPD-OFF: unpack loop structure (similar to pack but reads instead of writes)
        while (parser.hasMore()) {
            parser.skipWhitespace()
            if (!parser.hasMore()) break

            val c = parser.current()
            if (FormatParserHelpers.processModifier(parser, c)) {
                continue
            }

            if (c == '!') {
                FormatParserHelpers.processAlignment(parser)
                continue
            }
            // CPD-ON

            parser.advance()
            when (c) {
                'b' -> {
                    val v = readByte().toByte()
                    result.add(LuaNumber.of(v.toLong().toDouble()))
                }
                'B' -> {
                    val v = readByte()
                    result.add(LuaNumber.of(v.toDouble()))
                }
                'h' -> {
                    autoAlign(2)
                    result.add(NumericUnpackHelpers.unpackShort(::readByte, parser.littleEndian, signed = true))
                }
                'H' -> {
                    autoAlign(2)
                    result.add(NumericUnpackHelpers.unpackShort(::readByte, parser.littleEndian, signed = false))
                }
                'l' -> {
                    autoAlign(4)
                    result.add(NumericUnpackHelpers.unpackInt(::readByte, parser.littleEndian, signed = true))
                }
                'L' -> {
                    autoAlign(4)
                    result.add(NumericUnpackHelpers.unpackInt(::readByte, parser.littleEndian, signed = false))
                }
                'j' -> {
                    autoAlign(8)
                    result.add(NumericUnpackHelpers.unpackLong(::readByte, parser.littleEndian, signed = true))
                }
                'J' -> {
                    autoAlign(8)
                    result.add(NumericUnpackHelpers.unpackLong(::readByte, parser.littleEndian, signed = false))
                }
                'i' -> unpackVariableInteger(signed = true)
                'I' -> unpackVariableInteger(signed = false)
                'f' -> {
                    autoAlign(4)
                    result.add(NumericUnpackHelpers.unpackFloat(::readByte, parser.littleEndian))
                }
                'd' -> {
                    autoAlign(8)
                    result.add(NumericUnpackHelpers.unpackDouble(::readByte, parser.littleEndian))
                }
                'n' -> {
                    autoAlign(8)
                    result.add(NumericUnpackHelpers.unpackDouble(::readByte, parser.littleEndian))
                }
                'T' -> {
                    autoAlign(8)
                    result.add(NumericUnpackHelpers.unpackLong(::readByte, parser.littleEndian, signed = false))
                }
                'c' -> {
                    val size = parser.readNumber() ?: throw IllegalArgumentException("missing size for 'c'")
                    if (pos + size > data.length) {
                        throw RuntimeException("data string too short")
                    }
                    val sb = StringBuilder()
                    for (j in 0 until size) {
                        sb.append(readByte().toChar())
                    }
                    result.add(LuaString(sb.toString()))
                }
                's' -> {
                    val size = parser.readNumber() ?: 8
                    BinaryOperations.validateIntegerSize(size)
                    val length =
                        when (val lengthVal = NumericUnpackHelpers.unpackInteger(::readByte, size, parser.littleEndian, signed = false)) {
                            is LuaLong -> lengthVal.value
                            is LuaDouble -> lengthVal.value.toLong()
                            else -> 0L
                        }
                    if (length < 0 || length > Int.MAX_VALUE) {
                        throw RuntimeException("unfinished string")
                    }
                    val sb = StringBuilder()
                    for (j in 0 until length.toInt()) {
                        sb.append(readByte().toChar())
                    }
                    result.add(LuaString(sb.toString()))
                }
                'z' -> {
                    val sb = StringBuilder()
                    while (true) {
                        if (pos >= data.length) {
                            throw RuntimeException("unfinished string for format 'z'")
                        }
                        val b = readByte()
                        if (b == 0) break
                        sb.append(b.toChar())
                    }
                    result.add(LuaString(sb.toString()))
                }
                'x' -> readByte()
                'X' -> {
                    val (nextSize, alreadyAdvanced) = parser.peekNextFormatSize()
                    if (parser.maxAlign != null) {
                        val alignSize = minOf(nextSize, parser.maxAlign!!)
                        val padding = BinaryOperations.calculatePadding(pos, alignSize)
                        repeat(padding) { readByte() }
                    }
                    parser.consumeNextFormatChar(alreadyAdvanced)
                }
                else -> throw IllegalArgumentException("invalid format option '$c'")
            }
        }

        // Add final position as last return value
        result.add(LuaNumber.of((pos + 1).toDouble())) // Lua uses 1-based indexing
        return result
    }
}
