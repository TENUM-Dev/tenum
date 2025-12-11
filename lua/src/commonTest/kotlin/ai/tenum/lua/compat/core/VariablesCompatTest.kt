package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PHASE 1.3: Variables and Scoping
 *
 * Tests variable declarations, scoping rules, and the _ENV environment.
 * Based on: locals.lua, nextvar.lua
 *
 * Coverage:
 * - Local variables
 * - Global variables
 * - Variable shadowing
 * - Scope rules (do blocks, functions, loops)
 * - Multiple assignment
 * - Const variables (<const>) - NOT YET IMPLEMENTED
 * - Close variables (<close>) - NOT YET IMPLEMENTED
 * - _ENV environment - NOT YET IMPLEMENTED
 */
class VariablesCompatTest : LuaCompatTestBase() {
    // ========== Local Variables ==========

    @Test
    fun testBasicLocalVariable() {
        //language=lua
        assertLuaNumber(
            """
            local x = 1
            return x
        """,
            1.0,
        )
    }

    @Test
    fun testLocalVariableNil() {
        //language=lua
        val result =
            execute(
                """
            local x
            return x
        """,
            )
        assertTrue(result is LuaNil, "Uninitialized local should be nil")
    }

    @Test
    fun testLocalVariableReassignment() {
        //language=lua
        assertLuaNumber(
            """
            local x = 1
            x = 2
            return x
        """,
            2.0,
        )
    }

    @Test
    fun testMultipleLocalVariables() {
        //language=lua
        assertLuaNumber(
            """
            local a = 1
            local b = 2
            return a + b
        """,
            3.0,
        )
    }

    // ========== Global Variables ==========

    @Test
    fun testBasicGlobalVariable() {
        //language=lua
        assertLuaNumber(
            """
            x = 1
            return x
        """,
            1.0,
        )
    }

    @Test
    fun testGlobalVariableReassignment() {
        //language=lua
        assertLuaNumber(
            """
            x = 1
            x = 2
            return x
        """,
            2.0,
        )
    }

    @Test
    fun testMixedLocalGlobal() {
        assertLuaNumber(
            """
            local x = 1
            y = 2
            return x + y
        """,
            3.0,
        )
    }

    // ========== Variable Shadowing ==========

    @Test
    fun testLocalShadowsGlobal() {
        //language=lua
        assertLuaNumber(
            """
            x = 1
            local x = 2
            return x
        """,
            2.0,
        )
    }

    @Test
    fun testShadowingInDoBlock() {
        //language=lua
        assertLuaNumber(
            """
            local i = 10
            do local i = 100 end
            return i
        """,
            10.0,
        )
    }

    @Test
    fun testNestedShadowing() {
        //language=lua
        assertLuaNumber(
            """
            local i = 10
            do 
              local i = 100
              do local i = 1000 end
            end
            return i
        """,
            10.0,
        )
    }

    @Test
    fun testShadowingInIfBlock() {
        //language=lua
        assertLuaNumber(
            """
            local i = 10
            if true then
              local i = 30
            end
            return i
        """,
            10.0,
        )
    }

    @Test
    fun testShadowingInElseBlock() {
        //language=lua
        assertLuaNumber(
            """
            local i = 10
            if false then
              local i = 20
            else
              local i = 30
            end
            return i
        """,
            10.0,
        )
    }

    // ========== Do Blocks ==========

    @Test
    fun testDoBlockScope() {
        //language=lua
        val result =
            execute(
                """
            do local x = 1 end
            return x
        """,
            )
        assertTrue(result is LuaNil, "Variable defined in do block should not be accessible outside")
    }

    @Test
    fun testDoBlockWithReturn() {
        //language=lua
        assertLuaNumber(
            """
            local x = 1
            do x = 2 end
            return x
        """,
            2.0,
        )
    }

    @Test
    fun testNestedDoBlocks() {
        //language=lua
        assertLuaNumber(
            """
            local x = 1
            do do x = 2 end end
            return x
        """,
            2.0,
        )
    }

    // ========== Multiple Assignment ==========

    @Test
    fun testMultipleAssignmentBasic() {
        //language=lua
        assertLuaNumber(
            """
            local a, b = 1, 2
            return a + b
        """,
            3.0,
        )
    }

    @Test
    fun testMultipleAssignmentSwap() {
        //language=lua
        assertLuaNumber(
            """
            local a, b = 1, 2
            a, b = b, a
            return a
        """,
            2.0,
        )
    }

    @Test
    fun testMultipleAssignmentWithNil() {
        //language=lua
        assertLuaNumber(
            """
            local a, b = 1
            return a
        """,
            1.0,
        )
    }

    @Test
    fun testMultipleAssignmentExtraValues() {
        //language=lua
        assertLuaNumber(
            """
            local a, b = 1, 2, 3
            return a + b
        """,
            3.0,
        )
    }

    @Test
    fun testMultipleAssignmentFewerReturnValues() {
        //language=lua
        val result =
            execute(
                """
            local function f() return 10, 11, 12 end
            local a, b, c, d = f()
            assert(a == 10)
            assert(b == 11)
            assert(c == 12)
            assert(d == nil)
            return a
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testMultipleAssignmentOverwritePreviousValue() {
        //language=lua
        val result =
            execute(
                """
            local function f() return 10, 11, 12 end
            
            -- Match attrib.lua scenario
            local a, b, c, d = 1 and nil, 1 or nil, (1 and (nil or 1)), 6
            assert(not a and b and c and d==6)
            
            d = 20
            a, b, c, d = f()
            assert(a==10 and b==11 and c==12 and d==nil)
            
            a, b = f(), 1, 2, 3, f()
            assert(a==10 and b==1)
            
            return b
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    // ========== Scope in Loops ==========

    @Test
    fun testLocalInWhileLoop() {
        //language=lua
        assertLuaNumber(
            """
            local x = 0
            while x < 3 do
              local y = 1
              x = x + y
            end
            return x
        """,
            3.0,
        )
    }

    @Test
    fun testLocalInRepeatLoop() {
        //language=lua
        assertLuaNumber(
            """
            local x = 0
            repeat
              local y = 1
              x = x + y
            until x >= 3
            return x
        """,
            3.0,
        )
    }

    @Test
    fun testForLoopVariable() {
        //language=lua
        assertLuaNumber(
            """
            local sum = 0
            for i = 1, 3 do
              sum = sum + i
            end
            return sum
        """,
            6.0,
        )
    }

    @Test
    fun testForLoopVariableScope() {
        // for i = 1, 3 do end
        // return i (i should be nil - out of scope)
        //language=lua
        val result =
            execute(
                """
            for i = 1, 3 do end
            return i
        """,
            )
        assertTrue(result is LuaNil, "For loop variable should not be accessible outside loop")
    }

    // ========== Complex Scoping ==========

    @Test
    fun testComplexScoping() {
        // Based on locals.lua
        // Tests multiple levels of scoping with reassignment
        //language=lua
        assertLuaNumber(
            """
            local x = 10
            do
              local x = 20
              do
                local x = 30
              end
            end
            return x
        """,
            10.0,
        )
    }

    @Test
    fun testScopeWithOperations() {
        //language=lua
        assertLuaNumber(
            """
            local x = 1
            do
              local x = 2
              do
                local x = 3
                x = x * 2
              end
            end
            return x
        """,
            1.0,
        )
    }

    @Test
    fun testGlobalAssignmentInsideFunction() {
        // calls.lua line 66-71: Global variable assignment inside a function
        // When assigning to a variable that is not a local within a function,
        // it should modify the global variable, not create a local
        //language=lua
        assertLuaNumber(
            """
            t = nil
            function f(a, b, c)
                local d = 'a'
                t = {a, b, c, d}
            end
            f(1, 2)
            return t[1]
        """,
            1.0,
        )
    }
}
