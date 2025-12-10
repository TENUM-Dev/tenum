package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvAndGlobalScopeEdgeCasesTest : LuaCompatTestBase() {
    @Test
    fun testEnvFunctionAssignment() {
        val result =
            execute(
                """
            local _ENV = { }
            function foo() return 123 end
            return _ENV.foo()
        """,
            )
        assertEquals(123.0, (result as ai.tenum.lua.runtime.LuaNumber).toDouble())
    }

    @Test
    fun testEnvDoesNotAffectLocals() {
        val result =
            execute(
                """
            local _ENV = { x = 1 }
            local x = 99
            return x, _ENV.x
        """,
            )
        // Only the first return value is checked by default
        assertEquals(99.0, (result as ai.tenum.lua.runtime.LuaNumber).toDouble())
    }

    @Test
    fun testEnvAssignmentDoesNotLeak() {
        val result =
            execute(
                """
            do
                local _ENV = { y = 42 }
            end
            return y or 'not found'
        """,
            )
        assertEquals("not found", result.toString())
    }

    @Test
    fun testEnvMetatableFallback() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local mt = { __index = function(_,k) return k..'_fallback' end }
            local _ENV = setmetatable({}, mt)
            return missing
        """,
            )
        assertEquals("missing_fallback", result.toString())
    }

    @Test
    fun testEnvWithUpvalue() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local _ENV = { z = 7 }
            local function f() return z end
            return f()
        """,
            )
        assertEquals(7.0, (result as ai.tenum.lua.runtime.LuaNumber).toDouble())
    }
}
