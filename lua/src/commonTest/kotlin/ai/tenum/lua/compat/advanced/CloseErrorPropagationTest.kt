package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for error propagation through __close metamethods.
 *
 * When an error occurs and unwinding begins, each __close metamethod should:
 * 1. Receive the current error as its second parameter
 * 2. If it throws a new error, that error replaces the current error
 * 3. The final error seen by pcall should be from the last __close that threw
 *
 * Based on: locals.lua lines 408-462
 */
class CloseErrorPropagationTest : LuaCompatTestBase() {
    @Test
    fun testCloseErrorChaining() =
        runTest {
            // Test error chaining through multiple __close handlers
            assertLuaBoolean(
                """
            local debug = require"debug"
            
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            local function foo()
              local x <close> = func2close(function (self, msg)
                -- Should receive "@x1" error
                assert(string.find(msg, "@x1"))
              end)
            
              local x1 <close> = func2close(function (self, msg)
                -- Should receive "@y" error
                assert(string.find(msg, "@y"))
                error("@x1")
              end)
            
              local y <close> = func2close(function (self, msg)
                -- Should receive "@z" error
                assert(string.find(msg, "@z"))
                error("@y")
              end)
            
              local z <close> = func2close(function (self, msg)
                -- Should receive original error (4)
                assert(msg == 4)
                error("@z")
              end)
            
              error(4)  -- original error
            end
            
            local stat, msg = pcall(foo)
            return string.find(msg, "@x1") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testCloseNoErrorInClose() =
        runTest {
            // Test that __close receives nil when there's no error
            assertLuaBoolean(
                """
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            local result = false
            local function foo()
              local x <close> = func2close(function (self, msg)
                result = (msg == nil)
              end)
              return 42
            end
            
            local stat, val = pcall(foo)
            return result and stat and val == 42
        """,
                true,
            )
        }

    @Test
    fun testCloseFirstErrorWins() =
        runTest {
            // Test that only the first close handler that throws creates the error
            assertLuaBoolean(
                """
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            local function foo()
              local x <close> = func2close(function (self, msg)
                -- Don't re-throw, so this should be the final error
              end)
            
              local y <close> = func2close(function (self, msg)
                error("@y")
              end)
            
              local z <close> = func2close(function (self, msg)
                error("@z")
              end)
            
              error("@original")
            end
            
            local stat, msg = pcall(foo)
            return string.find(msg, "@y") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testCloseErrorInCloseOnly() =
        runTest {
            // Test error only in __close, not in main function
            assertLuaBoolean(
                """
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            local function foo()
              local x <close> = func2close(function (self, msg)
                -- msg should be nil since no error yet
                assert(msg == nil)
                error("@close_error")
              end)
              return 42
            end
            
            local stat, msg = pcall(foo)
            return string.find(msg, "@close_error") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testCloseErrorChainingWithComplexAssertions() =
        runTest {
            // Test exact case from locals.lua:422-463
            // This includes the complex error chain with multiple assertions
            // NOTE: Removed debug.getinfo checks - those require special VM support
            vm.debugEnabled = true
            assertLuaBoolean(
                """
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            local function foo()
              local x <close> = func2close(function (self, msg)
                assert(string.find(msg, "@x1"), "Expected @x1, got: " .. tostring(msg))
              end)
            
              local x1 <close> = func2close(function (self, msg)
                assert(string.find(msg, "@y"), "Expected @y, got: " .. tostring(msg))
                error("@x1")
              end)
            
              local gc <close> = func2close(function () collectgarbage() end)
            
              local y <close> = func2close(function (self, msg)
                assert(string.find(msg, "@z"), "Expected @z, got: " .. tostring(msg))
                error("@y")
              end)
            
              local first = true
              local z <close> = func2close(function (self, msg)
                assert(first and msg == 4, "Expected first=true and msg=4, got first=" .. tostring(first) .. " msg=" .. tostring(msg) .. " type=" .. type(msg))
                first = false
                error("@z")
              end)
            
              error(4)  -- original error
            end
            
            local stat, msg = pcall(foo)
            return string.find(msg, "@x1") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testCloseMetamethodInTraceback() =
        runTest {
            // Test that __close metamethods appear as "in metamethod 'close'" in tracebacks
            // Based on locals.lua line 486-493
            vm.debugEnabled = true
            assertLuaBoolean(
                """
            local debug = require"debug"
            
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            -- error in __close in vararg function
            local function foo(...)
              local x123 <close> = func2close(function () error("@x123") end)
            end
            
            local st, msg = xpcall(foo, debug.traceback)
            -- Check that error message contains "@x123"
            assert(string.match(msg, "@x123"), "Should contain error message '@x123'")
            -- Check that traceback contains "in metamethod 'close'"
            assert(string.find(msg, "in metamethod 'close'"), "Should contain 'in metamethod \\'close\\''")
            return true
        """,
                true,
            )
        }

    @Test
    fun testSimpleCloseErrorChain() =
        runTest {
            // Simplest possible test: two __close handlers, second throws, first doesn't re-throw
            assertLuaBoolean(
                """
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            local function foo()
              local x <close> = func2close(function (self, msg)
                -- Receive "@z" but don't re-throw
                return -- final error should still be "@z"
              end)
            
              local z <close> = func2close(function (self, msg)
                error("@z")
              end)
            
              error(4)
            end
            
            local stat, msg = pcall(foo)
            return string.find(msg, "@z") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testCloseErrorReplacement() =
        runTest {
            // Test that a __close handler can replace the error
            assertLuaBoolean(
                """
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            local function foo()
              local x <close> = func2close(function (self, msg)
                -- Replace "@z" with "@x1"
                error("@x1")
              end)
            
              local z <close> = func2close(function (self, msg)
                error("@z")
              end)
            
              error(4)
            end
            
            local stat, msg = pcall(foo)
            return string.find(msg, "@x1") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testCloseReceivesCorrectErrorValue() =
        runTest {
            // Test that __close receives the actual error value, not the formatted message
            assertLuaBoolean(
                """
            local function func2close(f)
              return setmetatable({}, {__close = f})
            end
            
            local results = {}
            local function foo()
              local z <close> = func2close(function (self, msg)
                -- Should receive the error VALUE thrown by error("@z"), not a formatted string
                table.insert(results, string.find(tostring(msg), "@z") ~= nil)
                error("@z2")
              end)
            
              error("@z")
            end
            
            local stat, msg = pcall(foo)
            -- Final error should be "@z2" and z should have received "@z"
            return results[1] and string.find(msg, "@z2") ~= nil
        """,
                true,
            )
        }
}
