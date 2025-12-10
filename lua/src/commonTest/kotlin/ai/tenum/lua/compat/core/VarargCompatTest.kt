package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PHASE 2.2: Varargs
 *
 * Tests Lua's variable argument functionality (...).
 * Based on: lua-5.4.8-tests/vararg.lua
 *
 * Coverage:
 * - Vararg parameters in functions
 * - Vararg expressions (...)
 * - select('#', ...) to get count
 * - Vararg unpacking
 * - Recursive vararg calls
 * - Vararg with multiple returns
 *
 * KNOWN BUG: Nested vararg forwarding (3+ levels) fails
 * =====================================================
 * Test Case: testTripleOneless
 *   function oneless(a, ...) return ... end
 *   x = oneless(oneless(oneless(1, 2, 3, 4)))
 *   Expected: 4, Actual: nil
 *
 * Root Cause (from debug output):
 *   When compiling nested function calls like f(g(h(...))), the compiler generates:
 *     CALL a=5 b=5 c=2  -- h(1,2,3,4) returns [2,3,4] but c=2 captures only first value
 *     CALL a=3 b=2 c=0  -- g(2) with Varargs: 0 (should be [3,4])
 *     CALL a=2 b=0 c=2  -- f() with no args, returns nil
 *
 *   The 'c' parameter in CALL controls return value capture:
 *     c=0: capture all returns (needed for vararg forwarding)
 *     c=n: capture n-1 returns (discards rest)
 *
 *   Bug: When inner call result is used as argument to outer call, compiler uses c=2
 *   (capture 1 value) instead of c=0 (capture all). This loses vararg values.
 *
 * Impact:
 *   - Single level works: oneless(1, 2) → 2 ✓
 *   - Double level works: oneless(oneless(1, 2, 3)) → 3 ✓
 *   - Triple level fails: oneless(oneless(oneless(1, 2, 3, 4))) → nil ✗
 *   - Affects vararg.lua line 95: recursive function with vararg forwarding
 *
 * Fix Location: Likely in CallCompiler.kt - need to detect when call result is used
 * as function argument and emit c=0 to preserve all return values for vararg forwarding.
 */
class VarargCompatTest : LuaCompatTestBase() {
    @Test
    fun testBasicVararg() =
        runTest {
            assertLuaNumber(
                """
            local function f(...)
                local x = {...}
                return x[1]
            end
            return f(10, 20, 30)
        """,
                10.0,
            )
        }

    @Test
    fun testVarargCount() =
        runTest {
            assertLuaNumber(
                """
            local function f(...)
                return select('#', ...)
            end
            return f(1, 2, 3, 4, 5)
        """,
                5.0,
            )
        }

    @Test
    fun testVarargWithNoArgs() =
        runTest {
            assertLuaNumber(
                """
            local function f(...)
                return select('#', ...)
            end
            return f()
        """,
                0.0,
            )
        }

    @Test
    fun testVarargPacking() =
        runTest {
            assertLuaNumber(
                """
            local function vararg(...)
                local t = {n = select('#', ...), ...}
                return t.n
            end
            return vararg(1, 2, 3, nil, 5)
        """,
                5.0,
            )
        }

    @Test
    fun testVarargWithNilValues() =
        runTest {
            assertLuaNumber(
                """
            local function vararg(...)
                return {n = select('#', ...), ...}
            end
            local t = vararg(nil, nil)
            return t.n
        """,
                2.0,
            )
        }

    @Test
    fun testVarargForwarding() =
        runTest {
            assertLuaNumber(
                """
            local function inner(a, b, c)
                return a + b + c
            end
            local function outer(...)
                return inner(...)
            end
            return outer(10, 20, 30)
        """,
                60.0,
            )
        }

    @Test
    fun testRecursiveVararg() =
        runTest {
            assertLuaNumber(
                """
            local function oneless(a, ...)
                return ...
            end
            
            local function f(n, a, ...)
                if n == 0 then
                    local b, c, d = ...
                    return a, b, c, d, oneless(oneless(oneless(...)))
                else
                    n, b, a = n-1, ..., a
                    return f(n, a, ...)
                end
            end
            
            local a, b, c, d, e = f(10, 5, 4, 3, 2, 1)
            return a
        """,
                5.0,
            )
        }

    @Test
    fun testRecursiveVarargMultipleReturns() =
        runTest {
            vm.debugEnabled = true
            assertLuaTrue(
                """
            local function oneless(a, ...)
                return ...
            end
            
            local function f(n, a, ...)
                if n == 0 then
                    local b, c, d = ...
                    return a, b, c, d, oneless(oneless(oneless(...)))
                else
                    n, b, a = n-1, ..., a
                    return f(n, a, ...)
                end
            end
            
            local a, b, c, d, e = f(10, 5, 4, 3, 2, 1)
            
            -- This is the assertion from line 95 of vararg.lua
            assert(a == 5 and b == 4 and c == 3 and d == 2 and e == 1)
            return true
        """,
            )
        }

    @Test
    fun testVarargAfterEmptyCall() =
        runTest {
            assertLuaTrue(
                """
            local function oneless(a, ...)
                return ...
            end
            
            local function f(n, a, ...)
                if n == 0 then
                    local b, c, d = ...
                    return a, b, c, d, oneless(oneless(oneless(...)))
                else
                    n, b, a = n-1, ..., a
                    return f(n, a, ...)
                end
            end
            
            -- Test calling with fewer arguments (this pattern from vararg.lua line 98)
            local a, b, c, d, e = f(4)
            
            assert(a == nil and b == nil and c == nil and d == nil and e == nil)
            return true
        """,
            )
        }

    @Test
    fun testVarargInTableAccess() =
        runTest {
            assertLuaNumber(
                """
            local function f(...)
                local x = {n = select('#', ...), ...}
                return x.n
            end
            return f(10, 20, 30)
        """,
                3.0,
            )
        }

    @Test
    fun testVarargFirstArgument() =
        runTest {
            assertLuaNumber(
                """
            local function f(first, ...)
                local x = {...}
                return first
            end
            return f(100, 200, 300)
        """,
                100.0,
            )
        }

    @Test
    fun testSelectWithVararg() =
        runTest {
            assertLuaNumber(
                """
            local function f(...)
                return select(2, ...)
            end
            return f(10, 20, 30, 40)
        """,
                20.0,
            )
        }

    @Test
    fun testVarargUnpackInCall() =
        runTest {
            assertLuaNumber(
                """
            local function sum(a, b, c)
                return a + b + c
            end
            local function caller(...)
                return sum(...)
            end
            return caller(1, 2, 3)
        """,
                6.0,
            )
        }

    @Test
    fun testVarargInMultipleAssignment() =
        runTest {
            assertLuaNumber(
                """
            local function f(...)
                return ...
            end
            local a, b, c = f(10, 20, 30)
            return a
        """,
                10.0,
            )
        }

    @Test
    fun testVarargRecursionDepth10() =
        runTest {
            // Simplified version to isolate the exact failure case
            assertLuaNumber(
                """
            local function oneless(a, ...)
                return ...
            end
            
            local function f(n, a, ...)
                if n == 0 then
                    local b, c, d = ...
                    local e = oneless(oneless(oneless(...)))
                    return a, b, c, d, e
                else
                    local newN = n - 1
                    local b = ...
                    return f(newN, a, ...)
                end
            end
            
            -- Call with depth 10 like vararg.lua line 95
            local a, b, c, d, e = f(10, 5, 4, 3, 2, 1)
            
            return a
        """,
                5.0,
            )
        }

    @Test
    fun testMinimalRecursiveVararg() =
        runTest {
            // Most minimal version - just test the recursion pattern
            assertLuaNumber(
                """
            local function f(n, a, ...)
                if n == 0 then
                    return a
                else
                    return f(n - 1, a, ...)
                end
            end
            
            return f(3, 42, 1, 2, 3)
        """,
                42.0,
            )
        }

    @Test
    fun testVarargInMultipleAssignmentInRecursion() =
        runTest {
            // Test the specific pattern: n, b, a = n-1, ..., a
            assertLuaNumber(
                """
            local function f(n, a, ...)
                if n == 0 then
                    return a
                else
                    n, b, a = n-1, ..., a
                    return f(n, a, ...)
                end
            end
            
            return f(2, 5, 4, 3)
        """,
                5.0,
            )
        }

    @Test
    fun testOnelessPattern() =
        runTest {
            // Test the oneless pattern alone
            assertLuaNumber(
                """
            local function oneless(a, ...)
                return ...
            end
            
            local x = oneless(1, 2, 3, 4)
            return x
        """,
                2.0,
            )
        }

    @Test
    fun testDoubleOneless() =
        runTest {
            // Test double oneless
            assertLuaNumber(
                """
            local function oneless(a, ...)
                return ...
            end
            
            local x = oneless(oneless(1, 2, 3))
            return x
        """,
                3.0,
            )
        }

    @Test
    fun testTripleOneless() =
        runTest {
            // Test triple oneless like in the failing assertion
            // BUG: This test fails because nested function call returns are not captured correctly
            // The compiler generates CALL with c=2 (capture 1 value) instead of c=0 (capture all)
            // causing vararg values [3, 4] to be lost after the first call
            // Uncomment the line below to see detailed VM execution trace:
            vm.debugEnabled = true
            assertLuaNumber(
                """
            local function oneless(a, ...)
                return ...
            end
            
            local x = oneless(oneless(oneless(1, 2, 3, 4)))
            return x
        """,
                4.0,
            )
        }

    @Test
    fun testMultipleReturnValuesInNestedCalls() =
        runTest {
            // Simpler test showing the same bug with explicit multiple returns
            assertLuaNumber(
                """
            local function returnThree()
                return 1, 2, 3
            end
            
            local function takeFirst(a, ...)
                return ...
            end
            
            -- This should pass 2 and 3 as varargs to takeFirst
            local x = takeFirst(returnThree())
            return x
        """,
                2.0,
            )
        }

    @Test
    fun testNestedCallsPreserveAllReturns() =
        runTest {
            // Test that all return values are preserved through nested calls
            assertLuaNumber(
                """
            local function returnFour()
                return 10, 20, 30, 40
            end
            
            local function passThrough(...)
                return ...
            end
            
            -- Each level should preserve all return values
            local a, b, c, d = passThrough(returnFour())
            return d
        """,
                40.0,
            )
        }

    // ========================================
    // Tests for select() function with varargs
    // (Consolidated from SelectNoVarargBugTest.kt)
    // ========================================

    @Test
    fun testSelectWithTableUnpack() =
        runTest {
            // select(3, table.unpack{10,20,30,40}) should return 30, 40
            val result =
                execute(
                    """
            a = {select(3, table.unpack{10,20,30,40})}
            if #a == 2 and a[1] == 30 and a[2] == 40 then
                return 1
            else
                print("Expected: #a=2, a[1]=30, a[2]=40")
                print("Actual: #a=" .. #a .. ", a[1]=" .. tostring(a[1]) .. ", a[2]=" .. tostring(a[2]))
                return 0
            end
        """,
                )
            assertLuaNumber(result, 1.0)
        }

    @Test
    fun testSelectWithNoVarargs() =
        runTest {
            // select(1) with no varargs should return nothing (empty table)
            val result =
                execute(
                    """
            a = {select(1)}
            return #a
        """,
                )
            assertLuaNumber(result, 0.0)
        }

    @Test
    fun testSelectWithNoVarargsNextNil() =
        runTest {
            // select(1) creates empty table, next() should return nil
            val result =
                execute(
                    """
            a = {select(1)}
            return next(a)
        """,
                )
            assertTrue(result is ai.tenum.lua.runtime.LuaNil, "Expected nil from next(empty table), got ${result::class.simpleName}")
        }

    @Test
    fun testSelectNegativeIndexNoVarargs() =
        runTest {
            // select(-1) with no varargs should return nothing
            val result =
                execute(
                    """
            a = {select(-1)}
            return #a
        """,
                )
            assertLuaNumber(result, 0.0)
        }

    @Test
    fun testSelectNegativeWithVarargs() =
        runTest {
            // select(-1, 3, 5, 7) should return just 7 (last element)
            val result =
                execute(
                    """
            a = {select(-1, 3, 5, 7)}
            if #a == 1 and a[1] == 7 then
                return 1
            else
                return 0
            end
        """,
                )
            assertLuaNumber(result, 1.0)
        }

    @Test
    fun testSelectNegativeTwoWithVarargs() =
        runTest {
            // select(-2, 3, 5, 7) should return last 2 elements: 5, 7
            val result =
                execute(
                    """
            a = {select(-2, 3, 5, 7)}
            if #a == 2 and a[1] == 5 and a[2] == 7 then
                return 1
            else
                return 0
            end
        """,
                )
            assertLuaNumber(result, 1.0)
        }

    @Test
    fun testSelectCountWithNoVarargs() =
        runTest {
            // select('#') with no varargs should return 0
            val result =
                execute(
                    """
            return select('#')
        """,
                )
            assertLuaNumber(result, 0.0)
        }

    @Test
    fun testSelectCountWithVarargs() =
        runTest {
            // select('#', 1, 2, 3) should return count: 3
            val result =
                execute(
                    """
            return select('#', 1, 2, 3)
        """,
                )
            assertLuaNumber(result, 3.0)
        }

    // ========================================
    // Tests for varargs in dynamically loaded chunks
    // (Consolidated from VarargLoadBugTest.kt)
    // ========================================

    @Test
    fun testLoadWithVarargBasic() =
        runTest {
            // load() creates function that accepts varargs
            val result =
                execute(
                    """
            local f = load("return ...")
            return f(42)
        """,
                )
            assertLuaNumber(result, 42.0)
        }

    @Test
    fun testLoadWithVarargMultiple() =
        runTest {
            // load() with multiple vararg returns
            val result =
                execute(
                    """
            local f = load("return ...")
            local a, b, c = f(1, 2, 3)
            return a + b + c
        """,
                )
            assertLuaNumber(result, 6.0)
        }

    @Test
    fun testLoadWithVarargInTable() =
        runTest {
            // load() with {...} table constructor
            val result =
                execute(
                    """
            local f = load("return {...}")
            local x = f(2, 3)
            return x[1]
        """,
                )
            assertLuaNumber(result, 2.0)
        }

    @Test
    fun testLoadWithVarargInTableMultipleElements() =
        runTest {
            // Full test: {...} captures all varargs
            val result =
                execute(
                    """
            local f = load("return {...}")
            local x = f(2, 3)
            if x[1] == 2 and x[2] == 3 and x[3] == nil then
                return 1
            else
                return 0
            end
        """,
                )
            assertLuaNumber(result, 1.0)
        }

    @Test
    fun testLoadWithVarargTableAndSelect() =
        runTest {
            // Complex test combining {...} and select()
            val result =
                execute(
                    """
            local f = load([[
              local x = {...}
              for i=1,select('#', ...) do 
                if x[i] ~= select(i, ...) then
                  return false
                end
              end
              return true
            ]])
            
            return f("a", "b", nil, {})
        """,
                )
            assertTrue(result is ai.tenum.lua.runtime.LuaBoolean)
            assertEquals(true, (result as ai.tenum.lua.runtime.LuaBoolean).value)
        }

    @Test
    fun testLoadWithVarargEmptyCall() =
        runTest {
            // Edge case: calling loaded vararg function with no arguments
            val result =
                execute(
                    """
            local f = load("return {...}")
            local x = f()
            if x[1] == nil then
                return 1
            else
                return 0
            end
        """,
                )
            assertLuaNumber(result, 1.0)
        }

    @Test
    fun testLoadWithVarargLongForm() =
        runTest {
            // Using [[ ]] long string syntax
            val result =
                execute(
                    """
            local f = load[[ return {...} ]]
            local x = f(2, 3)
            return x[1] + x[2]
        """,
                )
            assertLuaNumber(result, 5.0)
        }
}
