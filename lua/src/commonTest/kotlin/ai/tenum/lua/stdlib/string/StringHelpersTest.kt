package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for ArgumentHelpers and StringPatternMatching domain objects
 */
class StringHelpersTest {
    // ============================================================================
    // ArgumentHelpers.coerceToString() tests
    // ============================================================================

    @Test
    fun testCoerceStringToString() {
        val result = ArgumentHelpers.coerceToString(LuaString("hello"))
        assertEquals("hello", result)
    }

    @Test
    fun testCoerceIntegerNumberToString() {
        val result = ArgumentHelpers.coerceToString(LuaNumber.of(42.0))
        assertEquals("42", result)
    }

    @Test
    fun testCoerceFloatNumberToString() {
        val result = ArgumentHelpers.coerceToString(LuaNumber.of(3.14))
        assertEquals("3.14", result)
    }

    @Test
    fun testCoerceNilToString() {
        val result = ArgumentHelpers.coerceToString(LuaNil)
        assertEquals("", result)
    }

    @Test
    fun testCoerceNullToString() {
        val result = ArgumentHelpers.coerceToString(null)
        assertEquals("", result)
    }

    // ============================================================================
    // StringPatternMatching.findString() tests
    // ============================================================================

    @Test
    fun testFindStringBasic() {
        val result = StringPatternMatching.findString("hello world", "world", 1, true)
        assertEquals(2, result.size)
        assertEquals(7.0, (result[0] as LuaNumber).toDouble()) // Start index
        assertEquals(11.0, (result[1] as LuaNumber).toDouble()) // End index
    }

    @Test
    fun testFindStringAtStart() {
        val result = StringPatternMatching.findString("hello world", "hello", 1, true)
        assertEquals(2, result.size)
        assertEquals(1.0, (result[0] as LuaNumber).toDouble())
        assertEquals(5.0, (result[1] as LuaNumber).toDouble())
    }

    @Test
    fun testFindStringNotFound() {
        val result = StringPatternMatching.findString("hello world", "xyz", 1, true)
        assertEquals(1, result.size)
        assertTrue(result[0] is LuaNil)
    }

    @Test
    fun testFindStringWithInit() {
        val result = StringPatternMatching.findString("hello hello", "hello", 7, true)
        assertEquals(2, result.size)
        assertEquals(7.0, (result[0] as LuaNumber).toDouble())
        assertEquals(11.0, (result[1] as LuaNumber).toDouble())
    }

    @Test
    fun testFindStringNegativeInit() {
        val result = StringPatternMatching.findString("hello world", "world", -5, true)
        assertEquals(2, result.size)
        assertEquals(7.0, (result[0] as LuaNumber).toDouble())
    }

    @Test
    fun testFindStringInitOutOfBounds() {
        val result = StringPatternMatching.findString("hello", "test", 100, true)
        assertEquals(1, result.size)
        assertTrue(result[0] is LuaNil)
    }
}
