package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for string.format %x/%X (hexadecimal) format
 * Based on strings.lua lines 256-260
 */
class StringFormatHexTest : LuaCompatTestBase() {
    @Test
    fun testHexFormatBasic() {
        // strings.lua line 256: assert(string.format("%x", 0.0) == "0")
        assertLuaString("return string.format('%x', 0.0)", "0")
    }

    @Test
    fun testHexFormatWithZeroPadding() {
        // strings.lua line 257: assert(string.format("%02x", 0.0) == "00")
        assertLuaString("return string.format('%02x', 0.0)", "00")
    }

    @Test
    fun testHexFormatUppercaseWithZeroPadding() {
        // strings.lua line 258: assert(string.format("%08X", 0xFFFFFFFF) == "FFFFFFFF")
        assertLuaString("return string.format('%08X', 0xFFFFFFFF)", "FFFFFFFF")
    }

    @Test
    fun testIntegerFormatWithZeroPaddingPositive() {
        // strings.lua line 259: assert(string.format("%+08d", 31501) == "+0031501")
        assertLuaString("return string.format('%+08d', 31501)", "+0031501")
    }

    @Test
    fun testIntegerFormatWithZeroPaddingNegative() {
        // strings.lua line 260: assert(string.format("%+08d", -30927) == "-0030927")
        assertLuaString("return string.format('%+08d', -30927)", "-0030927")
    }

    @Test
    fun testIntegerFormatWithZeroPaddingNegativeNoSign() {
        // strings.lua line 349: assert(string.format("%013i", -100) == "-000000000100")
        assertLuaString("return string.format('%013i', -100)", "-000000000100")
    }

    @Test
    fun testPrecisionZeroWithZeroValueUnsigned() {
        // strings.lua line 350: assert(string.format("%.u", 0) == "")
        // C printf spec: precision 0 with value 0 produces empty string
        assertLuaString("return string.format('%.u', 0)", "")
    }

    @Test
    fun testPrecisionZeroWithZeroValueHexLowercase() {
        // Precision 0 with value 0 produces empty string for %x
        assertLuaString("return string.format('%.x', 0)", "")
    }

    @Test
    fun testPrecisionZeroWithZeroValueHexUppercase() {
        // Precision 0 with value 0 produces empty string for %X
        assertLuaString("return string.format('%.X', 0)", "")
    }

    @Test
    fun testPrecisionZeroWithZeroValueOctal() {
        // Precision 0 with value 0 produces empty string for %o
        assertLuaString("return string.format('%.o', 0)", "")
    }

    @Test
    fun testPrecisionZeroWithZeroValueDecimal() {
        // Precision 0 with value 0 produces empty string for %d
        assertLuaString("return string.format('%.d', 0)", "")
    }

    @Test
    fun testPrecisionZeroWithZeroValueInteger() {
        // Precision 0 with value 0 produces empty string for %i
        assertLuaString("return string.format('%.i', 0)", "")
    }

    @Test
    fun testPrecisionZeroWithNonZeroValue() {
        // Precision 0 with non-zero value still produces output
        assertLuaString("return string.format('%.u', 5)", "5")
        assertLuaString("return string.format('%.x', 15)", "f")
        assertLuaString("return string.format('%.d', -10)", "-10")
    }

    @Test
    fun testPrecisionForMinimumDigits() {
        // Precision for integers means minimum digit count
        assertLuaString("return string.format('%2.5d', -100)", "-00100")
        assertLuaString("return string.format('%.5u', 42)", "00042")
        assertLuaString("return string.format('%.4x', 255)", "00ff")
    }

    @Test
    fun testCharacterFormatWithLeftAlignAndWidth() {
        // strings.lua line 352: assert(string.format("%-16c", 97) == "a               ")
        assertLuaString("return string.format('%-16c', 97)", "a               ")
    }

    @Test
    fun testExponentialFormatUppercaseWithSpaceFlag() {
        // strings.lua line 360: Tests that "% 1.0E" format works correctly
        // This test verifies:
        // 1. E format is supported
        // 2. Space flag adds space for positive numbers
        // 3. Exponent has at least 2 digits (Lua uses 3)
        execute(
            """
            local result = string.format("% 1.0E", 100)
            -- Must start with space (space flag)
            assert(string.sub(result, 1, 1) == " ", "Expected space at start, got: " .. result)
            -- Must contain E+ for exponential
            assert(string.find(result, "E%+"), "Expected 'E+' in output, got: " .. result)
            -- Expected format: " 1E+nn" where nn is 2 digits
            assert(#result >= 6, "Expected at least 6 chars, got " .. #result .. ": " .. result)
        """,
        )
    }

    @Test
    fun testExponentialFormatLowercaseWithSpaceFlag() {
        // strings.lua line 361: Tests that "% .1g" format works correctly
        // This test verifies:
        // 1. %g uses exponential format for large numbers
        // 2. Space flag adds space for positive numbers
        execute(
            """
            local result = string.format("% .1g", 2^10)
            -- Must start with space (space flag)
            assert(string.sub(result, 1, 1) == " ", "Expected space at start, got: " .. result)
            -- Must contain e+ for exponential
            assert(string.find(result, "e%+"), "Expected 'e+' in output, got: " .. result)
            -- Expected format: " 1e+nn" where nn is 2 digits  
            assert(#result >= 6, "Expected at least 6 chars, got " .. #result .. ": " .. result)
        """,
        )
    }

    @Test
    fun testFormatValidationWidthTooLarge() {
        // strings.lua line 371: check("%100.3d", "invalid conversion")
        // Width or precision > 99 should error
        execute(
            """
            local function checkerror(msg, f, ...)
                local s, err = pcall(f, ...)
                assert(not s and string.find(err, msg), "Expected error containing '" .. msg .. "', got: " .. tostring(err))
            end
            checkerror("invalid conversion", string.format, "%100.3d", 10)
        """,
        )
    }

    @Test
    fun testFormatValidationPrecisionTooLarge() {
        // strings.lua line 373: check("%1.100d", "invalid conversion")
        // Precision > 99 should error
        execute(
            """
            local function checkerror(msg, f, ...)
                local s, err = pcall(f, ...)
                assert(not s and string.find(err, msg), "Expected error containing '" .. msg .. "', got: " .. tostring(err))
            end
            checkerror("invalid conversion", string.format, "%1.100d", 10)
        """,
        )
    }

    @Test
    fun testFormatValidationInvalidFormatCharacter() {
        // strings.lua line 375: check("%t", "invalid conversion")
        // Invalid format character should error
        execute(
            """
            local function checkerror(msg, f, ...)
                local s, err = pcall(f, ...)
                assert(not s and string.find(err, msg), "Expected error containing '" .. msg .. "', got: " .. tostring(err))
            end
            checkerror("invalid conversion", string.format, "%t", 10)
        """,
        )
    }

    @Test
    fun testFormatValidationNoValue() {
        // strings.lua line 377: check("%d %d", "no value")
        // Missing value for format spec should error
        execute(
            """
            local function checkerror(msg, f, ...)
                local s, err = pcall(f, ...)
                assert(not s and string.find(err, msg), "Expected error containing '" .. msg .. "', got: " .. tostring(err))
            end
            checkerror("no value", string.format, "%d %d", 10)
        """,
        )
    }

    @Test
    fun testFormatValidationTooLongWidth() {
        // strings.lua line 376: check("%"..aux.."d", "too long") where aux is 600 '0's
        // Width string that's too long should error with "too long"
        execute(
            """
            local function checkerror(msg, f, ...)
                local s, err = pcall(f, ...)
                assert(not s and string.find(err, msg), "Expected error containing '" .. msg .. "', got: " .. tostring(err))
            end
            local aux = string.rep('0', 600)
            checkerror("too long", string.format, "%"..aux.."d", 10)
        """,
        )
    }
}
