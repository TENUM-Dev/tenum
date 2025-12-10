@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test
import kotlin.test.assertTrue

class DebugFunctionSyntaxTest : LuaCompatTestBase() {
    @Test
    fun testDotSyntaxLocal() =
        runTest {
            val result =
                execute(
                    """
            local lib = {}
            function lib.test()
                return 42
            end
            return lib.test()
        """,
                )
            assertLuaNumber(result, 42.0)
        }

    @Test
    fun testColonSyntaxLocal() =
        runTest {
            val result =
                execute(
                    """
            local lib = {value = 10}
            function lib:test()
                return self.value
            end
            return lib:test()
        """,
                )
            assertLuaNumber(result, 10.0)
        }

    @Test
    fun testMixedSimple() =
        runTest {
            val result =
                execute(
                    """
            local lib = {}
            function lib.create()
                return {x = 5}
            end
            function lib:getValue()
                return self.x
            end
            
            local obj = lib.create()
            setmetatable(obj, {__index = lib})
            return obj:getValue()
        """,
                )
            assertLuaNumber(result, 5.0)
        }

    @Test
    fun testMixedLikeFailingTest() =
        runTest {
            val result =
                execute(
                    """
            local lib = {}
            
            function lib.create(value)
                local obj = {value = value}
                setmetatable(obj, {__index = lib})
                return obj
            end
            
            local x = lib.create(5)  -- Call the function, create object
            
            function lib:double()
                return 42
            end
            
            return lib.double
        """,
                )
            assertTrue(
                result !is ai.tenum.lua.runtime.LuaNil,
                "lib.double should exist after lib.create! Got: $result",
            )
        }

    @Test
    fun testFunctionWithNoArgs() =
        runTest {
            val code = """
            function test()
                return 1
            end
            return test()
        """
            assertLuaNumber(code, 1.0)
        }

    @Test
    fun testFunctionWithOneArg() =
        runTest {
            val code = """
            function test(a)
                return a
            end
            return test(42)
        """
            assertLuaNumber(code, 42.0)
        }

    @Test
    fun testFunctionWithMultipleArgs() =
        runTest {
            val code = """
            function test(a, b)
                return a + b
            end
            return test(10, 20)
        """
            assertLuaNumber(code, 30.0)
        }

    @Test
    fun testFunctionWithVarargs() =
        runTest {
            val code = """
            function test(...)
                local args = {...}
                return args[1] + args[2]
            end
            return test(5, 8)
        """
            assertLuaNumber(code, 13.0)
        }

    @Test
    fun testFunctionWithMixedArgsAndVarargs() =
        runTest {
            val code = """
            function test(a, ...)
                local args = {...}
                return a + args[1]
            end
            return test(1, 2, 3)
        """
            assertLuaNumber(code, 3.0)
        }
}
