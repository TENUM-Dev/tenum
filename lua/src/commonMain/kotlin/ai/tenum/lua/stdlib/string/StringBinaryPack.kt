package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue

/**
 * Binary packing operations for string.pack and string.packsize.
 */
object StringBinaryPack {
    /**
     * Compute size of packed data for given format string
     */
    fun computePackSize(format: String): Int {
        if (format.isEmpty()) throw IllegalArgumentException("empty format")

        val parser = FormatParser(format)
        var total = 0

        // Helper to check for overflow when adding to total
        fun addSize(size: Int) {
            if (size > 0 && total > Int.MAX_VALUE - size) {
                throw RuntimeException("format result too large")
            }
            total += size
        }

        // Helper to apply automatic alignment before adding a type's size
        fun autoAlign(size: Int) {
            val padding = BinaryOperations.autoAlign(total, size, parser.maxAlign)
            addSize(padding)
        }

        while (parser.hasMore()) {
            parser.skipWhitespace()
            if (!parser.hasMore()) break

            val c = parser.current()
            // endianness/alignment markers
            if (c == '<' || c == '>' || c == '=') {
                if (c == '<') parser.littleEndian = true
                if (c == '>') parser.littleEndian = false
                parser.advance()
                continue
            }
            if (c == '!') {
                parser.advance()
                parser.skipWhitespace()
                val alignValue = parser.readNumber()
                parser.maxAlign = alignValue ?: 8
                continue
            }

            parser.advance()
            when (c) {
                'b', 'B' -> addSize(1)
                'h', 'H' -> {
                    autoAlign(2)
                    addSize(2)
                }
                'i', 'I' -> {
                    val num = parser.readNumber()
                    val size = num ?: 4
                    val actualSize = if (parser.maxAlign != null) minOf(size, parser.maxAlign!!) else size
                    autoAlign(actualSize)
                    addSize(actualSize)
                }
                'l', 'L' -> {
                    autoAlign(4)
                    addSize(4)
                }
                'T' -> {
                    autoAlign(8)
                    addSize(8)
                }
                'j', 'J' -> {
                    autoAlign(8)
                    addSize(8)
                }
                'f' -> {
                    autoAlign(4)
                    addSize(4)
                }
                'd' -> {
                    autoAlign(8)
                    addSize(8)
                }
                'n' -> {
                    autoAlign(8)
                    addSize(8)
                }
                'c' -> {
                    val num = parser.readNumber() ?: throw IllegalArgumentException("missing size for 'c'")
                    addSize(num)
                }
                's' -> {
                    val size = parser.readNumber() ?: 8
                    addSize(size)
                    throw IllegalArgumentException("variable-length format")
                }
                'z' -> throw IllegalArgumentException("variable-length format")
                'x' -> addSize(1)
                'X' -> {
                    val (nextSize, alreadyAdvanced) = parser.peekNextFormatSize()
                    if (parser.maxAlign != null) {
                        val alignSize = minOf(nextSize, parser.maxAlign!!)
                        val padding = BinaryOperations.calculatePadding(total, alignSize)
                        addSize(padding)
                    }
                    parser.consumeNextFormatChar(alreadyAdvanced)
                }
                'p' -> throw IllegalArgumentException("unsupported format p")
                else -> throw IllegalArgumentException("invalid format option '$c'")
            }
        }

        return total
    }

    /**
     * Pack values according to format string into a binary string.
     */
    fun packValues(
        format: String,
        values: List<LuaValue<*>>,
    ): String {
        val result = StringBuilder()
        var valueIndex = 0
        val parser = FormatParser(format)

        // Auto-align current position to given size (when ! is active)
        fun autoAlign(size: Int) {
            val padding = BinaryOperations.autoAlign(result.length, size, parser.maxAlign)
            repeat(padding) { result.append('\u0000') }
        }

        // Helper to pack variable-size integers ('i' and 'I')
        fun packVariableInteger(signed: Boolean) {
            val size = parser.readNumber() ?: 4
            BinaryOperations.validateIntegerSize(size)
            BinaryOperations.validatePowerOf2(size, parser.maxAlign)
            val actualSize = if (parser.maxAlign != null) minOf(size, parser.maxAlign!!) else size
            autoAlign(actualSize)
            NumericPackHelpers.packInteger(result, values, valueIndex++, actualSize, parser.littleEndian, signed)
        }

        // CPD-OFF: pack loop structure (similar to unpack but with validation)
        while (parser.hasMore()) {
            parser.skipWhitespace()
            if (!parser.hasMore()) break

            val c = parser.current()
            if (FormatParserHelpers.processModifier(parser, c)) {
                continue
            }

            if (c == '!') {
                FormatParserHelpers.processAlignment(parser)
                val align = parser.maxAlign ?: 8
                BinaryOperations.validateIntegerSize(align)
                // CPD-ON
                continue
            }

            parser.advance()
            when (c) {
                'b', 'B' -> {
                    val v = ((values.getOrNull(valueIndex++) as? LuaNumber)?.toDouble()?.toLong() ?: 0L).toByte()
                    result.append(v.toInt().and(0xFF).toChar())
                }
                'h' -> {
                    autoAlign(2)
                    NumericPackHelpers.packShort(result, values, valueIndex++, parser.littleEndian, signed = true)
                }
                'H' -> {
                    autoAlign(2)
                    NumericPackHelpers.packShort(result, values, valueIndex++, parser.littleEndian, signed = false)
                }
                'l' -> {
                    autoAlign(4)
                    NumericPackHelpers.packInt(result, values, valueIndex++, parser.littleEndian, signed = true)
                }
                'L' -> {
                    autoAlign(4)
                    NumericPackHelpers.packInt(result, values, valueIndex++, parser.littleEndian, signed = false)
                }
                'j' -> {
                    autoAlign(8)
                    NumericPackHelpers.packLong(result, values, valueIndex++, parser.littleEndian, signed = true)
                }
                'J' -> {
                    autoAlign(8)
                    NumericPackHelpers.packLong(result, values, valueIndex++, parser.littleEndian, signed = false)
                }
                'i' -> packVariableInteger(signed = true)
                'I' -> packVariableInteger(signed = false)
                'f' -> {
                    autoAlign(4)
                    NumericPackHelpers.packFloat(result, values, valueIndex++, parser.littleEndian)
                }
                'd' -> {
                    autoAlign(8)
                    NumericPackHelpers.packDouble(result, values, valueIndex++, parser.littleEndian)
                }
                'n' -> {
                    autoAlign(8)
                    NumericPackHelpers.packDouble(result, values, valueIndex++, parser.littleEndian)
                }
                'T' -> {
                    autoAlign(8)
                    NumericPackHelpers.packLong(result, values, valueIndex++, parser.littleEndian, signed = false)
                }
                'c' -> {
                    val size = parser.readNumber() ?: throw IllegalArgumentException("missing size for 'c'")
                    val str = ArgumentHelpers.coerceToString(values.getOrNull(valueIndex++))
                    if (str.length > size) {
                        throw RuntimeException("string longer than given size")
                    }
                    for (j in 0 until size) {
                        result.append(str.getOrNull(j) ?: '\u0000')
                    }
                }
                's' -> {
                    val size = parser.readNumber() ?: 8
                    BinaryOperations.validateIntegerSize(size)
                    val str = ArgumentHelpers.coerceToString(values.getOrNull(valueIndex++))
                    val maxLength =
                        when {
                            size >= 8 -> Long.MAX_VALUE
                            else -> (1L shl (size * 8)) - 1
                        }
                    if (str.length.toLong() > maxLength) {
                        throw RuntimeException("does not fit")
                    }
                    NumericPackHelpers.packInteger(
                        result,
                        listOf(LuaNumber.of(str.length.toDouble())),
                        0,
                        size,
                        parser.littleEndian,
                        signed = false,
                    )
                    result.append(str)
                }
                'z' -> {
                    val str = ArgumentHelpers.coerceToString(values.getOrNull(valueIndex++))
                    if (str.contains('\u0000')) {
                        throw RuntimeException("string contains zeros")
                    }
                    result.append(str)
                    result.append('\u0000')
                }
                'x' -> result.append('\u0000')
                'X' -> {
                    val (nextSize, alreadyAdvanced) = parser.peekNextFormatSize()
                    if (parser.maxAlign != null) {
                        val alignSize = minOf(nextSize, parser.maxAlign!!)
                        val padding = BinaryOperations.calculatePadding(result.length, alignSize)
                        repeat(padding) { result.append('\u0000') }
                    }
                    parser.consumeNextFormatChar(alreadyAdvanced)
                }
                else -> throw RuntimeException("invalid format option '$c'")
            }
        }

        return result.toString()
    }
}
