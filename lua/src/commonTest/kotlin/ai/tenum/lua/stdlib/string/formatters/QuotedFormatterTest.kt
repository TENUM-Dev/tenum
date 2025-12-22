package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuotedFormatterTest {
    private val formatter = QuotedFormatter()

    // ============================================================================
    // Basic types
    // ============================================================================

    @Test
    fun testQuotedBoolean() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaBoolean.TRUE, spec)
        assertEquals("true", result)
    }

    @Test
    fun testQuotedBooleanFalse() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaBoolean.FALSE, spec)
        assertEquals("false", result)
    }

    @Test
    fun testQuotedNil() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNil, spec)
        assertEquals("nil", result)
    }

    // ============================================================================
    // Number formatting
    // ============================================================================

    @Test
    fun testQuotedInteger() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNumber.of(42), spec)
        assertEquals("42", result)
    }

    @Test
    fun testQuotedNegativeInteger() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNumber.of(-123), spec)
        assertEquals("-123", result)
    }

    @Test
    fun testQuotedMinInteger() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNumber.of(Long.MIN_VALUE.toDouble()), spec)
        assertEquals("0x8000000000000000", result)
    }

    @Test
    fun testQuotedFloat() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNumber.of(3.14), spec)
        // Float should use hex float format
        assertTrue(result.startsWith("0x1."))
        assertTrue(result.contains("p"))
    }

    @Test
    fun testQuotedNaN() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNumber.of(Double.NaN), spec)
        assertEquals("(0/0)", result)
    }

    @Test
    fun testQuotedInfinity() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNumber.of(Double.POSITIVE_INFINITY), spec)
        assertEquals("1e9999", result)
    }

    @Test
    fun testQuotedNegativeInfinity() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNumber.of(Double.NEGATIVE_INFINITY), spec)
        assertEquals("-1e9999", result)
    }

    @Test
    fun testQuotedZero() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaNumber.of(0.0), spec)
        assertEquals("0", result)
    }

    // ============================================================================
    // String formatting
    // ============================================================================

    @Test
    fun testQuotedSimpleString() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString("hello"), spec)
        assertEquals("\"hello\"", result)
    }

    @Test
    fun testQuotedStringWithQuote() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString("say \"hi\""), spec)
        assertEquals("\"say \\\"hi\\\"\"", result)
    }

    @Test
    fun testQuotedStringWithBackslash() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString("path\\file"), spec)
        assertEquals("\"path\\\\file\"", result)
    }

    @Test
    fun testQuotedStringWithNewline() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString("line1\nline2"), spec)
        assertEquals("\"line1\\nline2\"", result)
    }

    @Test
    fun testQuotedStringWithTab() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString("tab\there"), spec)
        assertEquals("\"tab\\there\"", result)
    }

    @Test
    fun testQuotedStringWithCarriageReturn() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString("line\r\n"), spec)
        assertEquals("\"line\\r\\n\"", result)
    }

    @Test
    fun testQuotedStringWithNullByte() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString("null\u0000byte"), spec)
        assertEquals("\"null\\0byte\"", result)
    }

    @Test
    fun testQuotedStringWithControlCharacter() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString("test\u0001"), spec)
        assertEquals("\"test\\1\"", result)
    }

    @Test
    fun testQuotedEmptyString() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val result = formatter.format(LuaString(""), spec)
        assertEquals("\"\"", result)
    }

    // ============================================================================
    // Error cases
    // ============================================================================

    @Test
    fun testQuotedTableThrowsError() {
        val spec = FormatSpecifier(emptySet(), null, null, 'q')
        val exception =
            assertFailsWith<RuntimeException> {
                formatter.format(LuaTable(), spec)
            }
        assertTrue(exception.message?.contains("no literal form") == true)
    }
}
