package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNumber

/**
 * Shared helpers for unpacking numeric values from binary format.
 */
internal object NumericUnpackHelpers {
    fun unpackShort(
        readByte: () -> Int,
        littleEndian: Boolean,
        signed: Boolean,
    ): LuaNumber {
        val b0 = readByte()
        val b1 = readByte()
        val v =
            if (littleEndian) {
                b0 or (b1 shl 8)
            } else {
                (b0 shl 8) or b1
            }
        val result =
            if (signed) {
                v.toShort().toLong()
            } else {
                v.toLong().and(0xFFFF)
            }
        return LuaNumber.Companion.of(result.toDouble())
    }

    fun unpackInt(
        readByte: () -> Int,
        littleEndian: Boolean,
        signed: Boolean,
    ): LuaNumber {
        val b0 = readByte()
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        val v =
            if (littleEndian) {
                b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            } else {
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        val result =
            if (signed) {
                v.toLong()
            } else {
                v.toLong().and(0xFFFFFFFF)
            }
        return LuaNumber.Companion.of(result.toDouble())
    }

    fun unpackLong(
        readByte: () -> Int,
        littleEndian: Boolean,
        signed: Boolean,
    ): LuaNumber {
        val bytes = IntArray(8) { readByte() }
        var v = 0L
        if (littleEndian) {
            for (j in 7 downTo 0) {
                v = (v shl 8) or bytes[j].toLong()
            }
        } else {
            for (j in 0..7) {
                v = (v shl 8) or bytes[j].toLong()
            }
        }
        return LuaLong(v)
    }

    fun unpackInteger(
        readByte: () -> Int,
        size: Int,
        littleEndian: Boolean,
        signed: Boolean,
    ): LuaNumber {
        val bytes = IntArray(size) { readByte() }
        BinaryOperations.validateIntegerOverflow(bytes, size, littleEndian, signed)
        val v = BinaryOperations.bytesToLong(bytes, size, littleEndian, signed)
        return LuaLong(v)
    }

    fun unpackFloat(
        readByte: () -> Int,
        littleEndian: Boolean,
    ): LuaNumber {
        val bytes = IntArray(4) { readByte() }
        val bits =
            if (littleEndian) {
                bytes[0] or (bytes[1] shl 8) or (bytes[2] shl 16) or (bytes[3] shl 24)
            } else {
                (bytes[0] shl 24) or (bytes[1] shl 16) or (bytes[2] shl 8) or bytes[3]
            }
        val floatVal = Float.fromBits(bits)
        return LuaNumber.Companion.of(floatVal.toDouble())
    }

    fun unpackDouble(
        readByte: () -> Int,
        littleEndian: Boolean,
    ): LuaNumber {
        val bytes = LongArray(8) { readByte().toLong() }
        val bits =
            if (littleEndian) {
                bytes[0] or (bytes[1] shl 8) or (bytes[2] shl 16) or (bytes[3] shl 24) or
                    (bytes[4] shl 32) or (bytes[5] shl 40) or (bytes[6] shl 48) or (bytes[7] shl 56)
            } else {
                (bytes[0] shl 56) or (bytes[1] shl 48) or (bytes[2] shl 40) or (bytes[3] shl 32) or
                    (bytes[4] shl 24) or (bytes[5] shl 16) or (bytes[6] shl 8) or bytes[7]
            }
        val doubleVal = Double.fromBits(bits)
        return LuaNumber.Companion.of(doubleVal)
    }
}
