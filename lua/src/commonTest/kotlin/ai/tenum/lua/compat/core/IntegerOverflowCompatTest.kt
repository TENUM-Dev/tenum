package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for integer arithmetic overflow/wraparound behavior.
 * Lua 5.4 requires proper 64-bit wraparound semantics for integer operations.
 */
class IntegerOverflowCompatTest : LuaCompatTestBase() {
    @Test
    fun testMaxIntegerPlusOne() {
        // Critical: Long.MAX_VALUE + 1 should wrap to Long.MIN_VALUE
        val result =
            execute(
                """
                local max = math.maxinteger
                local result = max + 1
                return result == math.mininteger and math.type(result) == 'integer'
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "MAX + 1 should wrap to MIN")
    }

    @Test
    fun testMinIntegerMinusOne() {
        // Critical: Long.MIN_VALUE - 1 should wrap to Long.MAX_VALUE
        val result =
            execute(
                """
                local min = math.mininteger
                local result = min - 1
                return result == math.maxinteger and math.type(result) == 'integer'
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "MIN - 1 should wrap to MAX")
    }

    @Test
    fun testMaxIntegerMultiplyTwo() {
        // Long.MAX_VALUE * 2 should wrap (not saturate)
        val result =
            execute(
                """
                local max = math.maxinteger
                local result = max * 2
                return math.type(result) == 'integer' and result ~= max
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "MAX * 2 should wrap (stay integer, not saturate)")
    }

    @Test
    fun testLargeIntegerAddition() {
        // Test addition that overflows
        val result =
            execute(
                """
                local a = 9223372036854775807  -- Long.MAX_VALUE
                local b = 1
                local sum = a + b
                return math.type(sum) == 'integer' and sum < 0
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "Large positive overflow should wrap to negative")
    }

    @Test
    fun testDirectLongWraparound() {
        // Direct test of the wraparound behavior
        vm.debugEnabled = true
        val result = execute("return 9223372036854775807 + 1")
        assertTrue(result is LuaLong, "Result should be LuaLong, got: ${result::class.simpleName}")
        assertEquals(Long.MIN_VALUE, result.value, "Should wrap to MIN_VALUE")
    }

    @Test
    fun testOverflowInBinaryOp() {
        // Test the binaryOp path (used by DIV, MOD, IDIV, POW after metamethod check)
        // Actually, DIV always returns float, so let's test with string coercion
        val result =
            execute(
                """
                local max = math.maxinteger
                local one = "1"  -- String that coerces to integer
                local result = max + one
                return math.type(result) == 'integer' and result == math.mininteger
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "String coercion path should also wrap correctly")
    }
}
