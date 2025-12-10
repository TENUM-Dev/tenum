package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.jvm.JvmName
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * PHASE 5.2: Standard Library - Math Arithmetic Operations
 *
 * Tests arithmetic operations and edge cases.
 * Based on: math.lua lines 114-155, 201-333, 519-637
 *
 * Coverage:
 * - Modulo operator (%) with various types
 * - Floor division (//) operations
 * - Arithmetic with extreme values
 * - Integer overflow and underflow behavior
 * - Comparison operations between floats and integers
 */
class MathArithmeticCompatTest : LuaCompatTestBase() {
    // ========== Modulo Operator ==========

    @Test
    fun testModuloBasic() =
        runTest {
            // From math.lua:519-537
            assertLuaTrue("return (-4 % 3) == 2 and math.type(-4 % 3) == 'integer'")
            assertLuaTrue("return (4 % -3) == -2 and math.type(4 % -3) == 'integer'")
            assertLuaTrue("return (-4.0 % 3) == 2.0 and math.type(-4.0 % 3) == 'float'")
            assertLuaTrue("return (4 % -3.0) == -2.0 and math.type(4 % -3.0) == 'float'")
            assertLuaTrue("return (4 % -5) == -1 and math.type(4 % -5) == 'integer'")
            assertLuaTrue("return (4 % -5.0) == -1.0 and math.type(4 % -5.0) == 'float'")
            assertLuaTrue("return (4 % 5) == 4 and math.type(4 % 5) == 'integer'")
            assertLuaTrue("return (4 % 5.0) == 4.0 and math.type(4 % 5.0) == 'float'")
            assertLuaTrue("return (-4 % -5) == -4 and math.type(-4 % -5) == 'integer'")
            assertLuaTrue("return (-4 % -5.0) == -4.0 and math.type(-4 % -5.0) == 'float'")
            assertLuaTrue("return (-4 % 5) == 1 and math.type(-4 % 5) == 'integer'")
            assertLuaTrue("return (-4 % 5.0) == 1.0 and math.type(-4 % 5.0) == 'float'")
        }

    @Test
    fun testModuloWithDecimals() =
        runTest {
            // From math.lua:538-542
            assertLuaNumber("return 4.25 % 4", 0.25)
            assertLuaNumber("return 10.0 % 2", 0.0)
            // Note: -10.0 % 2 and -10.0 % -2 return -0.0 in Lua (negative zero)
            assertLuaNumber("return -10.0 % 2", -0.0)
            assertLuaNumber("return -10.0 % -2", -0.0)

            // Pi modulo tests
            assertLuaTrue("return math.abs((math.pi - math.pi % 1) - 3) < 1e-10")
            assertLuaTrue("return math.abs((math.pi - math.pi % 0.001) - 3.141) < 1e-10")
        }

    @Test
    fun testModuloVerySmallNumbers() =
        runTest {
            // From math.lua:544-558 - testing with very small numbers
            val code =
                """
                -- Find smallest representable positive number
                local i, j = 0, 20000
                while i < j do
                    local m = (i + j) // 2
                    if 10^-m > 0 then
                        i = m + 1
                    else
                        j = m
                    end
                end
                
                local b = 10^-(i - (i // 10))   -- a very small number
                if b <= 0 or b * b ~= 0 then return true end  -- Skip if no subnormal support
                
                local delta = b / 1000
                local function eq(a, b, delta)
                    return math.abs(a - b) <= delta
                end
                
                return eq((2.1 * b) % (2 * b), (0.1 * b), delta) and
                       eq((-2.1 * b) % (2 * b), (2 * b) - (0.1 * b), delta) and
                       eq((2.1 * b) % (-2 * b), (0.1 * b) - (2 * b), delta) and
                       eq((-2.1 * b) % (-2 * b), (-0.1 * b), delta)
                """.trimIndent()
            assertLuaTrue(code, "Modulo with very small numbers should work correctly")
        }

    @Test
    fun testModuloIntegerFloatConsistency() =
        runTest {
            // From math.lua:561-572 - basic consistency between integer and float modulo
            for (i in -10..10) {
                for (j in -10..10) {
                    if (j != 0) {
                        val code = "return (($i + 0.0) % $j) == ($i % $j)"
                        assertLuaTrue(code, "Integer and float modulo should be consistent for $i % $j")
                    }
                }
            }
        }

    @Test
    fun testModuloPowersOfTwo() =
        runTest {
            // From math.lua:574-580
            for (i in 0..10) {
                for (j in -10..10) {
                    if (j != 0) {
                        val code = "return (2^$i) % $j == (1 << $i) % $j"
                        assertLuaTrue(code, "Powers of 2 modulo should be consistent: 2^$i % $j")
                    }
                }
            }
        }

    @Test
    fun testModuloPrecisionWithLargeIntegers() =
        runTest {
            // From math.lua:597-603 - Tests precision of modulo for large left-shifted numbers
            // Critical: Integers must remain as integers (LuaLong), not convert to Double
            // When (1 << 54) is converted to Double, it loses precision and (1<<54) % 3 gives wrong result
            execute(
                """
                local i = 10
                while (1 << i) > 0 do
                    assert((1 << i) % 3 == i % 2 + 1, string.format("Failed at i=%d", i))
                    i = i + 1
                end
                """.trimIndent(),
            )
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testModuloExtremeCases() =
        runTest {
            // From math.lua:594-603
            assertLuaTrue("return (math.mininteger % math.mininteger) == 0")
            assertLuaTrue("return (math.maxinteger % math.maxinteger) == 0")
            assertLuaTrue("return ((math.mininteger + 1) % math.mininteger) == (math.mininteger + 1)")
            assertLuaTrue("return ((math.maxinteger - 1) % math.maxinteger) == (math.maxinteger - 1)")
            assertLuaTrue("return (math.mininteger % math.maxinteger) == (math.maxinteger - 1)")
            assertLuaTrue("return (math.mininteger % -1) == 0")
            assertLuaTrue("return (math.mininteger % -2) == 0")
            assertLuaTrue("return (math.maxinteger % -2) == -1")
        }

    // ========== Floor Division ==========

    @Test
    fun testFloorDivisionBasic() =
        runTest {
            // From math.lua:119-133
            val testCases = listOf(-16, -15, -3, -2, -1, 0, 1, 2, 3, 15)
            for (i in testCases) {
                for (j in testCases) {
                    if (j != 0) {
                        // Test floor division formula
                        val code = "return ($i // $j) == math.floor($i / $j)"
                        assertLuaTrue(code, "Floor division formula should hold for $i // $j")
                    }
                }
            }
        }

    @Test
    fun testFloorDivisionTypes() =
        runTest {
            // From math.lua:135-149
            assertLuaTrue("return (1 // 0.0) == (1 / 0)") // infinity
            assertLuaTrue("return (-1 // 0.0) == (-1 / 0)") // -infinity
            assertLuaTrue("return (3.5 // 1.5) == 2.0 and math.type(3.5 // 1.5) == 'float'")
            assertLuaTrue("return (3.5 // -1.5) == -3.0 and math.type(3.5 // -1.5) == 'float'")

            // Test different opcode variations
            val code =
                """
                local x, y
                x = 1; local r1 = (x // 0.0) == (1/0)
                x = 1.0; local r2 = (x // 0) == (1/0)
                x = 3.5; local r3 = (x // 1) == 3.0 and math.type(x // 1) == 'float'
                local r4 = (x // -1) == -4.0 and math.type(x // -1) == 'float'
                
                x = 3.5; y = 1.5; local r5 = (x // y) == 2.0 and math.type(x // y) == 'float'
                x = 3.5; y = -1.5; local r6 = (x // y) == -3.0 and math.type(x // y) == 'float'
                
                return r1 and r2 and r3 and r4 and r5 and r6
                """.trimIndent()
            assertLuaTrue(code, "Floor division should work with various operand types")
        }

    @Test
    fun testFloorDivisionExtremes() =
        runTest {
            // From math.lua:151-161
            assertLuaTrue("return (math.maxinteger // math.maxinteger) == 1")
            assertLuaTrue("return (math.maxinteger // 1) == math.maxinteger")
            assertLuaTrue("return ((math.maxinteger - 1) // math.maxinteger) == 0")
            assertLuaTrue("return (math.maxinteger // (math.maxinteger - 1)) == 1")
            assertLuaTrue("return (math.mininteger // math.mininteger) == 1")
            assertLuaTrue("return ((math.mininteger + 1) // math.mininteger) == 0")
            assertLuaTrue("return (math.mininteger // (math.mininteger + 1)) == 1")
            assertLuaTrue("return (math.mininteger // 1) == math.mininteger")
            assertLuaTrue("return (math.mininteger // -1) == -math.mininteger")
            assertLuaTrue("return (math.maxinteger // -1) == -math.maxinteger")
        }

    // ========== Integer Arithmetic Edge Cases ==========

    @Test
    fun testIntegerArithmetic() =
        runTest {
            // From math.lua:114-118
            assertLuaTrue("return math.mininteger < (math.mininteger + 1)")
            assertLuaTrue("return (math.maxinteger - 1) < math.maxinteger")
            assertLuaTrue("return (0 - math.mininteger) == math.mininteger") // overflow wraps
            assertLuaTrue("return (math.mininteger * math.mininteger) == 0") // overflow wraps to 0
            assertLuaTrue("return (math.maxinteger * math.maxinteger * math.maxinteger) == math.maxinteger")
        }

    @Test
    fun testNegativeExponents() =
        runTest {
            // From math.lua:163-173
            assertLuaNumber("return 2^-3", 1.0 / 8.0)
            assertLuaTrue("return math.abs((-3)^-3 - (1 / (-3)^3)) < 1e-10")

            // Test various combinations
            for (i in -3..3) {
                for (j in -3..3) {
                    if (i != 0 || j > 0) { // avoid domain errors 0^(-n)
                        val code = "return math.abs(($i)^($j) - (1 / ($i)^(-($j)))) < 1e-10"
                        assertLuaTrue(code, "Negative exponent identity should hold for $i^$j")
                    }
                }
            }
        }

    // ========== Float/Integer Comparisons ==========

    @Test
    @Ignore // TODO: readable once supported
    fun testFloatIntegerComparisons() =
        runTest {
            // From math.lua:201-217
            assertLuaTrue("return 1 < 1.1 and not (1 < 0.9)")
            assertLuaTrue("return 1 <= 1.1 and not (1 <= 0.9)")
            assertLuaTrue("return -1 < -0.9 and not (-1 < -1.1)")
            assertLuaTrue("return 1 <= 1.1 and not (-1 <= -1.1)")
            assertLuaTrue("return -1 < -0.9 and not (-1 < -1.1)")
            assertLuaTrue("return -1 <= -0.9 and not (-1 <= -1.1)")
            assertLuaTrue("return math.mininteger <= (math.mininteger + 0.0)")
            assertLuaTrue("return (math.mininteger + 0.0) <= math.mininteger")
            assertLuaTrue("return not (math.mininteger < (math.mininteger + 0.0))")
            assertLuaTrue("return not ((math.mininteger + 0.0) < math.mininteger)")
            assertLuaTrue("return math.maxinteger < (math.mininteger * -1.0)")
            assertLuaTrue("return math.maxinteger <= (math.mininteger * -1.0)")
        }

    @Test
    fun testFloatIntegerBoundaries() =
        runTest {
            // From math.lua:174-200 and 218-266
            val code =
                """
                -- Test basic boundary conditions
                local intbits = 64  -- Assume 64-bit integers
                local floatbits = 53  -- IEEE double precision mantissa
                
                if floatbits < intbits then
                    -- Float cannot represent all integers
                    local result1 = (2.0^floatbits == (1 << floatbits))
                    local result2 = ((2.0^floatbits - 1.0) == ((1 << floatbits) - 1.0))
                    local result3 = ((2.0^floatbits - 1.0) ~= (1 << floatbits))
                    local result4 = ((2.0^floatbits + 1.0) ~= ((1 << floatbits) + 1))
                    return result1 and result2 and result3 and result4
                else
                    -- Float can represent all integers
                    local result1 = (math.maxinteger == (math.maxinteger + 0.0))
                    local result2 = ((math.maxinteger - 1) == (math.maxinteger - 1.0))
                    local result3 = ((math.mininteger + 1) == (math.mininteger + 1.0))
                    local result4 = (math.maxinteger ~= (math.maxinteger - 1.0))
                    return result1 and result2 and result3 and result4
                end
                """.trimIndent()
            assertLuaTrue(code, "Float/integer boundary conditions should be correct")
        }

    @Test
    fun testMaxIntegerVsFloatComparison() =
        runTest {
            // From math.lua:214-220 - Tests the specific case where 2^63 (as float)
            // is compared with Long.MAX_VALUE (2^63-1 as integer)
            // This tests the bug where Long.MAX_VALUE.toDouble() rounds to 2^63,
            // making them equal as doubles but not mathematically equal
            val code =
                """
                local maxint = math.maxinteger
                local minint = math.mininteger
                local intbits = math.floor(math.log(maxint, 2) + 0.5) + 1
                
                -- fmaxi1 = 2^(intbits-1) = 2^63 (as float, approximately 9.223372e+18)
                -- maxint = 2^63 - 1 = 9223372036854775807 (as integer)
                -- When maxint is converted to double, it rounds to 2^63
                local fmaxi1 = 2^(intbits - 1)
                
                -- These assertions test that comparisons handle the rounding correctly
                local test1 = (maxint < fmaxi1)           -- 2^63-1 < 2^63 should be true
                local test2 = (maxint <= fmaxi1)          -- 2^63-1 <= 2^63 should be true  
                local test3 = not (fmaxi1 <= maxint)      -- NOT(2^63 <= 2^63-1) should be true
                local test4 = (minint <= -2^(intbits - 1))
                local test5 = (-2^(intbits - 1) <= minint)
                
                return test1 and test2 and test3 and test4 and test5
                """.trimIndent()
            assertLuaTrue(code, "Comparison between 2^63 (float) and Long.MAX_VALUE should handle rounding correctly")
        }

    @Test
    fun testNaNComparisons() =
        runTest {
            // From math.lua:268-285
            val code =
                """
                local NaN = 0/0
                local result1 = not (NaN < 0)
                local result2 = not (NaN > math.mininteger)
                local result3 = not (NaN <= -9)
                local result4 = not (NaN <= math.maxinteger)
                local result5 = not (NaN < math.maxinteger)
                local result6 = not (math.mininteger <= NaN)
                local result7 = not (math.mininteger < NaN)
                local result8 = not (4 <= NaN)
                local result9 = not (4 < NaN)
                return result1 and result2 and result3 and result4 and result5 and 
                       result6 and result7 and result8 and result9
                """.trimIndent()
            assertLuaTrue(code, "NaN should not compare as less or greater than any number")
        }

    @Test
    fun testArithmeticOverflowErrors() =
        runTest {
            // From math.lua:287-301 - compile-time overflow checks
            assertError("divide by zero") {
                execute("return 2 // 0")
            }
        }

    // ========== Helper Functions ==========

    @Test
    fun testCeilFloorWithMinIntegerPlusZero() =
        runTest {
            // From math.lua:690 - math.ceil(minint + 0.0) must return integer type
            // When minint is converted to float and back via ceil, it must preserve integer type
            assertLuaTrue("return math.ceil(math.mininteger + 0.0) == math.mininteger")
            assertLuaTrue("return math.type(math.ceil(math.mininteger + 0.0)) == 'integer'")

            // Same for floor
            assertLuaTrue("return math.floor(math.mininteger + 0.0) == math.mininteger")
            assertLuaTrue("return math.type(math.floor(math.mininteger + 0.0)) == 'integer'")
        }

    // ========== Type Preservation in Arithmetic ==========

    @Test
    fun testArithmeticTypePreservation() =
        runTest {
            // From math.lua:728-731 - When any operand is float, result must be float
            // This tests the core Lua 5.4 semantics: integer op integer = integer, but any float = float

            // Addition: integer + float = float
            assertLuaTrue("return math.type(2 + 0.0) == 'float'")
            assertLuaTrue("return math.type(0.0 + 2) == 'float'")
            assertLuaTrue("return math.type(2.0 + 3.0) == 'float'")
            assertLuaTrue("return math.type(2 + 3) == 'integer'")

            // Subtraction: integer - float = float
            assertLuaTrue("return math.type(5 - 0.0) == 'float'")
            assertLuaTrue("return math.type(5.0 - 2) == 'float'")
            assertLuaTrue("return math.type(5 - 2) == 'integer'")

            // Multiplication: integer * float = float
            assertLuaTrue("return math.type(3 * 1.0) == 'float'")
            assertLuaTrue("return math.type(1.0 * 3) == 'float'")
            assertLuaTrue("return math.type(3 * 2) == 'integer'")

            // Modulo: integer % float = float
            assertLuaTrue("return math.type(5 % 2.0) == 'float'")
            assertLuaTrue("return math.type(5.0 % 2) == 'float'")
            assertLuaTrue("return math.type(5 % 2) == 'integer'")

            // The critical test case from math.lua:730-731
            // math.fmod(i + 0.0, j) should receive a float as first argument
            val i = 2
            val j = 5
            assertLuaTrue("local i, j = $i, $j; local mf = math.fmod(i + 0.0, j); return math.type(mf) == 'float'")
        }

    @JvmName("assertErrorJvm")
    private fun assertError(
        expectedMessage: String,
        block: () -> Unit,
    ) {
        try {
            block()
            kotlin.test.fail("Expected error with message containing '$expectedMessage'")
        } catch (e: Exception) {
            kotlin.test.assertTrue(
                e.message?.contains(expectedMessage, ignoreCase = true) == true,
                "Expected error message to contain '$expectedMessage', but got: ${e.message}",
            )
        }
    }
}
