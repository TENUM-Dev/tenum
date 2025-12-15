package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext
import kotlinx.datetime.Clock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.truncate

/**
 * Lua 5.4 math library implementation.
 *
 * Provides mathematical functions including:
 * - Basic operations (abs, ceil, floor, max, min, modf, fmod)
 * - Trigonometric functions (sin, cos, tan, asin, acos, atan)
 * - Exponential and logarithmic (exp, log, sqrt)
 * - Angle conversion (deg, rad)
 * - Random numbers (random, randomseed)
 * - Type checking (type, tointeger, ult)
 * - Constants (pi, huge, mininteger, maxinteger)
 */
class MathLib : LuaLibrary {
    override val name: String = "math"

    // Lua 5.4 uses a 64-bit Linear Congruential Generator (LCG)
    // LCG formula: next_state = (state * multiplier + increment) mod 2^64
    // These constants are from Knuth's MMIX (see TAOCP Vol 2, 3rd Ed, p. 106)
    private val lcgMultiplier: ULong = 0x5851f42d4c957f2dUL
    private val lcgIncrement: ULong = 1UL

    // Initialize with a seed based on system time (like Lua 5.4 does)
    private val initialSeed = Clock.System.now().toEpochMilliseconds()
    private var randomState: ULong = initialSeed.toULong()

    // Keep last seed parts so math.randomseed() with no args can return them
    // Initialize with the actual seed used
    private var seedPartHigh: Long = initialSeed
    private var seedPartLow: Long = 0L

    override fun register(context: LuaLibraryContext) {
        val lib = LuaTable()

        // Constants
        lib[LuaString("pi")] = LuaNumber.of(PI)
        lib[LuaString("huge")] = LuaNumber.of(Double.POSITIVE_INFINITY)
        lib[LuaString("mininteger")] = LuaNumber.of(Long.MIN_VALUE)
        lib[LuaString("maxinteger")] = LuaNumber.of(Long.MAX_VALUE)

        // Basic functions
        lib[LuaString("abs")] = LuaNativeFunction { args -> mathAbs(args) }
        lib[LuaString("ceil")] = LuaNativeFunction { args -> mathCeil(args) }
        lib[LuaString("floor")] = LuaNativeFunction { args -> mathFloor(args) }
        lib[LuaString("max")] = LuaNativeFunction { args -> mathMax(args) }
        lib[LuaString("min")] = LuaNativeFunction { args -> mathMin(args) }
        lib[LuaString("modf")] = LuaNativeFunction { args -> mathModf(args) }
        lib[LuaString("fmod")] = LuaNativeFunction { args -> mathFmod(args) }

        // Power and roots
        lib[LuaString("sqrt")] = LuaNativeFunction { args -> mathSqrt(args) }
        lib[LuaString("exp")] = LuaNativeFunction { args -> mathExp(args) }
        lib[LuaString("log")] = LuaNativeFunction { args -> mathLog(args) }

        // Trigonometric functions
        lib[LuaString("sin")] = LuaNativeFunction { args -> mathSin(args) }
        lib[LuaString("cos")] = LuaNativeFunction { args -> mathCos(args) }
        lib[LuaString("tan")] = LuaNativeFunction { args -> mathTan(args) }
        lib[LuaString("asin")] = LuaNativeFunction { args -> mathAsin(args) }
        lib[LuaString("acos")] = LuaNativeFunction { args -> mathAcos(args) }
        lib[LuaString("atan")] = LuaNativeFunction { args -> mathAtan(args) }

        // Angle conversion
        lib[LuaString("deg")] = LuaNativeFunction { args -> mathDeg(args) }
        lib[LuaString("rad")] = LuaNativeFunction { args -> mathRad(args) }

        // Type functions
        lib[LuaString("type")] = LuaNativeFunction { args -> mathType(args) }
        lib[LuaString("tointeger")] = LuaNativeFunction { args -> mathTointeger(args) }
        lib[LuaString("ult")] = LuaNativeFunction { args -> mathUlt(args) }

        // Random functions
        lib[LuaString("random")] = LuaNativeFunction { args -> mathRandom(args) }
        lib[LuaString("randomseed")] = LuaNativeFunction { args -> mathRandomseed(args) }

        context.registerGlobal("math", lib)
    }

    // ========== Basic Functions ==========

    private fun mathAbs(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val value = args.getOrNull(0)
        val result: LuaValue<*> =
            when (value) {
                is LuaLong -> {
                    // Preserve integer type for integers
                    // Special case: abs(mininteger) = mininteger (overflow)
                    if (value.value == Long.MIN_VALUE) {
                        value
                    } else {
                        LuaLong(abs(value.value))
                    }
                }
                is LuaDouble -> LuaDouble(abs(value.value))
                else -> {
                    // Fallback for non-number types
                    val x = getNumberArg(args, 0, "abs")
                    LuaNumber.of(abs(x))
                }
            }
        return listOf(result)
    }

    private inline fun applyRoundingFunction(
        args: List<LuaValue<*>>,
        functionName: String,
        mathOp: (Double) -> Double,
    ): List<LuaValue<*>> {
        // If already an integer, return it unchanged
        val arg = args.getOrNull(0)
        if (arg is LuaLong) {
            return listOf(arg)
        }

        val x = getNumberArg(args, 0, functionName)
        val result: LuaValue<*> = LuaNumber.of(mathOp(x))
        return listOf(result)
    }

    private fun mathCeil(args: List<LuaValue<*>>): List<LuaValue<*>> = applyRoundingFunction(args, "ceil", ::ceil)

    private fun mathFloor(args: List<LuaValue<*>>): List<LuaValue<*>> = applyRoundingFunction(args, "floor", ::floor)

    private fun mathMax(args: List<LuaValue<*>>): List<LuaValue<*>> {
        if (args.isEmpty()) {
            throw RuntimeException("bad argument #1 to 'max' (value expected)")
        }

        // Check if all arguments are integers
        var allIntegers = true
        for (arg in args) {
            if (arg !is LuaLong) {
                allIntegers = false
                break
            }
        }

        if (allIntegers) {
            // All integers - find max and preserve integer type
            var maxVal = (args[0] as LuaLong).value
            for (i in 1 until args.size) {
                val current = (args[i] as LuaLong).value
                if (current > maxVal) {
                    maxVal = current
                }
            }
            return listOf(LuaLong(maxVal))
        } else {
            // Mixed types - convert to double
            var maxVal = getNumberArg(args, 0, "max")
            for (i in 1 until args.size) {
                val current = getNumberArg(args, i, "max")
                if (current > maxVal) {
                    maxVal = current
                }
            }
            return listOf(LuaNumber.of(maxVal))
        }
    }

    private fun mathMin(args: List<LuaValue<*>>): List<LuaValue<*>> {
        if (args.isEmpty()) {
            throw RuntimeException("bad argument #1 to 'min' (value expected)")
        }

        // Check if all arguments are integers
        var allIntegers = true
        for (arg in args) {
            if (arg !is LuaLong) {
                allIntegers = false
                break
            }
        }

        if (allIntegers) {
            // All integers - find min and preserve integer type
            var minVal = (args[0] as LuaLong).value
            for (i in 1 until args.size) {
                val current = (args[i] as LuaLong).value
                if (current < minVal) {
                    minVal = current
                }
            }
            return listOf(LuaLong(minVal))
        } else {
            // Mixed types - convert to double
            var minVal = getNumberArg(args, 0, "min")
            for (i in 1 until args.size) {
                val current = getNumberArg(args, i, "min")
                if (current < minVal) {
                    minVal = current
                }
            }
            return listOf(LuaNumber.of(minVal))
        }
    }

    private fun mathModf(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "modf")

        // Handle special cases: infinity and NaN
        val intPart: Double
        val fracPart: Double

        when {
            x.isInfinite() -> {
                // For ±infinity, integer part is ±infinity, fractional part is 0.0
                intPart = x
                fracPart = 0.0
            }
            x.isNaN() -> {
                // For NaN, both parts are NaN
                intPart = x
                fracPart = x
            }
            else -> {
                // Normal case
                intPart = truncate(x)
                fracPart = x - intPart
            }
        }

        return buildList {
            add(LuaNumber.of(intPart))
            // Fractional part is always a float in Lua 5.4, even for integer inputs
            add(LuaDouble(fracPart))
        }
    }

    private fun mathFmod(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val xVal = args.getOrNull(0)
        val yVal = args.getOrNull(1)

        // Preserve integer type when both arguments are integers (LuaLong type, not just integer value)
        if (xVal is LuaLong && yVal is LuaLong) {
            val x = xVal.value
            val y = yVal.value
            if (y == 0L) {
                throw IllegalArgumentException("bad argument #2 to 'fmod' (zero)")
            }
            // fmod for integers uses truncated division: x - trunc(x/y) * y
            val result = x - (x / y) * y
            return listOf(LuaLong(result))
        } else {
            // Use floating-point fmod - always return float type when any arg is float
            val x = getNumberArg(args, 0, "fmod")
            val y = getNumberArg(args, 1, "fmod")
            if (y == 0.0) {
                throw IllegalArgumentException("bad argument #2 to 'fmod' (zero)")
            }
            // fmod returns x - trunc(x/y) * y (different from Lua's % operator)
            val result = x - truncate(x / y) * y
            return listOf(LuaDouble(result))
        }
    }

    // ========== Power and Roots ==========

    private fun mathSqrt(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "sqrt")
        val result: LuaValue<*> = LuaNumber.of(sqrt(x))
        return listOf(result)
    }

    private fun mathExp(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "exp")
        val result: LuaValue<*> = LuaNumber.of(exp(x))
        return listOf(result)
    }

    private fun mathLog(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "log")

        val result: LuaValue<*> =
            if (args.size >= 2) {
                val base = getNumberArg(args, 1, "log")
                LuaNumber.of(ln(x) / ln(base))
            } else {
                LuaNumber.of(ln(x))
            }

        return listOf(result)
    }

    // ========== Trigonometric Functions ==========

    private fun mathSin(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "sin")
        val result: LuaValue<*> = LuaNumber.of(sin(x))
        return listOf(result)
    }

    private fun mathCos(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "cos")
        val result: LuaValue<*> = LuaNumber.of(cos(x))
        return listOf(result)
    }

    private fun mathTan(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "tan")
        val result: LuaValue<*> = LuaNumber.of(tan(x))
        return listOf(result)
    }

    private fun mathAsin(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "asin")
        val result: LuaValue<*> = LuaNumber.of(asin(x))
        return listOf(result)
    }

    private fun mathAcos(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "acos")
        val result: LuaValue<*> = LuaNumber.of(acos(x))
        return listOf(result)
    }

    private fun mathAtan(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val y = getNumberArg(args, 0, "atan")

        val result: LuaValue<*> =
            if (args.size >= 2) {
                // atan2(y, x)
                val x = getNumberArg(args, 1, "atan")
                LuaNumber.of(atan2(y, x))
            } else {
                LuaNumber.of(atan(y))
            }

        return listOf(result)
    }

    // ========== Angle Conversion ==========

    private fun mathDeg(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "deg")
        val result: LuaValue<*> = LuaNumber.of(x * 180.0 / PI)
        return listOf(result)
    }

    private fun mathRad(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val x = getNumberArg(args, 0, "rad")
        val result: LuaValue<*> = LuaNumber.of(x * PI / 180.0)
        return listOf(result)
    }

    // ========== Type Functions ==========

    private fun mathType(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val value = args.getOrNull(0)

        val result: LuaValue<*> =
            when (value) {
                is LuaLong -> LuaString("integer")
                is LuaDouble -> LuaString("float")
                else -> LuaNil
            }

        return listOf(result)
    }

    private fun mathTointeger(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val value = args.getOrNull(0)

        val result: LuaValue<*> =
            when (value) {
                is LuaLong -> {
                    // Already an exact integer - pass through unchanged
                    value
                }
                is LuaDouble -> {
                    val numValue = value.value
                    // Check if the double can be exactly represented as a Long
                    if (numValue.isFinite() && numValue == floor(numValue)) {
                        // Check if within Long range: [-2^63, 2^63-1]
                        // Note: -2^63 is exactly representable, but 2^63-1 rounds to 2^63 in double
                        // So we check: -9223372036854775808 <= x <= 9223372036854775807
                        // But 9223372036854775807.0 rounds to ~9.223e18 (which equals 2^63)
                        // Correct check: ensure value doesn't exceed actual Long.MAX_VALUE mathematically
                        if (numValue >= -9223372036854775808.0 && numValue < 9223372036854775808.0) {
                            val longValue = numValue.toLong()
                            // Verify round-trip to ensure exact representability
                            if (longValue.toDouble() == numValue) {
                                LuaNumber.of(longValue)
                            } else {
                                LuaNil
                            }
                        } else {
                            LuaNil
                        }
                    } else {
                        LuaNil
                    }
                }
                is LuaString -> {
                    // Try to convert string to number first, then apply integer conversion
                    val coercedNumber = value.coerceToNumber()
                    when (coercedNumber) {
                        is LuaLong -> {
                            // String parsed to exact integer - pass through unchanged
                            coercedNumber
                        }
                        is LuaDouble -> {
                            val numValue = coercedNumber.value
                            // Check if the double can be exactly represented as a Long
                            if (numValue.isFinite() &&
                                numValue == floor(numValue) &&
                                numValue.toLong().toDouble() == numValue // Ensure exact representability
                            ) {
                                LuaNumber.of(numValue.toLong())
                            } else {
                                LuaNil
                            }
                        }
                        else -> LuaNil
                    }
                }
                else -> LuaNil
            }

        return listOf(result)
    }

    private fun mathUlt(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val m = getNumberArg(args, 0, "ult").toLong()
        val n = getNumberArg(args, 1, "ult").toLong()

        // Unsigned comparison: treat as unsigned longs
        val result: LuaValue<*> =
            if (m.toULong() < n.toULong()) {
                LuaBoolean.TRUE
            } else {
                LuaBoolean.FALSE
            }

        return listOf(result)
    }

    // ========== Random Functions ==========

    private fun mathRandomseed(args: List<LuaValue<*>>): List<LuaValue<*>> =
        when (args.size) {
            0 -> {
                // Return current seed parts (without resetting)
                listOf(LuaNumber.of(seedPartHigh), LuaNumber.of(seedPartLow))
            }
            1 -> {
                val seed = getNumberArg(args, 0, "randomseed").toLong()
                // Single-part seed: use as high part, low = 0
                seedPartHigh = seed
                seedPartLow = 0L
                // Initialize LCG state using SplitMix64 algorithm (like Lua 5.4)
                randomState = initializeRandomState(seed.toULong())
                // Return the seed parts
                listOf(LuaNumber.of(seedPartHigh), LuaNumber.of(seedPartLow))
            }
            else -> {
                val x = getNumberArg(args, 0, "randomseed").toLong()
                val y = getNumberArg(args, 1, "randomseed").toLong()
                seedPartHigh = x
                seedPartLow = y
                // Combine x and y into a 64-bit state
                // High 32 bits from x, low 32 bits from y
                val highPart = (x.toULong() and 0xFFFFFFFFUL) shl 32
                val lowPart = y.toULong() and 0xFFFFFFFFUL
                val combinedSeed = highPart or lowPart
                // Initialize using SplitMix64
                randomState = initializeRandomState(combinedSeed)
                // Return the seed parts
                listOf(LuaNumber.of(seedPartHigh), LuaNumber.of(seedPartLow))
            }
        }

    /**
     * Initialize the random state using two SplitMix64 steps XORed together.
     * This matches Lua 5.4's randseed initialization which calls splitmix64 twice.
     */
    private fun initializeRandomState(seed: ULong): ULong {
        // Call splitmix64 twice and XOR the results (like Lua 5.4)
        val result1 = splitmix64(seed)
        val result2 = splitmix64(result1)
        return result1 xor result2
    }

    /**
     * SplitMix64 algorithm for better quality seed initialization.
     */
    private fun splitmix64(x: ULong): ULong {
        var z = x + 0x9e3779b97f4a7c15UL
        z = (z xor (z shr 30)) * 0xbf58476d1ce4e5b9UL
        z = (z xor (z shr 27)) * 0x94d049bb133111ebUL
        return z xor (z shr 31)
    }

    private fun mathRandom(args: List<LuaValue<*>>): List<LuaValue<*>> {
        // Validate argument count
        if (args.size > 2) {
            throw RuntimeException("wrong number of arguments to 'random' (too many arguments)")
        }

        val result: LuaValue<*> =
            when (args.size) {
                0 -> {
                    // random() - returns [0, 1)
                    // Generate next LCG state and convert to float
                    val randomValue = nextRandom()
                    LuaNumber.of(project64ToDouble(randomValue))
                }
                1 -> {
                    // random(n) - returns [1, n] for n > 0, or full range integer for n == 0
                    val n = getNumberArg(args, 0, "random").toLong()
                    when {
                        n == 0L -> {
                            // Special case: random(0) returns a random integer in [mininteger, maxinteger]
                            // Use splitmix64 (I2Int) transformation like Lua 5.4 for better bit distribution
                            val randomValue = nextRandom()
                            val transformed = splitmix64(randomValue)
                            LuaNumber.of(transformed.toLong())
                        }
                        n > 0 -> {
                            // Generate in range [1, n] inclusive
                            LuaNumber.of(randomLongInclusive(1, n))
                        }
                        else -> {
                            throw RuntimeException("bad argument #1 to 'random' (interval is empty)")
                        }
                    }
                }
                else -> {
                    // random(m, n) - returns [m, n]
                    // Compare LuaValues before converting to avoid rounding issues
                    val mVal = args.getOrNull(0)
                    val nVal = args.getOrNull(1)

                    // Check if interval is empty by comparing LuaValues directly
                    val isEmpty =
                        when {
                            mVal is LuaLong && nVal is LuaLong -> nVal.value < mVal.value
                            else -> {
                                // For doubles or mixed types, convert and compare
                                val mDouble = getNumberArg(args, 0, "random")
                                val nDouble = getNumberArg(args, 1, "random")
                                nDouble < mDouble
                            }
                        }

                    if (isEmpty) {
                        throw RuntimeException("bad argument #2 to 'random' (interval is empty)")
                    }

                    // Now convert for actual generation
                    val m = getNumberArg(args, 0, "random").toLong()
                    val n = getNumberArg(args, 1, "random").toLong()
                    // Generate in range [m, n] inclusive
                    LuaNumber.of(randomLongInclusive(m, n))
                }
            }

        return listOf(result)
    }

    /**
     * Generate next random number using Lua 5.4's LCG algorithm.
     * Formula: state = state * a + c (mod 2^64)
     */
    private fun nextRandom(): ULong {
        randomState = randomState * lcgMultiplier + lcgIncrement
        return randomState
    }

    /**
     * Convert a 64-bit unsigned integer to a double in [0, 1).
     * This matches Lua 5.4's project64 function.
     *
     * From Lua 5.4.8 lmathlib.c:
     * - Takes the upper 53 bits (double mantissa size)
     * - Shifts right by 11 bits to discard lower bits: value >> 11
     * - Divides by 2^53 to normalize to [0, 1)
     *
     * This ensures the result has exactly 53 bits of precision and no extra bits.
     */
    private fun project64ToDouble(value: ULong): Double {
        // Shift right by 11 bits to keep only upper 53 bits
        // Then multiply by 2^-53 (same as dividing by 2^53)
        val upper53Bits = value shr 11
        // Use exact power of 2 division: 2^-53 = 1.1102230246251565e-16
        return upper53Bits.toDouble() * 1.1102230246251565e-16
    }

    /**
     * Generate a random Long in the inclusive range [from, to].
     * Handles all edge cases including when to == Long.MAX_VALUE.
     * Uses the LCG to generate the random value.
     */
    private fun randomLongInclusive(
        from: Long,
        to: Long,
    ): Long {
        if (from == to) return from
        if (from == Long.MIN_VALUE && to == Long.MAX_VALUE) {
            // Full range: just return the next random value as signed long
            return nextRandom().toLong()
        }

        // Calculate range size (to - from + 1)
        // If this overflows, the range spans more than Long.MAX_VALUE values
        val rangeSize = to - from + 1

        return if (rangeSize > 0) {
            // Normal case: range size fits in a Long
            // Use rejection sampling to avoid modulo bias
            val range = rangeSize.toULong()
            val max = ULong.MAX_VALUE
            val limit = max - (max % range)
            var randomValue: ULong
            do {
                randomValue = nextRandom()
            } while (randomValue >= limit)
            from + (randomValue % range).toLong()
        } else {
            // Range size overflow: range is very large
            // Split the range in two and choose randomly
            val randomValue = nextRandom()
            if ((randomValue and 1UL) == 0UL) {
                // Lower half: [from, -1]
                val subRange = (-1L - from + 1L).toULong()
                from + (randomValue % subRange).toLong()
            } else {
                // Upper half: [0, to]
                val subRange = (to + 1L).toULong()
                (randomValue % subRange).toLong()
            }
        }
    }

    // ========== Helper Functions ==========

    private fun getNumberArg(
        args: List<LuaValue<*>>,
        index: Int,
        funcName: String,
    ): Double {
        val value = args.getOrNull(index)
        return when (value) {
            is LuaNumber -> value.toDouble()
            else -> {
                // Get type name, checking for custom __name in metatable
                val mtName = (value?.metatable as? LuaTable)?.get(LuaString("__name")) as? LuaString
                val typeName = mtName?.value ?: (value?.type()?.name?.lowercase() ?: "no value")
                throw RuntimeException(
                    "bad argument #${index + 1} to '$funcName' (number expected, got $typeName)",
                )
            }
        }
    }
}
