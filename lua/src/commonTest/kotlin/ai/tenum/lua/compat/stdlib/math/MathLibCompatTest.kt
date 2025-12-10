package ai.tenum.lua.compat.stdlib.math

// CPD-OFF: test file with intentional Lua script pattern duplications

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PHASE 5.2: Standard Library - Math Library (Overview/Legacy Tests)
 *
 * Original comprehensive math library tests.
 * Based on: math.lua
 *
 * NOTE: For comprehensive coverage, see the specialized test files:
 * - MathBasicFunctionsCompatTest.kt - Basic functions (abs, ceil, floor, max, min, modf, fmod, sqrt, exp, log)
 * - MathTrigonometricCompatTest.kt - Trigonometric functions (sin, cos, tan, asin, acos, atan, deg, rad)
 * - MathTypeAndConversionCompatTest.kt - Type functions (math.type, math.tointeger, math.ult, tonumber)
 * - MathRandomCompatTest.kt - Random number generation (random, randomseed)
 * - MathArithmeticCompatTest.kt - Arithmetic operations and edge cases (modulo, floor division, overflow)
 * - MathConstantsCompatTest.kt - Mathematical constants (pi, huge, mininteger, maxinteger)
 * - MathFloatIntegerCompatTest.kt - Float/Integer behavior, NaN, infinity, precision
 * - MathHexadecimalCompatTest.kt - Hexadecimal number parsing and conversion
 *
 * This file contains basic coverage for compatibility.
 */
class MathLibCompatTest : LuaCompatTestBase() {
    // ========== Basic Math Functions ==========

    @Test
    fun testMathAbs() =
        runTest {
            assertLuaNumber("return math.abs(5)", 5.0)
            assertLuaNumber("return math.abs(-5)", 5.0)
            assertLuaNumber("return math.abs(0)", 0.0)
            assertLuaNumber("return math.abs(-3.5)", 3.5)
        }

    @Test
    fun testMathCeil() =
        runTest {
            assertLuaNumber("return math.ceil(3.2)", 4.0)
            assertLuaNumber("return math.ceil(3.8)", 4.0)
            assertLuaNumber("return math.ceil(-3.2)", -3.0)
            assertLuaNumber("return math.ceil(5)", 5.0)
        }

    @Test
    fun testMathFloor() =
        runTest {
            assertLuaNumber("return math.floor(3.2)", 3.0)
            assertLuaNumber("return math.floor(3.8)", 3.0)
            assertLuaNumber("return math.floor(-3.2)", -4.0)
            assertLuaNumber("return math.floor(5)", 5.0)
        }

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
    fun testMathMin() =
        runTest {
            assertLuaNumber("return math.min(1, 2, 3)", 1.0)
            assertLuaNumber("return math.min(3, 2, 1)", 1.0)
            assertLuaNumber("return math.min(-1, -2, -3)", -3.0)
            assertLuaNumber("return math.min(5)", 5.0)
            assertLuaNumber("return math.min(1.5, 2.5)", 1.5)
        }

    @Test
    fun testMathModf() =
        runTest {
            assertLuaNumber("local i, f = math.modf(3.5); return i", 3.0)
            assertLuaNumber("local i, f = math.modf(3.5); return f", 0.5)

            assertLuaNumber("local i, f = math.modf(-3.5); return i", -3.0)
            assertLuaNumber("local i, f = math.modf(-3.5); return f", -0.5)

            assertLuaNumber("local i, f = math.modf(5); return i", 5.0)
            assertLuaNumber("local i, f = math.modf(5); return f", 0.0)
        }

    @Test
    fun testMathModfTypesForIntegers() =
        runTest {
            // From math.lua:104 - integer argument should return integer and float types
            // This matches the assertion: assert(eqT(a, 3) and eqT(b, 0.0))
            val code =
                """
                local a, b = math.modf(3)
                return math.type(a) == 'integer' and math.type(b) == 'float' and a == 3 and b == 0.0
                """.trimIndent()
            assertLuaTrue(code, "math.modf(integer) should return (integer, float)")
        }

    @Test
    fun testMathFmod() =
        runTest {
            assertLuaNumber("return math.fmod(7, 3)", 1.0)
            assertLuaNumber("return math.fmod(-7, 3)", -1.0)
            assertLuaNumber("return math.fmod(7, -3)", 1.0)
            assertLuaNumber("return math.fmod(7.5, 2)", 1.5)
        }

    // ========== Power and Roots ==========

    @Test
    fun testMathSqrt() =
        runTest {
            assertLuaNumber("return math.sqrt(16)", 4.0)
            assertLuaNumber("return math.sqrt(0)", 0.0)
            assertLuaNumber("return math.sqrt(2)", 1.4142135623730951, 0.0000001)
        }

    @Test
    fun testMathExp() =
        runTest {
            assertLuaNumber("return math.exp(0)", 1.0)
            assertLuaNumber("return math.exp(1)", 2.718281828459045, 0.0000001)
            assertLuaNumber("return math.exp(2)", 7.38905609893065, 0.0000001)
        }

    @Test
    fun testMathLog() =
        runTest {
            assertLuaNumber("return math.log(1)", 0.0)
            assertLuaNumber("return math.log(2.718281828459045)", 1.0, 0.0000001)
            // log with base
            assertLuaNumber("return math.log(100, 10)", 2.0, 0.0000001)
            assertLuaNumber("return math.log(8, 2)", 3.0, 0.0000001)
        }

    // ========== Trigonometric Functions ==========

    @Test
    fun testMathSin() =
        runTest {
            assertLuaNumber("return math.sin(0)", 0.0)
            assertLuaNumber("return math.sin(math.pi / 2)", 1.0, 0.0000001)
            assertLuaNumber("return math.sin(math.pi)", 0.0, 0.0000001)
        }

    @Test
    fun testMathCos() =
        runTest {
            assertLuaNumber("return math.cos(0)", 1.0)
            assertLuaNumber("return math.cos(math.pi / 2)", 0.0, 0.0000001)
            assertLuaNumber("return math.cos(math.pi)", -1.0, 0.0000001)
        }

    @Test
    fun testMathTan() =
        runTest {
            assertLuaNumber("return math.tan(0)", 0.0)
            assertLuaNumber("return math.tan(math.pi / 4)", 1.0, 0.0000001)
        }

    @Test
    fun testMathAsin() =
        runTest {
            assertLuaNumber("return math.asin(0)", 0.0)
            assertLuaNumber("return math.asin(1)", 1.5707963267948966, 0.0000001)
            assertLuaNumber("return math.asin(-1)", -1.5707963267948966, 0.0000001)
        }

    @Test
    fun testMathAcos() =
        runTest {
            assertLuaNumber("return math.acos(1)", 0.0, 0.0000001)
            assertLuaNumber("return math.acos(0)", 1.5707963267948966, 0.0000001)
            assertLuaNumber("return math.acos(-1)", 3.141592653589793, 0.0000001)
        }

    @Test
    fun testMathAtan() =
        runTest {
            assertLuaNumber("return math.atan(0)", 0.0)
            assertLuaNumber("return math.atan(1)", 0.7853981633974483, 0.0000001)
            // atan with 2 args (atan2)
            assertLuaNumber("return math.atan(1, 1)", 0.7853981633974483, 0.0000001)
            assertLuaNumber("return math.atan(1, -1)", 2.356194490192345, 0.0000001)
        }

    // ========== Angle Conversion ==========

    @Test
    fun testMathDeg() =
        runTest {
            assertLuaNumber("return math.deg(0)", 0.0)
            assertLuaNumber("return math.deg(math.pi)", 180.0, 0.0000001)
            assertLuaNumber("return math.deg(math.pi / 2)", 90.0, 0.0000001)
        }

    @Test
    fun testMathRad() =
        runTest {
            assertLuaNumber("return math.rad(0)", 0.0)
            assertLuaNumber("return math.rad(180)", 3.141592653589793, 0.0000001)
            assertLuaNumber("return math.rad(90)", 1.5707963267948966, 0.0000001)
        }

    // ========== Constants ==========

    @Test
    fun testMathPi() =
        runTest {
            assertLuaNumber("return math.pi", 3.141592653589793, 0.0000001)
        }

    @Test
    fun testMathHuge() =
        runTest {
            val result = execute("return math.huge")
            assertTrue(result is LuaNumber)
            assertTrue(result.toDouble().isInfinite())
            assertTrue(result.toDouble() > 0)
        }

    @Test
    fun testMathMinInteger() =
        runTest {
            assertLuaNumber("return math.mininteger", Long.MIN_VALUE.toDouble())
        }

    @Test
    fun testMathMaxInteger() =
        runTest {
            assertLuaNumber("return math.maxinteger", Long.MAX_VALUE.toDouble())
        }

    // ========== Type Functions ==========

    @Test
    fun testMathType() =
        runTest {
            assertLuaString("return math.type(3)", "integer")
            assertLuaString("return math.type(3.5)", "float")
            assertLuaNil("return math.type('x')")
            assertLuaNil("return math.type(nil)")
        }

    @Test
    fun testMathTointeger() =
        runTest {
            assertLuaNumber("return math.tointeger(3)", 3.0)
            assertLuaNumber("return math.tointeger(3.0)", 3.0)
            assertLuaNil("return math.tointeger(3.5)")
            assertLuaNil("return math.tointeger('x')")
        }

    @Test
    fun testMathUlt() =
        runTest {
            // Unsigned less than comparison
            assertLuaBoolean("return math.ult(2, 3)", true)
            assertLuaBoolean("return math.ult(3, 2)", false)
            assertLuaBoolean("return math.ult(2, 2)", false)
            // Test with negative numbers (treated as large unsigned)
            assertLuaBoolean("return math.ult(-1, 1)", false)
        }

    // ========== Random Numbers ==========

    @Test
    fun testMathRandomseed() =
        runTest {
            // Just verify it doesn't error
            execute("math.randomseed(12345)")
            execute("math.randomseed(0)")
        }

    @Test
    fun testMathRandom() =
        runTest {
            // Test random() returns [0, 1)
            val result1 = execute("return math.random()")
            assertTrue(result1 is LuaNumber)
            val value1 = result1.toDouble()
            assertTrue(value1 >= 0.0 && value1 < 1.0)

            // Test random(n) returns [1, n]
            val result2 = execute("return math.random(10)")
            assertTrue(result2 is LuaNumber)
            val value2 = result2.toDouble()
            assertTrue(value2 >= 1.0 && value2 <= 10.0)

            // Test random(m, n) returns [m, n]
            val result3 = execute("return math.random(5, 10)")
            assertTrue(result3 is LuaNumber)
            val value3 = result3.toDouble()
            assertTrue(value3 >= 5.0 && value3 <= 10.0)
        }

    @Test
    fun testMathRandomDeterministic() =
        runTest {
            // With same seed, should get same sequence
            val result1 =
                execute(
                    """
            math.randomseed(42)
            return math.random()
        """,
                )

            val result2 =
                execute(
                    """
            math.randomseed(42)
            return math.random()
        """,
                )

            assertTrue(result1 is LuaNumber)
            assertTrue(result2 is LuaNumber)
            assertEquals(result1.toDouble(), result2.toDouble())
        }

    @Test
    fun testMathTypeWithIntegerAndFloat() =
        runTest {
            // Test that integer values return "integer"
            assertLuaString("return math.type(42)", "integer")
            assertLuaString("return math.type(math.maxinteger)", "integer")
            assertLuaString("return math.type(-5)", "integer")

            // Test that float values return "float"
            assertLuaString("return math.type(42.5)", "float")
            assertLuaString("return math.type(math.pi)", "float")

            // Test that integer + 0.0 becomes float
            assertLuaString("return math.type(42 + 0.0)", "float")

            // Test that non-numbers return nil
            val result = execute("return math.type('hello')")
            assertTrue(result is LuaNil)
        }
}
