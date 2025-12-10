package ai.tenum.lua.compat.advanced.metatables

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests iteration metamethods: __pairs, __ipairs
 */
class IterationMetamethodsTest : LuaCompatTestBase() {
    @Test
    fun testPairsMetamethodInvocation() =
        runTest {
            val code =
                """
                local t = {10,20}
                local called = false
                setmetatable(t, {__pairs = function(tbl)
                    called = true
                    return function() return nil end, tbl, nil
                end})
                for k,v in pairs(t) do end
                return called
                """.trimIndent()
            assertLuaBoolean(code, true)
        }

    @Test
    fun testIpairsMetamethodInvocation() =
        runTest {
            val code =
                """
                local t = {1,2,3}
                local called = false
                setmetatable(t, {__ipairs = function(tbl)
                    called = true
                    return function() return nil end, tbl, 0
                end})
                for i,v in ipairs(t) do end
                return called
                """.trimIndent()
            assertLuaBoolean(code, true)
        }

    @Test
    fun testPairsErrorWhenMetamethodIsNotCallable() =
        runTest {
            val code =
                """
                local t = {}
                setmetatable(t, {__pairs = 42})
                for k,v in pairs(t) do end
                """.trimIndent()
            assertThrowsError(code, "attempt to call a number value")
        }

    @Test
    fun testIpairsErrorWhenMetamethodIsNotCallable() =
        runTest {
            val code =
                """
                local t = {}
                setmetatable(t, {__ipairs = "string"})
                for k,v in ipairs(t) do end
                """.trimIndent()
            assertThrowsError(code, "attempt to call a string value")
        }
}
