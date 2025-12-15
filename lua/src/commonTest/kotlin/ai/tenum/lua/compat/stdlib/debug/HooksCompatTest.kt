package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Tests for debug.sethook and debug.gethook - execution hooks.
 *
 * Covers:
 * - Line hooks ('l')
 * - Call hooks ('c')
 * - Return hooks ('r')
 * - Hook state retrieval
 * - Hook disabling
 */
class HooksCompatTest : LuaCompatTestBase() {
    @Test
    fun testGetHookEmpty() =
        runTest {
            assertLuaNil("return debug.gethook()")
        }

    @Test
    fun testSetHookLine() =
        runTest {
            vm.debugEnabled = true
            execute(
                """
            local count = 0
            local function hook(event)
                print("Line hook called, event: " .. tostring(event))
                count = count + 1
            end
            debug.sethook(hook, "l")
            print("Hook set, about to execute lines")
            local x = 1
            local y = 2
            print("After lines, count = " .. tostring(count))
            debug.sethook()  -- disable hook
            assert(count > 0, "count was " .. tostring(count) .. ", expected > 0")
        """,
            )
        }

    @Test
    fun testSetHookCall() =
        runTest {
            vm.debugEnabled = true
            execute(
                """
            local count = 0
            local function hook(event)
                print("Hook called with event: " .. tostring(event))
                if event == "call" then
                    count = count + 1
                    print("Count incremented to: " .. tostring(count))
                end
            end
            local function f() end
            debug.sethook(hook, "c")
            print("About to call f() twice")
            f()
            f()
            print("After calls, count = " .. tostring(count))
            debug.sethook()  -- disable hook
            -- Native functions also trigger call hooks in Lua 5.4
            -- Count includes: print, f, f, print, tostring, debug.sethook = 6
            assert(count == 6, "count was " .. tostring(count) .. ", expected 6")
        """,
            )
        }

    @Test
    fun testSetHookCallForNativeFunctions() =
        runTest {
            vm.debugEnabled = true
            execute(
                """
            local count = 0
            local function hook(event)
                if event == "call" then
                    count = count + 1
                end
            end
            debug.sethook(hook, "c")
            -- Native function calls should trigger hooks
            assert(true)  -- native function
            local x = tostring(42)  -- native function
            debug.sethook()  -- disable hook
            -- Should have at least 2 calls (assert + tostring)
            assert(count >= 2, "count was " .. tostring(count) .. ", expected >= 2")
        """,
            )
        }

    @Test
    fun testSetHookReturn() =
        runTest {
            vm.debugEnabled = true
            execute(
                """
            local count = 0
            local function hook(event)
                print("Hook called with event: " .. tostring(event))
                if event == "return" then
                    count = count + 1
                    print("Count incremented to: " .. tostring(count))
                end
            end
            local function f() return 1 end
            debug.sethook(hook, "r")
            print("About to call f()")
            f()
            print("After f(), count = " .. tostring(count))
            debug.sethook()  -- disable hook
            assert(count >= 1, "count was " .. tostring(count) .. ", expected >= 1")
        """,
            )
        }

    @Test
    fun testSetHookLineTracking() =
        runTest {
            // Test from db.lua:19-28 and 127-132
            // This tests that line hooks fire on the correct lines
            execute(
                """
                local function test (s, l, p)
                  collectgarbage()   -- avoid gc during trace
                  local function f (event, line)
                    assert(event == 'line')
                    local expected = table.remove(l, 1)
                    print("Hook fired: expected=" .. tostring(expected) .. ", actual=" .. tostring(line))
                    if p then print(expected, line) end
                    assert(expected == line, "wrong trace!! expected " .. tostring(expected) .. ", got " .. tostring(line))
                  end
                  debug.sethook(f,"l"); load(s)(); debug.sethook()
                  if #l > 0 then
                    print("Remaining lines not visited:")
                    for i=1,#l do
                      print("  " .. tostring(l[i]))
                    end
                  end
                  assert(#l == 0, "not all expected lines were visited, remaining: " .. #l)
                end
                
                test([[if
math.sin(1)
then
  a=1
else
  a=2
end
]], {2,3,4,7})
            """,
            )
        }

    @Test
    fun testGetHook() =
        runTest {
            assertLuaBoolean(
                """
            local function hook() end
            debug.sethook(hook, "l")
            local h, mask = debug.gethook()
            debug.sethook()  -- disable
            return h == hook
        """,
                true,
            )
        }

    @Test
    fun testLineHookFunctionDefinitionVisitedTwice() =
        runTest {
            // Test from db.lua:134 - function definition line should be visited twice:
            // 1. When function is defined
            // 2. When function is called (CALL hook + LINE hook for function body)
            execute(
                """
            local function test (s, l, p)
              collectgarbage()
              local function f (event, line)
                assert(event == 'line')
                local expected = table.remove(l, 1)
                if p then print("expected=" .. tostring(expected) .. ", actual=" .. tostring(line)) end
                assert(expected == line, "wrong trace!! expected " .. tostring(expected) .. ", got " .. tostring(line))
              end
              debug.sethook(f,"l"); load(s)(); debug.sethook()
              assert(#l == 0, "not all lines visited, remaining: " .. #l)
            end
            
            test([[
local function foo()
end
foo()
A = 1
A = 2
A = 3
]], {2, 3, 2, 4, 5, 6}, true)
        """,
            )
        }

    @Test
    fun testLineHookWhileLoopTracking() =
        runTest {
            // Test from db.lua:164 - while loop should track lines correctly
            // Expected: {1,2,3,4,3,4,3,4,3,5}
            // Line 1: local a
            // Line 2: a=1
            // Line 3: while a<=3 do (condition check - 4 times: a=1,2,3,4)
            // Line 4: a=a+1 (body - 3 times when a=1,2,3)
            // Line 5: end
            execute(
                """
            local function test (s, l, p)
              collectgarbage()
              local function f (event, line)
                assert(event == 'line')
                local expected = table.remove(l, 1)
                if p then print("expected=" .. tostring(expected) .. ", actual=" .. tostring(line)) end
                assert(expected == line, "wrong trace!! expected " .. tostring(expected) .. ", got " .. tostring(line))
              end
              debug.sethook(f,"l"); load(s)(); debug.sethook()
              if #l > 0 then
                print("Remaining lines not visited:")
                for i=1,#l do
                  print("  " .. tostring(l[i]))
                end
              end
              assert(#l == 0, "not all lines visited, remaining: " .. #l)
            end
            
            test([[local a
a=1
while a<=3 do
  a=a+1
end
]], {1,2,3,4,3,4,3,4,3,5}, true)
        """,
            )
        }

    @Test
    fun testLineHookForLoopTracking() =
        runTest {
            // Test from db.lua:178 - numeric for loop should track lines correctly
            // Expected: {1,2,1,2,1,2,1,3}
            // Line 1: for i=1,3 do (loop header - 4 times: before i=1,2,3 and exit when i=4)
            // Line 2: a=i (body - 3 times when i=1,2,3)
            // Line 3: end
            execute(
                """
            local function test (s, l, p)
              collectgarbage()
              local function f (event, line)
                assert(event == 'line')
                local expected = table.remove(l, 1)
                if p then print("expected=" .. tostring(expected) .. ", actual=" .. tostring(line)) end
                assert(expected == line, "wrong trace!! expected " .. tostring(expected) .. ", got " .. tostring(line))
              end
              debug.sethook(f,"l"); load(s)(); debug.sethook()
              if #l > 0 then
                print("Remaining lines not visited:")
                for i=1,#l do
                  print("  " .. tostring(l[i]))
                end
              end
              assert(#l == 0, "not all lines visited, remaining: " .. #l)
            end
            
            test([[for i=1,3 do
  a=i
end
]], {1,2,1,2,1,2,1,3}, true)
        """,
            )
        }

    @Test
    fun testLineHookSingleLineForLoop() =
        runTest {
            // Test from db.lua:188 - single-line for loop should track line 1 four times
            // Expected: {1,1,1,1}
            // Line 1: for i=1,4 do a=1 end (loop iterations when i=1,2,3,4)
            execute(
                """
            local lines = {}
            local function test (s, l, p)
              collectgarbage()
              local function f (event, line)
                assert(event == 'line')
                table.insert(lines, line)
                local expected = table.remove(l, 1)
                if p then print("expected=" .. tostring(expected) .. ", actual=" .. tostring(line)) end
                if expected ~= line then
                  print("WRONG TRACE!! expected " .. tostring(expected) .. ", got " .. tostring(line))
                  print("All lines so far:")
                  for i=1,#lines do print("  " .. tostring(lines[i])) end
                  error("wrong trace!!")
                end
              end
              debug.sethook(f,"l"); load(s)(); debug.sethook()
              if #l > 0 then
                print("Remaining lines not visited:")
                for i=1,#l do
                  print("  " .. tostring(l[i]))
                end
                print("Lines actually visited:")
                for i=1,#lines do
                  print("  " .. tostring(lines[i]))
                end
              end
              assert(#l == 0, "not all lines visited, remaining: " .. #l)
            end
            
            test([[for i=1,4 do a=1 end]], {1,1,1,1}, true)
        """,
            )
        }

    @Test
    fun testLineHookMultiLineExpression() =
        runTest {
            vm.debugEnabled = true
            // Test from db.lua:192-210 - multi-line expression line tracking
            // Verifies that line hooks fire for operands in multi-line binary expressions
            execute(
                """
            local lines = {}
            local function hook(event, line)
                if event == 'line' then
                    table.insert(lines, line)
                end
            end
            
            -- Build test string: b[1] + b[1] with line gaps
            local i = 10
            local j = 10
            local s = "local b = {10}\na = b[1]" .. string.rep("\n", i) .. " + " .. string.rep("\n", j) .. "b[1]\nb = 4"
            
            debug.sethook(hook, "l")
            load(s)()
            debug.sethook()
            
            -- Verify we get line visits for the operands
            -- lua54 visits: 1 (local b), 12 (operator/left), 22 (right), 12 (op), 22 (result), 23 (b=4)
            -- Find key lines in the visited lines
            local found_line_1 = false
            local found_line_12 = false
            local found_line_22 = false
            for _, line in ipairs(lines) do
                if line == 1 then found_line_1 = true end
                if line == 12 then found_line_12 = true end
                if line == 22 then found_line_22 = true end
            end
            
            assert(found_line_1, "Line 1 (local b) should be visited")
            assert(found_line_12, "Line 12 (operator/first operand) should be visited")
            assert(found_line_22, "Line 22 (second operand) should be visited")
        """,
            )
        }

    // Investigation notes (Dec 2025):
    // db.lua:192-210 tests line tracking with expressions like "a = b[1] + b[1]" where
    // the operands and operators are on different lines separated by many blank lines.
    // Expected behavior: lines 12 and 22 should each be visited twice
    // Root cause: lua54 generates separate GETI instructions for each b[1] reference,
    // each tagged with its source line. Our implementation needs to match this.

    @Test
    fun testLineHookMultiLineExpressionExactSequence() =
        runTest {
            // Test from db.lua:207 - exact line sequence expected by the official test suite
            // This is the failing case: expression with operands on lines separated by many blank lines
            // vm.debugEnabled = true
            execute(
                """
            local lines_visited = {}
            local function test (s, l, p)
              collectgarbage()
              local function f (event, line)
                assert(event == 'line')
                table.insert(lines_visited, line)
                print("Hook fired: line " .. line)
                local expected = table.remove(l, 1)
                if expected ~= line then
                  print("ERROR: expected " .. tostring(expected) .. ", got " .. tostring(line))
                  assert(false, "wrong trace!! expected " .. tostring(expected) .. ", got " .. tostring(line))
                end
              end
              debug.sethook(f,"l"); load(s)(); debug.sethook()
              print("All expected lines visited!")
              assert(#l == 0, "not all lines visited, remaining: " .. #l)
            end
            
            -- Simplified version of db.lua:207 pattern
            -- With i=10, j=10: expects lines {1, 12, 22, 12, 22, 23}
            -- The pattern is: "a = b[1] X + Y b[1]" where X and Y are newline sequences
            -- This means the FIRST b[1] is on line 2, but tokens within the expression
            -- can be on different lines based on where X and Y put them.
            local i = 10
            local j = 10
            local s = "local b = {10}\na = b[1]" .. string.rep("\n", i) .. " + " .. string.rep("\n", j) .. " b[1]\nb = 4"
            -- Expected: line 1 (local b), line 2+i (the +), line 2+i+j (second b[1]), 
            --           line 2+i (back to + for operation), line 2+i+j (second b[1] evaluation), line 3+i+j (b = 4)
            -- But lua54 actually expects: {1, 12, 22, 12, 22, 23}
            -- This suggests: line 1, first GETI at line 12, second GETI at line 22, 
            --               ADD at line 12, then line 23
            test(s, {1, 2 + i, 2 + i + j, 2 + i, 2 + i + j, 3 + i + j})
        """,
            )
        }

    @Test
    fun testLineHookMultiLineExpressionSimplestCase() =
        runTest {
            // Test db.lua:207 pattern with i=1, j=1 (the simplest case that fails)
            execute(
                """
            local function test (s, l, p)
              collectgarbage()
              local lines = {}
              local function f (event, line)
                assert(event == 'line')
                table.insert(lines, line)
                if p then print("line", line) end
              end
              debug.sethook(f,"l"); load(s)(); debug.sethook()
              
              -- Check we got the right lines
              if #lines ~= #l then
                print("ERROR: Expected " .. #l .. " lines, got " .. #lines)
                print("Expected: " .. table.concat(l, ", "))
                print("Got:      " .. table.concat(lines, ", "))
                assert(false, "wrong number of lines")
              end
              
              for i = 1, #l do
                if lines[i] ~= l[i] then
                  print("ERROR at position " .. i .. ": expected " .. l[i] .. ", got " .. lines[i])
                  print("Expected: " .. table.concat(l, ", "))
                  print("Got:      " .. table.concat(lines, ", "))
                  assert(false, "wrong trace")
                end
              end
            end
            
            -- Test with i=1, j=1
            local i = 1
            local j = 1
            local s = "local b = {10}\na = b[1]" .. string.rep("\n", i) .. " + " .. string.rep("\n", j) .. " b[1]\nb = 4"
            print("Testing with i=" .. i .. ", j=" .. j)
            print("Expected lines: " .. table.concat({1, 2 + i, 2 + i + j, 2 + i, 2 + i + j, 3 + i + j}, ", "))
            test(s, {1, 2 + i, 2 + i + j, 2 + i, 2 + i + j, 3 + i + j}, true)
        """,
            )
        }

    @Test
    @Ignore // Slow test - runs all combinations
    fun testLineHookMultiLineExpressionAllCombinations() =
        runTest {
            // Test from db.lua:192-210 - full test with all combinations
            // This tests the exact same combinations as db.lua to ensure compatibility
            execute(
                """
            local function test (s, l, p)
              collectgarbage()
              local function f (event, line)
                assert(event == 'line')
                local expected = table.remove(l, 1)
                if p then print(expected, line) end
                assert(expected == line, "wrong trace!! expected " .. tostring(expected) .. ", got " .. tostring(line))
              end
              debug.sethook(f,"l"); load(s)(); debug.sethook()
              assert(#l == 0, "not all lines visited, remaining: " .. #l)
            end
            
            -- Test with all combinations from db.lua:196-207
            local a = {1, 2, 3, 10, 124, 125, 126, 127, 128, 129, 130,
                       255, 256, 257, 500, 1000}
            local s = [[
               local b = {10}
               a = b[1] X + Y b[1]
               b = 4
            ]]
            for _, i in ipairs(a) do
              local subs = {X = string.rep("\n", i)}
              for _, j in ipairs(a) do
                subs.Y = string.rep("\n", j)
                local s_test = string.gsub(s, "[XY]", subs)
                print("Testing with i=" .. i .. ", j=" .. j .. " -> lines: " .. table.concat({1, 2 + i, 2 + i + j, 2 + i, 2 + i + j, 3 + i + j}, ", "))
                test(s_test, {1, 2 + i, 2 + i + j, 2 + i, 2 + i + j, 3 + i + j})
              end
            end
            print("All combinations passed!")
        """,
            )
        }

    @Test
    fun testLineHookSimpleAssignments_namewhat() =
        runTest {
            vm.debugEnabled = true
            // Test from db.lua:310-323 (part 1)
            // Tests that debug.getinfo returns namewhat="hook" inside a hook function
            execute(
                """
            local count = 0
            local function f()
                assert(debug.getinfo(1).namewhat == "hook")
                count = count + 1
            end
            debug.sethook(f, "l")
            local a = 0
            _ENV.a = a
            a = 1
            debug.sethook()
            assert(count == 4, "count was " .. tostring(count) .. ", expected 4")
        """,
            )
        }

    @Test
    fun testLineHookSimpleAssignments_traceback() =
        runTest {
            vm.debugEnabled = true
            // Test from db.lua:310-323 (part 2)
            // Tests that debug.traceback contains "hook" when called from inside a hook
            execute(
                """
            local count = 0
            local function f()
                local tb = debug.traceback()
                assert(string.find(tb, "hook"), "traceback should contain 'hook': " .. tb)
                count = count + 1
            end
            debug.sethook(f, "l")
            local a = 0
            _ENV.a = a
            a = 1
            debug.sethook()
            -- Line hooks fire on each line
            assert(count >= 4, "count was " .. tostring(count) .. ", expected >= 4")
        """,
            )
        }

    @Test
    fun testLineHookSimpleAssignments_count() =
        runTest {
            // Test from db.lua:310-323 (part 3)
            // Tests that line hooks fire exactly 4 times for simple assignments
            // Lines: debug.sethook, local a, _ENV.a = a, a = 1
            execute(
                """
            local count = 0
            local function f()
                count = count + 1
            end
            debug.sethook(f, "l")
            local a = 0
            _ENV.a = a
            a = 1
            debug.sethook()
            assert(count == 4, "count was " .. tostring(count) .. ", expected 4")
        """,
            )
        }

    @Test
    fun testGetHookAfterClearing() =
        runTest {
            // Test from db.lua:424 - after clearing hook with sethook(), gethook() should return nil
            execute(
                """
            local function hook() end
            debug.sethook(hook, "l")
            -- Hook is set, gethook() should return values
            local h1 = debug.gethook()
            assert(h1 ~= nil, "gethook() should return hook function when hook is set")
            
            -- Clear the hook
            debug.sethook()
            
            -- After clearing, gethook() should return nil
            assert(not debug.gethook(), "gethook() should return nil after clearing hook")
        """,
            )
        }

    @Test
    fun testLineHookLocalFunctionVisibility() =
        runTest {
            // Test from db.lua:676-692 - local function should be visible to debugger only after complete definition
            // Line 3 ('return x') should see "(temporary)" - the function is being created
            // Line 4 ('end') should see "A" - after the function is complete and assigned
            execute(
                """
            co = load[[
              local A = function ()
                return x
              end
              return
            ]]
            
            local a = 0
            -- 'A' should be visible to debugger only after its complete definition
            debug.sethook(function (e, l)
              if l == 3 then a = a + 1; assert(debug.getlocal(2, 1) == "(temporary)")
              elseif l == 4 then a = a + 1; assert(debug.getlocal(2, 1) == "A")
              end
            end, "l")
            co()  -- run local function definition
            debug.sethook()  -- turn off hook
            assert(a == 2, "expected 2 hook calls, got " .. tostring(a))   -- ensure all two lines where hooked
        """,
            )
        }
}
