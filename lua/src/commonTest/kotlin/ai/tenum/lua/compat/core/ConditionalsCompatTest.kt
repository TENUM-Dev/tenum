package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Phase 2.1: Conditional Statements Compatibility Tests
 *
 * Tests Lua 5.4.8 conditional statement features:
 * - if-then-end
 * - if-then-else-end
 * - if-then-elseif-else-end
 * - Nested conditionals
 * - Truthiness rules (nil and false are falsy, everything else is truthy)
 */
class ConditionalsCompatTest : LuaCompatTestBase() {
    // ============================================
    // Basic if-then-end
    // ============================================

    @Test
    fun testBasicIfTrue() {
        val code =
            """
            local x = 0
            if true then
                x = 1
            end
            return x
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testBasicIfFalse() {
        val code =
            """
            local x = 0
            if false then
                x = 1
            end
            return x
            """.trimIndent()
        assertLuaNumber(code, 0.0)
    }

    @Test
    fun testIfWithComparison() {
        val code =
            """
            local x = 5
            if x > 3 then
                return 10
            end
            return 0
            """.trimIndent()
        assertLuaNumber(code, 10.0)
    }

    @Test
    fun testIfWithEquality() {
        val code =
            """
            local x = 5
            if x == 5 then
                return 100
            end
            return 0
            """.trimIndent()
        assertLuaNumber(code, 100.0)
    }

    // ============================================
    // if-then-else-end
    // ============================================

    @Test
    fun testIfElseTrue() {
        val code =
            """
            local x = 5
            if x > 3 then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testIfElseFalse() {
        val code =
            """
            local x = 2
            if x > 3 then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 2.0)
    }

    @Test
    fun testIfElseWithLocalVariables() {
        val code =
            """
            local result = 0
            if 10 > 5 then
                local a = 1
                result = a
            else
                local b = 2
                result = b
            end
            return result
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    // ============================================
    // if-then-elseif-else-end
    // ============================================

    @Test
    fun testElseIfFirstTrue() {
        val code =
            """
            local x = 1
            if x == 1 then
                return 10
            elseif x == 2 then
                return 20
            else
                return 30
            end
            """.trimIndent()
        assertLuaNumber(code, 10.0)
    }

    @Test
    fun testElseIfSecondTrue() {
        val code =
            """
            local x = 2
            if x == 1 then
                return 10
            elseif x == 2 then
                return 20
            else
                return 30
            end
            """.trimIndent()
        assertLuaNumber(code, 20.0)
    }

    @Test
    fun testElseIfElse() {
        val code =
            """
            local x = 3
            if x == 1 then
                return 10
            elseif x == 2 then
                return 20
            else
                return 30
            end
            """.trimIndent()
        assertLuaNumber(code, 30.0)
    }

    @Test
    fun testMultipleElseIf() {
        val code =
            """
            local x = 3
            if x == 1 then
                return 1
            elseif x == 2 then
                return 2
            elseif x == 3 then
                return 3
            elseif x == 4 then
                return 4
            else
                return 0
            end
            """.trimIndent()
        assertLuaNumber(code, 3.0)
    }

    @Test
    fun testElseIfWithoutElse() {
        val code =
            """
            local x = 5
            if x == 1 then
                return 1
            elseif x == 2 then
                return 2
            elseif x == 3 then
                return 3
            end
            return 0
            """.trimIndent()
        assertLuaNumber(code, 0.0)
    }

    // ============================================
    // Truthiness rules
    // ============================================

    @Test
    fun testNilIsFalsy() {
        val code =
            """
            if nil then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 2.0)
    }

    @Test
    fun testFalseIsFalsy() {
        val code =
            """
            if false then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 2.0)
    }

    @Test
    fun testZeroIsTruthy() {
        val code =
            """
            if 0 then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testEmptyStringIsTruthy() {
        val code =
            """
            if "" then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testNumberIsTruthy() {
        val code =
            """
            if 5 then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testStringIsTruthy() {
        val code =
            """
            if "hello" then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    // ============================================
    // Nested conditionals
    // ============================================

    @Test
    fun testNestedIfInThen() {
        val code =
            """
            local x = 5
            local y = 10
            if x > 0 then
                if y > 5 then
                    return 1
                else
                    return 2
                end
            else
                return 3
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testNestedIfInElse() {
        val code =
            """
            local x = -5
            local y = 10
            if x > 0 then
                return 1
            else
                if y > 5 then
                    return 2
                else
                    return 3
                end
            end
            """.trimIndent()
        assertLuaNumber(code, 2.0)
    }

    @Test
    fun testDeeplyNestedIf() {
        val code =
            """
            local a = 1
            local b = 2
            local c = 3
            if a == 1 then
                if b == 2 then
                    if c == 3 then
                        return 100
                    end
                end
            end
            return 0
            """.trimIndent()
        assertLuaNumber(code, 100.0)
    }

    // ============================================
    // Complex conditions
    // ============================================

    @Test
    fun testIfWithLogicalAnd() {
        val code =
            """
            local x = 5
            local y = 10
            if x > 0 and y > 5 then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testIfWithLogicalOr() {
        val code =
            """
            local x = 0
            local y = 10
            if x > 5 or y > 5 then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testIfWithLogicalNot() {
        val code =
            """
            local x = false
            if not x then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testIfWithComplexExpression() {
        val code =
            """
            local a = 5
            local b = 10
            local c = 15
            if (a < b) and (b < c) and not (a > c) then
                return 1
            else
                return 2
            end
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    // ============================================
    // Scoping tests
    // ============================================

    @Test
    fun testLocalScopeInThen() {
        val code =
            """
            local x = 1
            if true then
                local x = 2
                -- Inner x should be 2
            end
            return x  -- Outer x should still be 1
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testLocalScopeInElse() {
        val code =
            """
            local x = 1
            if false then
                local x = 2
            else
                local x = 3
            end
            return x  -- Outer x should still be 1
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testLocalScopeInElseIf() {
        val code =
            """
            local x = 1
            if false then
                local x = 2
            elseif true then
                local x = 3
            else
                local x = 4
            end
            return x  -- Outer x should still be 1
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    // ============================================
    // Multiple statements in blocks
    // ============================================

    @Test
    fun testMultipleStatementsInThen() {
        val code =
            """
            local x = 0
            if true then
                x = 1
                x = x + 1
                x = x * 2
            end
            return x
            """.trimIndent()
        assertLuaNumber(code, 4.0)
    }

    @Test
    fun testMultipleStatementsInElse() {
        val code =
            """
            local x = 0
            if false then
                x = 1
            else
                x = 2
                x = x + 3
                x = x * 2
            end
            return x
            """.trimIndent()
        assertLuaNumber(code, 10.0)
    }

    // ============================================
    // Edge cases
    // ============================================

    @Test
    fun testEmptyThenBlock() {
        val code =
            """
            local x = 1
            if true then
            end
            return x
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testEmptyElseBlock() {
        val code =
            """
            local x = 1
            if false then
                x = 2
            else
            end
            return x
            """.trimIndent()
        assertLuaNumber(code, 1.0)
    }

    @Test
    fun testIfAfterIf() {
        val code =
            """
            local x = 0
            if true then
                x = 1
            end
            if true then
                x = x + 1
            end
            return x
            """.trimIndent()
        assertLuaNumber(code, 2.0)
    }
}
