package ai.tenum.lua.compat.stdlib.table

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Compatibility tests for table.concat()
 * Based on official Lua 5.4.8 test suite (strings.lua)
 */
class TableConcatCompatTest : LuaCompatTestBase() {
    @Test
    fun testTableConcatBasic() {
        assertLuaString("return table.concat({'a', 'b', 'c'})", "abc")
    }

    @Test
    fun testTableConcatWithSeparator() {
        assertLuaString("return table.concat({'a', 'b', 'c'}, ', ')", "a, b, c")
    }

    @Test
    fun testTableConcatNumbers() {
        assertLuaString("return table.concat({1, 2, 3})", "123")
    }

    @Test
    fun testTableConcatWithRange() {
        assertLuaString("return table.concat({'a', 'b', 'c', 'd'}, '-', 2, 3)", "b-c")
    }

    @Test
    fun testTableConcatSingleElement() {
        assertLuaString("return table.concat({'hello'})", "hello")
    }

    @Test
    fun testTableConcatEmpty() {
        assertLuaString("return table.concat({})", "")
    }

    @Test
    fun testTableConcatPartialRange() {
        assertLuaString("return table.concat({'a', 'b', 'c', 'd', 'e'}, '|', 2, 4)", "b|c|d")
    }

    @Test
    fun testTableConcatErrorOnNilValue() {
        assertThrowsError(
            "table.concat({1, nil, 3}, '', 1, 3)",
            "invalid value (nil) at index",
        )
    }

    @Test
    fun testTableConcatErrorOnNilAtNegativeIndex() {
        assertThrowsError(
            "table.concat({}, ' ', -1, -1)",
            "invalid value (nil) at index -1",
        )
    }

    @Test
    fun testTableConcatErrorOnNilAtZeroIndex() {
        assertThrowsError(
            "table.concat({}, ' ', 0, 0)",
            "invalid value (nil) at index 0",
        )
    }

    @Test
    fun testTableConcatErrorAtMaxInteger() {
        assertThrowsError(
            "local maxi = math.maxinteger; table.concat({}, ' ', maxi, maxi)",
            "invalid value (nil) at index",
        )
    }

    @Test
    fun testTableConcatErrorAtMinInteger() {
        assertThrowsError(
            "local mini = math.mininteger; table.concat({}, ' ', mini, mini)",
            "invalid value (nil) at index",
        )
    }

    @Test
    fun testTableConcatErrorWrongType() {
        // strings.lua line 391: checkerror("table expected", table.concat, 3)
        assertLuaBoolean(
            """
            local function checkerror (msg, f, ...)
              local s, err = pcall(f, ...)
              return not s and string.find(err, msg) ~= nil
            end
            return checkerror("table expected", table.concat, 3)
            """.trimIndent(),
            true,
        )
    }

    @Test
    fun testTableConcatErrorAtMaxIntegerIndex() {
        // strings.lua line 392: checks error message contains maxinteger index
        assertLuaBoolean(
            """
            local maxi = math.maxinteger
            local function checkerror (msg, f, ...)
              local s, err = pcall(f, ...)
              print("Status:", s, "Error:", err, "Looking for:", msg)
              return not s and string.find(err, msg, 1, true) ~= nil
            end
            return checkerror("at index " .. maxi, table.concat, {}, " ", maxi, maxi)
            """.trimIndent(),
            true,
        )
    }

    @Test
    fun testTableConcatErrorOnNonStringValue() {
        // strings.lua line 411: table.concat should fail on non-string/non-number
        assertLuaBoolean(
            """
            local s, err = pcall(table.concat, {"a", "b", {}})
            print("pcall result: s =", s, ", err =", err)
            print("Expected: s should be false")
            print("Test result:", not s)
            return not s
            """.trimIndent(),
            true,
        )
    }

    @Test
    fun testTableConcatAtMaxIntegerBoundary() {
        // strings.lua line 408-409: preserves Long precision for maxinteger keys
        assertLuaString(
            """
            local maxi = math.maxinteger
            return table.concat({[maxi] = "alo", [maxi - 1] = "y"}, "-", maxi - 1, maxi)
            """.trimIndent(),
            "y-alo",
        )
    }
}
