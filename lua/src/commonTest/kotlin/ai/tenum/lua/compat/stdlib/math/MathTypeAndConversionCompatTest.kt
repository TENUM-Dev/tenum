package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNil
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PHASE 5.2: Standard Library - Math Type and Conversion Functions
 *
 * Tests type checking and conversion functions in the math library.
 * Based on: math.lua lines 35-36, 397-503, 705-712, 638-653
 *
 * Coverage:
 * - math.type - check number type (integer/float)
 * - math.tointeger - convert to integer if possible
 * - math.ult - unsigned less than comparison
 * - tonumber - string to number conversion (various bases)
 */
class MathTypeAndConversionCompatTest : LuaCompatTestBase() {
    // ========== Math Type Function ==========

    @Test
    fun testMathType() =
        runTest {
            assertLuaString("return math.type(3)", "integer")
            assertLuaString("return math.type(3.5)", "float")
            assertLuaNil("return math.type('x')")
            assertLuaNil("return math.type(nil)")
            assertLuaNil("return math.type({})")
            assertLuaNil("return math.type(true)")
        }

    @Test
    fun testMathTypeWithIntegerAndFloat() =
        runTest {
            // From math.lua:35-36
            assertLuaTrue("return math.type(0) == 'integer' and math.type(0.0) == 'float' and not math.type('10')")

            // Test that integer values return "integer"
            assertLuaString("return math.type(42)", "integer")
            assertLuaString("return math.type(math.maxinteger)", "integer")
            assertLuaString("return math.type(-5)", "integer")
            assertLuaString("return math.type(math.mininteger)", "integer")

            // Test that float values return "float"
            assertLuaString("return math.type(42.5)", "float")
            assertLuaString("return math.type(math.pi)", "float")
            assertLuaString("return math.type(math.huge)", "float")
            assertLuaString("return math.type(-math.huge)", "float")

            // Test that integer + 0.0 becomes float
            assertLuaString("return math.type(42 + 0.0)", "float")

            // Test that non-numbers return nil
            val result = execute("return math.type('hello')")
            assertTrue(result is LuaNil)
        }

    // ========== Math ToInteger Function ==========

    @Test
    fun testMathTointeger() =
        runTest {
            assertLuaNumber("return math.tointeger(3)", 3.0)
            assertLuaNumber("return math.tointeger(3.0)", 3.0)
            assertLuaNil("return math.tointeger(3.5)")
            assertLuaNil("return math.tointeger('x')")
            assertLuaNil("return math.tointeger(nil)")
        }

    @Test
    fun testMathTointegerExtensive() =
        runTest {
            // From math.lua:705-712
            assertLuaTrue(
                "return math.tointeger(math.mininteger) == math.mininteger and math.type(math.tointeger(math.mininteger)) == 'integer'",
            )
            assertLuaTrue("return math.tointeger(tostring(math.mininteger)) == math.mininteger")
            assertLuaTrue(
                "return math.tointeger(math.maxinteger) == math.maxinteger and math.type(math.tointeger(math.maxinteger)) == 'integer'",
            )
            assertLuaTrue("return math.tointeger(tostring(math.maxinteger)) == math.maxinteger")
            assertLuaTrue("return math.tointeger(math.mininteger + 0.0) == math.mininteger")

            // Should return nil for non-representable integers
            assertLuaNil("return math.tointeger(0.0 - math.mininteger)")
            assertLuaNil("return math.tointeger(math.pi)")
            assertLuaNil("return math.tointeger(-math.pi)")
            assertLuaNil("return math.tointeger(math.huge)")
            assertLuaNil("return math.tointeger(-math.huge)")
            assertLuaNil("return math.tointeger(0/0)") // NaN

            // String conversions
            assertLuaNumber("return math.tointeger('34.0')", 34.0)
            assertLuaNil("return math.tointeger('34.3')")
            assertLuaNil("return math.tointeger({})")
        }

    // ========== Math ULT Function (Unsigned Less Than) ==========

    @Test
    fun testMathUlt() =
        runTest {
            // Basic unsigned comparisons
            assertLuaBoolean("return math.ult(2, 3)", true)
            assertLuaBoolean("return math.ult(3, 2)", false)
            assertLuaBoolean("return math.ult(2, 2)", false)
        }

    @Test
    fun testMathUltWithNegativeNumbers() =
        runTest {
            // From math.lua:638-653 - Test with negative numbers (treated as large unsigned)
            assertLuaBoolean("return math.ult(-1, 1)", false) // -1 as unsigned is very large
            assertLuaBoolean("return math.ult(-2, -1)", true)
            assertLuaBoolean("return math.ult(2, -1)", true) // 2 < large unsigned value of -1
            assertLuaBoolean("return math.ult(-2, -2)", false)
            assertLuaBoolean("return math.ult(math.maxinteger, math.mininteger)", true)
            assertLuaBoolean("return math.ult(math.mininteger, math.maxinteger)", false)
        }

    // ========== ToNumber Function ==========

    @Test
    fun testTonumberBasic() =
        runTest {
            // From math.lua:397-409
            assertLuaNumber("return tonumber(3.4)", 3.4)
            assertLuaTrue("return tonumber(3) == 3 and math.type(tonumber(3)) == 'integer'")
            assertLuaTrue("return tonumber(math.maxinteger) == math.maxinteger and tonumber(math.mininteger) == math.mininteger")
            assertLuaNumber("return tonumber(1/0)", Double.POSITIVE_INFINITY) // infinity
            assertLuaNumber("return tonumber(-1/0)", Double.NEGATIVE_INFINITY) // -infinity
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testTonumberWithStrings() =
        runTest {
            // From math.lua:410-424
            assertLuaNumber("return tonumber('0')", 0.0)
            assertLuaNil("return tonumber('')")
            assertLuaNil("return tonumber('  ')")
            assertLuaNil("return tonumber('-')")
            assertLuaNil("return tonumber('  -0x ')")
            assertLuaNil("return tonumber({})")

            // Valid number formats
            assertLuaTrue("return tonumber('+0.01') == 1/100")
            assertLuaTrue("return tonumber('+.01') == 0.01")
            assertLuaTrue("return tonumber('.01') == 0.01")
            assertLuaTrue("return tonumber('-1.') == -1")
            assertLuaTrue("return tonumber('+1.') == 1")

            // Invalid formats
            assertLuaNil("return tonumber('+ 0.01')")
            assertLuaNil("return tonumber('+.e1')")
            assertLuaNil("return tonumber('1e')")
            assertLuaNil("return tonumber('1.0e+')")
            assertLuaNil("return tonumber('.')")

            // Scientific notation
            assertLuaNumber("return tonumber('-012')", -10.0)
            assertLuaNumber("return tonumber('-1.2e2')", -120.0)
        }

    @Test
    fun testTonumberWithBase() =
        runTest {
            // From math.lua:435-447
            assertLuaNumber("return tonumber('  001010  ', 2)", 10.0)
            assertLuaNumber("return tonumber('  001010  ', 10)", 1010.0)
            assertLuaNumber("return tonumber('  -1010  ', 2)", -10.0)
            assertLuaNumber("return tonumber('10', 36)", 36.0)
            assertLuaNumber("return tonumber('  -10  ', 36)", -36.0)
            assertLuaNumber("return tonumber('  +1Z  ', 36)", 71.0) // 36 + 35
            assertLuaNumber("return tonumber('  -1z  ', 36)", -71.0) // -36 - 35
            assertLuaNumber("return tonumber('-fFfa', 16)", -65530.0) // -(10+(16*(15+(16*(15+(16*15))))))

            // Large base conversions
            for (i in 2..36) {
                val code = "return tonumber('\\t10000000000\\t', $i) == $i^10"
                assertLuaTrue(code, "Base $i conversion test")
            }
        }

    @Test
    fun testTonumberHexadecimalFormats() =
        runTest {
            // From math.lua:425-434
            assertLuaTrue("return tonumber('0xffffffffffff') == (1 << (4*12)) - 1")

            // Test with maximum representable integers
            val intbitsCode =
                """
                local intbits = 64  -- Assuming 64-bit integers for this test
                return tonumber('0x' .. string.rep('f', (intbits//4))) == -1
                """.trimIndent()
            assertLuaTrue(intbitsCode)
        }

    @Test
    fun testTonumberInvalidFormats() =
        runTest {
            // From math.lua:458-503
            // Invalid formats should return nil
            assertLuaNil("return tonumber('fFfa', 15)")
            assertLuaNil("return tonumber('099', 8)")
            assertLuaNil("return tonumber('1\\0', 2)")
            assertLuaNil("return tonumber('', 8)")
            assertLuaNil("return tonumber('  ', 9)")
            assertLuaNil("return tonumber('0xf', 10)")

            // These should not parse as numbers in Lua
            assertLuaNil("return tonumber('inf')")
            assertLuaNil("return tonumber(' INF ')")
            assertLuaNil("return tonumber('Nan')")
            assertLuaNil("return tonumber('nan')")

            // Various invalid string formats
            assertLuaNil("return tonumber('1  a')")
            assertLuaNil("return tonumber('1  a', 2)")
            assertLuaNil("return tonumber('1\\0')")
            assertLuaNil("return tonumber('1 \\0')")
            assertLuaNil("return tonumber('1\\0 ')")
            assertLuaNil("return tonumber('e1')")
            assertLuaNil("return tonumber('e  1')")
            assertLuaNil("return tonumber(' 3.4.5 ')")
        }

    @Test
    fun testTonumberInvalidHexFormats() =
        runTest {
            // From math.lua:506-517
            assertLuaNil("return tonumber('0x')")
            assertLuaNil("return tonumber('x')")
            assertLuaNil("return tonumber('x3')")
            assertLuaNil("return tonumber('0x3.3.3')") // two decimal points
            assertLuaNil("return tonumber('00x2')")
            assertLuaNil("return tonumber('0x 2')")
            assertLuaNil("return tonumber('0 x2')")
            assertLuaNil("return tonumber('23x')")
            assertLuaNil("return tonumber('- 0xaa')")
            assertLuaNil("return tonumber('-0xaaP ')") // no exponent
            assertLuaNil("return tonumber('0x0.51p')")
            assertLuaNil("return tonumber('0x5p+-2')")
        }

    @Test
    fun testStringArithmeticConversion() =
        runTest {
            // From math.lua:330-333 and 747-751
            assertLuaNumber("return '2' + 1", 3.0)
            assertLuaNumber("return '2 ' + 1", 3.0)
            assertLuaNumber("return ' -2 ' + 1", -1.0)
            assertLuaNumber("return ' -0xa ' + 1", -9.0)

            // String preservation despite arithmetic
            val code =
                """
                local a, b = '10', '20'
                local sum, mult, sub, div, neg = a+b, a*b, a-b, a/b, -b
                return sum == 30 and mult == 200 and sub == -10 and div == 0.5 and neg == -20
                       and a == '10' and b == '20'
                """.trimIndent()
            assertLuaTrue(code, "Strings should remain unchanged after arithmetic operations")
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testNumericOverflows() =
        runTest {
            // From math.lua:334-349
            // Test literal integer overflows
            assertLuaTrue(
                "return tonumber(tostring(math.maxinteger)) == math.maxinteger and math.type(tonumber(tostring(math.maxinteger))) == 'integer'",
            )
            assertLuaTrue(
                "return tonumber(tostring(math.mininteger)) == math.mininteger and math.type(tonumber(tostring(math.mininteger))) == 'integer'",
            )

            // Large numbers should become floats
            assertLuaTrue("return math.type(tonumber('1' .. string.rep('0', 30))) == 'float'")
            assertLuaTrue("return tonumber('1' .. string.rep('0', 30)) == 1e30")
            assertLuaTrue("return tonumber('-1' .. string.rep('0', 30)) == -1e30")

            // Hexadecimal format wraps around for integers
            assertLuaTrue("return tonumber('0x1' .. string.rep('0', 30)) == 0")
        }

    @Test
    fun testTonumberIntegerOverflow() =
        runTest {
            // From math.lua:355 - tonumber with overflow by 1
            // When a decimal string exceeds Long.MAX_VALUE, it should parse as a float
            val code =
                """
                local function eqT(a, b)
                    return a == b and math.type(a) == math.type(b)
                end
                
                local function incd(n)
                    local s = string.format("%d", n)
                    s = string.gsub(s, "%d$", function(d)
                        assert(d ~= '9')
                        return string.char(string.byte(d) + 1)
                    end)
                    return s
                end
                
                local maxint = math.maxinteger
                local minint = math.mininteger
                
                -- tonumber with overflow by 1 should return a float
                return eqT(tonumber(incd(maxint)), maxint + 1.0) and
                       eqT(tonumber(incd(minint)), minint - 1.0)
                """.trimIndent()
            assertLuaTrue(code, "tonumber should parse integer overflow as float")
        }

    @Test
    fun testLexerNumericLimits() =
        runTest {
            // From math.lua:355-360
            assertLuaTrue("return math.mininteger == load('return ' .. math.mininteger)()")
            assertLuaTrue("return math.maxinteger == load('return ' .. math.maxinteger)()")
            assertLuaTrue("return 10000000000000000000000.0 == 10000000000000000000000")
            assertLuaTrue("return -10000000000000000000000.0 == -10000000000000000000000")
        }
}
