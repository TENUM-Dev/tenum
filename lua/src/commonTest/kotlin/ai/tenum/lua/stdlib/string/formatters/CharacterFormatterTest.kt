package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.test.Test
import kotlin.test.assertEquals

class CharacterFormatterTest {
    private val formatter = CharacterFormatter()

    @Test
    fun testBasicCharacterFormatting() {
        val spec = FormatSpecifier(emptySet(), null, null, 'c')
        val result = formatter.format(LuaNumber.of(65), spec)
        assertEquals("A", result)
    }

    @Test
    fun testCharacterLowercase() {
        val spec = FormatSpecifier(emptySet(), null, null, 'c')
        val result = formatter.format(LuaNumber.of(97), spec)
        assertEquals("a", result)
    }

    @Test
    fun testCharacterDigit() {
        val spec = FormatSpecifier(emptySet(), null, null, 'c')
        val result = formatter.format(LuaNumber.of(48), spec)
        assertEquals("0", result)
    }

    @Test
    fun testCharacterSpace() {
        val spec = FormatSpecifier(emptySet(), null, null, 'c')
        val result = formatter.format(LuaNumber.of(32), spec)
        assertEquals(" ", result)
    }

    @Test
    fun testCharacterNewline() {
        val spec = FormatSpecifier(emptySet(), null, null, 'c')
        val result = formatter.format(LuaNumber.of(10), spec)
        assertEquals("\n", result)
    }

    @Test
    fun testCharacterWithWidth() {
        val spec = FormatSpecifier(emptySet(), 5, null, 'c')
        val result = formatter.format(LuaNumber.of(65), spec)
        assertEquals("    A", result)
    }

    @Test
    fun testCharacterWithLeftJustify() {
        val spec = FormatSpecifier(setOf('-'), 5, null, 'c')
        val result = formatter.format(LuaNumber.of(65), spec)
        assertEquals("A    ", result)
    }

    @Test
    fun testCharacterZero() {
        val spec = FormatSpecifier(emptySet(), null, null, 'c')
        val result = formatter.format(LuaNumber.of(0), spec)
        assertEquals("\u0000", result)
    }

    @Test
    fun testCharacterHighValue() {
        val spec = FormatSpecifier(emptySet(), null, null, 'c')
        val result = formatter.format(LuaNumber.of(255), spec)
        assertEquals("ÿ", result)
    }
}
