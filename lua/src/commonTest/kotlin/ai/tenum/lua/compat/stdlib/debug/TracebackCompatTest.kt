package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for debug.traceback - stack trace generation.
 *
 * Covers:
 * - Basic traceback generation
 * - Traceback with custom messages
 * - Traceback from specific stack levels
 */
class TracebackCompatTest : LuaCompatTestBase() {
    @Test
    fun testTraceback() =
        runTest {
            assertLuaString(
                """
            local function f()
                return debug.traceback()
            end
            local tb = f()
            return type(tb)
        """,
                "string",
            )
        }

    @Test
    fun testTracebackWithMessage() =
        runTest {
            assertLuaBoolean(
                """
            local tb = debug.traceback("error message")
            return string.find(tb, "error message") ~= nil
        """,
                true,
            )
        }

    @Test
    fun testTracebackLevel() =
        runTest {
            // Traceback from specific level
            execute(
                """
            local function g()
                return debug.traceback("msg", 2)
            end
            local function f()
                return g()
            end
            local tb = f()
            assert(type(tb) == "string")
        """,
            )
        }

    @Test
    fun testTracebackReturnsNonStringArgumentUnchanged() =
        runTest {
            // When first argument is not a string, debug.traceback should return it unchanged
            // This is the Lua 5.4 behavior tested in db.lua:695-696
            execute(
                """
            assert(debug.traceback(print) == print)
            assert(debug.traceback(print, 4) == print)
            
            -- Test with other non-string types
            local t = {}
            assert(debug.traceback(t) == t)
            assert(debug.traceback(42) == 42)
            assert(debug.traceback(true) == true)
            assert(debug.traceback(nil) == nil)
        """,
            )
        }

    @Test
    fun testTracebackLevelZeroIncludesTracebackFunction() =
        runTest {
            // When level is 0, the traceback should include debug.traceback itself
            // This is tested in db.lua:700
            execute(
                """
            local tb = debug.traceback("test", 0)
            -- Should include 'debug.traceback' in the output
            assert(string.find(tb, "'debug.traceback'"), "Level 0 should include debug.traceback function")
            
            -- Compare with level 1 which should not include it
            local tb1 = debug.traceback("test", 1)
            assert(not string.find(tb1, "'debug.traceback'"), "Level 1 should not include debug.traceback function")
        """,
            )
        }

    @Test
    fun testTracebackShowsNativeFunctionNames() =
        runTest {
            // Test from db.lua:704-707 - C-function names should appear in traceback
            // When pcall calls debug.traceback, the stack should show "pcall"
            execute(
                """
            do
                local st, msg = (function () return pcall end)()(debug.traceback)
                assert(st == true and string.find(msg, "pcall"), "traceback should contain 'pcall' function name")
            end
        """,
            )
        }

    @Test
    fun testTracebackWithCoroutine() =
        runTest {
            // Test from db.lua:734-756 - debug.traceback(coroutine, message, level)
            // Should return traceback of the specified coroutine with full call stack
            execute(
                """
            local function f(n)
                if n > 0 then 
                    f(n-1)
                else 
                    coroutine.yield()
                end
            end
            
            local co = coroutine.create(f)
            coroutine.resume(co, 3)
            
            -- Get traceback of the coroutine - should show yield + 4 recursive calls to f
            local tb = debug.traceback(co, nil, 0)
            assert(type(tb) == "string", "traceback should be a string")
            assert(string.find(tb, "stack traceback:"), "should contain traceback header")
            
            -- Count lines in traceback (should have header + yield + 4 frames)
            local lineCount = 0
            for _ in string.gmatch(tb, "[^\n]+") do
                lineCount = lineCount + 1
            end
            assert(lineCount >= 5, "should have at least 5 lines: header + yield + 4 frames, got " .. lineCount)
            
            -- Verify traceback contains 'yield'
            assert(string.find(tb, "yield"), "traceback should contain 'yield'")
        """,
            )
        }

    @Test
    fun testTracebackWithCoroutineExactFormat() =
        runTest {
            // Test from db.lua:732-756 - Exact format check
            // This test replicates the checktraceback function from db.lua
            execute(
                """
            local undef = nil
            
            local function checktraceback (co, p, level)
              local tb = debug.traceback(co, nil, level)
              local i = 0
              for l in string.gmatch(tb, "[^\n]+\n?") do
                -- Use plain text search to avoid pattern matching issues with =(load)
                assert(i == 0 or string.find(l, p[i], 1, true), "Line " .. i .. " should contain '" .. tostring(p[i]) .. "' but got: " .. l)
                i = i+1
              end
              assert(p[i] == undef, "Expected " .. (i-1) .. " lines but pattern has more at index " .. i .. ": " .. tostring(p[i]))
            end
            
            local function f (n)
              if n > 0 then f(n-1)
              else coroutine.yield() end
            end
            
            local co = coroutine.create(f)
            coroutine.resume(co, 3)
            -- Using "load" instead of "=(load)" to avoid pattern matching issues
            checktraceback(co, {"yield", "load", "load", "load", "load"})
            checktraceback(co, {"load", "load", "load", "load"}, 1)
            checktraceback(co, {"load", "load", "load"}, 2)
            checktraceback(co, {"load"}, 4)
            checktraceback(co, {}, 40)
        """,
            )
        }

    @Test
    fun testTracebackWithCoroutineShouldShowUpvalueDescriptor() =
        runTest {
            // Test from db.lua:732-756 - verify upvalue descriptor is shown for named functions
            execute(
                """
            local function f (n)
              if n > 0 then f(n-1)
              else coroutine.yield() end
            end
            
            local co = coroutine.create(f)
            coroutine.resume(co, 3)
            
            local tb = debug.traceback(co, nil, 0)
            print("=== ACTUAL TRACEBACK ===")
            print(tb)
            print("=== END TRACEBACK ===")
            
            -- Verify traceback contains "in upvalue 'f'" for named functions
            assert(string.find(tb, "in upvalue 'f'", 1, true), "Traceback should contain 'in upvalue ''f''' for named function f, got:\n" .. tb)
        """,
            )
        }

    @Test
    fun testTracebackWithDeadCoroutineShouldPreserveStack() =
        runTest {
            // Test from db.lua:830-836 - dead coroutines should preserve their error stack
            execute(
                """
            local function f (n)
              if n > 0 then f(n-1)
              else error(0) end
            end
            
            local co = coroutine.create(f)
            local ok, msg = coroutine.resume(co, 3)
            
            -- After error, coroutine is dead but should still have traceback
            assert(coroutine.status(co) == "dead", "Coroutine should be dead after error")
            
            local tb = debug.traceback(co, nil, 0)
            print("=== DEAD COROUTINE TRACEBACK ===")
            print(tb)
            print("=== END TRACEBACK ===")
            print("Traceback length: " .. #tb)
            print("Status: " .. coroutine.status(co))
            
            -- Verify traceback is not empty and contains function references
            assert(tb ~= "stack traceback:", "Dead coroutine traceback should not be empty, got: [" .. tb .. "]")
            assert(string.find(tb, "'error'", 1, true), "Traceback should contain 'error'")
            assert(string.find(tb, "'f'", 1, true), "Traceback should contain 'f'")
        """,
            )
        }

    @Test
    fun testTracebackWithCoroutineAnonymousFunction() =
        runTest {
            // Test from db.lua:758-791 - Anonymous function traceback
            // Anonymous functions should show "in function <source:line>"
            execute(
                """
            local co = coroutine.create(function (x)
                   local a = 1
                   coroutine.yield(debug.getinfo(1, "l"))
                   coroutine.yield(debug.getinfo(1, "l").currentline)
                   return a
                 end)
            
            local _, l = coroutine.resume(co, 10)
            local a,b,c = pcall(coroutine.resume, co)
            
            -- After second resume, coroutine should be suspended at second yield
            assert(coroutine.status(co) == "suspended", "Coroutine should be suspended")
            
            local tb = debug.traceback(co, nil, 0)
            
            -- Verify traceback format matches Lua 5.4.8:
            -- The traceback should contain a frame showing the anonymous function
            -- with format "in function <source:line>"
            
            -- Note: The yield function may show as '?' instead of 'coroutine.yield'
            -- due to how native functions are named in LuaK - this is a known limitation
            -- and doesn't affect the correctness of the traceback structure
            
            -- Check for "in function <" which indicates anonymous function format
            local hasAnonymous = string.find(tb, "in function <", 1, true) ~= nil
            assert(hasAnonymous, "Traceback should contain 'in function <' for anonymous functions, got:\n" .. tb)
            
            -- Verify we have at least 2 frames (native yield + anonymous function)
            local frameCount = 0
            for _ in string.gmatch(tb, "\t") do
                frameCount = frameCount + 1
            end
            assert(frameCount >= 2, "Traceback should have at least 2 frames, got " .. frameCount)
        """,
            )
        }

    @Test
    fun testTracebackOfFinishedCoroutine() =
        runTest {
            // Test from db.lua:794 - Finished coroutines should have empty traceback
            // When a coroutine is dead, debug.traceback should return just "stack traceback:"
            execute(
                """
            local function f(n)
                if n > 0 then 
                    f(n-1)
                else 
                    coroutine.yield()
                end
            end
            
            local co = coroutine.create(f)
            coroutine.resume(co, 3)
            
            -- Resume again to finish the coroutine
            local a, b = coroutine.resume(co)
            assert(a == true, "Resume should succeed")
            assert(coroutine.status(co) == "dead", "Coroutine should be dead")
            
            -- Get traceback of finished coroutine
            local tb = debug.traceback(co, nil, 0)
            
            -- Should only contain "stack traceback:" header with no frames
            assert(type(tb) == "string", "traceback should be a string")
            assert(string.find(tb, "stack traceback:", 1, true), "should contain traceback header")
            
            -- Count lines - should be exactly 1 (just the header)
            local lineCount = 0
            for _ in string.gmatch(tb, "[^\n]+") do
                lineCount = lineCount + 1
            end
            assert(lineCount == 1, "Finished coroutine should have only header line, got " .. lineCount .. " lines: " .. tb)
        """,
            )
        }

    @Test
    fun testTracebackOfFinishedCoroutineExactDbLuaScenario() =
        runTest {
            // Exact test from db.lua:758-799 that was failing at line 794
            // This tests the specific scenario with hooks and multiple yields
            execute(
                """
            local undef = nil
            
            local function checktraceback (co, p, level)
              local tb = debug.traceback(co, nil, level)
              local i = 0
              for l in string.gmatch(tb, "[^\n]+\n?") do
                assert(i == 0 or string.find(l, p[i], 1, true), "Failed at line " .. i .. ": expected '" .. tostring(p[i]) .. "' in '" .. l .. "'")
                i = i+1
              end
              assert(p[i] == undef, "Expected " .. (i-1) .. " pattern lines but got pattern[" .. i .. "]=" .. tostring(p[i]))
            end
            
            local co = coroutine.create(function (x)
                   local a = 1
                   coroutine.yield(debug.getinfo(1, "l"))
                   coroutine.yield(debug.getinfo(1, "l").currentline)
                   return a
                 end)
            
            local tr = {}
            local foo = function (e, l) if l then table.insert(tr, l) end end
            debug.sethook(co, foo, "lcr")
            
            local _, l = coroutine.resume(co, 10)
            local a,b,c = pcall(coroutine.resume, co)
            assert(a and b, "pcall resume should succeed")
            assert(coroutine.status(co) == "suspended", "Coroutine should still be suspended")
            
            -- After second yield, should still have traceback
            checktraceback(co, {"yield", "in function <"})
            
            -- Final resume completes the coroutine
            a,b = coroutine.resume(co)
            assert(a == true, "Final resume should succeed")
            assert(coroutine.status(co) == "dead", "Coroutine should be dead")
            
            -- THIS IS THE db.lua:794 TEST - finished coroutine should have empty traceback
            checktraceback(co, {})
        """,
            )
        }

    @Test
    fun testTracebackWithCoroutineMatchesDb738() =
        runTest {
            // Test from db.lua:751-756 - traceback of yielded coroutine should show function names
            // This was failing because we were incorrectly omitting function names in coroutine tracebacks
            execute(
                """
            local function f (n)
              if n > 0 then f(n-1)
              else coroutine.yield() end
            end

            local co = coroutine.create(f)
            coroutine.resume(co, 3)
            
            -- Get traceback - should show yield + 4 recursive calls to f
            local tb = debug.traceback(co, nil, 0)
            
            -- Should contain "yield" (from coroutine.yield)
            assert(string.find(tb, "yield"), "traceback should contain 'yield'")
            
            -- Should contain function name 'f' multiple times (4 recursive calls)
            local _, count = string.gsub(tb, "'f'", "")
            assert(count >= 4, "traceback should contain 'f' at least 4 times, got " .. count)
        """,
            )
        }

    @Test
    fun testTracebackDeadCoroutineWithMultipleYieldsMatchesDb836() =
        runTest {
            // Test from db.lua:836 - dead coroutine after error should preserve full call stack
            // This tests the case where f(3) yields, f(2) yields, f(1) yields, f(0) errors
            // The dead coroutine's traceback should show ALL 4 recursive calls to f
            execute(
                """
            local function f(i)
              if i == 0 then 
                error(i) 
              else 
                coroutine.yield()
                f(i-1)
              end
            end
            
            local co = coroutine.create(function(x) f(x) end)
            
            -- Resume 4 times: f(3) yields, f(2) yields, f(1) yields, f(0) errors
            assert(coroutine.resume(co, 3))  -- Start with f(3), yields
            assert(coroutine.resume(co))      -- Resume f(3), calls f(2), yields
            assert(coroutine.resume(co))      -- Resume f(2), calls f(1), yields
            local ok, err = coroutine.resume(co)  -- Resume f(1), calls f(0), errors
            assert(not ok, "final resume should fail due to error")
            assert(coroutine.status(co) == "dead", "coroutine should be dead after error")
            
            -- Get traceback of dead coroutine - should show error + all 4 recursive f calls
            local tb = debug.traceback(co, nil, 0)
            
            -- Should contain "error" (from error function)
            assert(string.find(tb, "'error'"), "traceback should contain 'error'")
            
            -- Should contain function name 'f' 4 times (all recursive calls: f(3), f(2), f(1), f(0))
            local _, count = string.gsub(tb, "'f'", "")
            assert(count >= 4, "traceback should contain 'f' at least 4 times, got " .. count)
            
            -- Should also show the anonymous wrapper function
            assert(string.find(tb, "in function <"), "traceback should contain anonymous wrapper")
        """,
            )
        }

    @Test
    fun testTracebackSizesMatchDb920() =
        runTest {
            // Test from db.lua:920-955 - traceback sizes should be limited to 10+11+1 lines
            // This tests Lua's behavior of truncating large tracebacks
            execute(
                """
            local function countlines(s)
                return select(2, string.gsub(s, "\n", ""))
            end
            
            local function deep(lvl, n)
                if lvl == 0 then
                    return (debug.traceback("message", n))
                else
                    return (deep(lvl-1, n))
                end
            end
            
            local function checkdeep(total, start)
                local s = deep(total, start)
                local rest = string.match(s, "^message\nstack traceback:\n(.*)$")
                local cl = countlines(rest)
                -- at most 10 lines in first part, 11 in second, plus '...'
                assert(cl <= 10 + 11 + 1, "traceback too long: " .. cl .. " lines")
                local brk = string.find(rest, "%.%.%.\t%(skip")
                if brk then   -- does message have '...'?
                    local rest1 = string.sub(rest, 1, brk)
                    local rest2 = string.sub(rest, brk, #rest)
                    assert(countlines(rest1) == 10 and countlines(rest2) == 11, "truncated traceback format incorrect")
                else
                    assert(cl == total - start + 2, "expected " .. (total - start + 2) .. " lines, got " .. cl)
                end
            end
            
            -- Test small depths (no truncation expected)
            for d = 1, 11 do
                for l = 1, d do
                    coroutine.wrap(checkdeep)(d, l)
                end
            end
        """,
            )
        }

    @Test
    fun testTracebackCoroutineWrapFrameCount() =
        runTest {
            // Minimal reproduction of db.lua:946 assertion failure
            // When debug.traceback is called from within coroutine.wrap,
            // the frame count must match Lua 5.4.8 exactly
            execute(
                """
            local function countlines(s)
                return select(2, string.gsub(s, "\n", ""))
            end
            
            local function deep(lvl, n)
                if lvl == 0 then
                    return (debug.traceback("message", n))
                else
                    return (deep(lvl-1, n))
                end
            end
            
            local function checkdeep(total, start)
                local s = deep(total, start)
                local rest = string.match(s, "^message\nstack traceback:\n(.*)$")
                local cl = countlines(rest)
                -- Formula from db.lua:946 - when no tail call collapsing occurs
                assert(cl == total - start + 2, 
                    string.format("Expected %d newlines, got %d. total=%d start=%d\nTraceback:\n%s", 
                        total - start + 2, cl, total, start, s))
            end
            
            -- Test case from db.lua:950-953
            -- total=1, start=1 should give 1-1+2=2 newlines
            coroutine.wrap(checkdeep)(1, 1)
            
            -- More test cases
            coroutine.wrap(checkdeep)(3, 1)  -- should give 3-1+2=4 newlines
            coroutine.wrap(checkdeep)(5, 2)  -- should give 5-2+2=5 newlines
        """,
            )
        }

    @Test
    fun testTracebackTruncation() =
        runTest {
            // Test traceback truncation for deep call stacks
            // Lua 5.4.8 shows first 10 frames + "...\t(skipping N levels)" + last 11 frames
            execute(
                """
            local function countlines(s)
                return select(2, string.gsub(s, "\n", ""))
            end
            
            local function deep(lvl, n)
                if lvl == 0 then
                    return (debug.traceback("message", n))
                else
                    return (deep(lvl-1, n))
                end
            end
            
            local function checkdeep(total, start)
                local s = deep(total, start)
                local rest = string.match(s, "^message\nstack traceback:\n(.*)$")
                local cl = countlines(rest)
                -- at most 10 lines in first part, 11 in second, plus '...'
                assert(cl <= 10 + 11 + 1, 
                    string.format("Too many lines: %d (max 22). total=%d start=%d", cl, total, start))
                local brk = string.find(rest, "%.%.%.\t%(skip")
                if brk then   -- does message have '...'?
                    local rest1 = string.sub(rest, 1, brk)
                    local rest2 = string.sub(rest, brk, #rest)
                    assert(countlines(rest1) == 10 and countlines(rest2) == 11,
                        string.format("Expected 10+11 lines with truncation, got %d+%d", 
                            countlines(rest1), countlines(rest2)))
                else
                    assert(cl == total - start + 2,
                        string.format("Expected %d newlines, got %d. total=%d start=%d", 
                            total - start + 2, cl, total, start))
                end
            end
            
            -- Test cases from db.lua:950-953
            -- These depths require truncation
            coroutine.wrap(checkdeep)(31, 1)   -- should truncate
            coroutine.wrap(checkdeep)(31, 10)  -- should truncate
            coroutine.wrap(checkdeep)(41, 5)   -- should truncate
            coroutine.wrap(checkdeep)(51, 20)  -- should truncate
        """,
            )
        }
}
