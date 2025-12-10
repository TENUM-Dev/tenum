package ai.tenum.lua.compiler

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.vm.LuaVmImpl
import kotlin.test.Test
import kotlin.test.assertEquals

class UpvalueChainingTest {
    @Test
    fun testNestedClosureUpvalueChaining() {
        // This Lua code creates a local variable in the main chunk,
        // captures it in a closure, and then captures that closure's upvalue in a nested closure.
        // The innermost closure should see the correct value.
        val code = """
            local x = 42
            local function outer()
                local function inner()
                    return x
                end
                return inner
            end
            local f = outer()
            return f()
        """
        val vm = LuaVmImpl()
        vm.debugEnabled = true
        val result = vm.execute(code)
        assertEquals(42.0, (result as LuaNumber).toDouble(), "Nested closure should see correct upvalue value")
    }
}
