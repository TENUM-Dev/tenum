package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * PHASE 3.1: Functions - Basics
 *
 * Tests function declarations, calls, and basic features.
 * Based on: calls.lua
 *
 * Coverage:
 * - Function declarations
 * - Function calls
 * - Parameters and arguments
 * - Return values (single and multiple)
 * - Variadic functions (...)
 * - Named vs anonymous functions
 * - First-class functions
 */
class FunctionsCompatTest : LuaCompatTestBase() {
    @Test
    fun testFunctionDeclaration() =
        runTest {
            val code =
                """
                function double(x)
                    return x * 2
                end
                return double(5)
                """.trimIndent()
            assertLuaNumber(code, 10.0)
        }

    @Test
    fun testFunctionCall() =
        runTest {
            val code =
                """
                function add(a, b)
                    return a + b
                end
                return add(3, 4)
                """.trimIndent()
            assertLuaNumber(code, 7.0)
        }

    @Test
    fun testFunctionWithParameters() =
        runTest {
            val code =
                """
                function multiply(a, b)
                    local result = a * b
                    return result
                end
                return multiply(6, 7)
                """.trimIndent()
            assertLuaNumber(code, 42.0)
        }

    @Test
    fun testFunctionReturnSingle() =
        runTest {
            val code =
                """
                function getValue()
                    return 123
                end
                return getValue()
                """.trimIndent()
            assertLuaNumber(code, 123.0)
        }

    @Test
    fun testFunctionReturnMultiple() =
        runTest {
            val code =
                """
                function getTwoValues()
                    return 10, 20
                end
                local a, b = getTwoValues()
                return a + b
                """.trimIndent()
            assertLuaNumber(code, 30.0)
        }

    @Test
    fun testFunctionReturnNone() =
        runTest {
            val code =
                """
                function doNothing()
                end
                local x = doNothing()
                if x == nil then
                    return 99
                else
                    return 0
                end
                """.trimIndent()
            assertLuaNumber(code, 99.0)
        }

    @Test
    fun testTooFewArguments() =
        runTest {
            val code =
                """
                function add(a, b)
                    if b == nil then
                        return a
                    else
                        return a + b
                    end
                end
                return add(5)
                """.trimIndent()
            assertLuaNumber(code, 5.0)
        }

    @Test
    fun testTooManyArguments() =
        runTest {
            val code =
                """
                function firstOnly(a)
                    return a
                end
                return firstOnly(10, 20, 30)
                """.trimIndent()
            assertLuaNumber(code, 10.0)
        }

    @Test
    fun testVariadicFunction() =
        runTest {
            val code =
                """
                function sum(...)
                    local total = 0
                    local args = {...}
                    for i = 1, #args do
                        total = total + args[i]
                    end
                    return total
                end
                return sum(1, 2, 3, 4, 5)
                """.trimIndent()
            assertLuaNumber(code, 15.0)
        }

    @Test
    fun testVariadicAccess() =
        runTest {
            // TODO: Test accessing ... directly
            skipTest("Direct vararg access not fully implemented")
        }

    @Test
    fun testVariadicWithNamedParams() =
        runTest {
            val code =
                """
                function sumAfterFirst(first, ...)
                    local total = 0
                    local args = {...}
                    for i = 1, #args do
                        total = total + args[i]
                    end
                    return first + total
                end
                return sumAfterFirst(10, 1, 2, 3)
                """.trimIndent()
            assertLuaNumber(code, 16.0)
        }

    @Test
    fun testAnonymousFunction() =
        runTest {
            val code =
                """
                local f = function(x)
                    return x * 3
                end
                return f(4)
                """.trimIndent()
            assertLuaNumber(code, 12.0)
        }

    @Test
    fun testFunctionAsValue() =
        runTest {
            val code =
                """
                function apply(f, x)
                    return f(x)
                end
                function square(n)
                    return n * n
                end
                return apply(square, 5)
                """.trimIndent()
            assertLuaNumber(code, 25.0)
        }

    @Test
    fun testFunctionInTable() =
        runTest {
            val code =
                """
                local t = {}
                t.f = function(x)
                    return x + 10
                end
                return t.f(5)
                """.trimIndent()
            assertLuaNumber(code, 15.0)
        }

    @Test
    fun testMethodSyntaxSugar() =
        runTest {
            // TODO: Test function t:method() end
            skipTest("Method syntax sugar not fully implemented")
        }

    @Test
    fun testRecursiveFunction() =
        runTest {
            val code =
                """
                function factorial(n)
                    if n <= 1 then
                        return 1
                    else
                        return n * factorial(n - 1)
                    end
                end
                return factorial(5)
                """.trimIndent()
            assertLuaNumber(code, 120.0)
        }

    @Test
    fun testTailCall() =
        runTest {
            // TODO: Test tail call optimization
            // For now, just test that tail calls work (without optimization)
            val code =
                """
                function helper(n, acc)
                    if n <= 0 then
                        return acc
                    else
                        return helper(n - 1, acc + n)
                    end
                end
                function sum(n)
                    return helper(n, 0)
                end
                return sum(10)
                """.trimIndent()
            assertLuaNumber(code, 55.0)
        }
}
