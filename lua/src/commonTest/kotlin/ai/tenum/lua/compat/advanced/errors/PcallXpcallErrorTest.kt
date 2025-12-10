package ai.tenum.lua.compat.advanced.errors

// CPD-OFF: test file with intentional test setup duplications

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for pcall, xpcall, error propagation, and stack traces.
 *
 * Coverage:
 * - error() function with different levels
 * - pcall success and failure cases
 * - xpcall with error handlers
 * - Error propagation through call stacks
 * - Stack trace formatting
 * - debug.traceback functionality
 */
class PcallXpcallErrorTest : LuaCompatTestBase() {
    @BeforeTest
    fun setup() {
        vm.execute(
            """
            function doit (s)
              local f, msg = load(s)
              if not f then return msg end
              local cond, msg = pcall(f)
              return (not cond) and msg
            end
            
            function checkmessage (prog, msg)
              local m = doit(prog)
              print("Checking message. Program: "..prog.." Message: "..tostring(m).." Expected substring: "..msg)
              assert(string.find(m, msg, 1, true), "Expected '" .. msg .. "' in error message, got: " .. tostring(m))
            end
            """.trimIndent(),
            "BeforeTest",
        )
    }

    @Test
    fun testError() =
        runTest {
            // error() should raise an exception that pcall can catch
            assertLuaBoolean(
                """
            local ok, err = pcall(function()
                error("test error")
            end)
            return not ok
        """,
                true,
            )
        }

    @Test
    fun testErrorMessage() =
        runTest {
            // Error message should be preserved
            assertLuaBoolean(
                """
            local ok, err = pcall(function()
                error("my custom error")
            end)
            return string.find(tostring(err), "my custom error") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testErrorLevel0ReturnsRawMessage() =
        runTest {
            // From errors.lua line 46: error('hi', 0) should return just 'hi'
            // Level 0 means no location information should be added
            val result = execute("""return doit("error('hi', 0)")""")
            // Result should be exactly "hi" with no location information
            assertLuaString(result, "hi")
        }

    @Test
    fun testErrorLevel0NoStackTrace() =
        runTest {
            // Verify that level 0 error has no stack trace or location prefix
            val result =
                execute(
                    """
                local ok, err = pcall(function()
                    error("pure message", 0)
                end)
                return err
            """,
                )

            val errorMsg = (result as ai.tenum.lua.runtime.LuaString).value
            // Should be exactly "pure message" - no source location, no stack trace
            assertLuaString(result, "pure message")
        }

    @Test
    fun testErrorNoArguments() =
        runTest {
            // From errors.lua line 49: error() with no arguments should return nil
            val result =
                execute(
                    """
                local function doit (s)
                  local f, msg = load(s)
                  if not f then return msg end
                  local cond, msg = pcall(f)
                  return (not cond) and msg
                end
                
                return doit("error()")
            """,
                )

            // Result should be nil
            assertTrue(
                result is ai.tenum.lua.runtime.LuaNil,
                "Expected nil but got: $result (${result::class.simpleName})",
            )
        }

    @Test
    fun testErrorLevel1HasLocation() =
        runTest {
            // For comparison, level 1 (default) should have location info
            val result =
                execute(
                    """
                local ok, err = pcall(function()
                    error("with location", 1)
                end)
                return err
            """,
                )

            val errorMsg = (result as ai.tenum.lua.runtime.LuaString).value
            // Should contain location prefix, not just the raw message
            assertTrue(
                errorMsg != "with location",
                "Expected location prefix in error message but got: $errorMsg",
            )
        }

    @Test
    fun testErrorLevelParameter() =
        runTest {
            // Test that error level parameter correctly determines which stack frame's line number is reported
            // This is the pattern from errors.lua:440-447
            execute(
                """
                local function lineerror(s, expectedLine)
                    local ok, msg = pcall(load(s))
                    if expectedLine == false then
                        -- level=0 should have no line number
                        assert(not string.match(msg, ":(%d+):"), "Expected no line number for level=0, got: " .. tostring(msg))
                    else
                        local line = tonumber(string.match(msg, ":(%d+):"))
                        assert(line == expectedLine, "Expected line " .. expectedLine .. ", got: " .. tostring(line) .. " in message: " .. tostring(msg))
                    end
                end
                
                local p = [[
  function g() f() end
  function f(x) error('a', XX) end
g()
]]
                XX=3; lineerror(p, 3)
                XX=0; lineerror(p, false)
                XX=1; lineerror(p, 2)
                XX=2; lineerror(p, 1)
            """,
            )
        }

    @Test
    fun testPcallSuccess() =
        runTest {
            assertLuaNumber(
                """
            local ok, result = pcall(function() return 42 end)
            if ok then
                return result
            else
                return 0
            end
        """,
                42.0,
            )
        }

    @Test
    fun testPcallFailure() =
        runTest {
            assertLuaBoolean(
                """
            local ok, err = pcall(function()
                error("failure")
            end)
            return not ok and err ~= nil
        """,
                true,
            )
        }

    @Test
    fun testPcallWithArguments() =
        runTest {
            assertLuaNumber(
                """
            local ok, result = pcall(function(a, b) return a + b end, 10, 20)
            return result
        """,
                30.0,
            )
        }

    @Test
    fun testXpcallWithHandler() =
        runTest {
            assertLuaBoolean(
                """
            local handler_called = false
            local function handler(err)
                handler_called = true
                return "handled: " .. tostring(err)
            end
            local ok, result = xpcall(function()
                error("test")
            end, handler)
            return handler_called and not ok
        """,
                true,
            )
        }

    @Test
    fun testXpcallHandlerReceivesError() =
        runTest {
            vm.debugEnabled = true
            assertLuaBoolean(
                """
            local function handler(err)
                print("In handler, error: " .. tostring(err))
                return string.find(tostring(err), "my error") ~= nil
            end
            local ok, result = xpcall(function()
                error("my error")
            end, handler)
            return result == true
        """,
                true,
            )
        }

    @Test
    fun testErrorPropagation() =
        runTest {
            assertLuaBoolean(
                """
            local function level3()
                error("deep error")
            end
            local function level2()
                level3()
            end
            local function level1()
                level2()
            end
            local ok, err = pcall(level1)
            return not ok and string.find(tostring(err), "deep error") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testErrorInMetamethod() =
        runTest {
            // Error in metamethod should propagate correctly
            assertLuaBoolean(
                """
            local t1 = {}
            local t2 = {}
            setmetatable(t1, {
                __add = function(a, b)
                    error("can't add these")
                end
            })
            local ok, err = pcall(function()
                return t1 + t2
            end)
            return not ok and string.find(tostring(err), "can't add") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testStackTrace() =
        runTest {
            // Stack traces should be available in error messages
            execute(
                """
            local function level3()
                error("error at level 3")
            end
            local function level2()
                level3()
            end
            local function level1()
                level2()
            end
            local ok, err = pcall(level1)
            assert(not ok)
            assert(type(err) == "string")
            -- Stack trace is in the error object
        """,
            )
        }

    @Test
    fun testStackTraceFormat() =
        runTest {
            // Verify stack trace contains function call information
            execute(
                """
            local function deepFunction()
                error("deep error")
            end
            local function middleFunction()
                deepFunction()
            end
            local function topFunction()
                middleFunction()
            end
            
            local ok, err = pcall(topFunction)
            assert(not ok, "pcall should fail")
            
            -- Error should be a string with our message
            local errStr = tostring(err)
            assert(type(errStr) == "string", "error should convert to string")
            assert(string.find(errStr, "deep error") ~= nil, "error message should be preserved")
        """,
            )
        }

    @Test
    fun testDebugTracebackFormat() =
        runTest {
            // debug.traceback should produce formatted stack trace
            execute(
                """
            local function checkTraceback()
                local tb = debug.traceback()
                assert(type(tb) == "string", "traceback should be a string")
                assert(string.find(tb, "stack traceback:") ~= nil, "should contain 'stack traceback:'")
                return tb
            end
            
            local function caller()
                return checkTraceback()
            end
            
            local trace = caller()
            -- Verify it's a non-empty string with traceback marker
            assert(#trace > 0, "traceback should not be empty")
        """,
            )
        }

    @Test
    fun testDebugTracebackWithMessage() =
        runTest {
            // debug.traceback with custom message
            execute(
                """
            local tb = debug.traceback("Custom error message")
            assert(type(tb) == "string")
            assert(string.find(tb, "Custom error message") ~= nil, "should include custom message")
            assert(string.find(tb, "stack traceback:") ~= nil, "should include traceback header")
        """,
            )
        }

    @Test
    fun testDebugTracebackAtLevel() =
        runTest {
            // debug.traceback from specific call stack level
            execute(
                """
            local function level3()
                return debug.traceback("error", 1)
            end
            local function level2()
                return level3()
            end
            local function level1()
                return level2()
            end
            
            local tb = level1()
            assert(type(tb) == "string")
            assert(string.find(tb, "error") ~= nil)
            assert(string.find(tb, "stack traceback:") ~= nil)
        """,
            )
        }

    @Test
    fun testErrorMessagePreservation() =
        runTest {
            // Error messages should be preserved through call stack
            assertLuaBoolean(
                """
            local function throwError()
                error("Specific error: code 42")
            end
            local function wrapper1()
                throwError()
            end
            local function wrapper2()
                wrapper1()
            end
            
            local ok, err = pcall(wrapper2)
            -- Original error message should be in the error
            return string.find(tostring(err), "Specific error: code 42") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testXpcallStackTrace() =
        runTest {
            // xpcall error handler should receive error with stack info
            vm.debugEnabled = true
            execute(
                """
            local handlerGotError = false
            local function errorHandler(err)
                handlerGotError = true
                -- Handler receives the error message
                assert(type(err) == "string" or type(err) == "table", "error should be string or table")
                return "handled"
            end
            
            local function failingFunction()
                error("intentional failure")
            end
            
            local ok, result = xpcall(failingFunction, errorHandler)
            assert(not ok, "xpcall should return false on error")
            assert(handlerGotError, "error handler should have been called")
        """,
            )
        }

    @Test
    fun testPcallErrorMessageWithoutStackTrace() =
        runTest {
            // pcall should return ONLY the error message, not the full stack trace
            // This is critical for Lua 5.4 compatibility
            execute(
                """
                local ok, err = pcall(function()
                    error("test error message")
                end)
                
                -- Error message should be present
                assert(string.find(err, "test error message"), "Expected error message in: " .. tostring(err))
                
                -- Stack trace should NOT be in the error value from pcall
                assert(not string.find(err, "stack traceback"), "pcall error should not include stack trace, got: " .. tostring(err))
            """,
            )
        }

    @Test
    fun testPcallErrorMessageFormatting() =
        runTest {
            // pcall error messages should include source location but not stack trace
            execute(
                """
                local function doit(s)
                    local f, msg = load(s)
                    if not f then return msg end
                    local cond, msg = pcall(f)
                    return (not cond) and msg
                end
                
                local err = doit("error('test')")
                
                -- Should have source location prefix
                assert(string.find(err, ":"), "Error should have source location: " .. tostring(err))
                
                -- Should have the error message
                assert(string.find(err, "test", 1, true), "Error should contain 'test': " .. tostring(err))
                
                -- Should NOT have newlines (no stack trace)
                assert(not string.find(err, "\n"), "Error should be single line, got: " .. tostring(err))
            """,
            )
        }

    @Test
    fun testDebugInfoNotInErrorMessages() =
        runTest {
            // When debugEnabled is false (default), error messages should not include
            // VM debug information like pc, instr, registers
            vm.debugEnabled = false

            execute(
                """
                local ok, err = pcall(function()
                    local x = {}
                    return x + 1  -- Type error
                end)
                
                -- Should have the error
                assert(not ok, "Should have failed")
                assert(err, "Should have error message")
                
                -- Should NOT have debug info
                assert(not string.find(err, "pc=", 1, true), "Should not have pc= debug info")
                assert(not string.find(err, "instr=", 1, true), "Should not have instr= debug info")
                assert(not string.find(err, "registers=", 1, true), "Should not have registers= debug info")
            """,
            )
        }

    @Test
    fun testXpcallWithArgumentsAndErrorHandler() =
        runTest {
            // From errors.lua lines 602-605: xpcall with arguments
            // Tests that xpcall passes additional arguments to the function
            // and that error handler receives error and returns custom value
            execute(
                """
                -- Test 1: Successful xpcall with arguments (line 602-603)
                local a, b, c = xpcall(string.find, error, "alo", "al")
                assert(a == true, "xpcall should succeed, got: " .. tostring(a))
                assert(b == 1, "string.find should return 1, got: " .. tostring(b))
                assert(c == 2, "string.find should return 2, got: " .. tostring(c))
                
                -- Test 2: Failed xpcall with error handler returning table (line 604-605)
                -- string.find expects string as first arg, but gets boolean (true)
                -- Error handler returns a table {}, which should be preserved
                a, b, c = xpcall(string.find, function (x) return {} end, true, "al")
                assert(not a, "xpcall should fail when string.find gets boolean, got a=" .. tostring(a))
                assert(type(b) == "table", "error handler should return table, got type(b)=" .. type(b))
                assert(c == nil, "third return value should be nil, got: " .. tostring(c))
            """,
            )
        }

    @Test
    fun testNonStringErrorMessages() =
        runTest {
            // From errors.lua line 568-591: Non-string error messages
            // When error() or assert() is called with a non-string value (table, nil, etc.),
            // pcall/xpcall should return the EXACT SAME object, not a string conversion
            execute(
                """
                -- Test 1: error with table value
                local t = {}
                local res, msg = pcall(function () error(t) end)
                assert(not res and msg == t, "error(table) should return the exact table")
                
                -- Test 2: error with nil value
                res, msg = pcall(function () error(nil) end)
                assert(not res and msg == nil, "error(nil) should return nil")
                
                -- Test 3: xpcall with table error and handler
                local function f() error{msg='x'} end
                res, msg = xpcall(f, function (r) return {msg=r.msg..'y'} end)
                assert(msg.msg == 'xy', "xpcall handler should receive and transform table")
                
                -- Test 4: assert with extra arguments (non-string)
                res, msg = pcall(assert, false, "X", t)
                assert(not res and msg == "X", "assert with string message should return string")
                
                -- Test 5: assert with table message (errors.lua line 591)
                res, msg = pcall(assert, false, t)
                assert(not res and msg == t, "assert with table message should return exact table")
                
                -- Test 6: assert with nil message
                res, msg = pcall(assert, nil, nil)
                assert(not res and msg == nil, "assert with nil message should return nil")
            """,
            )
        }

    @Test
    fun testDebugGetinfoWithLineInfoInPcall() =
        runTest {
            // Test from errors.lua:587
            // The critical fix: debug.getinfo(1, "l") must return a table (not nil)
            // even when called at the top level or in contexts where the function is null
            execute(
                """
                -- This is the core fix: debug.getinfo must return a table, not nil
                local info = debug.getinfo(1, "l")
                assert(type(info) == "table", "debug.getinfo should return a table, got: " .. type(info))
                assert(type(info.currentline) == "number", "info.currentline should be a number")
                
                -- Verify it works inside pcall too
                local res, msg = pcall(function ()
                    local info2 = debug.getinfo(1, "l")
                    assert(type(info2) == "table", "debug.getinfo in pcall should return table")
                end)
                assert(res, "pcall should succeed, got: " .. tostring(msg))
            """,
            )
        }

    @Test
    fun testErrorMessageIncludesSourceNameInNestedFunction() =
        runTest {
            // Test for source name propagation to nested functions
            // Bug: nested functions had source="" causing error messages to show ":LINE:" instead of "filename:LINE:"
            // This reproduces the exact pattern from errors.lua:585-587
            execute(
                """
                -- Test that error messages in nested functions include the source filename
                local res, msg = pcall(function () assert(false) end)
                
                -- Error message should include source name, not just ":LINE:"
                assert(not res, "pcall should fail")
                assert(type(msg) == "string", "error message should be a string")
                
                -- The critical fix: should match pattern "filename:LINE: message"
                -- NOT just ":LINE: message" (which was the bug)
                local line = string.match(msg, "(%w+%.%w+):(%d+):")
                if not line then
                    -- If no filename.ext pattern, try to match any non-colon followed by colon and number
                    line = string.match(msg, "([^:]+):(%d+):")
                end
                
                assert(line ~= nil, "Error message should include source name before line number, got: " .. tostring(msg))
                
                -- Verify the pattern is NOT just ":NUMBER:" at the start
                assert(not string.match(msg, "^:(%d+):"), "Error should not start with ':LINE:', got: " .. tostring(msg))
            """,
            )
        }

    @Test
    fun testAssertWithoutArguments() =
        runTest {
            // Test from errors.lua:596-598 - assert() without arguments should error with "value expected"
            execute(
                """
                local res, msg = pcall(assert)
                assert(not res, "assert() without arguments should fail")
                assert(string.find(msg, "value expected"), "Error should contain 'value expected', got: " .. tostring(msg))
            """,
            )
        }

    @Test
    fun testXpcallErrorInErrorHandler() =
        runTest {
            // Test from calls.lua:160-167 - xpcall with error handler that itself causes error
            // When error handler fails (including stack overflow), should return "error in error handling"
            execute(
                """
                local function loop()
                    assert(pcall(loop))
                end
                
                local err, msg = xpcall(loop, loop)
                assert(not err, "xpcall should return false when error handler fails")
                assert(string.find(msg, "error"), "Error message should contain 'error', got: " .. tostring(msg))
            """,
            )
        }

    @Test
    fun testMethodTailCallOptimization() =
        runTest {
            // Test method tail call optimization - deep recursion should not overflow with TCO
            assertLuaNumber(
                """
                local obj = {}
                function obj:deep(n)
                    if n <= 0 then
                        return 101
                    else
                        return self:deep(n - 1)
                    end
                end
                
                -- This should use tail call optimization and not overflow
                return obj:deep(10000)
            """,
                101.0,
            )
        }

    @Test
    fun testFunctionTailCallOptimization() =
        runTest {
            // Test function tail call optimization - deep recursion should not overflow with TCO
            assertLuaNumber(
                """
                local function deep(n)
                    if n <= 0 then
                        return 42
                    else
                        return deep(n - 1)
                    end
                end
                
                -- This should use tail call optimization and not overflow
                return deep(10000)
            """,
                42.0,
            )
        }

    @Test
    fun testCallMetamethodBasic() =
        runTest {
            // Test from calls.lua:134-136 - basic __call metamethod
            execute(
                """
                local function foo(a, b, c)
                    return a, b, c
                end
                
                local t = setmetatable({}, {__call = foo})
                local function foo2(x) return t(10, x) end
                local a, b, c = foo2(100)
                assert(a == t and b == 10 and c == 100, "Expected t, 10, 100 but got " .. tostring(a) .. ", " .. tostring(b) .. ", " .. tostring(c))
            """,
            )
        }

    @Test
    fun testCallMetamethodChained() =
        runTest {
            // Test from calls.lua:173-187 - chained __call metamethods with tail call optimization
            // This creates 100 chained tables with __call, should work via TCO
            assertLuaNumber(
                """
                local n = 10000   -- depth
                
                local function foo()
                    if n == 0 then return 1023
                    else n = n - 1; return foo()
                    end
                end
                
                -- build a chain of __call metamethods ending in function 'foo'
                for i = 1, 100 do
                    foo = setmetatable({}, {__call = foo})
                end
                
                -- call the first one as a tail call (should use TCO through the chain)
                return (function() return foo() end)()
            """,
                1023.0,
            )
        }
}
