package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue

/**
 * Shared helpers for packing numeric values to binary format.
 */
internal object NumericPackHelpers {
    /**
     * Pack Int bits to bytes (4 bytes)
     */
    private inline fun packIntBitsToBytes(
        bits: Int,
        littleEndian: Boolean,
    ): ByteArray {
        // CPD-OFF: bit packing helpers (intentional structural similarity for different sizes)
        val bytes = ByteArray(4)
        if (littleEndian) {
            bytes[0] = bits.toByte()
            bytes[1] = (bits shr 8).toByte()
            bytes[2] = (bits shr 16).toByte()
            bytes[3] = (bits shr 24).toByte()
        } else {
            bytes[0] = (bits shr 24).toByte()
            bytes[1] = (bits shr 16).toByte()
            bytes[2] = (bits shr 8).toByte()
            bytes[3] = bits.toByte()
        }
        return bytes
        // CPD-ON
    }

    /**
     * Pack Long bits to bytes (8 bytes)
     */
    private inline fun packLongBitsToBytes(
        bits: Long,
        littleEndian: Boolean,
    ): ByteArray {
        val bytes = ByteArray(8)
        if (littleEndian) {
            bytes[0] = bits.toByte()
            bytes[1] = (bits shr 8).toByte()
            bytes[2] = (bits shr 16).toByte()
            bytes[3] = (bits shr 24).toByte()
            bytes[4] = (bits shr 32).toByte()
            bytes[5] = (bits shr 40).toByte()
            bytes[6] = (bits shr 48).toByte()
            bytes[7] = (bits shr 56).toByte()
        } else {
            bytes[0] = (bits shr 56).toByte()
            bytes[1] = (bits shr 48).toByte()
            bytes[2] = (bits shr 40).toByte()
            bytes[3] = (bits shr 32).toByte()
            bytes[4] = (bits shr 24).toByte()
            bytes[5] = (bits shr 16).toByte()
            bytes[6] = (bits shr 8).toByte()
            bytes[7] = bits.toByte()
        }
        return bytes
    }

    // CPD-OFF: pack function signatures (similar structure for different integer sizes)
    fun packShort(
        result: StringBuilder,
        values: List<LuaValue<*>>,
        index: Int,
        littleEndian: Boolean,
        signed: Boolean,
    ) {
        // CPD-ON
        val v = ((values.getOrNull(index) as? LuaNumber)?.toDouble()?.toLong() ?: 0L).toShort()
        val bytes = ByteArray(2)
        if (littleEndian) {
            bytes[0] = v.toByte()
            bytes[1] = (v.toInt() shr 8).toByte()
        } else {
            bytes[0] = (v.toInt() shr 8).toByte()
            bytes[1] = v.toByte()
        }
        BinaryOperations.packBytes(result, bytes)
    }

    fun packInt(
        result: StringBuilder,
        values: List<LuaValue<*>>,
        index: Int,
        littleEndian: Boolean,
        signed: Boolean,
    ) {
        val v = ((values.getOrNull(index) as? LuaNumber)?.toDouble()?.toLong() ?: 0L).toInt()
        val bytes = packIntBitsToBytes(v, littleEndian)
        BinaryOperations.packBytes(result, bytes)
    }

    fun packLong(
        result: StringBuilder,
        values: List<LuaValue<*>>,
        index: Int,
        littleEndian: Boolean,
        signed: Boolean,
    ) {
        val v =
            when (val num = values.getOrNull(index)) {
                is LuaLong -> num.value
                is LuaDouble -> num.value.toLong()
                else -> 0L
            }
        val bytes = ByteArray(8)
        if (littleEndian) {
            for (j in 0..7) {
                bytes[j] = (v shr (j * 8)).toByte()
            }
        } else {
            for (j in 0..7) {
                bytes[j] = (v shr ((7 - j) * 8)).toByte()
            }
        }
        BinaryOperations.packBytes(result, bytes)
    }

    fun packInteger(
        result: StringBuilder,
        values: List<LuaValue<*>>,
        index: Int,
        size: Int,
        littleEndian: Boolean,
        signed: Boolean,
    ) {
        val v =
            when (val num = values.getOrNull(index)) {
                is LuaLong -> num.value
                is LuaDouble -> num.value.toLong()
                else -> 0L
            }
        BinaryOperations.validateIntegerRange(v, size, signed)
        val bytes = BinaryOperations.longToBytes(v, size, littleEndian, signed)
        BinaryOperations.packBytes(result, bytes)
    }

    fun packFloat(
        result: StringBuilder,
        values: List<LuaValue<*>>,
        index: Int,
        littleEndian: Boolean,
    ) {
        val v = (values.getOrNull(index) as? LuaNumber)?.toDouble()?.toFloat() ?: 0.0f
        val bits = v.toRawBits()
        val bytes = packIntBitsToBytes(bits, littleEndian)
        BinaryOperations.packBytes(result, bytes)
    }

    fun packDouble(
        result: StringBuilder,
        values: List<LuaValue<*>>,
        index: Int,
        littleEndian: Boolean,
    ) {
        val v = (values.getOrNull(index) as? LuaNumber)?.toDouble() ?: 0.0
        val bits = v.toRawBits()
        val bytes = packLongBitsToBytes(bits, littleEndian)
        BinaryOperations.packBytes(result, bytes)
    }
}
