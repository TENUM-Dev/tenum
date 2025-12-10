package ai.tenum.lua.compat.stdlib.table

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compatibility tests for table.unpack()
 * Based on official Lua 5.4.8 test suite (sort.lua)
 */
class TableUnpackCompatTest : LuaCompatTestBase() {
    @Test
    fun testTableUnpackBasic() {
        assertLuaNumber("local a, b, c = table.unpack({10, 20, 30}); return a", 10.0)
    }

    @Test
    fun testTableUnpackSecondValue() {
        assertLuaNumber("local a, b, c = table.unpack({10, 20, 30}); return b", 20.0)
    }

    @Test
    fun testTableUnpackWithRange() {
        assertLuaNumber("local a, b = table.unpack({10, 20, 30, 40}, 2, 3); return a", 20.0)
    }

    @Test
    fun testTableUnpackWithRangeEnd() {
        assertLuaNumber("local a, b = table.unpack({10, 20, 30, 40}, 2, 3); return b", 30.0)
    }

    @Test
    fun testTableUnpackAll() {
        assertLuaNumber("local a, b, c = table.unpack({10, 20, 30}); return c", 30.0)
    }

    @Test
    fun testTableUnpackEmpty() {
        assertLuaNil("local a = table.unpack({}); return a")
    }

    @Test
    fun testTableUnpackEmptyRange() {
        // When i > j, should return empty
        assertLuaNil("local a = table.unpack({1, 2, 3}, 10, 6); return a")
    }

    @Test
    fun testTableUnpackTooManyResults() {
        // Should throw error when trying to unpack more than 1 million elements
        assertThrowsError(
            "table.unpack({}, 1, 2000000)",
            "too many results",
        )
    }

    @Test
    fun testTableUnpackTooManyResultsWithMaxInt() {
        // Should throw error with maxinteger range
        assertThrowsError(
            "local maxI = math.maxinteger; table.unpack({}, 1, maxI)",
            "too many results",
        )
    }

    @Test
    fun testTableUnpackTooManyResultsWithLargeRange() {
        // Should throw error with large int32 range
        assertThrowsError(
            "local maxi = (1 << 31) - 1; table.unpack({}, 0, maxi)",
            "too many results",
        )
    }

    @Test
    fun testTableUnpackReasonableSize() {
        // Should work fine with 2000 elements (like sort.lua test)
        assertLuaNumber(
            """
            local a = {}
            for i=1,2000 do a[i]=i end
            local x = {table.unpack(a)}
            return #x
            """.trimIndent(),
            2000.0,
        )
    }

    @Test
    fun testTableUnpackReasonableSizePartial() {
        // Should work fine with partial unpack of 3 elements from large table
        assertLuaNumber(
            """
            local a = {}
            for i=1,2000 do a[i]=i end
            local x = {table.unpack(a, 1998)}
            return #x
            """.trimIndent(),
            3.0,
        )
    }

    @Test
    fun testTableUnpackOutOfMemoryError() {
        // Test that OOM errors are caught and converted to proper Lua errors with stack trace
        val exception =
            kotlin
                .runCatching {
                    execute(
                        """
                        local function causeOOM()
                            -- This should trigger the size check and throw before actual OOM
                            table.unpack({}, 1, 2000000)
                        end
                        causeOOM()
                        """.trimIndent(),
                    )
                }.exceptionOrNull()

        // Should get a Lua error with proper message
        assertNotNull(exception)
        assertTrue(
            exception.message?.contains("too many results") == true ||
                exception.message?.contains("not enough memory") == true,
            "Expected OOM-related error message, got: ${exception.message}",
        )
        // Should have stack trace showing the Lua function
        assertTrue(
            exception.message?.contains("causeOOM") == true,
            "Expected stack trace to contain function name, got: ${exception.message}",
        )
    }

    @Test
    fun testTableUnpackWithMaxIntegerEdgeCases() {
        // Test edge case from sort.lua line 64: unpack(t, maxI - 1, maxI)
        assertLuaNumber(
            """
            local maxI = math.maxinteger
            local t = {[maxI - 1] = 12, [maxI] = 23}
            local a, b = table.unpack(t, maxI - 1, maxI)
            assert(a == 12 and b == 23)
            return 1
            """.trimIndent(),
            1.0,
        )
    }

    @Test
    fun testTableUnpackWithMaxIntegerSingleElement() {
        // Test edge case: unpack(t, maxI, maxI) - single element
        assertLuaNumber(
            """
            local maxI = math.maxinteger
            local t = {[maxI] = 23}
            local a, b = table.unpack(t, maxI, maxI)
            assert(a == 23 and b == nil)
            return 1
            """.trimIndent(),
            1.0,
        )
    }

    @Test
    fun testTableUnpackWithMinIntegerEdgeCases() {
        // Test edge case from sort.lua: unpack(t, minI, minI + 1)
        assertLuaNumber(
            """
            local minI = math.mininteger
            local t = {[minI] = 12.3, [minI + 1] = 23.5}
            local a, b = table.unpack(t, minI, minI + 1)
            assert(a == 12.3 and b == 23.5)
            return 1
            """.trimIndent(),
            1.0,
        )
    }

    @Test
    fun testTableUnpackWithMinIntegerSingleElement() {
        // Test edge case: unpack(t, minI, minI) - single element
        assertLuaNumber(
            """
            local minI = math.mininteger
            local t = {[minI] = 12.3}
            local a, b = table.unpack(t, minI, minI)
            assert(a == 12.3 and b == nil)
            return 1
            """.trimIndent(),
            1.0,
        )
    }

    @Test
    fun testTableUnpackOverflowPrevention() {
        // Test that overflow in range calculation is properly caught
        assertThrowsError(
            """
            local minI = math.mininteger
            local maxI = math.maxinteger
            table.unpack({}, minI, maxI)
            """.trimIndent(),
            "too many results",
        )
    }
}
