package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.vm.LuaVmImpl
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests that error stacks from dead coroutines don't leak into new coroutines' tracebacks.
 * Regression test for bug where lastErrorStack was unconditionally used even when a live
 * coroutine was executing debug.traceback().
 */
class TracebackCoroutineErrorStackIsolationTest {
    @Test
    fun testErrorStackDoesNotLeakAcrossCoroutines() {
        val vm = LuaVmImpl()

        val script =
            """
            -- Function that errors, creating a distinctive error stack
            local function errorFunc()
                error("deliberate error")
            end
            
            -- Create and run a coroutine that errors
            local co1 = coroutine.create(function()
                errorFunc()
            end)
            local ok1, err1 = coroutine.resume(co1)
            assert(not ok1, "Expected co1 to error")
            assert(coroutine.status(co1) == "dead", "Expected co1 to be dead")
            
            -- Now create a fresh coroutine and get its traceback
            -- This traceback should NOT contain frames from errorFunc()
            local tracebackResult = nil
            local co2 = coroutine.create(function()
                local function level2()
                    tracebackResult = debug.traceback("marker", 1)
                end
                local function level1()
                    level2()
                end
                level1()
            end)
            local ok2 = coroutine.resume(co2)
            assert(ok2, "Expected co2 to succeed")
            
            -- Verify the traceback contains co2's frames but NOT co1's errorFunc
            assert(tracebackResult:find("marker"), "Expected marker in traceback")
            assert(tracebackResult:find("level2"), "Expected level2 in traceback")
            assert(tracebackResult:find("level1"), "Expected level1 in traceback")
            assert(not tracebackResult:find("errorFunc"), "Should NOT find errorFunc from dead coroutine")
            assert(not tracebackResult:find("deliberate error"), "Should NOT find error message from dead coroutine")
            
            return "PASS"
            """.trimIndent()

        val result = vm.execute(script, "test")
        assertTrue(result.toString() == "PASS", "Test should pass")
    }

    @Test
    fun testTracebackInNewCoroutineAfterError() {
        val vm = LuaVmImpl()

        val script =
            """
            -- Coroutine that errors with a distinctive function name
            local function errorFunc()
                error("test error")
            end
            
            local co1 = coroutine.create(errorFunc)
            coroutine.resume(co1)
            
            -- New coroutine with different function names
            function f1()
                return debug.traceback("msg", 1)
            end
            
            function f2()
                -- Not a tail call - do something after calling f1
                local tb = f1()
                return tb
            end
            
            local tb = coroutine.wrap(f2)()
            
            -- The traceback should show frames from f2's execution, not errorFunc from co1
            assert(tb:find("f1"), "Expected f1 in traceback - found: " .. tb)
            assert(not tb:find("errorFunc"), "Should NOT find errorFunc from dead coroutine - found: " .. tb)
            assert(not tb:find("test error"), "Should NOT contain error message from co1 - found: " .. tb)
            
            return "PASS"
            """.trimIndent()

        val result = vm.execute(script, "test")
        assertTrue(result.toString() == "PASS", "Test should pass")
    }
}
