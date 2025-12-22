package ai.tenum.lua.stdlib.string.formatters

/**
 * IEEE 754 double-precision floating-point bit representation.
 */
data class DoubleBits(
    val negative: Boolean,
    val exponent: Int,
    val mantissa: Long,
)

/**
 * Utility functions for working with IEEE 754 double-precision floating-point representation.
 */
object DoubleUtils {
    /**
     * Extract IEEE 754 bit components from a Double value.
     */
    fun extractBits(value: Double): DoubleBits {
        val bits = value.toRawBits()
        val negative = bits and (1L shl 63) != 0L
        val exponent = ((bits shr 52) and 0x7FF).toInt() - 1023
        val mantissa = bits and 0xFFFFFFFFFFFFFL
        return DoubleBits(negative, exponent, mantissa)
    }
}
