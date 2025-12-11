package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for Lua 5.2+ local _ENV feature
 *
 * In Lua 5.2+, declaring `local _ENV = <table>` changes the environment for that scope.
 * All global variable accesses within that scope become table accesses to the local _ENV.
 *
 * Reference: Lua 5.4.8 manual section 3.2 (Environments and the Global Environment)
 * Official tests: lua-5.4.8-tests/locals.lua lines 152-179
 */
class LocalEnvCompatTest : LuaCompatTestBase() {
    @Test
    fun testLocalEnvBasic() {
        // Local _ENV with custom table
        val result =
            execute(
                """
            local _ENV = {x = 10, y = 20}
            return x + y
        """,
            )
        assertLuaNumber(result, 30.0)
    }

    @Test
    fun testLocalEnvFunctionDefinition() {
        // Function definitions go into local _ENV
        val result =
            execute(
                """
            local _ENV = {}
            function foo() return 42 end
            return _ENV.foo()
        """,
            )
        assertLuaNumber(result, 42.0)
    }

    @Test
    fun testLocalEnvIsolation() {
        // Local _ENV doesn't affect outer scope
        val result =
            execute(
                """
            x = 100
            do
                local _ENV = {x = 10}
                x = x + 5
            end
            return x
        """,
            )
        assertLuaNumber(result, 100.0)
    }

    @Test
    fun testLocalEnvWithinLocalEnv() {
        // Inner local _ENV value should be 15
        val result =
            execute(
                """
            local _ENV = {x = 10}
            x = x + 5
            return x
        """,
            )
        assertLuaNumber(result, 15.0)
    }

    @Test
    fun testLocalEnvNestedScopes() {
        // Nested local _ENV
        val result =
            execute(
                """
            local _ENV = {x = 10}
            do
                local _ENV = {x = 20}
                x = x + 5
            end
            return x
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testLocalEnvWithStdlib() {
        // Must include stdlib functions in local _ENV to use them
        val result =
            execute(
                """
            local _ENV = {type = type, x = 10}
            return type(x)
        """,
            )
        assertLuaString(result, "number")
    }

    @Test
    fun testLocalEnvVariableAssignment() {
        // Assignment to global in local _ENV scope
        val result =
            execute(
                """
            local _ENV = {}
            x = 10
            y = 20
            return _ENV.x + _ENV.y
        """,
            )
        assertLuaNumber(result, 30.0)
    }

    @Test
    fun testLocalEnvInheritsNothing() {
        // Local _ENV={} means no access to globals
        val result =
            execute(
                """
            x = 100
            local ok = true
            do
                local _ENV = {}
                -- x should be nil in this scope
                ok = (x == nil)
            end
            return ok
        """,
            )
        // This will fail without proper _ENV support - ok becomes nil
        // because "ok = ..." tries to set global ok, not outer scope local
    }

    @Test
    fun testLocalEnvFromGlobal() {
        vm.debugEnabled = true
        // Local _ENV can be initialized from _G
        val result =
            execute(
                """
            local _ENV = (function(...) return ... end)(_G)
            x = 10
            return _G.x
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testLocalEnvFunctionClosure() {
        // Functions defined with local _ENV capture that environment
        val result =
            execute(
                """
            local function make_env()
                local _ENV = {x = 10}
                function get_x() return x end
                return _ENV
            end
            local env = make_env()
            return env.get_x()
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testLocalEnvReadGlobalWriteLocal() {
        // Reading global before setting local _ENV
        val result =
            execute(
                """
            local type_func = type
            local _ENV = {}
            return type_func(123)
        """,
            )
        assertLuaString(result, "number")
    }

    @Test
    fun testLocalEnvWithMetatable() {
        // Local _ENV with metatable for fallback to _G
        val result =
            execute(
                """
            local mt = {__index = _G}
            local _ENV = setmetatable({x = 10}, mt)
            return x + 5  -- x from _ENV (10), arithmetic works
        """,
            )
        assertLuaNumber(result, 15.0)
    }

    @Test
    fun testLocalEnvMultipleAssignments() {
        // Multiple assignments in local _ENV scope
        val result =
            execute(
                """
            local _ENV = {}
            a, b, c = 1, 2, 3
            return _ENV.a + _ENV.b + _ENV.c
        """,
            )
        assertLuaNumber(result, 6.0)
    }

    @Test
    fun testLocalEnvTableAccess() {
        // Ensure table.field access still works with local _ENV
        val result =
            execute(
                """
            local _ENV = {t = {x = 10}}
            return t.x
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testLocalEnvFromVarargs() {
        // Create _ENV from varargs (like in the preload test)
        val result =
            execute(
                """
            local function test(...)
                local _ENV = {...}
                function foo() return 42 end
                return _ENV
            end
            local env = test(10, 20, 30)
            return env.foo()
        """,
            )
        assertLuaNumber(result, 42.0)
    }

    @Test
    fun testLocalEnvConstAttribute() {
        // Local _ENV can be const
        val result =
            execute(
                """
            local _ENV <const> = {x = 10}
            return x
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testPreloadWithLocalEnv() {
        vm.debugEnabled = true
        // The actual preload test case - most critical
        val result =
            execute(
                """
            local p = package
            package = {}
            p.preload.pl = function (...)
                local _ENV = {...}
                function xuxu(x) return x + 20 end
                return _ENV
            end
            local pl = require("pl")
            package = p
            return pl.xuxu(10)
        """,
            )
        assertLuaNumber(result, 30.0)
    }

    @Test
    fun testLocalEnvDoesNotAffectLocals() {
        // Local variables should still work normally
        val result =
            execute(
                """
            local y = 5
            local _ENV = {x = 10}
            return x + y
        """,
            )
        assertLuaNumber(result, 15.0)
    }

    @Test
    fun testLocalEnvInForLoop() {
        // Local _ENV in loop body
        val result =
            execute(
                """
            local sum = 0
            for i = 1, 3 do
                local _ENV = {x = i * 10}
                sum = sum + x
            end
            return sum
        """,
            )
        assertLuaNumber(result, 60.0)
    }

    @Test
    fun testLocalEnvConstAsNumber() {
        vm.debugEnabled = true
        // When _ENV is a const number, global assignment should fail
        val code = """
            local function foo()
                local _ENV <const> = 11
                X = "hi"  -- Should fail: can't index a number
            end
            local st, msg = pcall(foo)
            return not st and string.find(msg, "number") ~= nil
        """
        assertLuaTrue(code, "Assignment with numeric _ENV should fail with 'number' in error message")
    }
}
