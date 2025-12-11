package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNumber
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PHASE 5.2: Standard Library - Math Constants
 *
 * Tests mathematical constants in the math library.
 * Based on: math.lua lines 7-13, 107-112
 *
 * Coverage:
 * - math.pi - π (pi) constant
 * - math.huge - positive infinity
 * - math.mininteger - minimum integer value
 * - math.maxinteger - maximum integer value
 */
class MathConstantsCompatTest : LuaCompatTestBase() {
    // ========== Pi Constant ==========

    @Test
    fun testMathPi() =
        runTest {
            assertLuaNumber("return math.pi", 3.141592653589793, 0.0000001)

            // Pi should be a float
            assertLuaString("return math.type(math.pi)", "float")

            // Basic π relationships
            assertLuaTrue("return math.abs(2 * math.pi - 6.283185307179586) < 1e-10", "2π value")
            assertLuaTrue("return math.abs(math.pi / 2 - 1.5707963267948966) < 1e-10", "π/2 value")
        }

    // ========== Infinity Constant ==========

    @Test
    fun testMathHuge() =
        runTest {
            val result = execute("return math.huge")
            assertTrue(result is LuaNumber)
            assertTrue(result.toDouble().isInfinite())
            assertTrue(result.toDouble() > 0, "math.huge should be positive infinity")

            // From math.lua:107-108
            assertLuaTrue("return math.huge > 10e30")
            assertLuaTrue("return -math.huge < -10e30")

            // Infinity arithmetic
            assertLuaTrue("return math.huge + 1 == math.huge")
            assertLuaTrue("return math.huge * 2 == math.huge")
            assertLuaTrue("return math.huge - math.huge ~= math.huge - math.huge") // NaN
        }

    @Test
    fun testMathHugeType() =
        runTest {
            // math.huge should be a float
            assertLuaString("return math.type(math.huge)", "float")
            assertLuaString("return math.type(-math.huge)", "float")
        }

    // ========== Integer Bounds ==========

    @Test
    fun testMathMinInteger() =
        runTest {
            assertLuaNumber("return math.mininteger", Long.MIN_VALUE.toDouble())

            // Should be an integer type
            assertLuaString("return math.type(math.mininteger)", "integer")

            // From math.lua:7-13 - relationship to bit operations
            val code =
                """
                local minint = math.mininteger
                local maxint = math.maxinteger
                local intbits = 64  -- Assuming 64-bit system
                
                -- minint should be 2^(intbits-1) 
                -- maxint should be minint - 1
                return minint == -(2^63) and maxint == (minint - 1)
                """.trimIndent()
            assertLuaTrue(code, "Integer bounds should follow 2's complement rules")
        }

    @Test
    fun testMathMaxInteger() =
        runTest {
            assertLuaNumber("return math.maxinteger", Long.MAX_VALUE.toDouble())

            // Should be an integer type
            assertLuaString("return math.type(math.maxinteger)", "integer")

            // Relationship with mininteger
            assertLuaTrue("return math.maxinteger == -math.mininteger - 1")
        }

    @Test
    fun testIntegerBoundsArithmetic() =
        runTest {
            // From math.lua:114-118 - integer arithmetic properties
            assertLuaTrue("return math.mininteger < (math.mininteger + 1)")
            assertLuaTrue("return (math.maxinteger - 1) < math.maxinteger")
            assertLuaTrue("return (0 - math.mininteger) == math.mininteger") // overflow
            assertLuaTrue("return (math.mininteger * math.mininteger) == 0") // overflow
            assertLuaTrue("return (math.maxinteger * math.maxinteger * math.maxinteger) == math.maxinteger")
        }

    @Test
    fun testIntegerBoundsWithFloats() =
        runTest {
            // Conversion to floats should preserve magnitude
            assertLuaTrue("return (math.maxinteger + 0.0) == 2^63 - 1")
            assertLuaTrue("return (math.mininteger + 0.0) == math.mininteger")
            assertLuaTrue("return (math.mininteger + 0.0) == -(2^63)")
        }

    // ========== Special Constants Combinations ==========

    @Test
    fun testConstantsCombinations() =
        runTest {
            // Pi with infinity
            assertLuaTrue("return math.pi < math.huge")
            assertLuaTrue("return -math.pi > -math.huge")

            // Pi with integer bounds
            assertLuaTrue("return math.pi > 0")
            assertLuaTrue("return math.pi < math.maxinteger")
            assertLuaTrue("return math.pi > math.mininteger")

            // Infinity comparisons with integer bounds
            assertLuaTrue("return math.huge > math.maxinteger")
            assertLuaTrue("return -math.huge < math.mininteger")
        }

    @Test
    fun testConstantsArithmetic() =
        runTest {
            // Operations with pi
            assertLuaTrue("return math.abs(math.sin(math.pi)) < 1e-10", "sin(π) ≈ 0")
            assertLuaTrue("return math.abs(math.cos(math.pi) + 1) < 1e-10", "cos(π) ≈ -1")

            // Operations with huge
            assertLuaNumber("return 1 / math.huge", 0.0)
            assertLuaNumber("return 1 / -math.huge", -0.0)

            // Operations with integer bounds
            assertLuaTrue("return math.abs(math.mininteger) == math.mininteger") // Special case
            assertLuaTrue("return math.abs(math.maxinteger) == math.maxinteger")
        }

    @Test
    fun testConstantsStringConversion() =
        runTest {
            // Constants should convert to strings appropriately
            val piStr = execute("return tostring(math.pi)")
            assertTrue(piStr.toString().contains("3.14"))

            val hugeStr = execute("return tostring(math.huge)")
            assertTrue(hugeStr.toString().contains("inf", ignoreCase = true))

            val maxIntStr = execute("return tostring(math.maxinteger)")
            assertTrue(maxIntStr.toString().contains("922337")) // Part of Long.MAX_VALUE

            val minIntStr = execute("return tostring(math.mininteger)")
            assertTrue(minIntStr.toString().contains("-922337")) // Part of Long.MIN_VALUE
        }

    @Test
    fun testConstantsEquality() =
        runTest {
            // Constants should be equal to themselves
            assertLuaTrue("return math.pi == math.pi")
            assertLuaTrue("return math.huge == math.huge")
            assertLuaTrue("return math.mininteger == math.mininteger")
            assertLuaTrue("return math.maxinteger == math.maxinteger")

            // But not equal to each other (except special cases)
            assertLuaTrue("return math.pi ~= math.huge")
            assertLuaTrue("return math.mininteger ~= math.maxinteger")
            assertLuaTrue("return math.pi ~= math.maxinteger")
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testConstantsImmutability() =
        runTest {
            // Constants should not be modifiable (this tests the implementation)
            // These should not crash and should preserve the original values
            execute("math.pi = 3; math.huge = 100; math.mininteger = 0; math.maxinteger = 1")

            // Values should be unchanged (if implementation protects them)
            assertLuaTrue("return math.pi > 3.1 and math.pi < 3.2") // Approximately π
            assertTrue(execute("return math.huge").let { it is LuaNumber && it.toDouble().isInfinite() })
        }

    @Test
    fun testConstantsWithMathOperations() =
        runTest {
            // Test constants work properly with math library functions
            assertLuaTrue("return math.floor(math.pi) == 3")
            assertLuaTrue("return math.ceil(math.pi) == 4")

            // Huge with math functions
            assertLuaNumber("return math.floor(math.huge)", Double.POSITIVE_INFINITY)
            assertLuaNumber("return math.ceil(math.huge)", Double.POSITIVE_INFINITY)
            assertLuaNumber("return math.abs(math.huge)", Double.POSITIVE_INFINITY)
            assertLuaNumber("return math.abs(-math.huge)", Double.POSITIVE_INFINITY)

            // Integer bounds with math functions
            assertLuaTrue("return math.floor(math.maxinteger) == math.maxinteger")
            assertLuaTrue("return math.ceil(math.mininteger) == math.mininteger")
            assertLuaTrue("return math.abs(math.maxinteger) == math.maxinteger")
        }
}
