package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvAndGlobalScopeCompatTest : LuaCompatTestBase() {
    @Test
    fun testSetmetatableGlobalAvailable() {
        val result =
            execute(
                """
            local t = {}
            setmetatable(t, { __index = function() return 42 end })
            return getmetatable(t).__index(nil)
        """,
            )
        assertEquals(42.0, (result as ai.tenum.lua.runtime.LuaNumber).toDouble())
    }

    @Test
    fun testLocalEnvShadowsGlobal() {
        val result =
            execute(
                """
            local _ENV = { setmetatable = function() return 'shadowed' end }
            return setmetatable({}, {})
        """,
            )
        assertEquals("shadowed", result.toString())
    }

    @Test
    fun testGlobalEnvFallback() {
        val result =
            execute(
                """
            local _ENV = {}
            if setmetatable == nil then return 'not found' else return 'found' end
        """,
            )
        assertEquals("not found", result.toString())
    }

    @Test
    fun testGlobalAssignmentAffectsEnv() {
        val result =
            execute(
                """
            local _ENV = { x = 1 }
            x = 99
            return _ENV.x
        """,
            )
        assertEquals(99.0, (result as ai.tenum.lua.runtime.LuaNumber).toDouble())
    }

    @Test
    fun testNestedEnvScopes() {
        val result =
            execute(
                """
            local _ENV = { x = 1 }
            do
                local _ENV = { x = 2 }
            end
            return x
        """,
            )
        assertEquals(1.0, (result as ai.tenum.lua.runtime.LuaNumber).toDouble())
    }
}
