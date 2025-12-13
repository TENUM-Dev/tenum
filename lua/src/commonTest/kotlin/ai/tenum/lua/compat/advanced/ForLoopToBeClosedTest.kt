package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for to-be-closed variables returned by generic for loop iterators.
 *
 * In Lua 5.4, when a generic for loop's iterator function returns more than 3 values,
 * the 4th and subsequent values are treated as to-be-closed variables. Their __close
 * metamethods should be called when the loop exits (normally or via break).
 *
 * Based on: lua-5.4.8-tests/locals.lua lines 298-315
 *
 * NOTE: These tests are currently marked as skipped because the implementation is incomplete.
 * See aidoc/for-loop-to-be-closed-variables.md for details.
 */
class ForLoopToBeClosedTest : LuaCompatTestBase() {
    @Test
    fun testForLoopClosesExtraReturnValues() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local flag = false
            local x = setmetatable({},
                {__close = function () assert(flag == false); flag = true end})
            -- return an empty iterator, nil, nil, and 'x' to be closed
            local function a ()
                return (function () return nil end), nil, nil, x
            end
            for k in a() do
                -- loop body never executes since iterator returns nil immediately
            end
            -- 'x' must be closed when loop exits
            return flag
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "4th return value from iterator should be closed when loop exits")
    }

    @Test
    fun testForLoopClosesWithConstLocals() {
        // This is the exact pattern from locals.lua:298-315
        vm.debugEnabled = true
        val result =
            execute(
                """
            local flag = false
            local x = setmetatable({},
                {__close = function () assert(flag == false); flag = true end})
            -- return an empty iterator, nil, nil, and 'x' to be closed
            local function a ()
                return (function () return nil end), nil, nil, x
            end
            local v <const> = 1
            local w <const> = 1
            local x <const> = 1
            local y <const> = 1
            local z <const> = 1
            for k in a() do
                a = k
            end    -- ending the loop must close 'x'
            return flag   -- 'x' must be closed here
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "4th return value from iterator should be closed even with const locals")
    }

    @Test
    fun testForLoopClosesAfterIterations() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            local count = 0
            
            local x = setmetatable({}, {__close = function() closed = true end})
            
            local function iterator()
                local i = 0
                return function()
                    i = i + 1
                    if i <= 3 then return i end
                end, nil, nil, x
            end
            
            for val in iterator() do
                count = count + 1
            end
            
            -- x should be closed after loop exits
            return closed and count == 3
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "To-be-closed variable should be closed after all iterations")
    }

    @Test
    fun testForLoopClosesMultipleValues() {
        // Note: Current implementation only supports ONE to-be-closed variable (4th return value)
        // This matches the Lua 5.4.8 test suite which only uses one to-be-closed variable
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            
            local x = setmetatable({}, {__close = function() closed = true end})
            
            local function iterator()
                return function() return nil end, nil, nil, x
            end
            
            for k in iterator() do
            end
            
            return closed
        """,
            )
        assertTrue(result is ai.tenum.lua.runtime.LuaBoolean)
        assertEquals(true, result.value, "To-be-closed variable should be closed")
    }

    @Test
    fun testForLoopClosesOnBreak() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            local x = setmetatable({}, {__close = function() closed = true end})
            
            local function iterator()
                local i = 0
                return function()
                    i = i + 1
                    if i <= 10 then return i end
                end, nil, nil, x
            end
            
            for val in iterator() do
                if val == 3 then break end
            end
            
            return closed
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "To-be-closed variable should be closed when loop exits via break")
    }

    @Test
    fun testForLoopClosesOnReturn() {
        // This is the exact failing test from locals.lua:337-361
        // Bug in 5.4.3: calls cannot be tail in the scope of to-be-closed variables
        // This must be valid for tbc variables created by 'for' loops.
        vm.debugEnabled = true
        val result =
            execute(
                """
            local function func2close(f)
                return setmetatable({}, {__close = f})
            end
            
            local closed = false
            
            local function foo()
                return function() return true end, 0, 0,
                       func2close(function() closed = true end)
            end
            
            local function tail() return closed end
            
            local function foo1()
                for k in foo() do return tail() end
            end
            
            -- When foo1() is called:
            -- 1. The for loop is entered with a to-be-closed variable
            -- 2. The iterator returns true, so loop body executes
            -- 3. return tail() executes - at this moment, closed is still false
            -- 4. When returning from foo1, the to-be-closed variable must be closed
            -- 5. After foo1 returns, closed should be true
            
            local result = foo1()
            -- tail() was called before __close, so result should be false
            assert(result == false, "tail() should return false (closed was false at call time)")
            -- but after foo1 returns, the __close should have been called
            return closed
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(
            true,
            result.value,
            "To-be-closed variable should be closed when function returns from inside loop",
        )
    }
}
