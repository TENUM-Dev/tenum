package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
