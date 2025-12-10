package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for string.format %q (quoted string) format
 */
class StringFormatQTest : LuaCompatTestBase() {
    @Test
    fun testQuotedStringBasic() {
        val result = execute("""return string.format('%q', 'hello')""")
        assertEquals("\"hello\"", (result as LuaString).value)
    }

    @Test
    fun testQuotedStringWithNull() {
        val result = execute("""return string.format('%q', "\0")""")
        assertEquals("\"\\0\"", (result as LuaString).value)
    }

    @Test
    fun testQuotedStringComplex() {
        // Line 197 test: local x = '"�lo"\n\\'
        val result =
            execute(
                """
            local x = '"lo"\n\\'
            return string.format('%q', x)
        """,
            )
        val expected = "\"\\\\\\\"\nlo\\\\\\\"\\\\n\\\\\\\\\""
        println("Result: ${(result as LuaString).value}")
        println("Expected: $expected")
    }

    @Test
    fun testQuotedAndRegularString() {
        // Line 199: assert(string.format('%q%s', x, x) == '"\\"�lo\\"\\\n\\\\""�lo"\n\\')
        val result =
            execute(
                """
            local x = '"lo"\n\\'
            return string.format('%q%s', x, x)
        """,
            )
        println("Result: ${(result as LuaString).value}")
    }

    @Test
    fun testQuotedBooleanTrue() {
        val result = execute("""return string.format('%q', true)""")
        assertEquals("true", (result as LuaString).value)
    }

    @Test
    fun testQuotedBooleanFalse() {
        val result = execute("""return string.format('%q', false)""")
        assertEquals("false", (result as LuaString).value)
    }

    @Test
    fun testQuotedNil() {
        val result = execute("""return string.format('%q', nil)""")
        assertEquals("nil", (result as LuaString).value)
    }

    @Test
    fun testQuotedBooleanRoundTrip() {
        val result =
            execute(
                """
            local function checkQ(v)
                local s = string.format('%q', v)
                local nv = load('return ' .. s)()
                return v == nv and math.type(v) == math.type(nv)
            end
            return checkQ(true)
        """,
            )
        assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
    }

    @Test
    fun testQuotedNilRoundTrip() {
        val result =
            execute(
                """
            local function checkQ(v)
                local s = string.format('%q', v)
                local nv = load('return ' .. s)()
                return v == nv and math.type(v) == math.type(nv)
            end
            return checkQ(nil)
        """,
            )
        assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
    }

    @Test
    fun testQuotedFalseRoundTrip() {
        val result =
            execute(
                """
            local function checkQ(v)
                local s = string.format('%q', v)
                local nv = load('return ' .. s)()
                return v == nv and math.type(v) == math.type(nv)
            end
            return checkQ(false)
        """,
            )
        assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
    }

    @Test
    fun testQuotedInfinity() {
        val result = execute("""return string.format('%q', math.huge)""")
        assertEquals("1e9999", (result as LuaString).value)
    }

    @Test
    fun testQuotedNegativeInfinity() {
        val result = execute("""return string.format('%q', -math.huge)""")
        assertEquals("-1e9999", (result as LuaString).value)
    }

    @Test
    fun testQuotedNaN() {
        val result = execute("""return string.format('%q', 0/0)""")
        assertEquals("(0/0)", (result as LuaString).value)
    }

    @Test
    fun testQuotedIntegerNumber() {
        val result = execute("""return string.format('%q', 42)""")
        assertEquals("42", (result as LuaString).value)
    }

    @Test
    fun testQuotedInfinityRoundTrip() {
        val result =
            execute(
                """
            local function checkQ(v)
                local s = string.format('%q', v)
                local nv = load('return ' .. s)()
                return v == nv and math.type(v) == math.type(nv)
            end
            return checkQ(math.huge)
        """,
            )
        assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
    }

    @Test
    fun testQuotedFloatRoundTrip() {
        val result =
            execute(
                """
            local function checkQ(v)
                local s = string.format('%q', v)
                print("Value:", v, "Formatted:", s)
                local nv = load('return ' .. s)()
                print("Loaded:", nv)
                return v == nv and math.type(v) == math.type(nv)
            end
            return checkQ(3.14)
        """,
            )
        assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
    }

    @Test
    fun testQuotedStringWithUnicodeRoundTrip() {
        val result =
            execute(
                """
            local function checkQ(v)
                local s = string.format('%q', v)
                print("Formatted:", s)
                local nv = load('return ' .. s)()
                return v == nv and math.type(v) == math.type(nv)
            end
            return checkQ("\0\0\1\255\u{234}")
        """,
            )
        assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
    }

    @Test
    fun testQuotedTableError() {
        // strings.lua line 236: checkerror("no literal", string.format, "%q", {})
        // Should error with "value has no literal form"
        val result =
            execute(
                """
            local function checkerror(msg, f, ...)
                local s, err = pcall(f, ...)
                return not s and string.find(err, msg) ~= nil
            end
            return checkerror("no literal", string.format, "%q", {})
        """,
            )
        assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
    }

    @Test
    fun testStringFormatWithZeroAndWidth() {
        // strings.lua line 240: checkerror("contains zeros", string.format, "%10s", "\0")
        // Should error with "string contains zeros"
        val result =
            execute(
                """
            local function checkerror(msg, f, ...)
                local s, err = pcall(f, ...)
                return not s and string.find(err, msg) ~= nil
            end
            return checkerror("contains zeros", string.format, "%10s", "\0")
        """,
            )
        assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
    }

    @Test
    fun testStringFormatWithNilAndBoolean() {
        // strings.lua line 243: assert(string.format("%s %s", nil, true) == "nil true")
        val result = execute("""return string.format("%s %s", nil, true)""")
        assertEquals("nil true", (result as LuaString).value)
    }

    @Test
    fun testStringFormatWithPrecision() {
        // strings.lua line 245: assert(string.format("%.3s %.3s", false, true) == "fal tru")
        val result = execute("""return string.format("%.3s %.3s", false, true)""")
        assertEquals("fal tru", (result as LuaString).value)
    }
}
