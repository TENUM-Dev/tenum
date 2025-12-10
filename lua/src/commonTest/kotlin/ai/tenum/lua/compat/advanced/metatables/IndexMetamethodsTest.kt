package ai.tenum.lua.compat.advanced.metatables

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests __index and __newindex metamethods
 */
class IndexMetamethodsTest : LuaCompatTestBase() {
    @Test
    fun testIndexMetamethod() =
        runTest {
            val code =
                """
                local t = {}
                local mt = {__index = {x = 42}}
                setmetatable(t, mt)
                return t.x
                """.trimIndent()
            assertLuaNumber(code, 42.0)
        }

    @Test
    fun testIndexMetamethodFunction() =
        runTest {
            val code =
                """
                local t = {}
                local mt = {
                    __index = function(tbl, key)
                        if key == "answer" then
                            return 42
                        end
                        return nil
                    end
                }
                setmetatable(t, mt)
                return t.answer
                """.trimIndent()
            assertLuaNumber(code, 42.0)
        }

    @Test
    fun testNewindexMetamethod() =
        runTest {
            val code =
                """
                local t = {}
                local store = {}
                local mt = {__newindex = store}
                setmetatable(t, mt)
                t.x = 10
                return store.x
                """.trimIndent()
            assertLuaNumber(code, 10.0)
        }

    @Test
    fun testNewindexMetamethodFunction() =
        runTest {
            val code =
                """
                local t = {}
                local count = 0
                local mt = {
                    __newindex = function(tbl, key, value)
                        count = count + 1
                    end
                }
                setmetatable(t, mt)
                t.a = 1
                t.b = 2
                return count
                """.trimIndent()
            assertLuaNumber(code, 2.0)
        }
}
