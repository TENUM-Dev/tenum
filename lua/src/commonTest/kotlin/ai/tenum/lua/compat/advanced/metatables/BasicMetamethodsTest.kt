package ai.tenum.lua.compat.advanced.metatables

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests basic metatable operations: setmetatable, getmetatable
 */
class BasicMetamethodsTest : LuaCompatTestBase() {
    @Test
    fun testSetmetatable() =
        runTest {
            val code =
                """
                local t = {}
                local mt = {x = 10}
                setmetatable(t, mt)
                local result = getmetatable(t)
                return result.x
                """.trimIndent()
            assertLuaNumber(code, 10.0)
        }

    @Test
    fun testGetmetatable() =
        runTest {
            val code =
                """
                local t = {a = 1}
                local mt = {b = 2}
                setmetatable(t, mt)
                return getmetatable(t) == mt
                """.trimIndent()
            assertLuaBoolean(code, true)
        }
}
