package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * TDD Test: Negative integer literals in for loops should preserve integer type
 *
 * Bug: `for j=-3,3 do` was producing float loop variables because -3 was being
 * evaluated at runtime via UNM opcode, not constant-folded at compile time.
 *
 * Expected behavior: -3 should be constant-folded to LuaLong(-3) at compile time,
 * so the loop uses integer arithmetic throughout.
 */
class NumericForLoopNegativeBoundsTest : LuaCompatTestBase() {
    @Test
    fun testForLoopWithNegativeStart_LoopVariableIsInteger() =
        runTest {
            val result =
                """
                for j=-3,3 do
                    if math.type(j) ~= 'integer' then
                        return false
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(result, "for j=-3,3: loop variable should be integer type")
        }

    @Test
    fun testForLoopWithNegativeStartAndEnd_LoopVariableIsInteger() =
        runTest {
            val result =
                """
                for j=-5,-1 do
                    if math.type(j) ~= 'integer' then
                        return false
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(result, "for j=-5,-1: loop variable should be integer type")
        }

    @Test
    fun testForLoopWithNegativeStep_LoopVariableIsInteger() =
        runTest {
            val result =
                """
                for j=3,-3,-1 do
                    if math.type(j) ~= 'integer' then
                        return false
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(result, "for j=3,-3,-1: loop variable should be integer type")
        }

    @Test
    fun testArithmeticWithNegativeLoopVariable() =
        runTest {
            val code =
                """
                local p = 140737488355328
                for j=-3,3 do
                    local diff = p - j
                    if math.type(diff) ~= 'integer' then
                        return false, 'Expected integer for p-j at j='..j..' but got '..math.type(diff)
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(code, "Subtraction of integer loop variable from large integer should stay integer")
        }
}
