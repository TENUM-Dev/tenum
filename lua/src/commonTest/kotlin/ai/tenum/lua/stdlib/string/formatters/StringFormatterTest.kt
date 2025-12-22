package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.test.Test
import kotlin.test.assertEquals

class StringFormatterTest {
    private val formatter =
        ai.tenum.lua.stdlib.string.formatters.StringFormatter { value ->
            when (value) {
                is LuaString -> value.value
                is LuaNumber -> value.toDouble().toString()
                else -> value.toString()
            }
        }

    @Test
    fun testSimpleString() {
        val spec = FormatSpecifier(formatChar = 's')
        val result = formatter.format(LuaString("hello"), spec)
        assertEquals("hello", result)
    }

    @Test
    fun testStringWithWidth() {
        val spec = FormatSpecifier(formatChar = 's', width = 10)
        val result = formatter.format(LuaString("hello"), spec)
        assertEquals("     hello", result)
    }

    @Test
    fun testStringWithWidthLeftJustify() {
        val spec = FormatSpecifier(formatChar = 's', width = 10, flags = setOf('-'))
        val result = formatter.format(LuaString("hello"), spec)
        assertEquals("hello     ", result)
    }

    @Test
    fun testStringWithPrecision() {
        val spec = FormatSpecifier(formatChar = 's', precision = 3)
        val result = formatter.format(LuaString("hello"), spec)
        assertEquals("hel", result)
    }

    @Test
    fun testStringWithWidthAndPrecision() {
        val spec = FormatSpecifier(formatChar = 's', width = 10, precision = 3)
        val result = formatter.format(LuaString("hello"), spec)
        assertEquals("       hel", result)
    }

    @Test
    fun testStringWithWidthPrecisionLeftJustify() {
        val spec = FormatSpecifier(formatChar = 's', width = 10, precision = 3, flags = setOf('-'))
        val result = formatter.format(LuaString("hello"), spec)
        assertEquals("hel       ", result)
    }

    @Test
    fun testStringNoTruncationIfShorter() {
        val spec = FormatSpecifier(formatChar = 's', precision = 10)
        val result = formatter.format(LuaString("hi"), spec)
        assertEquals("hi", result)
    }

    @Test
    fun testHandlesReturnsTrue() {
        assertEquals(true, formatter.handles('s'))
    }

    @Test
    fun testHandlesReturnsFalse() {
        assertEquals(false, formatter.handles('d'))
        assertEquals(false, formatter.handles('f'))
    }
}
