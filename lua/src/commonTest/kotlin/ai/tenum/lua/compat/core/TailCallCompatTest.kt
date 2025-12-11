package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for deep tail-call recursion behavior (reference outputs from lua54)
 */
class TailCallCompatTest : LuaCompatTestBase() {
    @Test
    fun testDeepTailRecursionCountdown() =
        runTest {
            vm.debugEnabled = false
            val code =
                """
                local function tail(n)
                    if n == 0 then return 0 end
                    return tail(n - 1)
                end
                return tail(10000)
                """.trimIndent()
            assertLuaNumber(code, 0.0)
        }

    @Test
    fun testDeepTailRecursionAccumulator() =
        runTest {
            vm.debugEnabled = false
            val code =
                """
                local function helper(n, acc)
                    if n == 0 then return acc end
                    return helper(n - 1, acc + n)
                end
                local function sum(n)
                    return helper(n, 0)
                end
                return sum(10000)
                """.trimIndent()
            // Using n=10000 above, expected sum = 10000 * 10001 / 2 = 50005000
            assertLuaNumber(code, 50005000.0)
        }
}
