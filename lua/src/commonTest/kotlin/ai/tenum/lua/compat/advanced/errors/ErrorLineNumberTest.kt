package ai.tenum.lua.compat.advanced.errors

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.vm.errorhandling.LuaException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for error line number accuracy.
 *
 * Coverage:
 * - Line number reporting in error messages
 * - Line numbers in for loops
 * - Line numbers with multiple statements
 * - Line numbers in nested contexts
 */
class ErrorLineNumberTest : LuaCompatTestBase() {
    @Test
    fun testLineNumberAccuracy() =
        runTest {
            // Test that error line numbers are accurately reported
            // This tests the fix for duplicate LineInfo entries bug
            execute(
                """
                local function getErrorLine(code)
                    local ok, err = pcall(function()
                        load(code)()
                    end)
                    if not ok then
                        -- Extract line number from error message format: "=(load):LINE:"
                        local line = string.match(err, ":(%d+):")
                        return tonumber(line)
                    end
                    return nil
                end
                
                -- Test 1: Error on line 2 after one empty line
                local code1 = [[

assert(false, "error on line 2")
]]
                local line1 = getErrorLine(code1)
                assert(line1 == 2, "Test 1: Expected line 2, got: " .. tostring(line1))
                
                -- Test 2: Error on line 5 after do...end block
                local code2 = [[
do
  local x = 1
end

assert(1 + 1 == 3, "error on line 5")
]]
                local line2 = getErrorLine(code2)
                assert(line2 == 5, "Test 2: Expected line 5, got: " .. tostring(line2))
                
                -- Test 3: Error reported correctly after multiple statements with expressions
                -- This reproduces the original bug where ExpressionCompiler was adding duplicate LineInfo
                local code3 = [[
do
    local s1 = string.rep("a", 10)
    local s2 = string.rep("b", 10)
end

assert(false, "error on line 6")
]]
                local line3 = getErrorLine(code3)
                assert(line3 == 6, "Test 3: Expected line 6, got: " .. tostring(line3))
            """,
            )
        }

    @Test
    fun testErrorLineNumberInForLoop() =
        runTest {
            // Test from errors.lua line 402-405
            // Tests that runtime errors in for loops report the correct line number
            execute(
                """
                function lineerror(s, l)
                  local err, msg = pcall(load(s))
                  local line = tonumber(string.match(msg, ":(%d+):"))
                  assert(line == l or (not line and not l), 
                    "Expected line " .. tostring(l) .. ", got " .. tostring(line))
                end
                
                -- Test 1: Error on line 2 in for limit
                lineerror("local a\n for i=1,'a' do \n print(i) \n end", 2)
                
                -- Test 2: Error on line 3 when calling iterator value
                lineerror("\n local a \n for k,v in 3 \n do \n print(k) \n end", 3)
                
                -- Test 3: Error on line 4 when calling iterator value (leading newlines)
                -- This is the failing case - error happens when trying to call 3 on line 4
                lineerror("\n\n for k,v in \n 3 \n do \n print(k) \n end", 4)
                
                -- Test 4: Error on line 1 in function declaration
                lineerror("function a.x.y ()\na=a+1\nend", 1)
                
                -- Test 5: Error on line 3 when adding undefined variable to table
                -- This reproduces errors.lua:407 - arithmetic operation across multiple lines
                lineerror("a = \na\n+\n{}", 3)
            """,
            )
        }

    @Test
    fun testErrorLineNumberWithSuccessfulPrecedingLine() =
        runTest {
            // Test that reproduces the strings.lua:349-350 bug
            // Error message said line 349 but actual failure was line 350
            // This happens when line N passes but line N+1 fails
            execute(
                """
                function checkerror(code, expectedLine)
                  local ok, err = pcall(function()
                    load(code)()
                  end)
                  if ok then
                    error("Expected error but code succeeded")
                  end
                  local line = tonumber(string.match(err, ":(%d+):"))
                  assert(line == expectedLine, 
                    "Expected error on line " .. expectedLine .. ", got line " .. tostring(line))
                end
                
                -- Test: Two consecutive assertions where first passes, second fails
                -- This mimics strings.lua lines 349-350
                local code = [[
assert(string.format("%013i", -100) == "-000000000100")
assert(string.format("%.u", 0) == "wrong_value")
]]
                -- Should report error on line 2, not line 1
                checkerror(code, 2)
                
                -- Test: Error on line 5 with multiple passing lines before it
                local code2 = [[
assert(1 + 1 == 2)
assert(2 * 2 == 4)
assert(3 - 1 == 2)
assert(4 / 2 == 2)
assert(5 > 10)
]]
                checkerror(code2, 5)
                
                -- Test: Verify line 1 passes but line 2 fails with string.format
                local code3 = [[
assert(string.format("%d", 42) == "42")
assert(string.format("%.d", 0) == "not_empty")
]]
                checkerror(code3, 2)
            """,
            )
        }

    @Test
    fun testErrorLineNumberAfterNewlineBeforeParenExpression() =
        runTest {
            // Test that reproduces calls.lua:210-211 pattern
            // Line 210: a = nil
            // Line 211: (function (x) a=x end)(23)
            // These should be parsed as TWO separate statements, not one
            execute(
                """
                -- Test 1: Reproduce calls.lua:210-211 pattern
                -- This should succeed because they are two separate statements
                a = nil
                (function (x) a=x end)(23)
                assert(a == 23, "a should be 23 after function call")
                
                -- Test 2: Verify that attempting to actually call nil still errors on correct line
                local function testNilCall()
                  local f = nil
                  f()  -- This is line 3 inside this function
                end
                
                local ok, err = pcall(testNilCall)
                assert(not ok, "calling nil should error")
                -- Error should mention "attempt to call" and show the function line
                assert(string.find(err, "attempt to call"), "Error should mention 'attempt to call', got: " .. tostring(err))
            """,
            )
        }

    @Test
    fun testFunctionCallErrorLineNumber() =
        runTest {
            // Reproduces calls.lua:310/316 bug
            // Error happens on line 316 (function call) but was reported as line 310 (end of function definition)
            // This happens because CallCompiler doesn't update ctx.currentLine before emitting CALL opcode

            // The bug manifests when:
            // 1. A function definition ends on line N
            // 2. Later (line N+X), a function call happens that triggers an error
            // 3. The error is reported as line N instead of line N+X
            execute(
                """
                local function getErrorLine(code)
                    local ok, err = pcall(function()
                        load(code)()
                    end)
                    if not ok then
                        -- Extract line number from error message
                        local line = string.match(err, ":(%d+):")
                        return tonumber(line)
                    end
                    return nil
                end
                
                -- Exact reproduction of calls.lua pattern:
                -- Function ends on line N, error-triggering call happens later
                local code = [[
local function dummy()
  return 1
end
-- Line 4: blank
-- Line 5: another statement  
local a = 1
-- Line 7: THE ACTUAL ERROR - calling nil
local x = nil
x()
]]
                
                local line = getErrorLine(code)
                -- Error should be on line 9 (x()), NOT line 3 (end of dummy function)
                assert(line == 9, "Expected error on line 9 (call site), got: " .. tostring(line))
            """,
            )
        }

    @Test
    fun testPcallWithLoadReturningNil() =
        runTest {
            // Test errors.lua:397 - pcall should catch "attempt to call nil" error
            // when load() fails and returns nil
            execute(
                """
                -- Test 1: load() returns nil, pcall should catch the error
                local s = "syntax error @#$"
                local f, errmsg = load(s)
                assert(f == nil, "load should return nil for syntax errors")
                assert(type(errmsg) == "string", "load should return error message")
                
                -- Test 2: pcall(nil) should return (false, error_message)
                local ok, err = pcall(nil)
                assert(ok == false, "pcall(nil) should return false")
                assert(type(err) == "string", "pcall(nil) should return error message")
                assert(string.find(err, "attempt to call"), "Error should mention 'attempt to call'")
                
                -- Test 3: pcall(load(invalid)) should catch the error
                local ok2, err2 = pcall(load("bad syntax @#$"))
                assert(ok2 == false, "pcall(load(invalid)) should return false")
                assert(type(err2) == "string", "Should return error message")
                
                -- Test 4: pcall should catch errors from calling non-functions
                local notfunc = 42
                local ok3, err3 = pcall(notfunc)
                assert(ok3 == false, "pcall(number) should return false")
                assert(string.find(err3, "attempt to call"), "Should mention 'attempt to call'")
            """,
            )
        }

    @Test
    fun testLineErrorWithPcallLoad() =
        runTest {
            // Test the lineerror() pattern from errors.lua
            // This tests that runtime errors report correct line numbers
            // even when the code is loaded dynamically
            execute(
                """
                local function lineerror(s, l)
                  local err, msg = pcall(load(s))
                  local line = tonumber(string.match(msg, ":(%d+):"))
                  assert(line == l or (not line and not l),
                    "Expected line " .. tostring(l) .. ", got " .. tostring(line) .. 
                    " for code: " .. string.sub(s, 1, 30))
                end
                
                -- Test various runtime errors with correct line numbers
                lineerror("local a\n for i=1,'a' do \n print(i) \n end", 2)
                lineerror("\n local a \n for k,v in 3 \n do \n print(k) \n end", 3)
                lineerror("\n\n for k,v in \n 3 \n do \n print(k) \n end", 4)
                lineerror("function a.x.y ()\na=a+1\nend", 1)
                lineerror("a = \na\n+\n{}", 3)
                lineerror("a = \n3\n+\n(\n4\n/\nprint)", 6)
                lineerror("a = \nprint\n+\n(\n4\n/\n7)", 3)
                lineerror("a\n=\n-\n\nprint\n;", 3)
            """,
            )
        }

    @Test
    fun testArithmeticErrorLineNumbers() =
        runTest {
            // Test that arithmetic errors across multiple lines report correct line
            execute(
                """
                local function getErrorLine(code)
                    local ok, err = pcall(function()
                        load(code)()
                    end)
                    if not ok then
                        local line = string.match(err, ":(%d+):")
                        return tonumber(line)
                    end
                    return nil
                end
                
                -- Test 1: Division by zero-like operation
                local code1 = [[
a = 
3
+
(
4
/
print)
]]
                local line1 = getErrorLine(code1)
                assert(line1 == 6, "Error should be on line 6 (division), got: " .. tostring(line1))
                
                -- Test 2: Unary minus on non-number
                local code2 = [[
a
=
-

print
;
]]
                local line2 = getErrorLine(code2)
                assert(line2 == 3, "Error should be on line 3 (unary minus), got: " .. tostring(line2))
                
                -- Test 3: Addition with nil
                local code3 = [[
a = 
a
+
{}
]]
                local line3 = getErrorLine(code3)
                assert(line3 == 3, "Error should be on line 3 (addition), got: " .. tostring(line3))
            """,
            )
        }

    @Test
    fun testMethodCallErrorLineNumbers() =
        runTest {
            // Test that method call errors report correct line numbers
            execute(
                """
                local function getErrorLine(code)
                    local ok, err = pcall(function()
                        load(code)()
                    end)
                    if not ok then
                        local line = string.match(err, ":(%d+):")
                        return tonumber(line)
                    end
                    return nil
                end
                
                -- Test 1: Method call on nil
                local code1 = [[
local a = nil
a:method()
]]
                local line1 = getErrorLine(code1)
                assert(line1 == 2, "Error should be on line 2 (method call), got: " .. tostring(line1))
                
                -- Test 2: Method call with nil method
                local code2 = [[
local obj = {}
obj:missing()
]]
                local line2 = getErrorLine(code2)
                assert(line2 == 2, "Error should be on line 2 (calling nil method), got: " .. tostring(line2))
                
                -- Test 3: Method call after multiple lines
                local code3 = [[
local x = {}
local y = {}
local z = {}
x:nonexistent()
]]
                local line3 = getErrorLine(code3)
                assert(line3 == 4, "Error should be on line 4, got: " .. tostring(line3))
            """,
            )
        }

    @Test
    fun testNestedCallErrorLineNumbers() =
        runTest {
            // Test that nested function calls report correct line numbers
            execute(
                """
                local function getErrorLine(code)
                    local ok, err = pcall(function()
                        load(code)()
                    end)
                    if not ok then
                        local line = string.match(err, ":(%d+):")
                        return tonumber(line)
                    end
                    return nil
                end
                
                -- Test 1: Nested calls where inner errors
                local code1 = [[
local function inner()
  error("inner error")
end
local function outer()
  inner()
end
outer()
]]
                local line1 = getErrorLine(code1)
                -- Error should be on line 2 (where error() is called)
                assert(line1 == 2, "Error should be on line 2 (inner error call), got: " .. tostring(line1))
                
                -- Test 2: Multiple nested calls
                local code2 = [[
local function a() error("from a") end
local function b() a() end
local function c() b() end
c()
]]
                local line2 = getErrorLine(code2)
                assert(line2 == 1, "Error should be on line 1, got: " .. tostring(line2))
            """,
            )
        }

    @Test
    fun testVarargCallErrorLineNumbers() =
        runTest {
            // Test that variadic function calls report correct line numbers
            execute(
                """
                local function getErrorLine(code)
                    local ok, err = pcall(function()
                        load(code)()
                    end)
                    if not ok then
                        local line = string.match(err, ":(%d+):")
                        return tonumber(line)
                    end
                    return nil
                end
                
                -- Test: Call with varargs that errors
                local code = [[
local function varfunc(...)
  local args = {...}
  error("error on line 3")
end

varfunc(1, 2, 3)
]]
                local line = getErrorLine(code)
                assert(line == 3, "Error should be on line 3, got: " .. tostring(line))
            """,
            )
        }

    @Test
    fun testLoadFunctionReaderLineNumbers() =
        runTest {
            // Tests load() with function reader (calls.lua:316 test case)
            execute(
                """
                -- Test 1: load() with function reader that returns complete code
                local called = false
                local function reader()
                    if not called then
                        called = true
                        return "return 42"
                    end
                    return nil
                end
                
                local func, err = load(reader)
                assert(func ~= nil, "load() with function reader should work, got error: " .. tostring(err))
                assert(func() == 42, "Function should return 42")
                
                -- Test 2: load() with character-by-character reader
                local code = "return 123"
                local i = 0
                local function charReader()
                    i = i + 1
                    if i <= #code then
                        return string.sub(code, i, i)
                    end
                    return nil
                end
                
                local func2, err2 = load(charReader, "test_char_reader")
                assert(func2 ~= nil, "Character reader should work, got error: " .. tostring(err2))
                assert(func2() == 123, "Function should return 123")
                
                -- Test 3: Binary mode should detect text chunks (calls.lua:316)
                local x = "return 456"
                local j = 0
                local function textReader()
                    j = j + 1
                    if j <= #x then
                        return string.sub(x, j, j)
                    end
                    return nil
                end
                
                -- Should fail with "attempt to load a text chunk"
                local func3, err3 = load(textReader, "test", "b")
                assert(func3 == nil, "Binary mode with text should fail")
                assert(string.find(err3, "attempt to load a text chunk"), 
                       "Expected 'attempt to load a text chunk' error, got: " .. tostring(err3))
                
                -- Test 4: Reader returning empty string signals end
                local returned = false
                local function emptyReader()
                    if not returned then
                        returned = true
                        return "return 789"
                    end
                    return ""  -- empty string = end
                end
                
                local func4 = load(emptyReader)
                assert(func4() == 789, "Empty string should signal end of chunk")
                
                -- Test 5: Reader returning non-string should error
                local function badReader()
                    return 123  -- not a string
                end
                
                local func5, err5 = load(badReader)
                assert(func5 == nil, "Reader returning non-string should fail")
                assert(string.find(err5, "must return a string"), 
                       "Expected 'must return a string' error, got: " .. tostring(err5))
            """,
            )
        }

    @Test
    fun testOffLineNumberBug() =
        runTest {
            val error =
                shouldThrow<LuaException> {
                    execute(
                        """-- test for generic load
local x = "-- a comment\0\0\0\n  x = 10 + \n23; \
     local a = function () x = 'hi' end; \
     return '\0'"
local function read1 (x)
  local i = 0
  return function ()
    collectgarbage()
    i=i+1
    return string.sub(x, i, i)
  end
end

local function cannotload (msg, a,b)
  assert(not a and string.find(b, msg))
end

a = assert(load(read1(x), "modname", "t", _G))
assert(a() == "\0" and _G.x == 33)
assert(debug.getinfo(a).source == "modname")
-- cannot read text in binary mode
cannotload("attempt to load a text chunk", load(read1(x), "modname", "b", {}))
cannotload("attempt to load a text chunk", load(x, "modname", "b"))

a = assert(load(function () return nil end))
a()  -- empty chunk

assert(not load(function () return true end))


-- small bug
local t = {nil, "return ", "3"}
f, msg = load(function () return table.remove(t, 1) end)
assert(f() == nil)   -- should read the empty chunk

-- another small bug (in 5.2.1)
f = load(string.dump(function () return 1 end), nil, "b", {})
assert(type(f) == "function" and f() == 1)


do   -- another bug (in 5.4.0)
  -- loading a binary long string interrupted by GC cycles
  local f = string.dump(function ()
    return '01234567890123456789012345678901234567890123456789'
  end)
  f = load(read1(f))
  assert(f() == '01234567890123456789012345678901234567890123456789')
end


x = string.dump(load("x = 1; return x"))
a = assert(load(read1(x), nil, "b"))
assert(a() == 1 and _G.x == 1)
cannotload("attempt to load a binary chunk", load(read1(x), nil, "t"))
cannotload("attempt to load a binary chunk", load(x, nil, "t"))
_G.x = nil

assert(not pcall(string.dump, print))  -- no dump of C functions

cannotload("unexpected symbol", load(read1("*a = 123")))
cannotload("unexpected symbol", load("*a = 123"))
cannotload("hhi", load(function () error("hhi") end))

-- any value is valid for _ENV
assert(load("return _ENV", nil, nil, 123)() == 123)


-- load when _ENV is not first upvalue
local x; XX = 123
local function h ()
  local y=x   -- use 'x', so that it becomes 1st upvalue
  return XX   -- global name
end
local d = string.dump(h)
x = load(d, "", "b")
assert(false, "THIS SHOULD FAIL")""",
                    )
                }
            error.errorMessage.shouldContain("(load):76: THIS SHOULD FAIL")
        }

    @Test
    fun testCallErrorLineNumberForFieldAccess() =
        runTest {
            // RED: Test for errors.lua:429
            // When calling a non-function value accessed via field access,
            // the error should be reported on the line where the call happens
            execute(
                """
                local function lineerror(s, l)
                  local err, msg = pcall(load(s))
                  local line = tonumber(string.match(msg, ":(%d+):"))
                  assert(line == l or (not line and not l), 
                    "Expected line " .. tostring(l) .. ", got " .. tostring(line))
                end
                
                -- Test from errors.lua:429
                -- a.x is a number (13), calling it should error on line 6
                lineerror([[
local a = {x = 13}
a
.
x
(
23 + a
)
]], 6)
            """,
            )
        }

    @Test
    fun testCallOnNilReportsCorrectLine() =
        runTest {
            execute(
                """
            local function lineerror(s, l)
              local err, msg = pcall(load(s))
              local line = tonumber(string.match(msg, ":(%d+):"))
              assert(line == l or (not line and not l), 
                "Expected line " .. tostring(l) .. ", got " .. tostring(line))
            end
            
            -- Lua 5.4.8 reports line 2 (where '(' is) for call errors
            lineerror([[
a
(
23)
]], 2)
        """,
            )
        }

    @Test
    fun testUnaryMinusErrorLine() =
        runTest {
            execute(
                """
            local function lineerror(s, l)
              local err, msg = pcall(load(s))
              local line = tonumber(string.match(msg, ":(%d+):"))
              assert(line == l or (not line and not l), 
                "Expected line " .. tostring(l) .. ", got " .. tostring(line))
            end
            
            -- Unary minus on line 3, should report line 3
            lineerror("a\n=\n-\n\nprint\n;", 3)
        """,
            )
        }
}
