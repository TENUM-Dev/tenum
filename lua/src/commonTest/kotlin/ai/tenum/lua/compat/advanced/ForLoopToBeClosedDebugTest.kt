package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Focused test for the break + to-be-closed variable bug in for loops.
 * Based on locals.lua:365-382
 */
class ForLoopToBeClosedDebugTest : LuaCompatTestBase() {
    @Test
    fun testForLoopToBeClosedDebug() {
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
                       func2close(function() 
                           print("__close called!")
                           closed = true 
                       end)
            end
            
            local function foo1()
                for k in foo() do 
                    print("In loop, about to return")
                    return closed
                end
            end
            
            print("Before calling foo1")
            local result = foo1()
            print("After calling foo1, result =", result, ", closed =", closed)
            
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

    @Test
    fun testBreakWithToBeClosedAndUpvalue() {
        // Bug in 5.4.4: 'break' may generate wrong 'close' instruction when
        // leaving a loop block, especially when there's an upvalue creation inside
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            
            local o1 = setmetatable({}, {__close=function() closed = true end})
            
            local function test()
                for k, v in next, {}, nil, o1 do
                    local function f() return k end   -- create an upvalue
                    break
                end
                assert(closed, "o1 should be closed after break")
            end
            
            test()
            return closed
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "To-be-closed variable should be closed after break, even with upvalue")
    }

    @Test
    fun testBreakWithToBeClosedSimple() {
        // Simpler version without upvalue
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            
            local o1 = setmetatable({}, {__close=function() closed = true end})
            
            local function test()
                for k, v in next, {}, nil, o1 do
                    break
                end
                assert(closed, "o1 should be closed after break")
            end
            
            test()
            return closed
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "To-be-closed variable should be closed after simple break")
    }

    @Test
    fun testNormalExitWithToBeClosedAndUpvalue() {
        // Verify normal exit also works
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            
            local o1 = setmetatable({}, {__close=function() closed = true end})
            
            local function test()
                for k, v in next, {}, nil, o1 do
                    local function f() return k end   -- create an upvalue
                end
                assert(closed, "o1 should be closed after normal exit")
            end
            
            test()
            return closed
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "To-be-closed variable should be closed after normal exit with upvalue")
    }
}
