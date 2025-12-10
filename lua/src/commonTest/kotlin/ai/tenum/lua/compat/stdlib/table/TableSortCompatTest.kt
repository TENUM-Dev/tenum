package ai.tenum.lua.compat.stdlib.table

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Compatibility tests for table.sort()
 * Based on official Lua 5.4.8 test suite (sort.lua)
 */
class TableSortCompatTest : LuaCompatTestBase() {
    @Test
    fun testTableSortNumbers() {
        assertLuaNumber("local t = {3, 1, 2}; table.sort(t); return t[1]", 1.0)
    }

    @Test
    fun testTableSortNumbersLast() {
        assertLuaNumber("local t = {3, 1, 2}; table.sort(t); return t[3]", 3.0)
    }

    @Test
    fun testTableSortStrings() {
        assertLuaString("local t = {'c', 'a', 'b'}; table.sort(t); return t[1]", "a")
    }

    @Test
    fun testTableSortWithComparator() {
        assertLuaNumber("local t = {1, 2, 3}; table.sort(t, function(a, b) return a > b end); return t[1]", 3.0)
    }

    @Test
    fun testTableSortDescending() {
        assertLuaNumber("local t = {1, 2, 3}; table.sort(t, function(a, b) return a > b end); return t[3]", 1.0)
    }

    @Test
    fun testTableSortEmpty() {
        assertLuaNumber("local t = {}; table.sort(t); return #t", 0.0)
    }

    @Test
    fun testTableSortSingleElement() {
        assertLuaNumber("local t = {42}; table.sort(t); return t[1]", 42.0)
    }

    @Test
    fun testTableSortWithConcat() {
        assertLuaString("local t = {3, 1, 2}; table.sort(t); return table.concat(t, '-')", "1-2-3")
    }

    @Test
    fun testTableSortDetectsInvalidOrderFunction() {
        // Test that table.sort detects invalid order functions (from sort.lua:201-206)
        // A comparator that always returns true violates the total order property
        execute(
            """
            local function checkerror(msg, f, ...)
                local st, err = pcall(f, ...)
                assert(not st, "should have errored")
                assert(string.find(err, msg, 1, true), 
                    string.format("expected error containing '%s', got '%s'", msg, err))
            end
            
            local function f(a, b) assert(a and b); return true end
            checkerror("invalid order function", table.sort, {1,2,3,4}, f)
            checkerror("invalid order function", table.sort, {1,2,3,4,5}, f)
            checkerror("invalid order function", table.sort, {1,2,3,4,5,6}, f)
        """,
        )
    }

    @Test
    fun testTableSortWithLtMetamethod() {
        // Test that table.sort uses __lt metamethod when no comparator provided (from sort.lua:303-310)
        execute(
            """
            local tt = {__lt = function (a,b) return a.val < b.val end}
            local a = {}
            for i=1,10 do  
                a[i] = {val=math.random(100)}
                setmetatable(a[i], tt)
            end
            table.sort(a)
            
            -- Check that table is sorted using __lt metamethod
            for n = #a, 2, -1 do
                assert(not (a[n].val < a[n-1].val), 
                    string.format("Elements not sorted: a[%d].val=%d >= a[%d].val=%d failed", 
                        n, a[n].val, n-1, a[n-1].val))
            end
        """,
        )
    }
}
