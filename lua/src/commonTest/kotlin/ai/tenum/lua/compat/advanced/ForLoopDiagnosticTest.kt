package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

class ForLoopDiagnosticTest : LuaCompatTestBase() {
    @Test
    fun testManualClose() {
        // Simplest possible test
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            
            local x = setmetatable({}, {
                __close = function() 
                    print("__close called!")
                    closed = true 
                end
            })
            
            -- Manually test: call __close directly
            local mt = getmetatable(x)
            if mt and mt.__close then
                print("Metatable has __close")
                mt.__close(x)
            else
                print("No __close found!")
            end
            
            return closed
        """,
            )
        assertEquals(true, (result as LuaBoolean).value)
    }

    @Test
    fun testForLoopInNestedFunction() {
        // Test when everything is inside a function (not main chunk)
        vm.debugEnabled = true
        val result =
            execute(
                """
            local function runTest()
                local function func2close(f)
                    return setmetatable({}, {__close = f})
                end
                
                local closed = false
                
                local function foo()
                    return function() return true end, 0, 0,
                           func2close(function() closed = true end)
                end
                
                local function foo1()
                    for k in foo() do return closed end
                end
                
                local r = foo1()
                return closed
            end
            
            return runTest()
        """,
            )
        assertEquals(true, (result as LuaBoolean).value)
    }
}
