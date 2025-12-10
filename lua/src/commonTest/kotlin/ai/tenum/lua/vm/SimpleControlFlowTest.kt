package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaNumber
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleControlFlowTest {
    @Test
    fun testSimpleIfTrue() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    if true then
                        return 99
                    end
                    return 0
                    """.trimIndent(),
                )

            assertTrue(result is LuaNumber, "Result should be a number but was ${result::class.simpleName}")
            assertEquals(LuaNumber.of(99), result, "Should return 99 from true branch")
        }

    @Test
    fun testSimpleIfFalse() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    if false then
                        return 99
                    end
                    return 77
                    """.trimIndent(),
                )

            assertTrue(result is LuaNumber, "Result should be a number but was ${result::class.simpleName}")
            assertEquals(LuaNumber.of(77), result, "Should return 77, skipping false branch")
        }

    @Test
    fun testIfWithComparison() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local x = 10
                    if x > 5 then
                        return 1
                    end
                    return 2
                    """.trimIndent(),
                )

            val resultValue = if (result is LuaNumber) result.value else null
            assertTrue(result is LuaNumber, "Result should be a number but was ${result::class.simpleName}: $result, value=$resultValue")
            assertEquals(LuaNumber.of(1.0), result, "10 > 5 should be true, returning 1, but got ${result.value}")
        }

    @Test
    fun testIfWithLocalVariableInThenBlock() =
        runTest {
            // Regression test: if statement where then-block allocates temp registers
            // This used to cause "Trying to free register X which is not at the top of the stack"
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local x = 5
                    if x > 3 then
                        local y = x + 2
                        return y
                    end
                    return 0
                    """.trimIndent(),
                )

            assertTrue(result is LuaNumber, "Result should be a number")
            assertEquals(7.0, result.toDouble(), "Should return 7 (5 + 2)")
        }

    @Test
    fun testIfWithFunctionCallInThenBlock() =
        runTest {
            // Regression test: function calls allocate temporary registers
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local function add(a, b)
                        return a + b
                    end
                    
                    local x = 5
                    if x > 3 then
                        local y = add(x, 2)
                        return y
                    end
                    return 0
                    """.trimIndent(),
                )

            assertTrue(result is LuaNumber, "Result should be a number")
            assertEquals(7.0, result.toDouble(), "Should return 7 from add(5, 2)")
        }

    @Test
    fun testWhileWithComplexBody() =
        runTest {
            // Regression test: while loop body allocates temporary registers
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local sum = 0
                    local i = 1
                    while i <= 3 do
                        local temp = i * 2
                        sum = sum + temp
                        i = i + 1
                    end
                    return sum
                    """.trimIndent(),
                )

            assertTrue(result is LuaNumber, "Result should be a number")
            assertEquals(12.0, result.toDouble(), "Should return 12 (2+4+6)")
        }

    @Test
    fun testForWithComplexBody() =
        runTest {
            // Regression test: for loop body allocates temporary registers
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local sum = 0
                    for i = 1, 3 do
                        local temp = i * 2
                        sum = sum + temp
                    end
                    return sum
                    """.trimIndent(),
                )

            assertTrue(result is LuaNumber, "Result should be a number")
            assertEquals(12.0, result.toDouble(), "Should return 12 (2+4+6)")
        }
}
