package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test string.format %p (pointer) format from strings.lua line 160-180
 * Reproduces the assertion failure at line 178-179
 */
class StringFormatPointerTest : LuaCompatTestBase() {
    @Test
    fun testPointerFormatNullValues() {
        // %p for non-reference types should return "(null)"
        assertLuaString("return string.format('%p', 4)", "(null)")
        assertLuaString("return string.format('%p', true)", "(null)")
        assertLuaString("return string.format('%p', nil)", "(null)")
        assertLuaString("return string.format('%p', false)", "(null)")
        assertLuaString("return string.format('%p', 1.5)", "(null)")
    }

    @Test
    fun testPointerFormatReferenceTypes() {
        // %p for reference types (tables, functions) should NOT return "(null)"
        val result1 = execute("return string.format('%p', {})")
        val result2 = execute("return string.format('%p', print)")

        // Both should be strings but not "(null)"
        assertLuaTrue("return string.format('%p', {}) ~= '(null)'")
        assertLuaTrue("return string.format('%p', print) ~= '(null)'")
    }

    @Test
    fun testPointerFormatWithWidth() {
        // Width specifier should pad the output
        assertLuaNumber("return #string.format('%90p', {})", 90.0)
        assertLuaNumber("return #string.format('%-60p', {})", 60.0)
    }

    @Test
    fun testPointerFormatRightPadding() {
        // Line 178: string.format("%10p", false) == string.rep(" ", 10 - #null) .. null
        val code = """
            local null = "(null)"
            local formatted = string.format("%10p", false)
            local expected = string.rep(" ", 10 - #null) .. null
            return formatted == expected
        """
        assertLuaBoolean(code, true)
    }

    @Test
    fun testPointerFormatLeftPadding() {
        // Line 179: string.format("%-12p", 1.5) == null .. string.rep(" ", 12 - #null)
        val code = """
            local null = "(null)"
            local formatted = string.format("%-12p", 1.5)
            local expected = null .. string.rep(" ", 12 - #null)
            return formatted == expected
        """
        assertLuaBoolean(code, true)
    }

    @Test
    fun testPointerFormatDifferentTables() {
        // Different tables should have different pointer values
        val code = """
            local t1 = {}
            local t2 = {}
            return string.format("%p", t1) ~= string.format("%p", t2)
        """
        assertLuaBoolean(code, true)
    }

    @Test
    fun testPointerFormatSameTable() {
        // Same table should have same pointer value
        val code = """
            local t = {}
            return string.format("%p", t) == string.format("%p", t)
        """
        assertLuaBoolean(code, true)
    }
}
