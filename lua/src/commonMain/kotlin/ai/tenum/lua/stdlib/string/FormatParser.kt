package ai.tenum.lua.stdlib.string

import ai.tenum.lua.stdlib.string.StringLib

/**
 * Shared format parser state that handles common operations across packsize, pack, and unpack.
 */
class FormatParser(
    val format: String,
) {
    var i = 0
    var littleEndian = true
    var maxAlign: Int? = null

    fun readNumber(): Int? {
        if (i >= format.length) return null
        val start = i
        while (i < format.length && format[i].isDigit()) i++
        if (i > start) {
            val numStr = format.substring(start, i)
            return try {
                numStr.toInt()
            } catch (e: NumberFormatException) {
                throw RuntimeException("invalid format")
            }
        }
        return null
    }

    fun skipWhitespace() {
        while (i < format.length && format[i].isWhitespace()) i++
    }

    fun hasMore(): Boolean = i < format.length

    fun current(): Char = format[i]

    fun advance() {
        i++
    }

    fun peekNextFormatSize(): Pair<Int, Boolean> {
        val j = i
        if (j >= format.length) {
            throw RuntimeException("invalid next option for option 'X'")
        }
        val nextChar = format[j]
        return when (nextChar) {
            'b', 'B', 'x' -> Pair(1, false)
            'h', 'H' -> Pair(2, false)
            'l', 'L' -> Pair(4, false)
            'i', 'I' -> {
                var k = j + 1
                while (k < format.length && format[k].isWhitespace()) k++
                val start = k
                while (k < format.length && format[k].isDigit()) k++
                val size = if (k > start) format.substring(start, k).toInt() else 4
                BinaryOperations.validateIntegerSize(size)
                i = k
                val actualSize = if (maxAlign != null) minOf(size, maxAlign!!) else size
                Pair(actualSize, true) // true means we already advanced i
            }
            'j', 'J', 'T', 'd', 'n' -> Pair(8, false)
            'f' -> Pair(4, false)
            else -> throw RuntimeException("invalid next option for option 'X'")
        }
    }

    fun consumeNextFormatChar(alreadyAdvanced: Boolean) {
        if (alreadyAdvanced) return // i was already advanced by peekNextFormatSize
        if (i >= format.length) return
        i++ // Consume the next format character
    }
}
