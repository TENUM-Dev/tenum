package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for string.format alternate form flag (#)
 * Based on strings.lua lines 345-347
 *
 * The # flag (alternate form) provides:
 * - %#o: prefixes octal with "0"
 * - %#x: prefixes hex with "0x"
 * - %#X: prefixes hex with "0X"
 */
class StringFormatAlternateFormTest : LuaCompatTestBase() {
    @Test
    fun testOctalAlternateFormWithWidth() {
        // strings.lua line 345: assert(string.format("%#12o", 10) == "         012")
        // 10 in octal is "12", with # flag becomes "012", padded to width 12
        assertLuaString("return string.format('%#12o', 10)", "         012")
    }

    @Test
    fun testHexLowercaseAlternateFormWithWidth() {
        // strings.lua line 346: assert(string.format("%#10x", 100) == "      0x64")
        // 100 in hex is "64", with # flag becomes "0x64", padded to width 10
        assertLuaString("return string.format('%#10x', 100)", "      0x64")
    }

    @Test
    fun testHexUppercaseAlternateFormWithLeftAlign() {
        // strings.lua line 347: assert(string.format("%#-17X", 100) == "0X64             ")
        // 100 in hex is "64", with # flag becomes "0X64", left-aligned to width 17
        assertLuaString("return string.format('%#-17X', 100)", "0X64             ")
    }

    @Test
    fun testOctalAlternateFormBasic() {
        // Test basic octal with # flag
        assertLuaString("return string.format('%#o', 10)", "012")
    }

    @Test
    fun testHexAlternateFormBasic() {
        // Test basic hex with # flag
        assertLuaString("return string.format('%#x', 100)", "0x64")
    }

    @Test
    fun testOctalWithoutAlternateForm() {
        // Without # flag, no prefix
        assertLuaString("return string.format('%o', 10)", "12")
    }

    @Test
    fun testHexWithoutAlternateForm() {
        // Without # flag, no prefix
        assertLuaString("return string.format('%x', 100)", "64")
    }

    @Test
    fun testFloatAlternateFormWithSignZeroPaddingAndPrecision() {
        // strings.lua line 351: assert(string.format("%+#014.0f", 100) == "+000000000100.")
        // %+ forces sign, # forces decimal point, 0 zero pads, 14 is width, .0 is precision
        assertLuaString("return string.format('%+#014.0f', 100)", "+000000000100.")
    }
}
