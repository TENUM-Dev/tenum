package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for multiple assignment evaluation order.
 * In Lua, all right-hand side expressions are evaluated BEFORE any left-hand side assignments.
 */
class MultipleAssignmentOrderTest : LuaCompatTestBase() {
    @Test
    fun testBasicMultipleAssignment() {
        val result =
            execute(
                """
            local x, y = 1, 2
            x, y = y, x
            return x
        """,
            )

        assertLuaNumber(result, 2.0)
    }

    @Test
    fun testMultipleAssignmentWithTableIndex() {
        val result =
            execute(
                """
            local a = {10, 20}
            local i = 1
            i, a[i] = 2, 99
            return a[1]
        """,
            )

        // a[1] gets 99 because old i was 1
        assertLuaNumber(result, 99.0)
    }

    @Test
    fun testComplexMultipleAssignment() {
        // From attrib.lua line 484
        val result =
            execute(
                """
            local a = {'a', 'b'}
            local i = 1
            local j = 2
            local b = a
            i, a[i], a, j, a[j], a[i+j] = j, i, i, b, j, i
            return type(a)
        """,
            )

        // After assignment: a becomes number 1
        assertLuaString(result, "number")
    }

    @Test
    fun testMultipleAssignmentRHSEvaluatedFirst() {
        val result =
            execute(
                """
            local x = 5
            local t = {10, 20, 30}
            x, t[x] = 1, 99
            return t[5]
        """,
            )

        // t[5] gets 99 because old x was 5
        assertLuaNumber(result, 99.0)
    }

    @Test
    fun testMultipleAssignmentWithVariableReassignment() {
        val result =
            execute(
                """
            local a = {100}
            local i = 1
            a, a[i] = {200}, 999
            return a[1]
        """,
            )

        // a gets new table {200}, old a[1] should have gotten 999 but a changed
        // so new a[1] is 200
        assertLuaNumber(result, 200.0)
    }

    @Test
    fun testMultipleAssignmentChainedTableAccess() {
        val result =
            execute(
                """
            local t = {{val = 1}, {val = 2}}
            local i = 1
            i, t[i].val = 2, 99
            return t[1].val
        """,
            )

        // t[1].val gets 99 because old i was 1
        assertLuaNumber(result, 99.0)
    }

    @Test
    fun testMultipleAssignmentInImmediatelyCalledFunction() {
        // From attrib.lua line 507
        val result =
            execute(
                """
            local t = {}
            (function (a) t[a], a = 10, 20 end)(1)
            return t[1]
        """,
            )

        // t[1] should be 10 (assigned using old value of a which was 1)
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testFunctionExpressionBasic() {
        val result =
            execute(
                """
            local f = (function() return 42 end)
            return f()
        """,
            )

        assertLuaNumber(result, 42.0)
    }

    @Test
    fun testImmediatelyInvokedFunctionExpression() {
        val result =
            execute(
                """
            return (function() return 42 end)()
        """,
            )

        assertLuaNumber(result, 42.0)
    }

    @Test
    fun testLocalVariableBeforeFunctionCall() {
        val result =
            execute(
                """
            local t = {}
            return type(t)
        """,
            )

        assertLuaString(result, "table")
    }

    @Test
    fun testLocalVariableBeforeImmediateFunctionCall() {
        val result =
            execute(
                """
            local t = {}
            local result = (function() return 42 end)()
            return result
        """,
            )

        assertLuaNumber(result, 42.0)
    }

    @Test
    fun testAssignmentInsideFunctionReferencingOuter() {
        val result =
            execute(
                """
            local t = {}
            (function() t[1] = 99 end)()
            return t[1]
        """,
            )

        assertLuaNumber(result, 99.0)
    }

    @Test
    fun testSimpleAssignmentInsideFunction() {
        val result =
            execute(
                """
            local t = {}
            (function() local x = 1; t[x] = 99 end)()
            return t[1]
        """,
            )

        assertLuaNumber(result, 99.0)
    }
}
