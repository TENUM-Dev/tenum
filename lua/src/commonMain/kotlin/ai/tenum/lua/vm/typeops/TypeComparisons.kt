package ai.tenum.lua.vm.typeops

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue

/**
 * Type comparison operations for Lua values.
 *
 * Implements Lua 5.4 equality and relational comparison semantics.
 */
internal class TypeComparisons {
    /**
     * Lua equality comparison (==).
     *
     * Rules:
     * - nil == nil
     * - boolean == boolean (by value)
     * - number == number (with integer/float conversion rules)
     * - string == string (by content)
     * - References are equal if they point to same object
     * - Different types are never equal
     *
     * Integer/float equality follows Lua 5.4 semantics:
     * - Both must represent the exact same mathematical value
     * - Floats outside the exact integer range [-2^53, 2^53] may not equal their integer representation
     */
    fun luaEquals(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean =
        when {
            left is LuaNil && right is LuaNil -> true
            left is LuaBoolean && right is LuaBoolean -> left.value == right.value
            left is LuaNumber && right is LuaNumber -> compareNumbers(left, right)
            left is LuaString && right is LuaString -> left.value == right.value
            left === right -> true
            else -> false
        }

    /**
     * Lua less-than comparison (<).
     *
     * Returns:
     * - true/false for comparable types (numbers, strings)
     * - null if types cannot be compared (triggers metamethod lookup)
     */
    fun luaLessThan(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean? {
        return when {
            left is LuaLong && right is LuaLong -> left.value < right.value
            left is LuaDouble && right is LuaDouble -> left.value < right.value
            left is LuaLong && right is LuaDouble -> compareMixedLessThan(left.value, right.value)
            left is LuaDouble && right is LuaLong -> {
                // Double < Long
                // Negate the comparison: Double < Long is equivalent to !(Long <= Double)
                // Which is: !(Long < Double || Long == Double)
                // = !compareMixedLessThan(Long, Double) && !(Long == Double mathematically)

                // First check if they're mathematically equal
                val longAsDouble = right.value.toDouble()
                if (longAsDouble == left.value) {
                    // They're equal as doubles, but might not be mathematically equal
                    // If Long converted exactly, they're equal
                    // If Long rounded, it's either greater or less than the double

                    // Check if the Long is within exact representable range
                    val maxExact = 9007199254740992L // 2^53
                    val minExact = -9007199254740992L

                    if (right.value in minExact..maxExact) {
                        // Exact conversion - they're mathematically equal
                        return false // Double not less than Long
                    }

                    // Long is outside exact range - rounding occurred
                    // When longAsDouble == left.value, they round to the same Double value
                    // The question is: which one is mathematically larger?

                    // Special case: if Long is at MAX_VALUE, it represents 2^63-1,
                    // but converts to 2^63 as Double. Any Double equal to 2^63 is
                    // mathematically >= Long.MAX_VALUE
                    if (right.value == Long.MAX_VALUE) {
                        // Long is 2^63-1, longAsDouble is 2^63
                        // If left.value == 2^63, then Double >= Long, so Double not < Long
                        return false
                    }

                    // Similarly for MIN_VALUE
                    if (right.value == Long.MIN_VALUE) {
                        // Long is -2^63 (exactly representable), so Double < Long only if
                        // the Double is actually less than -2^63
                        // But they're equal as doubles, so Double == -2^63, not less
                        return false
                    }

                    // Long is not at boundaries - check exact representation
                    val doubleAsLong = left.value.toLong()
                    if (doubleAsLong.toDouble() == left.value) {
                        return doubleAsLong < right.value
                    }

                    // Neither converts exactly - they rounded to the same double value
                    // The Long must be greater than the Double if it rounded down to it
                    // (for positive numbers, rounding down means Long > Double)
                    doubleAsLong != right.value
                } else {
                    // Not equal as doubles
                    // Check if Long rounded when converted to Double
                    val maxExact = 9007199254740992L // 2^53
                    val minExact = -9007199254740992L

                    if (right.value in minExact..maxExact) {
                        // Long is exactly representable - safe to compare as doubles
                        return left.value < longAsDouble
                    }

                    // Long is outside exact range - may have rounded
                    // Check if the Double is exactly representable as Long
                    val doubleAsLong = left.value.toLong()
                    if (doubleAsLong.toDouble() == left.value) {
                        // Double is exactly representable - compare as longs
                        return doubleAsLong < right.value
                    }

                    // Both have precision issues - compare as doubles
                    // This handles cases where both rounded to different values
                    left.value < longAsDouble
                }
            }
            left is LuaString && right is LuaString -> left.value.compareTo(right.value) < 0
            else -> null
        }
    }

    /**
     * Lua less-or-equal comparison (<=).
     *
     * Returns:
     * - true/false for comparable types (numbers, strings)
     * - null if types cannot be compared (triggers metamethod lookup)
     */
    fun luaLessOrEqual(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean? {
        return when {
            left is LuaLong && right is LuaLong -> left.value <= right.value
            left is LuaDouble && right is LuaDouble -> left.value <= right.value
            left is LuaLong && right is LuaDouble -> {
                // Long <= Double is  (Long < Double) || (Long == Double)
                val result = compareMixedLessThan(left.value, right.value)
                result || left.value.toDouble() == right.value
            }
            left is LuaDouble && right is LuaLong -> {
                // Double <= Long is (Double < Long) || (Double == Long)
                // Reuse the Double < Long logic
                val longAsDouble = right.value.toDouble()
                if (longAsDouble == left.value) {
                    // Equal as doubles - check if mathematically equal or if Double < Long
                    val maxExact = 9007199254740992L // 2^53
                    val minExact = -9007199254740992L

                    if (right.value in minExact..maxExact) {
                        // Exact conversion - they're mathematically equal
                        return true // Double <= Long when equal
                    }

                    // Long is outside exact range - rounding occurred
                    // When longAsDouble == left.value, they round to the same Double value

                    // Special case: if Long is at MAX_VALUE, it represents 2^63-1,
                    // but converts to 2^63 as Double. Any Double equal to 2^63 is
                    // mathematically >= Long.MAX_VALUE, so Double not <= Long
                    if (right.value == Long.MAX_VALUE) {
                        // Long is 2^63-1, Double is 2^63 (or close), so Double > Long
                        return false
                    }

                    // For MIN_VALUE, -2^63 is exactly representable as Double
                    // If they're equal as doubles, they're mathematically equal
                    if (right.value == Long.MIN_VALUE) {
                        return true // Double <= Long when equal
                    }

                    // Long is not at boundaries - check exact representation
                    val doubleAsLong = left.value.toLong()
                    if (doubleAsLong.toDouble() == left.value) {
                        return doubleAsLong <= right.value
                    }

                    // Neither converts exactly - Double < Long mathematically
                    true // They're equal as doubles but Double < Long mathematically
                } else {
                    // Not equal as doubles
                    // Check if Long rounded when converted to Double
                    val maxExact = 9007199254740992L // 2^53
                    val minExact = -9007199254740992L

                    if (right.value in minExact..maxExact) {
                        // Long is exactly representable - safe to compare as doubles
                        return left.value <= longAsDouble
                    }

                    // Long is outside exact range - may have rounded
                    // Check if the Double is exactly representable as Long
                    val doubleAsLong = left.value.toLong()
                    if (doubleAsLong.toDouble() == left.value) {
                        // Double is exactly representable - compare as longs
                        return doubleAsLong <= right.value
                    }

                    // Both have precision issues - compare as doubles
                    // This handles cases where both rounded to different values
                    left.value <= longAsDouble
                }
            }
            left is LuaString && right is LuaString -> left.value.compareTo(right.value) <= 0
            else -> null
        }
    }

    /**
     * Compare two Lua numbers for equality.
     *
     * Handles integer/float conversions following Lua 5.4 rules:
     * - Same type: direct comparison
     * - Mixed int/float: complex conversion with precision checks
     */
    private fun compareNumbers(
        left: LuaNumber,
        right: LuaNumber,
    ): Boolean =
        when {
            left is LuaLong && right is LuaLong -> left.value == right.value
            left is LuaDouble && right is LuaDouble -> left.value == right.value
            left is LuaLong && right is LuaDouble -> compareMixedNumbers(left.value, right.value)
            left is LuaDouble && right is LuaLong -> compareMixedNumbers(right.value, left.value)
            else -> false
        }

    /**
     * Compare integer and float for equality.
     *
     * Integer and float are equal only if:
     * 1. Float has no fractional part
     * 2. They represent the same mathematical value
     * 3. No precision loss occurs in conversion
     *
     * Doubles can exactly represent integers in [-2^53, 2^53].
     * Outside this range, not all consecutive integers are representable.
     */
    private fun compareMixedNumbers(
        intVal: Long,
        floatVal: Double,
    ): Boolean {
        // Float must be an integer value (no fractional part)
        if (floatVal != kotlin.math.floor(floatVal)) {
            return false
        }

        // Convert integer to double
        val intAsFloat = intVal.toDouble()

        // If they don't match as doubles, not equal
        if (intAsFloat != floatVal) {
            return false
        }

        // They match as doubles. But did conversion lose precision?
        // Doubles can exactly represent integers in range [-(2^53), 2^53]
        val maxExact = 9007199254740992L // 2^53
        val minExact = -9007199254740992L

        if (intVal > maxExact || intVal < minExact) {
            // Beyond exact range - check if round-trip preserves value
            val roundTrip = intAsFloat.toLong()
            if (roundTrip != intVal) {
                return false
            }

            // Round-trip matched. But was it due to saturation?
            // toLong() saturates to Long.MAX_VALUE or Long.MIN_VALUE
            if (intVal == Long.MAX_VALUE) {
                // Long.MAX_VALUE = 2^63 - 1, but as double rounds to 2^63
                // So they're not mathematically equal
                return false
            }

            // Long.MIN_VALUE = -2^63 is exactly representable as double
            // Other values in range passed round-trip test
            return true
        }

        // Within exact range
        return true
    }

    /**
     * Compare integer < float, handling precision loss correctly.
     *
     * Lua 5.4 spec: "If both arguments are numbers, then they are compared according
     * to their mathematical values, regardless of their subtypes."
     *
     * When precision loss occurs (integer outside [-2^53, 2^53]), we must ensure
     * the comparison reflects mathematical values, not rounded values.
     */
    private fun compareMixedLessThan(
        intVal: Long,
        floatVal: Double,
    ): Boolean {
        // Handle special float values
        if (floatVal.isNaN()) {
            return false
        }
        if (floatVal.isInfinite()) {
            return floatVal > 0 // int < +inf is true, int < -inf is false
        }

        // If the integer is within the exact representable range, safe to convert
        val maxExact = 9007199254740992L // 2^53
        val minExact = -9007199254740992L

        if (intVal in minExact..maxExact) {
            // Safe to convert to double - no precision loss
            return intVal.toDouble() < floatVal
        }

        // Integer is outside exact range - precision loss may occur
        // Strategy: convert integer to double and compare, but handle equality specially
        val intAsFloat = intVal.toDouble()

        when {
            intAsFloat < floatVal -> return true
            intAsFloat > floatVal -> return false
            else -> {
                // intAsFloat == floatVal
                // Due to rounding, intVal might not exactly equal the mathematical value of floatVal
                // We need to determine the mathematical relationship

                // Key insight: when intVal.toDouble() == floatVal but they differ mathematically,
                // the integer was rounded. We need to detect the direction of rounding.
                //
                // For toDouble() with positive values:
                // - Rounds to nearest representable double
                // - If result > original, the integer was rounded up
                // - If result < original, impossible (would have rounded to nearest)
                //
                // When intAsFloat > intVal mathematically, then intVal < floatVal

                // Try to detect rounding by checking if floatVal can be exactly represented as Long
                // If not, then we know rounding occurred

                // First check: can floatVal fit in a Long at all?
                // Note: Long.MAX_VALUE.toDouble() equals 2^63, which is > Long.MAX_VALUE
                // and Long.MIN_VALUE.toDouble() equals -2^63, which equals Long.MIN_VALUE

                if (floatVal >= Long.MIN_VALUE.toDouble() && floatVal <= Long.MAX_VALUE.toDouble()) {
                    // floatVal might fit in Long range, but check for edge cases

                    // Special case: floatVal == Long.MAX_VALUE.toDouble() (which is 2^63)
                    // This value is > Long.MAX_VALUE, so any Long < floatVal
                    if (floatVal == Long.MAX_VALUE.toDouble() && floatVal != Long.MIN_VALUE.toDouble()) {
                        // floatVal is 2^63, which is greater than any representable Long
                        // (note: -2^63 is representable as Long.MIN_VALUE, but +2^63 is not)
                        return true
                    }

                    // Try converting floatVal to Long
                    val floatAsInt = floatVal.toLong()

                    // Check if conversion was exact
                    if (floatAsInt.toDouble() == floatVal) {
                        // Exact conversion - compare as longs
                        return intVal < floatAsInt
                    }

                    // Float doesn't convert exactly to a long
                    // Since intAsFloat == floatVal, the integer was rounded
                    // Check if they differ to detect rounding
                    return intVal != floatAsInt
                }

                // floatVal is outside Long range
                return floatVal > 0 // positive infinity or very large float
            }
        }
    }
}
