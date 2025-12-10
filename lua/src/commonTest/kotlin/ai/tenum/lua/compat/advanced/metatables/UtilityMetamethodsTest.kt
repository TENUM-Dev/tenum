package ai.tenum.lua.compat.advanced.metatables

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests utility metamethods: __concat, __len, __call, __tostring, __name
 */
class UtilityMetamethodsTest : LuaCompatTestBase() {
    @Test
    fun testConcatMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = "Hello"}
                local t2 = {value = "World"}
                local mt = {
                    __concat = function(a, b)
                        return a.value .. " " .. b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 .. t2
                """.trimIndent()
            assertLuaString(code, "Hello World")
        }

    @Test
    fun testLenMetamethod() =
        runTest {
            val code =
                """
                local t = {}
                local mt = {
                    __len = function(tbl)
                        return 42
                    end
                }
                setmetatable(t, mt)
                return #t
                """.trimIndent()
            assertLuaNumber(code, 42.0)
        }

    @Test
    fun testCallMetamethod() =
        runTest {
            val code =
                """
                local t = {value = 10}
                local mt = {
                    __call = function(tbl, x)
                        return tbl.value + x
                    end
                }
                setmetatable(t, mt)
                return t(5)
                """.trimIndent()
            assertLuaNumber(code, 15.0)
        }

    @Test
    fun testTostringMetamethod() =
        runTest {
            val code =
                """
                local t = {name = "Alice"}
                local mt = {
                    __tostring = function(tbl)
                        return "Person: " .. tbl.name
                    end
                }
                setmetatable(t, mt)
                return tostring(t)
                """.trimIndent()
            assertLuaString(code, "Person: Alice")
        }

    @Test
    fun testTypeAndTostringUseNameMetamethod() =
        runTest {
            val codeType =
                """
                local o = setmetatable({}, {__name = "MyType"})
                return type(o)
                """.trimIndent()
            assertLuaString(codeType, "MyType")
            vm.debugEnabled = true
            val codeTostring =
                """
                local o = setmetatable({}, {__name = "MyType"})
                local s = tostring(o)
                print(s)
                print(string.len("MyType: "))
                print(string.sub(s, 1, string.len("MyType: ")))
                return string.sub(s, 1, string.len("MyType: ")) == "MyType: "
                """.trimIndent()
            assertLuaBoolean(codeTostring, true)
        }
}
