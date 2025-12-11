package ai.tenum.lua.compat.advanced.metatables

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests comparison metamethods: __eq, __lt, __le
 */
class ComparisonMetamethodsTest : LuaCompatTestBase() {
    @Test
    fun testEqMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 42}
                local t2 = {value = 42}
                local eqfunc = function(a, b)
                    return a.value == b.value
                end
                local mt = {__eq = eqfunc}
                setmetatable(t1, mt)
                setmetatable(t2, mt)
                return t1 == t2
                """.trimIndent()
            assertLuaBoolean(code, true)
        }

    @Test
    fun testLtMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 10}
                local t2 = {value = 20}
                local mt = {
                    __lt = function(a, b)
                        return a.value < b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 < t2
                """.trimIndent()
            assertLuaBoolean(code, true)
        }

    @Test
    fun testLeMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 10}
                local t2 = {value = 10}
                local mt = {
                    __le = function(a, b)
                        return a.value <= b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 <= t2
                """.trimIndent()
            assertLuaBoolean(code, true)
        }
}
