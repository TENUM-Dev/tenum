package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

class ForLoopMinimalTest : LuaCompatTestBase() {
    @Test
    fun testMinimalInlineFunction() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            
            local function test()
                local x = setmetatable({}, {
                    __close = function() closed = true end
                })
                for k in (function() return function() return true end, nil, nil, x end)() do
                    return closed  -- Should return false
                end
            end
            
            local r = test()  -- Calls test, which should close x before returning
            print("r =", r, ", closed =", closed)
            return closed  -- Should return true
        """,
            )
        assertEquals(true, (result as LuaBoolean).value)
    }

    @Test
    fun testMinimalWithNamedFunction() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            
            local function test()
                local function foo()
                    local x = setmetatable({}, {
                        __close = function() closed = true end
                    })
                    return function() return true end, nil, nil, x
                end
                
                for k in foo() do
                    return closed  -- Should return false
                end
            end
            
            local r = test()  -- Calls test, which should close x before returning
            return closed  -- Should return true
        """,
            )
        assertEquals(true, (result as LuaBoolean).value)
    }

    @Test
    fun testSameScopeAsOriginal() {
        // This matches the EXACT structure of the failing test
        vm.debugEnabled = true
        val result =
            execute(
                """
            local function func2close(f)
                return setmetatable({}, {__close = f})
            end
            
            local closed = false
            local close_called = false
            
            local function foo()
                return function() return true end, 0, 0,
                       func2close(function() 
                           close_called = true
                           closed = true 
                       end)
            end
            
            local function tail() return closed end
            
            local function foo1()
                for k in foo() do 
                    -- Check if the iterator actually returned a to-be-closed value
                    -- This would be the 4th return value
                    return tail() 
                end
            end
            
            -- Also test that foo() actually returns 4 values with the right structure
            local f, s, v, tbc = foo()
            print("foo() returns:")
            print("  f =", type(f), f)
            print("  s =", type(s), s)
            print("  v =", type(v), v)
            print("  tbc =", type(tbc), tbc)
            local mt = getmetatable(tbc)
            print("  tbc metatable =", mt)
            if mt then
                print("  tbc.__close =", mt.__close)
            end
            
            local result = foo1()
            print("After foo1(): result =", result, ", closed =", closed, ", close_called =", close_called)
            assert(result == false, "Expected result = false")
            assert(close_called == true, "Expected __close to be called")
            return closed
        """,
            )
        assertEquals(true, (result as LuaBoolean).value)
    }
}
