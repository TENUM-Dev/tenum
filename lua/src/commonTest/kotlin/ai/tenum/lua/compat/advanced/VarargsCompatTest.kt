package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 8.2: Varargs Compatibility Tests
 *
 * Based on official Lua 5.4.8 test suite (testes/vararg.lua, testes/calls.lua)
 *
 * Tests vararg (...) functionality:
 * - Basic ... parameter usage
 * - select() function
 * - table.pack() / table.unpack() with varargs
 * - Vararg forwarding
 * - Edge cases (empty varargs, nil in varargs, etc.)
 */
class VarargsCompatTest : LuaCompatTestBase() {
    // ========== Basic Vararg Syntax ==========

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testBasicVarargs() {
        val result =
            execute(
                """
            local function f(...)
                local a, b, c = ...
                return a, b, c
            end
            return f(10, 20, 30)
            """,
            )
        assertLuaNumber(result, 10.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargWithFixedParams() {
        val result =
            execute(
                """
            local function f(a, b, ...)
                local c, d = ...
                return a + b + (c or 0) + (d or 0)
            end
            return f(1, 2, 3, 4)
            """,
            )
        assertLuaNumber(result, 10.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testEmptyVarargs() {
        val result =
            execute(
                """
            local function f(...)
                local a = ...
                return a
            end
            return f()
            """,
            )
        assertTrue(result is LuaNil)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsWithNil() {
        val result =
            execute(
                """
            local function f(...)
                local a, b, c = ...
                if a == nil and b == nil and c == nil then
                    return true
                end
                return false
            end
            return f(nil, nil, nil)
            """,
            )
        assertLuaBoolean(result, true)
    }

    // ========== select() Function ==========

    @Test
    fun testSelectCount() {
        val result =
            execute(
                """
            return select('#', 10, 20, 30)
            """,
            )
        assertLuaNumber(result, 3.0)
    }

    @Test
    fun testSelectCountWithNils() {
        val result =
            execute(
                """
            return select('#', 10, nil, 30, nil)
            """,
            )
        assertLuaNumber(result, 4.0)
    }

    @Test
    fun testSelectIndex() {
        val result =
            execute(
                """
            return select(2, 10, 20, 30, 40)
            """,
            )
        assertLuaNumber(result, 20.0)
    }

    @Test
    fun testSelectIndexMultipleReturns() {
        val result =
            execute(
                """
            local function foo()
                return select(2, 10, 20, 30, 40)
            end
            local a, b, c = foo()
            return a
            """,
            )
        assertLuaNumber(result, 20.0)
    }

    @Test
    fun testSelectNegativeIndex() {
        val result =
            execute(
                """
            local a = select(-1, 3, 5, 7)
            return a
            """,
            )
        assertLuaNumber(result, 7.0)
    }

    @Test
    fun testSelectNegativeIndexMultiple() {
        val result =
            execute(
                """
            local a, b = select(-2, 3, 5, 7)
            return a
            """,
            )
        assertLuaNumber(result, 5.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testSelectWithVarargs() {
        val result =
            execute(
                """
            local function f(...)
                return select('#', ...)
            end
            return f(1, 2, 3, 4, 5)
            """,
            )
        assertLuaNumber(result, 5.0)
    }

    // ========== table.pack() ==========

    @Test
    fun testTablePack() {
        val result =
            execute(
                """
            local t = table.pack(10, 20, 30)
            return t.n
            """,
            )
        assertLuaNumber(result, 3.0)
    }

    @Test
    fun testTablePackWithNils() {
        val result =
            execute(
                """
            local t = table.pack(10, nil, 30, nil)
            return t.n
            """,
            )
        assertLuaNumber(result, 4.0)
    }

    @Test
    fun testTablePackAccessElements() {
        val result =
            execute(
                """
            local t = table.pack(10, 20, 30)
            return t[1] + t[2] + t[3]
            """,
            )
        assertLuaNumber(result, 60.0)
    }

    @Test
    fun testTablePackEmpty() {
        val result =
            execute(
                """
            local t = table.pack()
            return t.n
            """,
            )
        assertLuaNumber(result, 0.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testTablePackFromVarargs() {
        val result =
            execute(
                """
            local function f(...)
                local t = table.pack(...)
                return t.n
            end
            return f(1, 2, 3, 4, 5)
            """,
            )
        assertLuaNumber(result, 5.0)
    }

    // ========== table.unpack() with Varargs ==========

    // @Ignore("Requires table.unpack implementation")
    @Test
    fun testUnpackBasic() {
        val result =
            execute(
                """
            local a, b, c = table.unpack{10, 20, 30}
            return a + b + c
            """,
            )
        assertLuaNumber(result, 60.0)
    }

    @Test
    fun testUnpackWithRange() {
        val result =
            execute(
                """
            local a, b = table.unpack({10, 20, 30, 40}, 2, 3)
            return a + b
            """,
            )
        assertLuaNumber(result, 50.0)
    }

    @Test
    fun testUnpackEmptyRange() {
        val result =
            execute(
                """
            local a = table.unpack({10, 20, 30}, 10, 6)
            return a
            """,
            )
        assertTrue(result is LuaNil)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testUnpackInVarargs() {
        val result =
            execute(
                """
            local function f(...)
                local t = table.pack(...)
                return t[1] + t[2] + t[3]
            end
            return f(table.unpack{1, 2, 3})
            """,
            )
        assertLuaNumber(result, 6.0)
    }

    // ========== Vararg Forwarding ==========

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargForwarding() {
        val result =
            execute(
                """
            local function inner(...)
                local t = table.pack(...)
                return t.n
            end
            
            local function outer(...)
                return inner(...)
            end
            
            return outer(1, 2, 3, 4, 5)
            """,
            )
        assertLuaNumber(result, 5.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargForwardingWithFixedParams() {
        val result =
            execute(
                """
            local function inner(a, b, ...)
                local c = ...
                return a + b + (c or 0)
            end
            
            local function outer(x, ...)
                return inner(x, ...)
            end
            
            return outer(10, 20, 30)
            """,
            )
        assertLuaNumber(result, 60.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargPartialForwarding() {
        val result =
            execute(
                """
            local function f(...)
                local a, b = ...
                local function g(...)
                    local c, d = ...
                    return (a or 0) + (b or 0) + (c or 0) + (d or 0)
                end
                return g(select(3, ...))
            end
            return f(1, 2, 3, 4)
            """,
            )
        assertLuaNumber(result, 10.0)
    }

    // ========== Varargs in Table Constructors ==========

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsInTableConstructor() {
        val result =
            execute(
                """
            local function f(...)
                local t = {...}
                return t[1] + t[2] + t[3]
            end
            return f(10, 20, 30)
            """,
            )
        assertLuaNumber(result, 60.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsTableConstructorWithNils() {
        val result =
            execute(
                """
            local function f(...)
                local t = {...}
                return select('#', ...)
            end
            return f(10, nil, 30)
            """,
            )
        assertLuaNumber(result, 3.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsTableConstructorNField() {
        val result =
            execute(
                """
            local function f(...)
                local t = table.pack(...)
                return t.n
            end
            return f(10, nil, 30, nil, 50)
            """,
            )
        assertLuaNumber(result, 5.0)
    }

    // ========== Varargs with Multiple Returns ==========

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsMultipleReturns() {
        val result =
            execute(
                """
            local function f(...)
                return ...
            end
            local a, b, c = f(1, 2, 3)
            return a + b + c
            """,
            )
        assertLuaNumber(result, 6.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsReturnCount() {
        val result =
            execute(
                """
            local function f(...)
                return ...
            end
            local t = table.pack(f(10, 20, 30, 40))
            return t.n
            """,
            )
        assertLuaNumber(result, 4.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsNestedReturns() {
        val result =
            execute(
                """
            local function inner(...)
                return ...
            end
            local function outer(...)
                return inner(...)
            end
            local a, b, c = outer(5, 10, 15)
            return a + b + c
            """,
            )
        assertLuaNumber(result, 30.0)
    }

    // ========== Varargs in Tail Calls ==========

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsTailCall() {
        val result =
            execute(
                """
            local function foo(...)
                local t = table.pack(...)
                if t.n == 0 then return 42 end
                return foo(select(2, ...))
            end
            return foo(1, 2, 3, 4, 5)
            """,
            )
        assertLuaNumber(result, 42.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsTailCallForwarding() {
        val result =
            execute(
                """
            local function inner(a, b, c)
                return (a or 0) + (b or 0) + (c or 0)
            end
            local function outer(...)
                return inner(...)
            end
            return outer(10, 20, 30)
            """,
            )
        assertLuaNumber(result, 60.0)
    }

    // ========== Edge Cases ==========

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsOnlyNils() {
        val result =
            execute(
                """
            local function f(...)
                return select('#', ...)
            end
            return f(nil, nil, nil, nil)
            """,
            )
        assertLuaNumber(result, 4.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsMixedTypes() {
        val result =
            execute(
                """
            local function f(...)
                local a, b, c, d = ...
                local result = 0
                if type(a) == "number" then result = result + a end
                if type(b) == "string" then result = result + 1 end
                if type(c) == "boolean" and c then result = result + 10 end
                if d == nil then result = result + 100 end
                return result
            end
            return f(5, "hello", true, nil)
            """,
            )
        assertLuaNumber(result, 116.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsLargeCount() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local function f(...)
                return select('#', ...)
            end
            local t = {}
            for i = 1, 100 do t[i] = i end
            return f(table.unpack(t))
            """,
            )
        assertLuaNumber(result, 100.0)
    }

    @Test
    fun testSelectIndexBeyondCount() {
        val result =
            execute(
                """
            local a = select(10, 1, 2, 3)
            return a
            """,
            )
        assertTrue(result is LuaNil)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsInClosure() {
        val result =
            execute(
                """
            local function outer(...)
                local t = table.pack(...)
                return function()
                    return t.n
                end
            end
            local f = outer(1, 2, 3, 4, 5)
            return f()
            """,
            )
        assertLuaNumber(result, 5.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsMultipleFunctions() {
        val result =
            execute(
                """
            local function f1(...)
                return select('#', ...)
            end
            local function f2(...)
                return select('#', ...)
            end
            local a = f1(1, 2, 3)
            local b = f2(4, 5)
            return a + b
            """,
            )
        assertLuaNumber(result, 5.0)
    }

    // ========== Varargs with Operators ==========

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsInExpression() {
        val result =
            execute(
                """
            local function f(...)
                local a, b, c = ...
                return (a or 0) + (b or 0) + (c or 0)
            end
            return f(10, 20) + f(5)
            """,
            )
        assertLuaNumber(result, 35.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsConditional() {
        val result =
            execute(
                """
            local function f(...)
                if select('#', ...) > 2 then
                    return "many"
                else
                    return "few"
                end
            end
            local a = f(1, 2, 3, 4)
            local b = f(1)
            if a == "many" and b == "few" then
                return true
            end
            return false
            """,
            )
        assertLuaBoolean(result, true)
    }

    // ========== Compatibility with Lua 5.4 Behavior ==========

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsOfficialSuitePattern1() {
        // From testes/vararg.lua: basic vararg table creation
        val result =
            execute(
                """
            local function f(a, ...)
                local x = {n = select('#', ...), ...}
                local t = table.pack(...)
                return x.n == t.n
            end
            return f("ignored", 1, 2, 3)
            """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testVarargsOfficialSuitePattern2() {
        // From testes/vararg.lua: select with negative index
        val result =
            execute(
                """
            local a = {select(-1, 3, 5, 7)}
            return a[1]
            """,
            )
        assertLuaNumber(result, 7.0)
    }

    // @Ignore("Requires varargs (...) implementation")
    @Test
    fun testVarargsOfficialSuitePattern3() {
        vm.debugEnabled = true
        // From testes/calls.lua: vararg forwarding in tail call
        val result =
            execute(
                """
            local function foo(...)
                local t = table.pack(...)
                return t[1], t[2]
            end
            local function bar(...)
                return foo(...)
            end
            local a, b = bar(10, 20)
            return a + b
            """,
            )
        assertLuaNumber(result, 30.0)
    }
}
