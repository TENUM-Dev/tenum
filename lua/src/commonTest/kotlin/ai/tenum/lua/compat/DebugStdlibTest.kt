package ai.tenum.lua.compat

import kotlin.test.Test

class DebugStdlibTest : LuaCompatTestBase() {
    @Test
    fun testSetmetatableExists() {
        val code =
            """
            return type(setmetatable)
            """.trimIndent()

        val result = execute(code)
        assertLuaString(result, "function")
    }

    @Test
    fun testSetmetatableWorks() {
        val code =
            """
            local t = {}
            local mt = {__index = {x = 42}}
            setmetatable(t, mt)
            return t.x
            """.trimIndent()

        val result = execute(code)
        assertLuaNumber(result, 42.0)
    }
}
