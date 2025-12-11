package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for StringOperations domain object
 */
class StringOperationsTest {
    // ============================================================================
    // substringLua() tests
    // ============================================================================

    @Test
    fun testSubstringBasic() {
        val result = StringOperations.substringLua("hello", 1, 5)
        assertEquals("hello", result)
    }

    @Test
    fun testSubstringPartial() {
        val result = StringOperations.substringLua("hello", 2, 4)
        assertEquals("ell", result)
    }

    @Test
    fun testSubstringNegativeStart() {
        val result = StringOperations.substringLua("hello", -3, 5)
        assertEquals("llo", result)
    }

    @Test
    fun testSubstringNegativeEnd() {
        val result = StringOperations.substringLua("hello", 1, -1)
        assertEquals("hello", result)
    }

    @Test
    fun testSubstringBothNegative() {
        val result = StringOperations.substringLua("hello", -4, -2)
        assertEquals("ell", result)
    }

    @Test
    fun testSubstringZeroStart() {
        val result = StringOperations.substringLua("hello", 0, 3)
        assertEquals("hel", result)
    }

    @Test
    fun testSubstringOutOfBounds() {
        val result = StringOperations.substringLua("hello", 10, 20)
        assertEquals("", result)
    }

    @Test
    fun testSubstringEndBeforeStart() {
        val result = StringOperations.substringLua("hello", 4, 2)
        assertEquals("", result)
    }

    // ============================================================================
    // repeatString() tests
    // ============================================================================

    @Test
    fun testRepeatStringBasic() {
        val result = StringOperations.repeatString("ha", 3, "")
        assertEquals("hahaha", result)
    }

    @Test
    fun testRepeatStringWithSeparator() {
        val result = StringOperations.repeatString("foo", 3, "-")
        assertEquals("foo-foo-foo", result)
    }

    @Test
    fun testRepeatStringOnce() {
        val result = StringOperations.repeatString("test", 1, "-")
        assertEquals("test", result)
    }

    @Test
    fun testRepeatStringZero() {
        val result = StringOperations.repeatString("test", 0, "-")
        assertEquals("", result)
    }

    @Test
    fun testRepeatStringNegative() {
        val result = StringOperations.repeatString("test", -5, "-")
        assertEquals("", result)
    }

    @Test
    fun testRepeatStringEmptySeparator() {
        val result = StringOperations.repeatString("x", 5, "")
        assertEquals("xxxxx", result)
    }

    // ============================================================================
    // stringByte() tests
    // ============================================================================

    @Test
    fun testStringByteSingle() {
        val result = StringOperations.stringByte("A", 1, 1)
        assertEquals(1, result.size)
        assertTrue(result[0] is LuaNumber)
        assertEquals(65.0, (result[0] as LuaNumber).toDouble())
    }

    @Test
    fun testStringByteMultiple() {
        val result = StringOperations.stringByte("ABC", 1, 3)
        assertEquals(3, result.size)
        assertEquals(65.0, (result[0] as LuaNumber).toDouble()) // A
        assertEquals(66.0, (result[1] as LuaNumber).toDouble()) // B
        assertEquals(67.0, (result[2] as LuaNumber).toDouble()) // C
    }

    @Test
    fun testStringBytePartial() {
        val result = StringOperations.stringByte("hello", 2, 4)
        assertEquals(3, result.size)
        assertEquals(101.0, (result[0] as LuaNumber).toDouble()) // e
        assertEquals(108.0, (result[1] as LuaNumber).toDouble()) // l
        assertEquals(108.0, (result[2] as LuaNumber).toDouble()) // l
    }

    @Test
    fun testStringByteNegativeIndex() {
        val result = StringOperations.stringByte("hello", -1, -1)
        assertEquals(1, result.size)
        assertEquals(111.0, (result[0] as LuaNumber).toDouble()) // o
    }

    @Test
    fun testStringByteOutOfBounds() {
        // When start index is completely out of bounds, return empty list
        val result = StringOperations.stringByte("hi", 10, 20)
        assertEquals(0, result.size)
    }
}
