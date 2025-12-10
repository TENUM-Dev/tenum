package ai.tenum.lua.stdlib.string

/**
 * Shared binary operations and validation utilities for pack/unpack operations.
 */
object BinaryOperations {
    /**
     * Validate integer size is within limits [1,16]
     */
    fun validateIntegerSize(size: Int) {
        if (size < 1 || size > 16) {
            throw RuntimeException("integral size ($size) out of limits [1,16]")
        }
    }

    /**
     * Validate integer size is a power of 2 when alignment is active
     */
    fun validatePowerOf2(
        size: Int,
        maxAlign: Int?,
    ) {
        if (maxAlign != null && size > 0) {
            val isPowerOf2 = (size and (size - 1)) == 0
            if (!isPowerOf2) {
                throw RuntimeException("format asks for alignment not power of 2")
            }
        }
    }

    /**
     * Validate integer bytes for overflow when size > 8 bytes
     */
    fun validateIntegerOverflow(
        bytes: IntArray,
        size: Int,
        littleEndian: Boolean,
        signed: Boolean,
    ) {
        if (size <= 8) return

        val range = if (littleEndian) (8 until size) else (0 until (size - 8))
        for (j in range) {
            validateByteInRange(bytes[j], signed, size)
        }
    }

    /**
     * Validate that a byte value is within acceptable range for overflow check
     */
    private fun validateByteInRange(
        byte: Int,
        signed: Boolean,
        size: Int,
    ) {
        if (signed) {
            if (byte != 0 && byte != 0xFF) {
                throw RuntimeException("$size-byte integer does not fit into Lua Integer")
            }
        } else {
            if (byte != 0) {
                throw RuntimeException("$size-byte integer does not fit into Lua Integer")
            }
        }
    }

    /**
     * Calculate padding needed for alignment
     */
    fun calculatePadding(
        currentPos: Int,
        alignSize: Int,
    ): Int = (alignSize - (currentPos % alignSize)) % alignSize

    /**
     * Apply automatic alignment and return padding amount
     */
    fun autoAlign(
        currentPos: Int,
        size: Int,
        maxAlign: Int?,
    ): Int {
        maxAlign?.let { align ->
            val alignSize = minOf(size, align)
            return calculatePadding(currentPos, alignSize)
        }
        return 0
    }

    /**
     * Pack bytes into a result builder with endianness
     */
    fun packBytes(
        result: StringBuilder,
        bytes: ByteArray,
    ) {
        for (b in bytes) {
            result.append(b.toInt().and(0xFF).toChar())
        }
    }

    /**
     * Convert Long to bytes with specified size and endianness
     */
    fun longToBytes(
        value: Long,
        size: Int,
        littleEndian: Boolean,
        signed: Boolean = false,
    ): ByteArray {
        val bytes = ByteArray(size)

        if (littleEndian) {
            // Little-endian: low byte first
            for (j in 0 until minOf(size, 8)) {
                bytes[j] = (value shr (j * 8)).toByte()
            }
            // Pad remaining bytes with zeros (or sign extension for negative)
            for (j in 8 until size) {
                bytes[j] = if (signed && value < 0) 0xFF.toByte() else 0
            }
        } else {
            // Big-endian: high byte first
            if (size <= 8) {
                // Normal case: extract bytes from value
                for (j in 0 until size) {
                    bytes[j] = (value shr ((size - 1 - j) * 8)).toByte()
                }
            } else {
                // Overflow case: pad high bytes, then put all 8 bytes of value
                for (j in 0 until (size - 8)) {
                    bytes[j] = if (signed && value < 0) 0xFF.toByte() else 0
                }
                // Then put the 8 bytes of the value
                for (j in 0 until 8) {
                    bytes[size - 8 + j] = (value shr ((7 - j) * 8)).toByte()
                }
            }
        }

        return bytes
    }

    /**
     * Convert bytes to Long with specified size and endianness
     */
    fun bytesToLong(
        bytes: IntArray,
        size: Int,
        littleEndian: Boolean,
        signed: Boolean,
    ): Long {
        var v = 0L

        // Extract value from first/last 8 bytes
        if (littleEndian) {
            // Read from low to high, but cap at 8 bytes
            for (j in minOf(size, 8) - 1 downTo 0) {
                v = (v shl 8) or bytes[j].toLong()
            }
        } else {
            // Read from high to low, but skip padding bytes if size > 8
            val startIndex = maxOf(0, size - 8)
            for (j in startIndex until size) {
                v = (v shl 8) or bytes[j].toLong()
            }
        }

        // Sign extend if signed and size < 8
        if (signed && size < 8) {
            val signBit = 1L shl (size * 8 - 1)
            if (v and signBit != 0L) {
                v = v or ((-1L) shl (size * 8))
            }
        }

        return v
    }

    /**
     * Validate range for signed/unsigned integers of given size
     */
    fun validateIntegerRange(
        value: Long,
        size: Int,
        signed: Boolean,
    ) {
        // Validate unsigned overflow: unsigned formats cannot accept negative values
        if (!signed && value < 0) {
            throw RuntimeException("unsigned overflow")
        }

        // Validate range for the given size
        if (size < 8) {
            if (signed) {
                val max = (1L shl (size * 8 - 1)) - 1
                val min = -(1L shl (size * 8 - 1))
                if (value > max || value < min) {
                    throw RuntimeException("overflow")
                }
            } else {
                val max = (1L shl (size * 8)) - 1
                if (value > max) {
                    throw RuntimeException("unsigned overflow")
                }
            }
        }
    }
}
