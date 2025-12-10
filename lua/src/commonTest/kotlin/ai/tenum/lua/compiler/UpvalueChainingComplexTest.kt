package ai.tenum.lua.compiler

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.vm.LuaVmImpl
import kotlin.test.Test
import kotlin.test.assertEquals

class UpvalueChainingComplexTest {
    @Test
    fun testThreeLevelNestedClosure() {
        // Three-level closure: x in main, y in outer, z in middle, all captured by innermost
        val code = """
            local x = 10
            local function outer()
                local y = 20
                local function middle()
                    local z = 30
                    local function inner()
                        return x + y + z
                    end
                    return inner
                end
                return middle
            end
            local m = outer()
            local f = m()
            return f() -- should be 10+20+30 = 60
        """
        val vm = LuaVmImpl()
        val result = vm.execute(code)
        assertEquals(60.0, (result as LuaNumber).toDouble(), "Innermost closure should see all upvalues")
    }

    @Test
    fun testUpvalueMutationPropagation() {
        // Mutate an upvalue in an inner closure, see effect in outer
        val code = """
            local x = 5
            local function outer()
                local function inner()
                    x = x + 1
                end
                inner()
                return x
            end
            return outer() -- should be 6
        """
        val vm = LuaVmImpl()
        vm.debugEnabled = true
        val result = vm.execute(code)
        assertEquals(6.0, (result as LuaNumber).toDouble(), "Upvalue mutation should propagate")
    }

    @Test
    fun testToBeClosedUpvalue() {
        // Upvalue is a to-be-closed variable, ensure correct value in closure
        val code = """
            local function make()
                local f
                do
                    local x <close> = 99
                    function f() return x end
                end
                return f
            end
            local f = make()
            return f() -- should be 99
        """
        val vm = LuaVmImpl()
        vm.debugEnabled = true
        val result = vm.execute(code)
        assertEquals(99.0, (result as LuaNumber).toDouble(), "Closure should capture to-be-closed upvalue")
    }
}
