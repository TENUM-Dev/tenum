package ai.tenum.lua.compat.advanced.metatables

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests arithmetic metamethods: __add, __sub, __mul, __div, __mod, __pow, __unm, __idiv
 */
class ArithmeticMetamethodsTest : LuaCompatTestBase() {
    @Test
    fun testAddMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 10}
                local t2 = {value = 20}
                local mt = {
                    __add = function(a, b)
                        return a.value + b.value
                    end
                }
                setmetatable(t1, mt)
                setmetatable(t2, mt)
                return t1 + t2
                """.trimIndent()
            assertLuaNumber(code, 30.0)
        }

    @Test
    fun testSubMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 50}
                local t2 = {value = 20}
                local mt = {
                    __sub = function(a, b)
                        return a.value - b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 - t2
                """.trimIndent()
            assertLuaNumber(code, 30.0)
        }

    @Test
    fun testMulMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 5}
                local t2 = {value = 6}
                local mt = {
                    __mul = function(a, b)
                        return a.value * b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 * t2
                """.trimIndent()
            assertLuaNumber(code, 30.0)
        }

    @Test
    fun testDivMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 60}
                local t2 = {value = 2}
                local mt = {
                    __div = function(a, b)
                        return a.value / b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 / t2
                """.trimIndent()
            assertLuaNumber(code, 30.0)
        }

    @Test
    fun testModMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 33}
                local t2 = {value = 10}
                local mt = {
                    __mod = function(a, b)
                        return a.value % b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 % t2
                """.trimIndent()
            assertLuaNumber(code, 3.0)
        }

    @Test
    fun testPowMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 2}
                local t2 = {value = 5}
                local mt = {
                    __pow = function(a, b)
                        return a.value ^ b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 ^ t2
                """.trimIndent()
            assertLuaNumber(code, 32.0)
        }

    @Test
    fun testUnmMetamethod() =
        runTest {
            val code =
                """
                local t = {value = 42}
                local mt = {
                    __unm = function(a)
                        return -a.value
                    end
                }
                setmetatable(t, mt)
                return -t
                """.trimIndent()
            assertLuaNumber(code, -42.0)
        }

    @Test
    fun testIdivMetamethod() =
        runTest {
            val code =
                """
                local t1 = {value = 17}
                local t2 = {value = 5}
                local mt = {
                    __idiv = function(a, b)
                        return a.value // b.value
                    end
                }
                setmetatable(t1, mt)
                return t1 // t2
                """.trimIndent()
            assertLuaNumber(code, 3.0)
        }
}
