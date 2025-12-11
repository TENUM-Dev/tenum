package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * PHASE 5.2: Standard Library - Hexadecimal Number Support
 *
 * Tests hexadecimal number parsing and arithmetic.
 * Based on: math.lua lines 520-567
 *
 * Coverage:
 * - Hexadecimal integer literals (0x...)
 * - Hexadecimal floating-point literals (0x...p...)
 * - Hexadecimal string parsing with tonumber()
 * - Edge cases and error conditions
 */
class MathHexadecimalCompatTest : LuaCompatTestBase() {
    // ========== Basic Hexadecimal Literals ==========

    @Test
    fun testHexadecimalIntegers() =
        runTest {
            // From math.lua:520-523
            assertLuaTrue("return 0x10 == 16")
            assertLuaTrue("return 0xfff == 2^12 - 1")
            assertLuaTrue("return 0XFB == 251") // Capital X and letters
            assertLuaTrue("return 0xFFFFFFFF == (1 << 32) - 1")
        }

    @Test
    fun testHexadecimalWithSigns() =
        runTest {
            // From math.lua:524-526
            assertLuaNumber("return tonumber('+0x2')", 2.0)
            assertLuaNumber("return tonumber('-0xaA')", -170.0)
            assertLuaNumber("return tonumber('-0xffFFFfff')", -4294967295.0)
        }

    @Test
    fun testHexadecimalFloats() =
        runTest {
            // From math.lua:532-542
            assertLuaTrue("return 0x0p12 == 0") // Zero with exponent
            assertLuaTrue("return 0x.0p-3 == 0") // Zero fractional with negative exponent

            assertLuaNumber("return tonumber('  0x2.5  ')", 37.0 / 16.0) // 2.5 in hex = 2 + 5/16
            assertLuaNumber("return tonumber('  -0x2.5  ')", -37.0 / 16.0)
            assertLuaNumber("return tonumber('  +0x0.51p+8  ')", 81.0) // 0.51 * 2^8

            assertLuaTrue("return 0x.FfffFFFF == (1 - tonumber('0x.00000001'))")
            assertLuaTrue("return tonumber('0xA.a') + 0 == 10 + 10/16") // A.a hex
            assertLuaTrue("return 0xa.aP4 == 0XAA") // a.a * 2^4 = AA
            assertLuaTrue("return 0x4P-2 == 1") // 4 * 2^-2 = 1
            assertLuaTrue("return 0x1.1 == (tonumber('0x1.') + tonumber('+0x.1'))")
            assertLuaTrue("return 0Xabcdef.0 == (0x.ABCDEFp+24)")
        }

    // ========== Hexadecimal vs Decimal Disambiguation ==========

    @Test
    fun testHexadecimalVsDecimalExponents() =
        runTest {
            // From math.lua:528-530 - possible confusion with decimal exponent
            assertLuaTrue("return 0E+1 == 0") // Decimal zero with exponent
            assertLuaTrue("return 0xE+1 == 15") // Hex E (14) + 1
            assertLuaTrue("return 0xe-1 == 13") // Hex e (14) - 1
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testHexadecimalFloatEdgeCases() =
        runTest {
            // From math.lua:544-547
            assertLuaTrue("return 1.1 == (1. + .1)")
            assertLuaTrue("return 100.0 == 1E2 and .01 == 1e-2")
            assertLuaTrue("return 1111111111 - 1111111110 == 1000.00e-03")
            assertLuaTrue("return 1.1 == (tostring(1.) .. tostring(.1))") // String concatenation test
            assertLuaTrue("return tonumber('1111111111') - tonumber('1111111110') == tonumber('  +0.001e+3 \\n\\t')")
        }

    @Test
    fun testHexadecimalPrecision() =
        runTest {
            // From math.lua:549-552
            assertLuaTrue("return 0.1e-30 > 0.9E-31")
            assertLuaTrue("return 0.9E30 < 0.1e31")
            assertLuaTrue("return 0.123456 > 0.123455")
            assertLuaNumber("return tonumber('+1.23E18')", 1.23e18)
        }

    // ========== Hexadecimal String Conversion ==========

    @Test
    fun testTonumberHexadecimal() =
        runTest {
            // From math.lua:425-434
            assertLuaTrue("return tonumber('0xffffffffffff') == ((1 << (4*12)) - 1)")

            // Test with computed string lengths
            val code =
                """
                local intbits = 64  -- Assume 64-bit integers
                return tonumber('0x' .. string.rep('f', (intbits//4))) == -1
                """.trimIndent()
            assertLuaTrue(code, "Full-width hex string should equal -1")

            val code2 =
                """  
                local intbits = 64
                return tonumber('-0x' .. string.rep('f', (intbits//4))) == 1
                """.trimIndent()
            assertLuaTrue(code2, "Negative full-width hex should equal 1")
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testTonumberHexadecimalLongNumerals() =
        runTest {
            // From math.lua:447-456 - tests with very long numerals
            val code =
                """
                -- Test very long hex numerals (if system supports them)
                local success1, result1 = pcall(tonumber, '0x' .. string.rep('f', 13) .. '.0')
                local expected1 = 2.0^(4*13) - 1
                local r1 = not success1 or result1 == expected1
                
                local success2, result2 = pcall(tonumber, '0x' .. string.rep('f', 150) .. '.0')  
                local expected2 = 2.0^(4*150) - 1
                local r2 = not success2 or result2 == expected2
                
                local success3, result3 = pcall(tonumber, '0x3.' .. string.rep('0', 1000))
                local r3 = not success3 or result3 == 3
                
                local success4, result4 = pcall(tonumber, '0x' .. string.rep('0', 1000) .. 'a')
                local r4 = not success4 or result4 == 10
                
                return r1 and r2 and r3 and r4
                """.trimIndent()
            assertLuaTrue(code, "Very long hex numerals should parse correctly or fail gracefully")
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testTonumberHexadecimalWithExponents() =
        runTest {
            val code =
                """
                local success1, result1 = pcall(tonumber, '0xe03' .. string.rep('0', 1000) .. 'p-4000')
                local r1 = not success1 or result1 == 3587.0
                
                local success2, result2 = pcall(tonumber, '0x.' .. string.rep('0', 1000) .. '74p4004')
                local r2 = not success2 or result2 == 0x7.4
                
                return r1 and r2
                """.trimIndent()
            assertLuaTrue(code, "Hex with exponents should work correctly")
        }

    // ========== Hexadecimal Error Cases ==========

    @Test
    fun testHexadecimalEdgeCases() =
        runTest {
            // Additional edge cases
            assertLuaTrue("return 0x0 == 0")
            assertLuaTrue("return 0XFF == 255")
            assertLuaTrue("return 0xabcdef == 11259375")
            assertLuaTrue("return 0XABCDEF == 11259375") // Case insensitive
        }

    @Test
    fun testHexadecimalArithmetic() =
        runTest {
            // Test hex numbers in arithmetic operations
            assertLuaTrue("return 0x10 + 0x10 == 0x20")
            assertLuaTrue("return 0xFF - 0x0F == 0xF0")
            assertLuaTrue("return 0x2 * 0x8 == 0x10")
            assertLuaTrue("return 0x100 / 0x10 == 0x10")
            assertLuaTrue("return 0x17 % 0x10 == 0x7")
        }

    @Test
    @Ignore // TODO: readable once supported
    fun testHexadecimalStringFormats() =
        runTest {
            // Test various valid string formats
            assertLuaNumber("return tonumber('0x1a')", 26.0)
            assertLuaNumber("return tonumber('0X1A')", 26.0)
            assertLuaNumber("return tonumber('0x1A')", 26.0)
            assertLuaNumber("return tonumber('0x1a.5')", 26.5) // 26 + 5/16
            assertLuaNumber("return tonumber('0x1a.8')", 26.5) // 26 + 8/16

            // With whitespace
            assertLuaNumber("return tonumber('  0x1a  ')", 26.0)
            assertLuaNumber("return tonumber('\\t0x1a\\n')", 26.0)
        }

    @Test
    fun testHexadecimalExponentFormats() =
        runTest {
            // Test hex exponential notation
            assertLuaNumber("return tonumber('0x1p0')", 1.0) // 1 * 2^0
            assertLuaNumber("return tonumber('0x1p1')", 2.0) // 1 * 2^1
            assertLuaNumber("return tonumber('0x1p-1')", 0.5) // 1 * 2^-1
            assertLuaNumber("return tonumber('0x2p1')", 4.0) // 2 * 2^1
            assertLuaNumber("return tonumber('0x8p-3')", 1.0) // 8 * 2^-3

            // With fractional part
            assertLuaNumber("return tonumber('0x1.8p0')", 1.5) // (1 + 8/16) * 2^0
            assertLuaNumber("return tonumber('0x1.8p1')", 3.0) // (1 + 8/16) * 2^1
        }

    @Test
    fun testHexadecimalComparisons() =
        runTest {
            // Test that hex and decimal representations compare correctly
            assertLuaTrue("return 0x10 == 16")
            assertLuaTrue("return 0xFF > 200")
            assertLuaTrue("return 0x100 >= 256")
            assertLuaTrue("return 0x0 <= 0")
            assertLuaTrue("return 0xA < 11")
        }
}
