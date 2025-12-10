package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaNumber
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LuaVmTest {
    @Test
    fun testExecuteNumber() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return 42")
            assertEquals(LuaNumber.of(42.0), result)
        }

    @Test
    fun testExecuteNegativeNumber() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return -10")
            assertEquals(LuaNumber.of(-10.0), result)
        }

    @Test
    fun testExecuteAddition() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return 10 + 5")
            assertEquals(LuaNumber.of(15.0), result)
        }

    @Test
    fun testExecuteSubtraction() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return 10 - 3")
            assertEquals(LuaNumber.of(7.0), result)
        }

    @Test
    fun testExecuteMultiplication() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return 4 * 5")
            assertEquals(LuaNumber.of(20.0), result)
        }

    @Test
    fun testExecuteDivision() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return 20 / 4")
            assertEquals(LuaNumber.of(5.0), result)
        }

    @Test
    fun testExecuteModulo() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return 10 % 3")
            assertEquals(LuaNumber.of(1.0), result)
        }

    @Test
    fun testExecutePower() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return 2 ^ 3")
            assertEquals(LuaNumber.of(8.0), result)
        }

    @Test
    fun testExecuteUnaryMinus() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return -(5 + 3)")
            assertEquals(LuaNumber.of(-8.0), result)
        }

    @Test
    fun testExecuteStringLength() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return #\"hello\"")
            assertEquals(LuaNumber.of(5.0), result)
        }

    @Test
    fun testExecuteLocalVariable() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local x = 10
                    return x
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(10.0), result)
        }

    @Test
    fun testExecuteLocalVariableAssignment() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local x = 5
                    x = 10
                    return x
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(10.0), result)
        }

    @Test
    fun testExecuteGlobalVariable() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    x = 42
                    return x
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(42.0), result)
        }

    @Test
    fun testExecuteMultipleLocals() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local a = 10
                    local b = 20
                    return a + b
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(30.0), result)
        }

    @Test
    fun testExecuteComplexExpression() =
        runTest {
            val vm = LuaVmImpl()
            val result = vm.execute("return (1 + 2) * 3 - 4")
            assertEquals(LuaNumber.of(5.0), result)
        }

    @Test
    fun testExecuteIfStatement() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local x = 10
                    if x > 5 then
                        return 1
                    else
                        return 2
                    end
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(1.0), result)
        }

    @Test
    fun testExecuteIfElseStatement() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local x = 3
                    if x > 5 then
                        return 1
                    else
                        return 2
                    end
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(2.0), result)
        }

    @Test
    fun testExecuteWhileLoop() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local sum = 0
                    local i = 1
                    while i <= 5 do
                        sum = sum + i
                        i = i + 1
                    end
                    return sum
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(15.0), result)
        }

    @Test
    fun testExecuteRepeatLoop() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local i = 1
                    repeat
                        i = i + 1
                    until i > 5
                    return i
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(6.0), result)
        }

    @Test
    fun testExecuteForLoop() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local sum = 0
                    for i = 1, 5 do
                        sum = sum + i
                    end
                    return sum
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(15.0), result)
        }

    @Test
    fun testExecuteForLoopWithStep() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local sum = 0
                    for i = 2, 10, 2 do
                        sum = sum + i
                    end
                    return sum
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(30.0), result)
        }

    @Test
    fun testExecuteTableIndexAccess() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local t = {10, 20, 30}
                    return t[1]
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(10.0), result)
        }

    @Test
    fun testExecuteTableFieldAccess() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local t = {x = 42}
                    return t.x
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(42.0), result)
        }

    @Test
    fun testExecuteTableAssignment() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local t = {}
                    t.x = 99
                    return t.x
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(99.0), result)
        }

    @Test
    fun testExecuteDoBlock() =
        runTest {
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
                    local x = 10
                    do
                        local y = 20
                        x = x + y
                    end
                    return x
                    """.trimIndent(),
                )
            assertEquals(LuaNumber.of(30.0), result)
        }
}
