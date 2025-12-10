package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Phase 2.2: Loops Compatibility Tests
 *
 * Tests Lua 5.4.8 loop features:
 * - while loops
 * - repeat-until loops
 * - Numeric for loops (for i=start,end,step)
 * - Generic for loops (for k,v in pairs())
 * - break statement
 * - Nested loops
 * - Loop scope
 */
class LoopsCompatTest : LuaCompatTestBase() {
    // ============================================
    // While loops
    // ============================================

    @Test
    fun testBasicWhileLoop() {
        val code =
            """
            local sum = 0
            local i = 1
            while i <= 5 do
                sum = sum + i
                i = i + 1
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
    }

    @Test
    fun testWhileLoopZeroIterations() {
        val code =
            """
            local sum = 0
            local i = 10
            while i < 5 do
                sum = sum + i
                i = i + 1
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 0.0)
    }

    @Test
    fun testWhileWithComplexCondition() {
        val code =
            """
            local a = 1
            local b = 10
            local sum = 0
            while a < 5 and b > 5 do
                sum = sum + a
                a = a + 1
                b = b - 1
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 10.0) // 1+2+3+4 = 10
    }

    // ============================================
    // Repeat-until loops
    // ============================================

    @Test
    fun testBasicRepeatUntil() {
        val code =
            """
            local sum = 0
            local i = 1
            repeat
                sum = sum + i
                i = i + 1
            until i > 5
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
    }

    @Test
    fun testRepeatUntilOneIteration() {
        val code =
            """
            local x = 0
            repeat
                x = x + 1
            until true
            return x
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testRepeatUntilScope() {
        val code =
            """
            local sum = 0
            repeat
                local x = 10
                sum = sum + x
            until sum >= 10
            return sum
            """.trimIndent()
        assertLuaNumber(code, 10.0)
    }

    @Test
    fun testRepeatUntilLocalInCondition() {
        val code =
            """
            local result = 0
            repeat
                local x = 5
                result = result + 1
            until result >= x  -- x from loop body should be visible
            return result
            """.trimIndent()
        assertLuaNumber(code, 5.0)
    }

    // ============================================
    // Numeric for loops
    // ============================================

    @Test
    fun testBasicNumericFor() {
        val code =
            """
            local sum = 0
            for i = 1, 5 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
    }

    @Test
    fun testNumericForWithStep() {
        val code =
            """
            local sum = 0
            for i = 1, 10, 2 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 25.0) // 1+3+5+7+9 = 25
    }

    @Test
    fun testNumericForNegativeStep() {
        val code =
            """
            local sum = 0
            for i = 5, 1, -1 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 5+4+3+2+1 = 15
    }

    @Test
    fun testNumericForZeroIterations() {
        val code =
            """
            local sum = 0
            for i = 10, 1 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 0.0)
    }

    @Test
    fun testNumericForZeroIterationsNegativeStep() {
        val code =
            """
            local sum = 0
            for i = 1, 10, -1 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 0.0)
    }

    @Test
    fun testNumericForLoopVariable() {
        val code =
            """
            local last = 0
            for i = 1, 5 do
                last = i
            end
            return last
            """.trimIndent()
        assertLuaNumber(code, 5.0)
    }

    @Test
    fun testNumericForLoopVariableBounds() {
        // Test for loop with variable as upper bound
        val code =
            """
            local max = 5
            local sum = 0
            for i = 1, max do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
    }

    @Test
    fun testNumericForLoopAllVariableBounds() {
        // Test for loop with all bounds as variables
        val code =
            """
            local start = 1
            local stop = 5
            local step = 1
            local sum = 0
            for i = start, stop, step do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
    }

    @Test
    fun testNumericForStepTwo() {
        val code =
            """
            local sum = 0
            for i = 0, 10, 2 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 30.0) // 0+2+4+6+8+10 = 30
    }

    // ============================================
    // Break statement
    // ============================================

    @Test
    fun testBreakInWhileLoop() {
        val code =
            """
            local sum = 0
            local i = 1
            while i <= 10 do
                if i > 5 then
                    break
                end
                sum = sum + i
                i = i + 1
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
    }

    @Test
    fun testBreakInRepeatLoop() {
        val code =
            """
            local sum = 0
            local i = 1
            repeat
                if i > 5 then
                    break
                end
                sum = sum + i
                i = i + 1
            until false
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
    }

    @Test
    fun testBreakInNumericFor() {
        val code =
            """
            local sum = 0
            for i = 1, 10 do
                if i > 5 then
                    break
                end
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
    }

    @Test
    fun testBreakImmediately() {
        val code =
            """
            local x = 0
            for i = 1, 10 do
                break
                x = x + i
            end
            return x
            """.trimIndent()
        assertLuaNumber(code, 0.0)
    }

    // ============================================
    // Nested loops
    // ============================================

    @Test
    fun testNestedForLoops() {
        val code =
            """
            local sum = 0
            for i = 1, 3 do
                for j = 1, 3 do
                    sum = sum + (i * j)
                end
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 36.0) // (1*1+1*2+1*3) + (2*1+2*2+2*3) + (3*1+3*2+3*3) = 6+12+18 = 36
    }

    @Test
    fun testNestedWhileLoops() {
        val code =
            """
            local sum = 0
            local i = 1
            while i <= 3 do
                local j = 1
                while j <= 3 do
                    sum = sum + 1
                    j = j + 1
                end
                i = i + 1
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 9.0) // 3x3 = 9
    }

    @Test
    fun testNestedMixedLoops() {
        val code =
            """
            local sum = 0
            for i = 1, 3 do
                local j = 1
                while j <= 2 do
                    sum = sum + i + j
                    j = j + 1
                end
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 21.0) // (1+1+1+2) + (2+1+2+2) + (3+1+3+2) = 5+7+9 = 21
    }

    @Test
    fun testBreakInNestedLoop() {
        val code =
            """
            local sum = 0
            for i = 1, 5 do
                for j = 1, 5 do
                    if j > 3 then
                        break
                    end
                    sum = sum + 1
                end
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 15.0) // 5 outer iterations * 3 inner iterations = 15
    }

    // ============================================
    // Loop variable scope
    // ============================================

    @Test
    fun testForLoopVariableScope() {
        vm.debugEnabled = true
        val code =
            """
            local i = 100
            for i = 1, 5 do
                -- Inner i shadows outer i
            end
            return i  -- Outer i should still be 100
            """.trimIndent()
        assertLuaNumber(code, 100.0)
    }

    @Test
    fun testForLoopLocalScope() {
        val code =
            """
            local sum = 0
            for i = 1, 3 do
                local x = i * 10
                sum = sum + x
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 60.0) // 10+20+30 = 60
    }

    @Test
    fun testWhileLoopLocalScope() {
        val code =
            """
            local sum = 0
            local i = 1
            while i <= 3 do
                local x = i * 10
                sum = sum + x
                i = i + 1
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 60.0) // 10+20+30 = 60
    }

    // ============================================
    // Edge cases
    // ============================================

    @Test
    fun testForLoopSingleIteration() {
        val code =
            """
            local sum = 0
            for i = 5, 5 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 5.0)
    }

    @Test
    fun testForLoopLargeStep() {
        val code =
            """
            local sum = 0
            for i = 1, 10, 10 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 1.0) // Only one iteration
    }

    @Test
    fun testWhileLoopEmptyBody() {
        val code =
            """
            local i = 1
            while i > 10 do
            end
            return i
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testRepeatEmptyBody() {
        val code =
            """
            local i = 0
            repeat
                i = i + 1
            until i >= 1
            return i
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testMultipleStatementsInLoop() {
        val code =
            """
            local sum = 0
            local product = 1
            for i = 1, 3 do
                sum = sum + i
                product = product * i
            end
            return sum + product
            """.trimIndent()
        assertLuaNumber(code, 12.0) // sum=6, product=6, total=12
    }

    @Test
    fun testForLoopAfterFor() {
        val code =
            """
            local sum = 0
            for i = 1, 3 do
                sum = sum + i
            end
            for i = 1, 3 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 12.0) // 6+6 = 12
    }

    @Test
    fun testForLoopFloatBounds() {
        val code =
            """
            local sum = 0
            for i = 1.5, 3.5 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, 7.5) // 1.5+2.5+3.5 = 7.5
    }

    @Test
    fun testForLoopNegativeRange() {
        val code =
            """
            local sum = 0
            for i = -3, -1 do
                sum = sum + i
            end
            return sum
            """.trimIndent()
        assertLuaNumber(code, -6.0) // -3 + -2 + -1 = -6
    }
}
