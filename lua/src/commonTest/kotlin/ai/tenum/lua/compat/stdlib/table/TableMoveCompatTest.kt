package ai.tenum.lua.compat.stdlib.table

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Compatibility tests for table.move()
 * Based on official Lua 5.4.8 test suite (sort.lua)
 */
class TableMoveCompatTest : LuaCompatTestBase() {
    @Test
    fun testTableMoveBasic() {
        assertLuaNumber("local t1 = {1, 2, 3}; local t2 = {10, 20, 30}; table.move(t1, 1, 2, 1, t2); return t2[1]", 1.0)
    }

    @Test
    fun testTableMoveSecondElement() {
        assertLuaNumber("local t1 = {1, 2, 3}; local t2 = {10, 20, 30}; table.move(t1, 1, 2, 1, t2); return t2[2]", 2.0)
    }

    @Test
    fun testTableMoveOffset() {
        assertLuaNumber("local t1 = {1, 2, 3}; local t2 = {10, 20, 30}; table.move(t1, 1, 2, 2, t2); return t2[2]", 1.0)
    }

    @Test
    fun testTableMoveSameTable() {
        assertLuaNumber("local t = {1, 2, 3}; table.move(t, 1, 2, 3); return t[3]", 1.0)
    }

    @Test
    fun testTableMoveSameTableOverlap() {
        assertLuaNumber("local t = {1, 2, 3}; table.move(t, 1, 2, 2); return t[2]", 1.0)
    }

    @Test
    fun testTableMoveRange() {
        assertLuaNumber("local t1 = {1, 2, 3, 4, 5}; local t2 = {}; table.move(t1, 2, 4, 1, t2); return t2[1]", 2.0)
    }

    @Test
    fun testTableMoveWithLargeIntegerKeys() {
        // Test table.move with keys at the edge of maxinteger range (from sort.lua:128-130)
        execute(
            """
            local maxI = math.maxinteger
            local minI = math.mininteger
            
            -- Test moving from maxI range to negative indices
            local a = table.move({[maxI - 2] = 1, [maxI - 1] = 2, [maxI] = 3},
                                 maxI - 2, maxI, -10, {})
            assert(a[-10] == 1, "a[-10] should be 1")
            assert(a[-9] == 2, "a[-9] should be 2")
            assert(a[-8] == 3, "a[-8] should be 3")
            
            -- Test moving from minI range to negative indices
            local b = table.move({[minI] = 1, [minI + 1] = 2, [minI + 2] = 3},
                                 minI, minI + 2, -10, {})
            assert(b[-10] == 1, "b[-10] should be 1")
            assert(b[-9] == 2, "b[-9] should be 2")
            assert(b[-8] == 3, "b[-8] should be 3")
        """,
        )
    }

    @Test
    fun testTableMoveWithMetatableIndex() {
        // Test that table.move respects __index metamethod (from sort.lua:145-150)
        execute(
            """
            local function eqT(a, b)
              for k, v in pairs(a) do assert(b[k] == v) end 
              for k, v in pairs(b) do assert(a[k] == v) end 
            end
            
            local a = setmetatable({}, {
                  __index = function (_,k) return k * 10 end,
                  __newindex = error})
            local b = table.move(a, 1, 10, 3, {})
            eqT(a, {})
            eqT(b, {nil,nil,10,20,30,40,50,60,70,80,90,100})
        """,
        )
    }

    @Test
    fun testTableMoveStopsOnFirstError() {
        // Simplified test: verify that error in __newindex stops immediately
        execute(
            """
            local count = 0
            local a = setmetatable({}, {
                __index = function(_, k) count = count + 1; return k end,
                __newindex = function(_, k) error("stop here") end,
            })
            local st, msg = pcall(table.move, a, 1, 100, 1)
            assert(not st, "pcall should fail")
            assert(count == 1, "should only read once, but read " .. count .. " times")
        """,
        )
    }

    @Test
    fun testTableMoveWithLargeRangeStopsOnError() {
        // Test that table.move with very large ranges only accesses first elements
        // and stops immediately when __newindex throws error (from sort.lua:164-179)
        execute(
            """
            local maxI = math.maxinteger
            local minI = math.mininteger
            
            local function checkmove(f, e, t, x, y)
                local pos1, pos2
                local a = setmetatable({}, {
                    __index = function(_, k) pos1 = k end,
                    __newindex = function(_, k) pos2 = k; error() end,
                })
                local st, msg = pcall(table.move, a, f, e, t)
                assert(not st and not msg and pos1 == x and pos2 == y,
                    string.format("Expected pos1=%s, pos2=%s but got pos1=%s, pos2=%s", x, y, pos1, pos2))
            end
            
            -- These tests verify that table.move accesses the correct first elements
            -- without iterating through the entire (impossibly large) range
            checkmove(1, maxI, 0, 1, 0)
            checkmove(0, maxI - 1, 1, maxI - 1, maxI)
            checkmove(minI, -2, -5, -2, maxI - 6)
            checkmove(minI + 1, -1, -2, -1, maxI - 3)
            checkmove(minI, -2, 0, minI, 0)  -- non overlapping
            checkmove(minI + 1, -1, 1, minI + 1, 1)  -- non overlapping
        """,
        )
    }

    @Test
    fun testTableMoveWithOverflowErrors() {
        // Test that table.move throws appropriate errors for overflow cases (from sort.lua:181-188)
        execute(
            """
            local maxI = math.maxinteger
            local minI = math.mininteger
            
            local function checkerror(msg, f, ...)
                local st, err = pcall(f, ...)
                assert(not st, "should have errored")
                assert(string.find(err, msg, 1, true), 
                    string.format("expected error containing '%s', got '%s'", msg, err))
            end
            
            -- "too many" errors: source range is too large
            checkerror("too many", table.move, {}, 0, maxI, 1)
            checkerror("too many", table.move, {}, -1, maxI - 1, 1)
            checkerror("too many", table.move, {}, minI, -1, 1)
            checkerror("too many", table.move, {}, minI, maxI, 1)
            
            -- "wrap around" errors: destination range would overflow
            checkerror("wrap around", table.move, {}, 1, maxI, 2)
            checkerror("wrap around", table.move, {}, 1, 2, maxI)
            checkerror("wrap around", table.move, {}, minI, -2, 2)
        """,
        )
    }
}
