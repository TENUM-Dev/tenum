package ai.tenum.lua.compiler

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.vm.LuaVmImpl
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for upvalue closing with backward goto statements.
 * Based on the upvalue closing test from goto.lua line 148-217
 */
class GotoBackwardUpvalueClosingTest {
    @Test
    fun `backward goto should close and reopen upvalues`() =
        runTest {
            // 5 second timeout
            // Minimal test: a[3] and a[4] should NOT share upvalue 2 (local b)
            // This tests that when goto jumps back to ::l1::, the local b is properly closed
            // and a new instance is created on the next iteration
            val vm = LuaVmImpl()
            val result =
                vm.execute(
                    """
            local debug = require 'debug'
            
            local function foo ()
              local t = {}
              do
              local i = 1
              local a, b, c, d
              local safety = 0  -- Safety counter to prevent infinite loops
              t[1] = function () return a, b, c, d end
              ::l1::
              safety = safety + 1
              if safety > 100 then error("Safety abort: infinite loop detected") end
              local b
              do
                local c
                t[#t + 1] = function () return a, b, c, d end    -- t[2], t[4], t[6]
                if i > 2 then goto l2 end
                do
                  local d
                  t[#t + 1] = function () return a, b, c, d end   -- t[3], t[5]
                  i = i + 1
                  local a
                  goto l1
                end
              end
              end
              ::l2:: return t
            end
            
            local a = foo()
            
            -- a[3] and a[4] should NOT share upvalue 2 (local b)
            -- Each iteration of the loop should create a NEW local b
            local id3 = debug.upvalueid(a[3], 2)
            local id4 = debug.upvalueid(a[4], 2)
            
            return id3 ~= id4
        """,
                )

            result shouldBe LuaBoolean.TRUE
        }

    @Test
    fun `backward goto should close locals declared after label`() =
        runTest {
            // 5 second timeout
            // Test that locals declared after ::label:: are properly closed
            // when goto jumps back to ::label::
            val vm = LuaVmImpl()
            vm.debugEnabled = false

            val result =
                vm.execute(
                    """
            local debug = require 'debug'
            local funcs = {}
            
            local i = 1
            local safety = 0  -- Safety counter to prevent infinite loops
            ::loop::
            safety = safety + 1
            if safety > 100 then error("Safety abort: infinite loop detected") end
            local x = i  -- Each iteration should have its own x
            funcs[i] = function() return x end
            i = i + 1
            if i <= 3 then goto loop end
            
            -- funcs[1], funcs[2], funcs[3] should have different upvalue instances
            local id1 = debug.upvalueid(funcs[1], 1)
            local id2 = debug.upvalueid(funcs[2], 1)
            local id3 = debug.upvalueid(funcs[3], 1)
            
            local allDifferent = (id1 ~= id2) and (id2 ~= id3) and (id1 ~= id3)
            
            return allDifferent
        """,
                )

            result shouldBe LuaBoolean.TRUE
        }

    @Test
    fun `backward goto with nested scopes should close all locals after label`() =
        runTest {
            // 5 second timeout
            // More complex case with nested scopes
            val vm = LuaVmImpl()
            vm.debugEnabled = false

            val result =
                vm.execute(
                    """
            local debug = require 'debug'
            local funcs = {}
            
            do
                local i = 1
                local safety = 0  -- Safety counter to prevent infinite loops
                ::l1::
                safety = safety + 1
                if safety > 100 then error("Safety abort: infinite loop detected") end
                local a
                do
                    local b
                    funcs[i] = function() return a, b end
                    i = i + 1
                    if i <= 2 then goto l1 end
                end
            end
            
            -- funcs[1] and funcs[2] should have different upvalue IDs for both a and b
            local id1_a = debug.upvalueid(funcs[1], 1)
            local id2_a = debug.upvalueid(funcs[2], 1)
            local id1_b = debug.upvalueid(funcs[1], 2)
            local id2_b = debug.upvalueid(funcs[2], 2)
            
            return (id1_a ~= id2_a) and (id1_b ~= id2_b)
        """,
                )

            result shouldBe LuaBoolean.TRUE
        }
}
