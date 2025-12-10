package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.vm.errorhandling.LuaException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * PHASE 3.2: Functions - Closures
 *
 * Tests closures and upvalue capture.
 * Based on: closure.lua
 *
 * Coverage:
 * - Closures capturing local variables
 * - Upvalues
 * - Multiple closures sharing upvalues
 * - Nested closures
 * - Upvalue mutation
 * - Closure lifetimes
 */
class ClosuresCompatTest : LuaCompatTestBase() {
    @Test
    fun testBasicClosure() =
        runTest {
            val code =
                """
                function makeCounter()
                    local i = 0
                    return function()
                        i = i + 1
                        return i
                    end
                end
                local counter = makeCounter()
                local a = counter()
                local b = counter()
                local c = counter()
                return a + b + c
                """.trimIndent()
            assertLuaNumber(code, 6.0) // 1 + 2 + 3
        }

    @Test
    fun testUpvalueRead() =
        runTest {
            val code =
                """
                local x = 42
                local function getX()
                    return x
                end
                return getX()
                """.trimIndent()
            assertLuaNumber(code, 42.0)
        }

    @Test
    fun testUpvalueWrite() =
        runTest {
            val code =
                """
                local x = 10
                local function setX(val)
                    x = val
                end
                setX(20)
                return x
                """.trimIndent()
            assertLuaNumber(code, 20.0)
        }

    @Test
    fun testSharedUpvalue() =
        runTest {
            val code =
                """
                local shared = 0
                local function inc()
                    shared = shared + 1
                    return shared
                end
                local function dec()
                    shared = shared - 1
                    return shared
                end
                local a = inc()  -- shared = 1
                local b = inc()  -- shared = 2
                local c = dec()  -- shared = 1
                return a + b + c  -- 1 + 2 + 1 = 4
                """.trimIndent()
            assertLuaNumber(code, 4.0)
        }

    @Test
    fun testNestedClosure() =
        runTest {
            val code =
                """
                function outer(x)
                    return function(y)
                        return function(z)
                            return x + y + z
                        end
                    end
                end
                local f = outer(10)
                local g = f(20)
                return g(30)
                """.trimIndent()
            assertLuaNumber(code, 60.0)
        }

    @Test
    fun testClosureFactory() =
        runTest {
            val code =
                """
                function makeAdder(n)
                    return function(x)
                        return x + n
                    end
                end
                local add5 = makeAdder(5)
                local add10 = makeAdder(10)
                return add5(3) + add10(7)  -- 8 + 17 = 25
                """.trimIndent()
            assertLuaNumber(code, 25.0)
        }

    @Test
    fun testClosureInLoop() =
        runTest {
            vm.debugEnabled = true
            val code =
                """
                local f1, f2, f3
                for i = 1, 3 do
                    local x = i
                    if i == 1 then
                        f1 = function() return x end
                    elseif i == 2 then
                        f2 = function() return x end
                    else
                        f3 = function() return x end
                    end
                end
                return f1() + f2() + f3()  -- 1 + 2 + 3 = 6
                """.trimIndent()
            assertLuaNumber(code, 6.0)
        }

    @Test
    fun testUpvalueLifetime() =
        runTest {
            val code =
                """
                local f
                do
                    local x = 100
                    f = function() return x end
                end
                -- x is out of scope, but closure should still have it
                return f()
                """.trimIndent()
            assertLuaNumber(code, 100.0)
        }

    @Test
    fun testMultipleUpvalues() =
        runTest {
            val code =
                """
                local a = 1
                local b = 2
                local c = 3
                local function sum()
                    return a + b + c
                end
                return sum()
                """.trimIndent()
            assertLuaNumber(code, 6.0)
        }

    @Test
    fun testUpvalueFromDifferentScopes() =
        runTest {
            val code =
                """
                local x = 5
                local function outer()
                    local y = 10
                    return function()
                        return x + y
                    end
                end
                local f = outer()
                return f()
                """.trimIndent()
            assertLuaNumber(code, 15.0)
        }

    @Test
    fun testForLoopClosureFreshUpvalues() =
        runTest {
            // Test that each iteration of a for loop creates fresh upvalues
            // This reproduces the bug from closure.lua:70-78
            val code =
                """
                a = {}
                for i=1,3 do
                  a[i] = {get = function () return i end}
                end
                -- Each closure should have captured its own i value
                local v1 = a[1].get()
                local v2 = a[2].get()
                local v3 = a[3].get()
                assert(v1 == 1, "a[1].get() should be 1, got: " .. tostring(v1))
                assert(v2 == 2, "a[2].get() should be 2, got: " .. tostring(v2))
                assert(v3 == 3, "a[3].get() should be 3, got: " .. tostring(v3))
                return true
                """.trimIndent()
            assertLuaBoolean(code, true)
        }

    @Test
    fun testForLoopClosureWithBreak() =
        runTest {
            // Test closure upvalue isolation when loop exits early via break
            // This reproduces the exact bug from closure.lua:70-78 with the break statement
            val code =
                """
                a = {}
                for i=1,10 do
                  a[i] = {set = function(x) i=x end, get = function () return i end}
                  if i == 3 then break end
                end
                -- Verify only 3 elements were created
                assert(a[4] == nil, "a[4] should be nil")
                
                -- Each closure should have its own upvalue
                a[1].set(10)
                local v1 = a[1].get()
                local v2 = a[2].get()
                local v3 = a[3].get()
                
                assert(v1 == 10, "a[1].get() should be 10 after set(10), got: " .. tostring(v1))
                assert(v2 == 2, "a[2].get() should be 2 (not affected by a[1].set), got: " .. tostring(v2))
                assert(v3 == 3, "a[3].get() should be 3, got: " .. tostring(v3))
                
                -- Verify set() also works independently
                a[2].set('a')
                assert(a[3].get() == 3, "a[3].get() should still be 3")
                assert(a[2].get() == 'a', "a[2].get() should be 'a'")
                return true
                """.trimIndent()
            assertLuaBoolean(code, true)
        }

    @Test
    fun testRepeatUntilClosureFreshUpvalues() =
        runTest(timeout = 2.seconds) {
            // Test that each iteration of repeat-until creates fresh upvalues
            // This reproduces the bug from closure.lua:187-193
            // Timeout ensures infinite loop cases fail fast
            val code =
                """
                local a = {}
                local i = 1
                local counter = 0
                repeat
                  counter = counter + 1
                  if counter > 2000 then error("infinite loop detected") end
                  local x = i
                  a[i] = function () i = x+1; return x end
                until i > 10 or a[i]() ~= x
                -- The test should exit when a[i]() ~= x becomes true (iteration 1)
                -- because x is local to each iteration
                assert(i == 11, "i should be 11, got: " .. tostring(i))
                assert(a[1]() == 1, "a[1]() should be 1, got: " .. tostring(a[1]()))
                assert(i == 2, "i should be 2 after a[1](), got: " .. tostring(i))
                assert(a[3]() == 3, "a[3]() should be 3, got: " .. tostring(a[3]()))
                assert(i == 4, "i should be 4 after a[3](), got: " .. tostring(i))
                return true
                """.trimIndent()
            assertLuaBoolean(code, true)
        }

    @Test
    fun testManyUpvaluesArithmetic() =
        runTest {
            // Test from calls.lua - 200 upvalues exceeds Lua's 256 register limit
            // The inner function needs 200 registers for GETUPVAL + ~199 for arithmetic = 399 registers
            // This should produce a compilation error
            val code =
                """
                local nup = 400
                local prog = {"local a1"}
                for i = 2, nup do prog[#prog + 1] = ", a" .. i end
                prog[#prog + 1] = " = 1"
                for i = 2, nup do prog[#prog + 1] = ", " .. i end
                local sum = 1
                prog[#prog + 1] = "; return function () return a1"
                for i = 2, nup do prog[#prog + 1] = " + a" .. i; sum = sum + i end
                prog[#prog + 1] = " end"
                prog = table.concat(prog)
                local f = assert(load(prog))()
                return f()
                """.trimIndent()

            // Should throw compilation error about too many registers
            val exception =
                shouldThrow<LuaException> {
                    execute(code)
                }
            exception.message.shouldContain("too many registers")
            exception.message.shouldContain("limit is 256")
        }

    @Test
    fun testClosureUpvalueWithGotoLabel() =
        runTest {
            // Test from closure.lua:223
            // Tests that closures correctly capture upvalues when goto/labels are used
            execute(
                """
                local a = {}
                
                -- Create closures with different control flow using goto/labels
                for i = 1, 10 do
                  if i % 3 == 0 then
                    local y = 0
                    a[i] = function (x) local t = y; y = x; return t end
                  elseif i % 3 == 1 then
                    goto L1
                    ::L1::
                    local y = 1
                    a[i] = function (x) local t = y; y = x; return t end
                  elseif i % 3 == 2 then
                    local t
                    goto l4
                    ::l4a:: a[i] = t; goto l4b
                    ::l4::
                    local y = 2
                    t = function (x) local t = y; y = x; return t end
                    goto l4a
                    ::l4b::
                  end
                end
                
                -- Test that each closure captures the correct upvalue
                for i = 1, 10 do
                  assert(a[i](i * 10) == i % 3 and a[i]() == i * 10)
                end
                """,
            )
        }

    @Test
    fun testMinimalClosureGotoUpvalueBug() =
        runTest {
            // Minimal reproduction of closure.lua:223 bug
            // Tests ONLY the i % 3 == 2 case from the original test
            execute(
                """
                local a = {}
                local i = 2  -- Test the problematic case: i % 3 == 2
                
                do  -- Create block scope to match original test structure
                  local t
                  goto l4
                  ::l4a:: a[i] = t; goto l4b
                  ::l4::
                  local y = 2
                  t = function (x) local t = y; y = x; return t end
                  goto l4a
                  ::l4b::
                end
                
                -- Verify the closure works correctly
                assert(a[i](i * 10) == i % 3, "First call failed: expected " .. (i % 3) .. ", got " .. tostring(a[i](i * 10)))
                assert(a[i]() == i * 10, "Second call failed: expected " .. (i * 10) .. ", got " .. tostring(a[i]()))
                """,
            )
        }
}
