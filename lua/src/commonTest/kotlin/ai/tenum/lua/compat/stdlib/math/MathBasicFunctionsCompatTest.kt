package ai.tenum.lua.compat.stdlib.math

// CPD-OFF: test file with intentional Lua script pattern duplications

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * PHASE 5.2: Standard Library - Math Basic Functions
 *
 * Tests the basic math library functions.
 * Based on: math.lua lines 86-105, 694-730, 759-812
 *
 * Coverage:
 * - math.abs - absolute value
 * - math.ceil, math.floor - rounding functions
 * - math.max, math.min - extrema functions
 * - math.modf - integer and fractional parts
 * - math.fmod - floating-point modulo
 * - math.sqrt, math.exp, math.log - power and logarithm functions
 */
class MathBasicFunctionsCompatTest : LuaCompatTestBase() {
    // ========== Absolute Value ==========

    @Test
    fun testMathAbs() =
        runTest {
            assertLuaNumber("return math.abs(5)", 5.0)
            assertLuaNumber("return math.abs(-5)", 5.0)
            assertLuaNumber("return math.abs(0)", 0.0)
            assertLuaNumber("return math.abs(-3.5)", 3.5)
            assertLuaNumber("return math.abs(-10.43)", 10.43)
        }

    @Test
    fun testMathAbsWithMinMaxInt() =
        runTest {
            // From math.lua:655-657
            assertLuaTrue("return math.abs(math.mininteger) == math.mininteger")
            assertLuaTrue("return math.abs(math.maxinteger) == math.maxinteger")
            assertLuaTrue("return math.abs(-math.maxinteger) == math.maxinteger")
        }

    // ========== Ceiling Function ==========

    @Test
    fun testMathCeil() =
        runTest {
            assertLuaNumber("return math.ceil(3.2)", 4.0)
            assertLuaNumber("return math.ceil(3.8)", 4.0)
            assertLuaNumber("return math.ceil(-3.2)", -3.0)
            assertLuaNumber("return math.ceil(-3.4)", -3.0)
            assertLuaNumber("return math.ceil(5)", 5.0)
            assertLuaNumber("return math.ceil(0)", 0.0)
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testMathCeilWithExtremesAndEdgeCases() =
        runTest {
            // From math.lua:694-704
            assertLuaTrue("return math.ceil(3.4) == 4 and math.type(math.ceil(3.4)) == 'integer'")
            assertLuaTrue("return math.ceil(-3.4) == -3 and math.type(math.ceil(-3.4)) == 'integer'")
            assertLuaTrue("return math.ceil(math.maxinteger) == math.maxinteger")
            assertLuaTrue("return math.ceil(math.mininteger) == math.mininteger")
            assertLuaTrue("return math.ceil(math.mininteger + 0.0) == math.mininteger")

            // Large numbers
            assertLuaNumber("return math.ceil(1e50)", 1e50)
            assertLuaNumber("return math.ceil(-1e50)", -1e50)

            // Powers of 2
            assertLuaTrue("return math.ceil(2^31) == 2^31")
            assertLuaTrue("return math.ceil(2^32) == 2^32")
            assertLuaTrue("return math.ceil(2^63) == 2^63")
            assertLuaTrue("return math.ceil(2^64) == 2^64")
            assertLuaTrue("return math.ceil(2^31 - 0.5) == 2^31")
            assertLuaTrue("return math.ceil(2^32 - 0.5) == 2^32")
            assertLuaTrue("return math.ceil(2^63 - 0.5) == 2^63")
            assertLuaTrue("return math.ceil(2^64 - 0.5) == 2^64")

            // Infinity
            assertLuaNumber("return math.ceil(math.huge)", Double.POSITIVE_INFINITY)
            assertLuaNumber("return math.ceil(-math.huge)", Double.NEGATIVE_INFINITY)
        }

    @Test
    fun testMathCeilErrors() =
        runTest {
            // From math.lua:704-705
            assertError("number expected") { execute("return math.ceil({})") }
            assertError("number expected") { execute("return math.ceil(print)") }
        }

    // ========== Floor Function ==========

    @Test
    fun testMathFloor() =
        runTest {
            assertLuaNumber("return math.floor(3.2)", 3.0)
            assertLuaNumber("return math.floor(3.8)", 3.0)
            assertLuaNumber("return math.floor(-3.2)", -4.0)
            assertLuaNumber("return math.floor(-3.4)", -4.0)
            assertLuaNumber("return math.floor(5)", 5.0)
            assertLuaNumber("return math.floor(0)", 0.0)
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testMathFloorWithExtremesAndEdgeCases() =
        runTest {
            // From math.lua:694-704
            assertLuaTrue("return math.floor(3.4) == 3 and math.type(math.floor(3.4)) == 'integer'")
            assertLuaTrue("return math.floor(-3.4) == -4 and math.type(math.floor(-3.4)) == 'integer'")
            assertLuaTrue("return math.floor(math.maxinteger) == math.maxinteger")
            assertLuaTrue("return math.floor(math.mininteger) == math.mininteger")
            assertLuaTrue("return math.floor(math.mininteger + 0.0) == math.mininteger")

            // Large numbers
            assertLuaNumber("return math.floor(1e50)", 1e50)
            assertLuaNumber("return math.floor(-1e50)", -1e50)

            // Powers of 2
            assertLuaTrue("return math.floor(2^31) == 2^31")
            assertLuaTrue("return math.floor(2^32) == 2^32")
            assertLuaTrue("return math.floor(2^63) == 2^63")
            assertLuaTrue("return math.floor(2^64) == 2^64")
            assertLuaTrue("return math.floor(2^31 + 0.5) == 2^31")
            assertLuaTrue("return math.floor(2^32 + 0.5) == 2^32")
            assertLuaTrue("return math.floor(2^63 + 0.5) == 2^63")
            assertLuaTrue("return math.floor(2^64 + 0.5) == 2^64")

            // Infinity
            assertLuaNumber("return math.floor(math.huge)", Double.POSITIVE_INFINITY)
            assertLuaNumber("return math.floor(-math.huge)", Double.NEGATIVE_INFINITY)
        }

    @Test
    fun testMathFloorErrors() =
        runTest {
            // From math.lua:704-705
            assertError("number expected") { execute("return math.floor({})") }
            assertError("number expected") { execute("return math.floor(print)") }
        }

    // ========== Maximum Function ==========

    @Test
    fun testMathMax() =
        runTest {
            assertLuaNumber("return math.max(1, 2, 3)", 3.0)
            assertLuaNumber("return math.max(3, 2, 1)", 3.0)
            assertLuaNumber("return math.max(-1, -2, -3)", -1.0)
            assertLuaNumber("return math.max(5)", 5.0)
            assertLuaNumber("return math.max(1.5, 2.5)", 2.5)
        }

    @Test
    fun testMathMaxWithExtremesAndTypes() =
        runTest {
            // From math.lua:730-738
            assertError("value expected") { execute("return math.max()") }
            assertLuaTrue("return math.max(3) == 3 and math.type(math.max(3)) == 'integer'")
            assertLuaTrue("return math.max(3, 5, 9, 1) == 9 and math.type(math.max(3, 5, 9, 1)) == 'integer'")
            assertLuaNumber("return math.max(math.maxinteger, 10e60)", 10e60)
            assertLuaTrue("return math.max(math.mininteger, math.mininteger + 1) == math.mininteger + 1")
        }

    // ========== Minimum Function ==========

    @Test
    fun testMathMin() =
        runTest {
            assertLuaNumber("return math.min(1, 2, 3)", 1.0)
            assertLuaNumber("return math.min(3, 2, 1)", 1.0)
            assertLuaNumber("return math.min(-1, -2, -3)", -3.0)
            assertLuaNumber("return math.min(5)", 5.0)
            assertLuaNumber("return math.min(1.5, 2.5)", 1.5)
        }

    @Test
    fun testMathMinWithExtremesAndTypes() =
        runTest {
            // From math.lua:730-738, 740-744
            assertError("value expected") { execute("return math.min()") }
            assertLuaTrue("return math.min(3) == 3 and math.type(math.min(3)) == 'integer'")
            assertLuaTrue("return math.min(3, 5, 9, 1) == 1 and math.type(math.min(3, 5, 9, 1)) == 'integer'")
            assertLuaNumber("return math.min(3.2, 5.9, -9.2, 1.1)", -9.2)
            assertLuaNumber("return math.min(1.9, 1.7, 1.72)", 1.7)
            assertLuaNumber("return math.min(-10e60, math.mininteger)", -10e60)
            assertLuaTrue("return math.min(math.maxinteger, math.maxinteger - 1) == math.maxinteger - 1")
            assertLuaTrue("return math.min(math.maxinteger - 2, math.maxinteger, math.maxinteger - 1) == math.maxinteger - 2")
        }

    // ========== Modf Function ==========

    @Test
    fun testMathModf() =
        runTest {
            assertLuaNumber("local i, f = math.modf(3.5); return i", 3.0)
            assertLuaNumber("local i, f = math.modf(3.5); return f", 0.5)

            assertLuaNumber("local i, f = math.modf(-2.5); return i", -2.0)
            assertLuaNumber("local i, f = math.modf(-2.5); return f", -0.5)

            assertLuaNumber("local i, f = math.modf(5); return i", 5.0)
            assertLuaNumber("local i, f = math.modf(5); return f", 0.0)
        }

    @Test
    fun testMathModfExtremeCases() =
        runTest {
            // From math.lua:86-105
            assertLuaTrue("local a, b = math.modf(-3e23); return a == -3e23 and b == 0.0")
            assertLuaTrue("local a, b = math.modf(3e35); return a == 3e35 and b == 0.0")
            assertLuaTrue("local a, b = math.modf(-1/0); return a == -1/0 and b == 0.0") // -inf
            assertLuaTrue("local a, b = math.modf(1/0); return a == 1/0 and b == 0.0") // inf

            // NaN case
            val code =
                """
                local a, b = math.modf(0/0)
                return (a ~= a) and (b ~= b)  -- both should be NaN
                """.trimIndent()
            assertLuaTrue(code)
        }

    @Test
    fun testMathModfTypesForIntegers() =
        runTest {
            // From math.lua:104 - integer argument should return integer and float types
            val code =
                """
                local a, b = math.modf(3)
                return math.type(a) == 'integer' and math.type(b) == 'float' and a == 3 and b == 0.0
                """.trimIndent()
            assertLuaTrue(code, "math.modf(integer) should return (integer, float)")

            val code2 =
                """
                local a, b = math.modf(math.mininteger)
                return math.type(a) == 'integer' and math.type(b) == 'float'
                """.trimIndent()
            assertLuaTrue(code2, "math.modf(minint) should return (integer, float)")
        }

    // ========== Fmod Function ==========

    @Test
    fun testMathFmod() =
        runTest {
            assertLuaNumber("return math.fmod(7, 3)", 1.0)
            assertLuaNumber("return math.fmod(-7, 3)", -1.0)
            assertLuaNumber("return math.fmod(7, -3)", 1.0)
            assertLuaNumber("return math.fmod(7.5, 2)", 1.5)
            assertLuaNumber("return math.fmod(10, 3)", 1.0)
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testMathFmodWithIntegersAndTypes() =
        runTest {
            // From math.lua:713-729
            val testCases = listOf(-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6)
            for (i in testCases) {
                for (j in testCases) {
                    if (j != 0) {
                        val code =
                            """
                            local mi = math.fmod($i, $j)
                            local mf = math.fmod($i + 0.0, $j)
                            return mi == mf and math.type(mi) == 'integer' and math.type(mf) == 'float'
                            """.trimIndent()
                        assertLuaTrue(code, "fmod($i, $j) integer vs float types should match values but differ in type")
                    }
                }
            }
        }

    @Test
    fun testMathFmodExtremeCases() =
        runTest {
            // From math.lua:726-729
            assertLuaTrue("return math.fmod(math.mininteger, math.mininteger) == 0")
            assertLuaTrue("return math.fmod(math.maxinteger, math.maxinteger) == 0")
            assertLuaTrue("return math.fmod(math.mininteger + 1, math.mininteger) == math.mininteger + 1")
            assertLuaTrue("return math.fmod(math.maxinteger - 1, math.maxinteger) == math.maxinteger - 1")
        }

    @Test
    fun testMathFmodErrors() =
        runTest {
            // From math.lua:731
            assertError("zero") { execute("return math.fmod(3, 0)") }
        }

    @Test
    fun testMathFmodFloatInputsReturnFloat() =
        runTest {
            // From bitwise.lua:314 - even if result could be integer, float inputs return float
            assertLuaNumber("return math.fmod(2.0, 2.0^32)", 2.0)
            assertLuaTrue("return math.type(math.fmod(2.0, 2.0^32)) == 'float'")
            assertLuaTrue("return math.type(math.fmod(3, 2.0)) == 'float'")
            assertLuaTrue("return math.type(math.fmod(3.0, 2)) == 'float'")
        }

    // ========== Square Root Function ==========

    @Test
    fun testMathSqrt() =
        runTest {
            assertLuaNumber("return math.sqrt(16)", 4.0)
            assertLuaNumber("return math.sqrt(0)", 0.0)
            assertLuaNumber("return math.sqrt(2)", 1.4142135623730951, 0.0000001)
            assertLuaNumber("return math.sqrt(1)", 1.0)
            assertLuaNumber("return math.sqrt(4)", 2.0)
            assertLuaNumber("return math.sqrt(9)", 3.0)
        }

    @Test
    fun testMathSqrtConsistency() =
        runTest {
            // From math.lua:664
            assertLuaTrue("return math.abs(math.sqrt(10)^2 - 10) < 1e-10", "sqrt(10)^2 should equal 10")
        }

    // ========== Exponential Function ==========

    @Test
    fun testMathExp() =
        runTest {
            assertLuaNumber("return math.exp(0)", 1.0)
            assertLuaNumber("return math.exp(1)", 2.718281828459045, 0.0000001)
            assertLuaNumber("return math.exp(2)", 7.38905609893065, 0.0000001)
        }

    @Test
    fun testMathExpConsistency() =
        runTest {
            // From math.lua:668
            assertLuaTrue("return math.abs(math.exp(0) - 1) < 1e-10", "exp(0) should equal 1")
        }

    // ========== Logarithm Function ==========

    @Test
    fun testMathLog() =
        runTest {
            assertLuaNumber("return math.log(1)", 0.0)
            assertLuaNumber("return math.log(2.718281828459045)", 1.0, 0.0000001)
            // log with base
            assertLuaNumber("return math.log(100, 10)", 2.0, 0.0000001)
            assertLuaNumber("return math.log(8, 2)", 3.0, 0.0000001)
        }

    @Test
    fun testMathLogConsistency() =
        runTest {
            // From math.lua:664-667
            assertLuaTrue("return math.abs(math.log(2, 10) - math.log(2)/math.log(10)) < 1e-10", "log base conversion formula")
            assertLuaTrue("return math.abs(math.log(2, 2) - 1) < 1e-10", "log_2(2) should equal 1")
            assertLuaTrue("return math.abs(math.log(9, 3) - 2) < 1e-10", "log_3(9) should equal 2")
        }
}
