package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * PHASE 5.2: Standard Library - Math Trigonometric Functions
 *
 * Tests the trigonometric math library functions.
 * Based on: math.lua lines 654-669
 *
 * Coverage:
 * - math.sin, math.cos, math.tan - basic trigonometric functions
 * - math.asin, math.acos, math.atan - inverse trigonometric functions
 * - math.deg, math.rad - angle conversion functions
 * - Trigonometric identities and relationships
 */
class MathTrigonometricCompatTest : LuaCompatTestBase() {
    // ========== Basic Trigonometric Functions ==========

    @Test
    fun testMathSin() =
        runTest {
            assertLuaNumber("return math.sin(0)", 0.0)
            assertLuaNumber("return math.sin(math.pi / 2)", 1.0, 0.0000001)
            assertLuaNumber("return math.sin(math.pi)", 0.0, 0.0000001)
            assertLuaNumber("return math.sin(3 * math.pi / 2)", -1.0, 0.0000001)
            assertLuaNumber("return math.sin(-math.pi / 2)", -1.0, 0.0000001)
        }

    @Test
    fun testMathCos() =
        runTest {
            assertLuaNumber("return math.cos(0)", 1.0)
            assertLuaNumber("return math.cos(math.pi / 2)", 0.0, 0.0000001)
            assertLuaNumber("return math.cos(math.pi)", -1.0, 0.0000001)
            assertLuaNumber("return math.cos(3 * math.pi / 2)", 0.0, 0.0000001)
            assertLuaNumber("return math.cos(2 * math.pi)", 1.0, 0.0000001)
        }

    @Test
    fun testMathTan() =
        runTest {
            assertLuaNumber("return math.tan(0)", 0.0)
            assertLuaNumber("return math.tan(math.pi / 4)", 1.0, 0.0000001)
            assertLuaNumber("return math.tan(-math.pi / 4)", -1.0, 0.0000001)
        }

    @Test
    fun testTrigonometricIdentities() =
        runTest {
            // From math.lua:654 - sin²(x) + cos²(x) = 1
            assertLuaTrue("return math.abs(math.sin(-9.8)^2 + math.cos(-9.8)^2 - 1) < 1e-10", "sin²(x) + cos²(x) = 1")

            // From math.lua:655 - tan(π/4) = 1
            assertLuaTrue("return math.abs(math.tan(math.pi/4) - 1) < 1e-10", "tan(π/4) = 1")

            // From math.lua:656 - sin(π/2) = 1, cos(π/2) = 0
            assertLuaTrue(
                "return math.abs(math.sin(math.pi/2) - 1) < 1e-10 and math.abs(math.cos(math.pi/2)) < 1e-10",
                "sin(π/2) = 1, cos(π/2) = 0",
            )

            // Periodicity test from math.lua:669
            assertLuaTrue("return math.abs(math.sin(10) - math.sin(10%(2*math.pi))) < 1e-10", "sin periodicity")
        }

    // ========== Inverse Trigonometric Functions ==========

    @Test
    fun testMathAsin() =
        runTest {
            assertLuaNumber("return math.asin(0)", 0.0)
            assertLuaNumber("return math.asin(1)", 1.5707963267948966, 0.0000001) // π/2
            assertLuaNumber("return math.asin(-1)", -1.5707963267948966, 0.0000001) // -π/2
            assertLuaNumber("return math.asin(0.5)", 0.5235987755982988, 0.0000001) // π/6
        }

    @Test
    fun testMathAcos() =
        runTest {
            assertLuaNumber("return math.acos(1)", 0.0, 0.0000001)
            assertLuaNumber("return math.acos(0)", 1.5707963267948966, 0.0000001) // π/2
            assertLuaNumber("return math.acos(-1)", 3.141592653589793, 0.0000001) // π
            assertLuaNumber("return math.acos(0.5)", 1.0471975511965979, 0.0000001) // π/3
        }

    @Test
    fun testMathAtan() =
        runTest {
            assertLuaNumber("return math.atan(0)", 0.0)
            assertLuaNumber("return math.atan(1)", 0.7853981633974483, 0.0000001) // π/4
            assertLuaNumber("return math.atan(-1)", -0.7853981633974483, 0.0000001) // -π/4

            // atan with 2 args (atan2)
            assertLuaNumber("return math.atan(1, 1)", 0.7853981633974483, 0.0000001) // π/4
            assertLuaNumber("return math.atan(1, -1)", 2.356194490192345, 0.0000001) // 3π/4
            assertLuaNumber("return math.atan(-1, 1)", -0.7853981633974483, 0.0000001) // -π/4
            assertLuaNumber("return math.atan(-1, -1)", -2.356194490192345, 0.0000001) // -3π/4
        }

    @Test
    fun testInverseTrigonometricConsistency() =
        runTest {
            // From math.lua:657-658
            assertLuaTrue("return math.abs(math.atan(1) - math.pi/4) < 1e-10", "atan(1) = π/4")
            assertLuaTrue("return math.abs(math.acos(0) - math.pi/2) < 1e-10", "acos(0) = π/2")
            assertLuaTrue("return math.abs(math.asin(1) - math.pi/2) < 1e-10", "asin(1) = π/2")

            // From math.lua:658 - atan with 2 args
            assertLuaTrue("return math.abs(math.atan(1,0) - math.pi/2) < 1e-10", "atan(1,0) = π/2")
        }

    // ========== Angle Conversion ==========

    @Test
    fun testMathDeg() =
        runTest {
            assertLuaNumber("return math.deg(0)", 0.0)
            assertLuaNumber("return math.deg(math.pi)", 180.0, 0.0000001)
            assertLuaNumber("return math.deg(math.pi / 2)", 90.0, 0.0000001)
            assertLuaNumber("return math.deg(math.pi / 4)", 45.0, 0.0000001)
            assertLuaNumber("return math.deg(2 * math.pi)", 360.0, 0.0000001)
        }

    @Test
    fun testMathRad() =
        runTest {
            assertLuaNumber("return math.rad(0)", 0.0)
            assertLuaNumber("return math.rad(180)", 3.141592653589793, 0.0000001) // π
            assertLuaNumber("return math.rad(90)", 1.5707963267948966, 0.0000001) // π/2
            assertLuaNumber("return math.rad(45)", 0.7853981633974483, 0.0000001) // π/4
            assertLuaNumber("return math.rad(360)", 6.283185307179586, 0.0000001) // 2π
        }

    @Test
    fun testAngleConversionConsistency() =
        runTest {
            // From math.lua:659
            assertLuaTrue("return math.abs(math.deg(math.pi/2) - 90) < 1e-10", "deg(π/2) = 90")
            assertLuaTrue("return math.abs(math.rad(90) - math.pi/2) < 1e-10", "rad(90) = π/2")

            // Roundtrip conversion
            assertLuaTrue("return math.abs(math.rad(math.deg(1.5)) - 1.5) < 1e-10", "rad(deg(x)) = x")
            assertLuaTrue("return math.abs(math.deg(math.rad(57.3)) - 57.3) < 1e-10", "deg(rad(x)) = x")
        }

    // ========== Special Values and Edge Cases ==========

    @Test
    fun testTrigonometricSpecialValues() =
        runTest {
            // Test with infinity and NaN - per IEEE 754, infinity * 0 = NaN
            assertLuaTrue("return (math.huge * 0) ~= (math.huge * 0)") // NaN != NaN
            assertLuaTrue("return (math.sin(math.huge * 0)) ~= (math.sin(math.huge * 0))") // sin(NaN) = NaN, and NaN != NaN

            // Test negative values
            assertLuaTrue("return math.sin(-math.pi/2) == -math.sin(math.pi/2)", "sin(-x) = -sin(x)")
            assertLuaTrue("return math.cos(-math.pi/2) == math.cos(math.pi/2)", "cos(-x) = cos(x)")
            assertLuaTrue("return math.tan(-math.pi/4) == -math.tan(math.pi/4)", "tan(-x) = -tan(x)")
        }

    @Test
    fun testInverseTrigonometricDomains() =
        runTest {
            // asin domain is [-1, 1]
            val asinResult = execute("return math.asin(2)")
            // Should return NaN for values outside domain
            kotlin.test.assertTrue(asinResult.toString().contains("nan", ignoreCase = true))

            // acos domain is [-1, 1]
            val acosResult = execute("return math.acos(2)")
            // Should return NaN for values outside domain
            kotlin.test.assertTrue(acosResult.toString().contains("nan", ignoreCase = true))
        }

    @Test
    fun testTrigonometricWithPolarCoordinates() =
        runTest {
            // Test atan2 for converting Cartesian to polar coordinates
            assertLuaNumber("return math.atan(0, 1)", 0.0, 0.0000001) // positive x-axis
            assertLuaNumber("return math.atan(1, 0)", 1.5707963267948966, 0.0000001) // positive y-axis
            assertLuaNumber("return math.atan(0, -1)", 3.141592653589793, 0.0000001) // negative x-axis
            assertLuaNumber("return math.atan(-1, 0)", -1.5707963267948966, 0.0000001) // negative y-axis
        }
}
