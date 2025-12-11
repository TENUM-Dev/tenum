package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Comprehensive test suite for Lua 5.4 integer/float equality semantics
 *
 * Key rule: An integer I and a float F are equal if and only if:
 * 1. F has no fractional part (is an integer value)
 * 2. I is exactly representable in double precision (within ±2^53)
 * 3. The numeric values are the same
 *
 * Doubles can exactly represent all integers in the range [-(2^53), 2^53].
 * Beyond this range, precision is lost and equality should return false.
 */
class NumberEqualityCompatTest : LuaCompatTestBase() {
    // ========================================
    // Basic Equality Cases
    // ========================================

    @Test
    fun testIntegerEqualsSameInteger() =
        runTest {
            val code = "return 42 == 42"
            assertLuaTrue(code, "Same integers should be equal")
        }

    @Test
    fun testFloatEqualsSameFloat() =
        runTest {
            val code = "return 42.0 == 42.0"
            assertLuaTrue(code, "Same floats should be equal")
        }

    @Test
    fun testIntegerEqualsFloat_SmallValue() =
        runTest {
            val code = "return 42 == 42.0"
            assertLuaTrue(code, "Small integer should equal float with same value")
        }

    @Test
    fun testFloatEqualsInteger_SmallValue() =
        runTest {
            val code = "return 42.0 == 42"
            assertLuaTrue(code, "Small float should equal integer with same value")
        }

    @Test
    fun testIntegerNotEqualsFloatWithFraction() =
        runTest {
            val code = "return 42 == 42.5"
            assertLuaFalse(code, "Integer should not equal float with fractional part")
        }

    @Test
    fun testZeroIntegerEqualsZeroFloat() =
        runTest {
            val code = "return 0 == 0.0"
            assertLuaTrue(code, "Zero integer should equal zero float")
        }

    @Test
    fun testNegativeIntegerEqualsNegativeFloat() =
        runTest {
            val code = "return -42 == -42.0"
            assertLuaTrue(code, "Negative integer should equal negative float")
        }

    // ========================================
    // Precision Boundary Cases (2^53)
    // ========================================

    @Test
    fun testMaxSafeInteger_Exact() =
        runTest {
            // 2^53 - 1 = 9007199254740991 is the largest integer exactly representable in double
            val code =
                """
                local maxSafe = 9007199254740991
                return maxSafe == (maxSafe + 0.0)
                """.trimIndent()
            assertLuaTrue(code, "Max safe integer (2^53-1) should equal its float conversion")
        }

    @Test
    fun testMaxSafeInteger_PlusOne() =
        runTest {
            // 2^53 = 9007199254740992 is still exactly representable
            val code =
                """
                local n = 9007199254740992
                return n == (n + 0.0)
                """.trimIndent()
            assertLuaTrue(code, "2^53 should equal its float conversion")
        }

    @Test
    fun testBeyondMaxSafeInteger() =
        runTest {
            // 2^53 + 1 = 9007199254740993 loses precision when converted to double
            val code =
                """
                local n = 9007199254740993
                return n == (n + 0.0)
                """.trimIndent()
            assertLuaFalse(code, "2^53+1 should NOT equal its float conversion (precision lost)")
        }

    @Test
    fun testNegativeMaxSafeInteger() =
        runTest {
            val code =
                """
                local n = -9007199254740991
                return n == (n + 0.0)
                """.trimIndent()
            assertLuaTrue(code, "Negative max safe integer should equal its float conversion")
        }

    @Test
    fun testNegativeBeyondMaxSafeInteger() =
        runTest {
            val code =
                """
                local n = -9007199254740993
                return n == (n + 0.0)
                """.trimIndent()
            assertLuaFalse(code, "Beyond negative safe range should NOT equal float conversion")
        }

    // ========================================
    // Long.MAX_VALUE Cases
    // ========================================

    @Test
    fun testLongMaxValue_NotEqualToFloat() =
        runTest {
            // Long.MAX_VALUE = 9223372036854775807 is way beyond 2^53, loses precision
            val code =
                """
                local maxLong = math.maxinteger
                return maxLong == (maxLong + 0.0)
                """.trimIndent()
            assertLuaFalse(code, "Long.MAX_VALUE should NOT equal its float conversion")
        }

    @Test
    fun testLongMaxValue_MinusOne() =
        runTest {
            val code =
                """
                local maxLong = math.maxinteger
                return (maxLong - 1) == (maxLong - 1.0)
                """.trimIndent()
            assertLuaFalse(code, "Long.MAX_VALUE-1 should NOT equal its float conversion")
        }

    @Test
    fun testLongMinValue_NotEqualToFloat() =
        runTest {
            val code =
                """
                local minLong = math.mininteger
                return minLong == (minLong + 0.0)
                """.trimIndent()
            assertLuaTrue(code, "Long.MIN_VALUE DOES equal its float conversion in Lua 5.4")
        }

    // ========================================
    // Powers of 2 (exactly representable up to 2^53)
    // ========================================

    @Test
    fun testPowerOf2_Small() =
        runTest {
            val code = "return (1 << 10) == ((1 << 10) + 0.0)"
            assertLuaTrue(code, "2^10 = 1024 should equal its float")
        }

    @Test
    fun testPowerOf2_2to30() =
        runTest {
            val code = "return (1 << 30) == ((1 << 30) + 0.0)"
            assertLuaTrue(code, "2^30 should equal its float")
        }

    @Test
    fun testPowerOf2_2to52() =
        runTest {
            val code =
                """
                local n = 4503599627370496  -- 2^52
                return n == (n + 0.0)
                """.trimIndent()
            assertLuaTrue(code, "2^52 should equal its float")
        }

    @Test
    fun testPowerOf2_2to53() =
        runTest {
            val code =
                """
                local n = 9007199254740992  -- 2^53
                return n == (n + 0.0)
                """.trimIndent()
            assertLuaTrue(code, "2^53 should equal its float")
        }

    @Test
    fun testPowerOf2_2to54() =
        runTest {
            // 2^54 is beyond exact representation for odd numbers near it
            val code =
                """
                local n = 18014398509481984  -- 2^54
                return n == (n + 0.0)
                """.trimIndent()
            // 2^54 itself is exactly representable (even though numbers near it aren't)
            assertLuaTrue(code, "2^54 itself should equal its float (power of 2)")
        }

    @Test
    fun testPowerOf2_2to54_PlusOne() =
        runTest {
            val code =
                """
                local n = 18014398509481985  -- 2^54 + 1
                return n == (n + 0.0)
                """.trimIndent()
            assertLuaFalse(code, "2^54+1 should NOT equal its float (precision lost)")
        }

    // ========================================
    // Fractional Cases
    // ========================================

    @Test
    fun testIntegerNotEqualToHalf() =
        runTest {
            val code = "return 42 == 42.5"
            assertLuaFalse(code, "Integer should not equal float with .5")
        }

    @Test
    fun testIntegerNotEqualToVerySmallFraction() =
        runTest {
            val code = "return 1000 == 1000.000001"
            assertLuaFalse(code, "Integer should not equal float with tiny fraction")
        }

    @Test
    fun testLargeIntegerNotEqualToFraction() =
        runTest {
            val code =
                """
                local n = 1000000
                return n == (n + 0.1)
                """.trimIndent()
            assertLuaFalse(code, "Large integer should not equal float with fraction")
        }

    // ========================================
    // Attrib.lua Reproduction Cases
    // ========================================

    @Test
    fun testAttribMaxintCalculation() =
        runTest {
            // This reproduces the exact calculation from attrib.lua
            val code =
                """
                local maxint = math.maxinteger
                local count = 0
                while maxint ~= (maxint + 0.0) or (maxint - 1) ~= (maxint - 1.0) do
                    maxint = maxint // 2
                    count = count + 1
                end
                -- The loop should iterate until maxint fits in double precision
                return maxint <= 9007199254740992 and count > 0
                """.trimIndent()
            assertLuaTrue(code, "Attrib maxint calculation should reduce to safe range")
        }

    @Test
    fun testAttribMaxintEquality() =
        runTest {
            vm.debugEnabled = true
            val code =
                """
                local maxint = math.maxinteger
                while maxint ~= (maxint + 0.0) or (maxint - 1) ~= (maxint - 1.0) do
                    maxint = maxint // 2
                end
                local maxintF = maxint + 0.0
                -- After loop, maxint should equal maxintF
                return maxint == maxintF and math.type(maxint) == "integer" and math.type(maxintF) == "float"
                """.trimIndent()
            assertLuaTrue(code, "Calculated maxint should equal its float version")
        }

    @Test
    fun testAttribMaxintMinusOne() =
        runTest {
            val code =
                """
                local maxint = math.maxinteger
                while maxint ~= (maxint + 0.0) or (maxint - 1) ~= (maxint - 1.0) do
                    maxint = maxint // 2
                end
                -- After loop, maxint-1 should also equal its float version
                return (maxint - 1) == (maxint - 1.0)
                """.trimIndent()
            assertLuaTrue(code, "maxint-1 should equal its float version")
        }

    // ========================================
    // Edge Cases with Arithmetic
    // ========================================

    @Test
    fun testAdditionPreservesType() =
        runTest {
            val code =
                """
                local a = 42
                local b = 0.0
                local result = a + b
                return math.type(result) == "float"
                """.trimIndent()
            assertLuaTrue(code, "Integer + float should produce float")
        }

    @Test
    fun testSubtractionPreservesType() =
        runTest {
            val code =
                """
                local a = 100
                local b = 1.0
                local result = a - b
                return math.type(result) == "float"
                """.trimIndent()
            assertLuaTrue(code, "Integer - float should produce float")
        }

    @Test
    fun testEqualityAfterArithmetic_WithinRange() =
        runTest {
            val code =
                """
                local a = 1000
                local b = (a + 0.0) - 0.0
                return a == b
                """.trimIndent()
            assertLuaTrue(code, "Integer should equal float after arithmetic (within range)")
        }

    @Test
    fun testEqualityAfterArithmetic_BeyondRange() =
        runTest {
            vm.debugEnabled = true
            val code =
                """
                local a = math.maxinteger
                local b = (a + 0.0) - 0.0
                return a == b
                """.trimIndent()
            assertLuaFalse(code, "Integer should NOT equal float after arithmetic (beyond range)")
        }

    // ========================================
    // Comparison with Table Keys
    // ========================================

    @Test
    fun testTableKey_IntegerAndFloatEquivalent_SmallValue() =
        runTest {
            val code =
                """
                local t = {}
                t[42] = "integer key"
                return t[42.0] == "integer key"
                """.trimIndent()
            assertLuaTrue(code, "Float key should access same slot as integer key (small value)")
        }

    @Test
    fun testTableKey_FloatThenInteger_SmallValue() =
        runTest {
            val code =
                """
                local t = {}
                t[42.0] = "float key"
                return t[42] == "float key"
                """.trimIndent()
            assertLuaTrue(code, "Integer key should access same slot as float key (small value)")
        }

    @Test
    fun testTableKey_IntegerAndFloatEquivalent_MaxSafe() =
        runTest {
            val code =
                """
                local n = 9007199254740991  -- 2^53-1
                local t = {}
                t[n] = "value"
                return t[n + 0.0] == "value"
                """.trimIndent()
            assertLuaTrue(code, "Float key should access same slot as integer key (max safe)")
        }

    @Test
    fun testTableKey_BeyondRange_DifferentSlots() =
        runTest {
            vm.debugEnabled = true
            val code =
                """
                local n = 9007199254740993  -- Beyond 2^53
                local t = {}
                t[n] = "integer"
                t[n + 0.0] = "float"
                -- They should be different keys
                return t[n] == "integer" and t[n + 0.0] == "float"
                """.trimIndent()
            assertLuaTrue(code, "Integer and float beyond safe range should use different table slots")
        }

    // ========================================
    // Special Values
    // ========================================

    @Test
    fun testNegativeZero() =
        runTest {
            val code = "return 0 == -0.0"
            assertLuaTrue(code, "Zero should equal negative zero")
        }

    @Test
    fun testVeryLargeFloat_NoIntegerEquivalent() =
        runTest {
            val code =
                """
                local huge = 1e100
                return 1000000 == huge
                """.trimIndent()
            assertLuaFalse(code, "Small integer should not equal huge float")
        }

    @Test
    fun testVerySmallFloat_NoIntegerEquivalent() =
        runTest {
            val code =
                """
                local tiny = 1e-100
                return 0 == tiny
                """.trimIndent()
            assertLuaFalse(code, "Zero should not equal tiny positive float")
        }

    // ========================================
    // Mathematical Operations - Type Preservation
    // ========================================

    // Addition (+)
    @Test
    fun testAddition_IntegerPlusInteger() =
        runTest {
            val code = "return math.type(5 + 3) == 'integer'"
            assertLuaTrue(code, "Integer + Integer should return integer")
        }

    @Test
    fun testAddition_IntegerPlusFloat() =
        runTest {
            val code = "return math.type(5 + 3.0) == 'float'"
            assertLuaTrue(code, "Integer + Float should return float")
        }

    @Test
    fun testAddition_FloatPlusInteger() =
        runTest {
            val code = "return math.type(5.0 + 3) == 'float'"
            assertLuaTrue(code, "Float + Integer should return float")
        }

    @Test
    fun testAddition_FloatPlusFloat() =
        runTest {
            val code = "return math.type(5.0 + 3.0) == 'float'"
            assertLuaTrue(code, "Float + Float should return float")
        }

    @Test
    fun testAddition_IntegerOverflow() =
        runTest {
            val code =
                """
                local max = math.maxinteger
                local result = max + 1
                return math.type(result) == 'integer' and result == math.mininteger
                """.trimIndent()
            assertLuaTrue(code, "Integer overflow should wrap to mininteger")
        }

    @Test
    fun testAddition_LargeIntegers() =
        runTest {
            val code =
                """
                local a = 9007199254740992  -- 2^53
                local b = 1
                return math.type(a + b) == 'integer' and (a + b) == 9007199254740993
                """.trimIndent()
            assertLuaTrue(code, "Large integer + integer should preserve type")
        }

    // Subtraction (-)
    @Test
    fun testSubtraction_IntegerMinusInteger() =
        runTest {
            val code = "return math.type(10 - 3) == 'integer'"
            assertLuaTrue(code, "Integer - Integer should return integer")
        }

    @Test
    fun testSubtraction_IntegerMinusFloat() =
        runTest {
            val code = "return math.type(10 - 3.0) == 'float'"
            assertLuaTrue(code, "Integer - Float should return float")
        }

    @Test
    fun testSubtraction_FloatMinusInteger() =
        runTest {
            val code = "return math.type(10.0 - 3) == 'float'"
            assertLuaTrue(code, "Float - Integer should return float")
        }

    @Test
    fun testSubtraction_FloatMinusFloat() =
        runTest {
            val code = "return math.type(10.0 - 3.0) == 'float'"
            assertLuaTrue(code, "Float - Float should return float")
        }

    @Test
    fun testSubtraction_IntegerUnderflow() =
        runTest {
            val code =
                """
                local min = math.mininteger
                local result = min - 1
                return math.type(result) == 'integer' and result == math.maxinteger
                """.trimIndent()
            assertLuaTrue(code, "Integer underflow should wrap to maxinteger")
        }

    @Test
    fun testSubtraction_NegativeResult() =
        runTest {
            val code = "return math.type(5 - 10) == 'integer' and (5 - 10) == -5"
            assertLuaTrue(code, "Integer subtraction with negative result should be integer")
        }

    // Multiplication (*)
    @Test
    fun testMultiplication_IntegerTimesInteger() =
        runTest {
            val code = "return math.type(5 * 3) == 'integer'"
            assertLuaTrue(code, "Integer * Integer should return integer")
        }

    @Test
    fun testMultiplication_IntegerTimesFloat() =
        runTest {
            val code = "return math.type(5 * 3.0) == 'float'"
            assertLuaTrue(code, "Integer * Float should return float")
        }

    @Test
    fun testMultiplication_FloatTimesInteger() =
        runTest {
            val code = "return math.type(5.0 * 3) == 'float'"
            assertLuaTrue(code, "Float * Integer should return float")
        }

    @Test
    fun testMultiplication_FloatTimesFloat() =
        runTest {
            val code = "return math.type(5.0 * 3.0) == 'float'"
            assertLuaTrue(code, "Float * Float should return float")
        }

    @Test
    fun testMultiplication_IntegerOverflow() =
        runTest {
            val code =
                """
                local max = math.maxinteger
                local result = max * 2
                return math.type(result) == 'integer'
                """.trimIndent()
            assertLuaTrue(code, "Integer multiplication overflow should wrap (stay integer)")
        }

    @Test
    fun testMultiplication_LargeIntegers() =
        runTest {
            val code =
                """
                local a = 1000000
                local b = 1000000
                return math.type(a * b) == 'integer' and (a * b) == 1000000000000
                """.trimIndent()
            assertLuaTrue(code, "Large integer multiplication should preserve type")
        }

    @Test
    fun testMultiplication_Zero() =
        runTest {
            val code = "return math.type(1000000 * 0) == 'integer' and (1000000 * 0) == 0"
            assertLuaTrue(code, "Integer * 0 should return integer 0")
        }

    // Division (/)
    @Test
    fun testDivision_IntegerDivInteger_AlwaysFloat() =
        runTest {
            val code = "return math.type(10 / 2) == 'float'"
            assertLuaTrue(code, "Integer / Integer should always return float")
        }

    @Test
    fun testDivision_IntegerDivInteger_ExactResult() =
        runTest {
            val code = "return (10 / 2) == 5.0"
            assertLuaTrue(code, "10 / 2 should equal 5.0")
        }

    @Test
    fun testDivision_IntegerDivInteger_FractionalResult() =
        runTest {
            val code = "return (10 / 3) == (10.0 / 3.0)"
            assertLuaTrue(code, "10 / 3 should equal 10.0 / 3.0")
        }

    @Test
    fun testDivision_IntegerDivFloat() =
        runTest {
            val code = "return math.type(10 / 2.0) == 'float'"
            assertLuaTrue(code, "Integer / Float should return float")
        }

    @Test
    fun testDivision_FloatDivInteger() =
        runTest {
            val code = "return math.type(10.0 / 2) == 'float'"
            assertLuaTrue(code, "Float / Integer should return float")
        }

    @Test
    fun testDivision_FloatDivFloat() =
        runTest {
            val code = "return math.type(10.0 / 2.0) == 'float'"
            assertLuaTrue(code, "Float / Float should return float")
        }

    @Test
    fun testDivision_LargeIntegers() =
        runTest {
            val code =
                """
                local a = 1000000000000
                local b = 1000000
                return math.type(a / b) == 'float' and (a / b) == 1000000.0
                """.trimIndent()
            assertLuaTrue(code, "Large integer division should return float")
        }

    // Floor Division (//)
    @Test
    fun testFloorDivision_IntegerDivInteger() =
        runTest {
            val code = "return math.type(10 // 3) == 'integer'"
            assertLuaTrue(code, "Integer // Integer should return integer")
        }

    @Test
    fun testFloorDivision_IntegerDivInteger_ExactResult() =
        runTest {
            val code = "return (10 // 2) == 5"
            assertLuaTrue(code, "10 // 2 should equal integer 5")
        }

    @Test
    fun testFloorDivision_IntegerDivInteger_Floors() =
        runTest {
            val code = "return (10 // 3) == 3"
            assertLuaTrue(code, "10 // 3 should equal integer 3 (floor)")
        }

    @Test
    fun testFloorDivision_IntegerDivFloat() =
        runTest {
            val code = "return math.type(10 // 3.0) == 'float'"
            assertLuaTrue(code, "Integer // Float should return float")
        }

    @Test
    fun testFloorDivision_FloatDivInteger() =
        runTest {
            val code = "return math.type(10.0 // 3) == 'float'"
            assertLuaTrue(code, "Float // Integer should return float")
        }

    @Test
    fun testFloorDivision_FloatDivFloat() =
        runTest {
            val code = "return math.type(10.0 // 3.0) == 'float'"
            assertLuaTrue(code, "Float // Float should return float")
        }

    @Test
    fun testFloorDivision_NegativeOperands() =
        runTest {
            val code = "return (-10 // 3) == -4"
            assertLuaTrue(code, "-10 // 3 should equal -4 (floor toward negative)")
        }

    @Test
    fun testFloorDivision_LargeIntegers() =
        runTest {
            val code =
                """
                local max = math.maxinteger
                local result = max // 2
                return math.type(result) == 'integer' and result == 4611686018427387903
                """.trimIndent()
            assertLuaTrue(code, "Large integer // integer should preserve type")
        }

    // Modulo (%)
    @Test
    fun testModulo_IntegerModInteger() =
        runTest {
            val code = "return math.type(10 % 3) == 'integer'"
            assertLuaTrue(code, "Integer % Integer should return integer")
        }

    @Test
    fun testModulo_IntegerModInteger_Result() =
        runTest {
            val code = "return (10 % 3) == 1"
            assertLuaTrue(code, "10 % 3 should equal 1")
        }

    @Test
    fun testModulo_IntegerModFloat() =
        runTest {
            val code = "return math.type(10 % 3.0) == 'float'"
            assertLuaTrue(code, "Integer % Float should return float")
        }

    @Test
    fun testModulo_FloatModInteger() =
        runTest {
            val code = "return math.type(10.0 % 3) == 'float'"
            assertLuaTrue(code, "Float % Integer should return float")
        }

    @Test
    fun testModulo_FloatModFloat() =
        runTest {
            val code = "return math.type(10.0 % 3.0) == 'float'"
            assertLuaTrue(code, "Float % Float should return float")
        }

    @Test
    fun testModulo_NegativeOperands() =
        runTest {
            val code = "return (-10 % 3) == 2"
            assertLuaTrue(code, "-10 % 3 should equal 2 (Lua modulo semantics)")
        }

    @Test
    fun testModulo_LargeIntegers() =
        runTest {
            val code =
                """
                local a = 9007199254740992  -- 2^53
                local b = 1000
                return math.type(a % b) == 'integer'
                """.trimIndent()
            assertLuaTrue(code, "Large integer % integer should preserve type")
        }

    // Exponentiation (^)
    @Test
    fun testExponentiation_IntegerPowInteger_SmallResult() =
        runTest {
            val code = "return math.type(2 ^ 3) == 'float'"
            assertLuaTrue(code, "Power operator (^) ALWAYS returns float in Lua 5.4, even for integer^integer")
        }

    @Test
    fun testExponentiation_IntegerPowInteger_Result() =
        runTest {
            val code = "return (2 ^ 10) == 1024"
            assertLuaTrue(code, "2 ^ 10 should equal integer 1024")
        }

    @Test
    fun testExponentiation_IntegerPowFloat() =
        runTest {
            val code = "return math.type(2 ^ 3.0) == 'float'"
            assertLuaTrue(code, "Integer ^ Float should return float")
        }

    @Test
    fun testExponentiation_FloatPowInteger() =
        runTest {
            val code = "return math.type(2.0 ^ 3) == 'float'"
            assertLuaTrue(code, "Float ^ Integer should return float")
        }

    @Test
    fun testExponentiation_FloatPowFloat() =
        runTest {
            val code = "return math.type(2.0 ^ 3.0) == 'float'"
            assertLuaTrue(code, "Float ^ Float should return float")
        }

    @Test
    fun testExponentiation_NegativeExponent() =
        runTest {
            val code = "return math.type(2 ^ -3) == 'float'"
            assertLuaTrue(code, "Integer ^ Negative should return float")
        }

    @Test
    fun testExponentiation_FractionalResult() =
        runTest {
            val code = "return (2 ^ -3) == 0.125"
            assertLuaTrue(code, "2 ^ -3 should equal 0.125")
        }

    @Test
    fun testExponentiation_LargeResult() =
        runTest {
            val code =
                """
                local result = 2 ^ 60
                return math.type(result) == 'float' and result == 1152921504606846976.0
                """.trimIndent()
            assertLuaTrue(code, "Power operator (^) ALWAYS returns float, even for large integer exponents")
        }

    // Unary Negation (-)
    @Test
    fun testUnaryNegation_Integer() =
        runTest {
            val code = "return math.type(-42) == 'integer'"
            assertLuaTrue(code, "Unary negation of integer should return integer")
        }

    @Test
    fun testUnaryNegation_Float() =
        runTest {
            val code = "return math.type(-42.5) == 'float'"
            assertLuaTrue(code, "Unary negation of float should return float")
        }

    @Test
    fun testUnaryNegation_Zero() =
        runTest {
            val code = "return math.type(-0) == 'integer' and (-0) == 0"
            assertLuaTrue(code, "Unary negation of integer 0 should return integer 0")
        }

    @Test
    fun testUnaryNegation_LargeInteger() =
        runTest {
            val code =
                """
                local max = math.maxinteger
                local result = -max
                return math.type(result) == 'integer' and result == -9223372036854775807
                """.trimIndent()
            assertLuaTrue(code, "Negation of maxinteger should return integer")
        }

    @Test
    fun testUnaryNegation_MinInteger() =
        runTest {
            val code =
                """
                local min = math.mininteger
                local result = -min
                return math.type(result) == 'integer'
                """.trimIndent()
            assertLuaTrue(code, "Negation of mininteger should return integer (overflow)")
        }

    // Mixed Operations - Complex Expressions
    @Test
    fun testMixedOperations_IntegerChain() =
        runTest {
            val code = "return math.type(10 + 5 - 3 * 2) == 'integer'"
            assertLuaTrue(code, "Chain of integer operations should return integer")
        }

    @Test
    fun testMixedOperations_FloatContamination() =
        runTest {
            val code = "return math.type(10 + 5.0 - 3 * 2) == 'float'"
            assertLuaTrue(code, "One float in chain contaminates result to float")
        }

    @Test
    fun testMixedOperations_DivisionMakesFloat() =
        runTest {
            val code = "return math.type((10 + 5) / 3) == 'float'"
            assertLuaTrue(code, "Division makes result float even with integer operands")
        }

    @Test
    fun testMixedOperations_FloorDivisionKeepsInteger() =
        runTest {
            val code = "return math.type((10 + 5) // 3) == 'integer'"
            assertLuaTrue(code, "Floor division keeps integer when operands are integer")
        }

    @Test
    fun testMixedOperations_ParenthesizedIntegerMath() =
        runTest {
            val code = "return math.type(((10 + 5) * 2) // 3) == 'integer'"
            assertLuaTrue(code, "Parenthesized integer math with floor division should be integer")
        }

    // Edge Cases - Special Values
    @Test
    fun testSpecialValue_FloatZero() =
        runTest {
            val code = "return math.type(0.0) == 'float' and math.type(0) == 'integer'"
            assertLuaTrue(code, "0.0 is float, 0 is integer")
        }

    @Test
    fun testSpecialValue_NegativeZero() =
        runTest {
            val code = "return math.type(-0.0) == 'float' and (-0.0) == 0.0"
            assertLuaTrue(code, "-0.0 should be float and equal to 0.0")
        }

    @Test
    fun testSpecialValue_IntegerOne() =
        runTest {
            val code = "return math.type(1) == 'integer' and math.type(1.0) == 'float'"
            assertLuaTrue(code, "1 is integer, 1.0 is float")
        }
}
