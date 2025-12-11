package ai.tenum.lua.vm.metamethods

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test suite for MetamethodResolver.
 * Covers metamethod resolution and invocation for binary and unary operations using Lua code.
 */
class MetamethodResolverTest : LuaCompatTestBase() {
    /**
     * Test binaryOpWithMeta when metamethod is on the left operand.
     */
    @Test
    fun testBinaryOpWithMetaOnLeft() =
        runTest {
            assertLuaNumber(
                """
                local t = {}
                setmetatable(t, {
                    __add = function(a, b)
                        return 42
                    end
                })
                return t + 10
                """,
                42.0,
            )
        }

    /**
     * Test binaryOpWithMeta when metamethod is on the right operand.
     */
    @Test
    fun testBinaryOpWithMetaOnRight() =
        runTest {
            assertLuaNumber(
                """
                local t = {}
                setmetatable(t, {
                    __mul = function(a, b)
                        return 99
                    end
                })
                return 5 * t
                """,
                99.0,
            )
        }

    /**
     * Test binaryOpWithMeta when metamethod is on both operands (left takes precedence).
     */
    @Test
    fun testBinaryOpWithMetaOnBoth() =
        runTest {
            assertLuaNumber(
                """
                local t1 = {}
                setmetatable(t1, {
                    __sub = function(a, b)
                        return 100
                    end
                })
                local t2 = {}
                setmetatable(t2, {
                    __sub = function(a, b)
                        return 200
                    end
                })
                return t1 - t2
                """,
                100.0,
            )
        }

    /**
     * Test binaryOpWithMeta when no metamethod exists - should use fallback.
     */
    @Test
    fun testBinaryOpWithMetaFallback() =
        runTest {
            assertLuaNumber("return 15 / 3", 5.0)
        }

    /**
     * Test binaryOpWithMeta with various metamethods returning values.
     */
    @Test
    fun testBinaryOpVariousMetamethods() =
        runTest {
            assertLuaNumber(
                """
                local results = {}
                
                -- Test __sub
                local t1 = {}
                setmetatable(t1, { __sub = function(a, b) return 7 end })
                results[1] = t1 - 3
                
                -- Test __mul
                local t2 = {}
                setmetatable(t2, { __mul = function(a, b) return 8 end })
                results[2] = t2 * 2
                
                -- Test __div
                local t3 = {}
                setmetatable(t3, { __div = function(a, b) return 9 end })
                results[3] = t3 / 4
                
                return results[1] + results[2] + results[3]
                """,
                24.0, // 7 + 8 + 9
            )
        }

    /**
     * Test unaryOpWithMeta when metamethod exists.
     */
    @Test
    fun testUnaryOpWithMetaExists() =
        runTest {
            assertLuaNumber(
                """
                local t = {}
                setmetatable(t, {
                    __unm = function(a)
                        return -999
                    end
                })
                return -t
                """,
                -999.0,
            )
        }

    /**
     * Test unaryOpWithMeta when no metamethod exists - should use fallback.
     */
    @Test
    fun testUnaryOpWithMetaFallback() =
        runTest {
            assertLuaNumber("return -42", -42.0)
        }

    /**
     * Test unary operations with various metamethods.
     */
    @Test
    fun testUnaryOpVariousMetamethods() =
        runTest {
            assertLuaNumber(
                """
                local results = {}
                
                -- Test __unm
                local t1 = {}
                setmetatable(t1, { __unm = function(a) return 100 end })
                results[1] = -t1
                
                -- Test __bnot (bitwise NOT)
                local t2 = {}
                setmetatable(t2, { __bnot = function(a) return 200 end })
                results[2] = ~t2
                
                return results[1] + results[2]
                """,
                300.0, // 100 + 200
            )
        }
}
