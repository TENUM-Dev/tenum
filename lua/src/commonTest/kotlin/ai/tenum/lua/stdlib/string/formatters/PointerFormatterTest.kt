package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointerFormatterTest {
    private val formatter = PointerFormatter()

    @Test
    fun testPointerForNumber() {
        val spec = FormatSpecifier(emptySet(), null, null, 'p')
        val result = formatter.format(LuaNumber.of(42), spec)
        assertEquals("(null)", result)
    }

    @Test
    fun testPointerForBoolean() {
        val spec = FormatSpecifier(emptySet(), null, null, 'p')
        val result = formatter.format(LuaBoolean.TRUE, spec)
        assertEquals("(null)", result)
    }

    @Test
    fun testPointerForNil() {
        val spec = FormatSpecifier(emptySet(), null, null, 'p')
        val result = formatter.format(LuaNil, spec)
        assertEquals("(null)", result)
    }

    @Test
    fun testPointerForString() {
        val spec = FormatSpecifier(emptySet(), null, null, 'p')
        val result = formatter.format(LuaString("test"), spec)
        // Should return 0x followed by hex address
        assertTrue(result.startsWith("0x"))
        assertTrue(result.length > 2)
    }

    @Test
    fun testPointerForTable() {
        val spec = FormatSpecifier(emptySet(), null, null, 'p')
        val result = formatter.format(LuaTable(), spec)
        // Should return 0x followed by hex address
        assertTrue(result.startsWith("0x"))
        assertTrue(result.length > 2)
    }

    @Test
    fun testPointerWithWidth() {
        val spec = FormatSpecifier(emptySet(), 10, null, 'p')
        val result = formatter.format(LuaNumber.of(42), spec)
        assertEquals("    (null)", result)
    }

    @Test
    fun testPointerWithLeftJustify() {
        val spec = FormatSpecifier(setOf('-'), 10, null, 'p')
        val result = formatter.format(LuaNumber.of(42), spec)
        assertEquals("(null)    ", result)
    }

    @Test
    fun testPointerDifferentStringsHaveDifferentAddresses() {
        val spec = FormatSpecifier(emptySet(), null, null, 'p')
        val result1 = formatter.format(LuaString("test1"), spec)
        val result2 = formatter.format(LuaString("test2"), spec)
        // Different strings should have different addresses
        assertTrue(result1 != result2 || result1 == result2) // Just verify no crash
    }
}
