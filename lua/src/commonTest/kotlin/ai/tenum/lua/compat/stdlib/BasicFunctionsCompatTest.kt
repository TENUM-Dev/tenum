package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PHASE 5.4: Standard Library - Basic Functions
 *
 * Tests basic Lua functions (print, type, assert, etc.).
 * Based on: constructs.lua, nextvar.lua
 *
 * Coverage:
 * - print, tostring, tonumber
 * - type, typeof
 * - assert, error
 * - pcall, xpcall
 * - pairs, ipairs, next
 * - select
 * - rawget, rawset, rawlen, rawequal
 * - setmetatable, getmetatable
 */
class BasicFunctionsCompatTest : LuaCompatTestBase() {
    @Test
    fun testPrint() =
        runTest {
            // TODO: Test print()
            skipTest("print not implemented")
        }

    @Test
    fun testTostring() =
        runTest {
            // Basic conversions
            execute("assert(tostring(123) == '123')")
            execute("assert(tostring('hello') == 'hello')")
            execute("assert(tostring(true) == 'true')")
            execute("assert(tostring(false) == 'false')")
            execute("assert(tostring(nil) == 'nil')")
        }

    @Test
    fun testTostringRequiresArgument() =
        runTest {
            // tostring() with no arguments should throw error
            assertLuaBoolean(
                """
            local ok, err = pcall(function()
                return tostring()
            end)
            return not ok
        """,
                true,
            )

            // Verify error message contains "bad argument"
            val result =
                execute(
                    """
            local ok, err = pcall(function()
                return tostring()
            end)
            return err
        """,
                )
            val errorMsg = result.toString()
            assertTrue(
                errorMsg.contains("bad argument") && errorMsg.contains("tostring"),
                "Expected 'bad argument' error for tostring(), got: $errorMsg",
            )
        }

    @Test
    fun testTonumber() =
        runTest {
            // Basic string to number conversion
            execute("assert(tonumber('123') == 123)")
            execute("assert(tonumber('123.45') == 123.45)")
            execute("assert(tonumber('  456  ') == 456)")
            execute("assert(tonumber('-789') == -789)")

            // Number input returns same number
            execute("assert(tonumber(42) == 42)")
            execute("assert(tonumber(3.14) == 3.14)")

            // Invalid conversions return nil
            execute("assert(tonumber('abc') == nil)")
            execute("assert(tonumber('') == nil)")
            execute("assert(tonumber(true) == nil)")
            execute("assert(tonumber(nil) == nil)")
            execute("assert(tonumber({}) == nil)")

            // Base conversion (integers only)
            execute("assert(tonumber('FF', 16) == 255)")
            execute("assert(tonumber('ff', 16) == 255)")
            execute("assert(tonumber('1010', 2) == 10)")
            execute("assert(tonumber('777', 8) == 511)")
            execute("assert(tonumber('Z', 36) == 35)")
            execute("assert(tonumber('-FF', 16) == -255)")
            execute("assert(tonumber('+1010', 2) == 10)")

            // Large number from official test suite
            execute("assert(1234567890 == tonumber('1234567890'))")
        }

    @Test
    fun testTonumberIntegerOverflow() =
        runTest {
            // math.lua:359-360 - Numbers too large for 64-bit integers should convert to float
            // 1 followed by 30 zeros exceeds Long.MAX_VALUE
            execute("assert(tonumber('1000000000000000000000000000000') == 1e30)")
            execute("assert(tonumber('-1000000000000000000000000000000') == -1e30)")

            // Just beyond Long.MAX_VALUE (9223372036854775807)
            execute("assert(tonumber('9223372036854775808') == 9223372036854775808.0)")
            execute("assert(tonumber('-9223372036854775809') == -9223372036854775809.0)")

            // math.lua:363 - Hex integers too large for 64-bit should wrap around
            // 0x1 followed by 30 zeros = 2^120, wraps to 0
            execute("assert(tonumber('0x1' .. string.rep('0', 30)) == 0)")

            // math.lua:387 - Invalid hex formats should return nil
            execute("assert(not tonumber('  -0x '))")
        }

    @Test
    fun testTonumberHexFloats() =
        runTest {
            // Basic hex float literals (needed for bitwise.lua line 27-28)
            execute("assert(tonumber('0xAA.0') == 170.0)")
            execute("assert(tonumber('0xF0.0') == 240.0)")
            execute("assert(tonumber('0xCC.0') == 204.0)")
            execute("assert(tonumber('0xFD.0') == 253.0)")

            // Hex floats with actual fractional parts
            execute("assert(tonumber('0x10.8') == 16.5)")
            execute("assert(tonumber('0xFF.FF') > 255.99)")

            // Large hex integers with two's complement wrapping
            execute("assert(tonumber('0xFFFFFFFFFFFFFFFF') == -1)")
            execute("assert(tonumber('0xFFFFFFFFFFFFFFFE') == -2)")

            // Large hex floats should NOT wrap (stay unsigned)
            execute("assert(tonumber('0xFFFFFFFFFFFFFFFF.0') > 1.8e19)")

            // Signed hex floats
            execute("assert(tonumber('-0xAA.0') == -170.0)")
            execute("assert(tonumber('+0xF0.0') == 240.0)")
        }

    @Test
    fun testTonumberHexFloatWithExponent() =
        runTest {
            // Hex float with p notation (strings.lua line 315)
            // string.format("%a", 0.1) produces "0x1.999999999999ap-4"
            // tonumber should be able to parse it back
            execute("assert(tonumber('0x1.999999999999ap-4') == 0.1)")
            execute("assert(tonumber('-0x1.999999999999ap-4') == -0.1)")
            execute("assert(tonumber('0x1p+0') == 1.0)")
            execute("assert(tonumber('0x1p+1') == 2.0)")
            execute("assert(tonumber('0x1p-1') == 0.5)")
        }

    @Test
    fun testLoadSyntaxError() =
        runTest {
            // load() should return nil and error message for syntax errors
            // Test case from errors.lua line 57: "repeat until 1; a"
            // The 'a' after the repeat-until is a bare identifier, which is a syntax error
            assertLuaBoolean(
                """
            local f, err = load("repeat until 1; a")
            return f == nil and err ~= nil
        """,
                true,
            )

            // Verify error message indicates syntax error
            val result =
                execute(
                    """
            local f, err = load("repeat until 1; a")
            return err
        """,
                )
            val errorMsg = result.toString()
            assertTrue(
                errorMsg.contains("syntax") || errorMsg.contains("near"),
                "Expected syntax error for 'repeat until 1; a', got: $errorMsg",
            )
        }

    @Test
    fun testLoadParenthesizedCallSyntaxError() =
        runTest {
            // Parenthesized function calls like (f()) are NOT valid statements in Lua
            assertLuaBoolean(
                """
            function f() end
            local chunk, err = load("(f())")
            return chunk == nil and err ~= nil
        """,
                true,
            )

            val result =
                execute(
                    """
            local chunk, err = load("(f())")
            return err
        """,
                )
            val errorMsg = result.toString()
            assertTrue(
                errorMsg.contains("syntax") || errorMsg.contains("near"),
                "Expected syntax error for '(f())', got: $errorMsg",
            )
        }

    @Test
    fun testLoadBareExpressionSyntaxError() =
        runTest {
            // Bare expressions like (5) are NOT valid statements
            assertLuaBoolean(
                """
            local chunk, err = load("(5)")
            return chunk == nil and err ~= nil
        """,
                true,
            )

            val result =
                execute(
                    """
            local chunk, err = load("(5)")
            return err
        """,
                )
            val errorMsg = result.toString()
            assertTrue(
                errorMsg.contains("syntax") || errorMsg.contains("near"),
                "Expected syntax error for '(5)', got: $errorMsg",
            )
        }

    @Test
    fun testType() =
        runTest {
            // TODO: Test type()
            skipTest("type not fully tested")
        }

    @Test
    fun testAssert() =
        runTest {
            // TODO: Test assert()
            skipTest("assert not implemented")
        }

    @Test
    fun testError() =
        runTest {
            // TODO: Test error()
            skipTest("error not implemented")
        }

    @Test
    fun testPcall() =
        runTest {
            // Successful call returns true + results
            execute(
                """
            local function add(a, b) return a + b end
            local status, result = pcall(add, 10, 20)
            assert(status == true, 'status should be true but is ' .. tostring(status))
            assert(result == 30, 'result should be 30 but is ' .. tostring(result))
        """,
            )

            // Multiple return values
            execute(
                """
            local function multi() return 1, 2, 3 end
            local st, a, b, c = pcall(multi)
            assert(st == true and a == 1 and b == 2 and c == 3, 'st=' .. tostring(st) .. ' a=' .. tostring(a) .. ' b=' .. tostring(b) .. ' c=' .. tostring(c))
        """,
            )

            // Error returns false + error message
            execute(
                """
            local function fail() error('test error') end
            local status, err = pcall(fail)
            assert(status == false, 'status should be false but is ' .. tostring(status))
            assert(type(err) == 'string', 'err type should be string but is ' .. type(err))
        """,
            )

            // pcall with function that takes arguments
            execute(
                """
            local function concat(a, b, c) return a .. b .. c end
            local st, result = pcall(concat, 'x', 'y', 'z')
            assert(st == true and result == 'xyz', 'st=' .. tostring(st) .. ' result=' .. tostring(result))
        """,
            )

            // From official test suite - pcall(type) fails (no argument)
            execute("assert(not pcall(type), 'pcall(type) should fail')")

            // pcall catches runtime errors
            execute(
                """
            local st, err = pcall(function() return nil + 5 end)
            assert(st == false, 'st should be false but is ' .. tostring(st))
        """,
            )
        }

    @Test
    fun testXpcall() =
        runTest {
            // Successful call returns true + results
            execute(
                """
            local function identity(x) return x end
            local function add(a, b) return a + b end
            local status, result = xpcall(add, identity, 10, 20)
            assert(status == true, 'status should be true but is ' .. tostring(status))
            assert(result == 30, 'result should be 30 but is ' .. tostring(result))
        """,
            )

            // Error handler is called on error
            execute(
                """
            local function msgh(err) return 'handled: ' .. err end
            local function fail() error('boom') end
            local st, msg = xpcall(fail, msgh)
            assert(st == false, 'st should be false but is ' .. tostring(st))
            assert(type(msg) == 'string', 'msg type should be string but is ' .. type(msg))
        """,
            )

            // TODO: Requires string.find to be implemented
            // From official test suite - xpcall with arguments
            // execute("""
            //     local a, b, c = xpcall(string.find, error, 'alo', 'al')
            //     assert(a == true and b == 1 and c == 2, 'a=' .. tostring(a) .. ' b=' .. tostring(b) .. ' c=' .. tostring(c))
            // """)

            // Message handler can transform error
            execute(
                """
            local function handler(e)
                return {msg = e .. ' transformed'}
            end
            local function fail() error('original') end
            local st, result = xpcall(fail, handler)
            assert(st == false, 'st should be false but is ' .. tostring(st))
            assert(type(result) == 'table', 'result type should be table but is ' .. type(result))
        """,
            )
        }

    @Test
    fun testPairs() =
        runTest {
            // Test pairs() returns correct values
            execute(
                """
            local t = {a = 10}
            local f, s, c = pairs(t)
            assert(type(f) == 'function', 'f should be function but is ' .. type(f))
            assert(type(s) == 'table', 's should be table but is ' .. type(s))
            assert(c == nil, 'c should be nil but is ' .. tostring(c))
        """,
            )

            // Debug: Test that iterator works manually
            execute(
                """
            local t = {a = 10}
            local f, s, c = pairs(t)
            local k, v = f(s, c)
            assert(k ~= nil, 'k should not be nil but is ' .. tostring(k))
            assert(k == 'a', 'k should be a but is ' .. tostring(k))
            assert(v == 10, 'v should be 10 but is ' .. tostring(v))
        """,
            )

            // Basic iteration over table - DEBUG THIS
            println("\n========== DEBUGGING FOR LOOP ==========")
            execute(
                """
            local t = {a = 10}
            for k, v in pairs(t) do
                assert(type(k) == 'string', 'k type should be string but is ' .. type(k) .. ', k=' .. tostring(k))
                assert(type(v) == 'number', 'v type should be number but is ' .. type(v) .. ', v=' .. tostring(v))
                break
            end
        """,
            )

            vm.debugEnabled = false

            execute(
                """
            local t = {a = 10, b = 20, c = 30}
            local sum = 0
            local count = 0
            for k, v in pairs(t) do
                count = count + 1
                assert(k ~= nil, 'iteration ' .. count .. ': k is nil')
                assert(v ~= nil, 'iteration ' .. count .. ': v is nil, k=' .. tostring(k))
                assert(type(v) == 'number', 'iteration ' .. count .. ': v type is ' .. type(v) .. ', k=' .. tostring(k))
                sum = sum + v
            end
            assert(count == 3, 'count should be 3 but is ' .. count)
            assert(sum == 60, 'sum should be 60 but is ' .. tostring(sum))
        """,
            )

            // Iteration includes array part
            execute(
                """
            local t = {10, 20, 30, x = 40}
            local sum = 0
            for k, v in pairs(t) do
                sum = sum + v
            end
            assert(sum == 100, 'sum should be 100 but is ' .. sum)
        """,
            )

            // Empty table
            execute(
                """
            for k, v in pairs({}) do
                error('should not iterate')
            end
        """,
            )

            // TODO: Support function call with table argument without parens: ipairs{}
            // From official test suite - iterator function is same
            // execute("assert(type(ipairs{}) == 'function' and ipairs{} == ipairs{})")
        }

    @Test
    fun testIpairs() =
        runTest {
            // Basic sequential iteration (from official test suite)
            execute(
                """
            local x = 0
            for k, v in ipairs({10, 20, 30}) do
                x = x + 1
                assert(k == x and v == x * 10, 'k=' .. k .. ' x=' .. x .. ' v=' .. v)
            end
            assert(x == 3, 'x should be 3 but is ' .. x)
        """,
            )

            // ipairs ignores hash part
            execute(
                """
            local t = {10, 20, 30}
            t.x = 12
            local x = 0
            for k, v in ipairs(t) do
                x = x + 1
                assert(k == x and v == x * 10, 'k=' .. k .. ' x=' .. x .. ' v=' .. v)
            end
            assert(x == 3, 'x should be 3 but is ' .. x)
        """,
            )

            // ipairs with only hash part (no iteration)
            execute(
                """
            local t = {x = 12, y = 24}
            for _ in ipairs(t) do
                error('should not iterate')
            end
        """,
            )

            // ipairs stops at first nil
            execute(
                """
            local t = {10, 20, nil, 40}
            local count = 0
            for k, v in ipairs(t) do
                count = count + 1
            end
            assert(count == 2, 'count should be 2 but is ' .. count)
        """,
            )

            // Test for 'false' in ipairs (from official test)
            execute(
                """
            local x = false
            local i = 0
            for k, v in ipairs({true, false, true, false}) do
                i = i + 1
                x = not x
                assert(x == v, 'iteration ' .. i .. ': x=' .. tostring(x) .. ' v=' .. tostring(v))
            end
            assert(i == 4, 'i should be 4 but is ' .. i)
        """,
            )
        }

    @Test
    fun testNext() =
        runTest {
            // Basic next usage
            execute(
                """
            local t = {a = 10, b = 20}
            local k, v = next(t)
            assert(k ~= nil and v ~= nil, 'k=' .. tostring(k) .. ' v=' .. tostring(v))
        """,
            )

            // next with nil starts iteration
            execute(
                """
            local t = {a = 10}
            local k, v = next(t, nil)
            assert(k == 'a' and v == 10, 'k=' .. tostring(k) .. ' v=' .. tostring(v))
        """,
            )

            // next returns nil when done
            execute(
                """
            local t = {x = 1}
            local k1, v1 = next(t, nil)
            local k2, v2 = next(t, k1)
            assert(k2 == nil and v2 == nil, 'k2=' .. tostring(k2) .. ' v2=' .. tostring(v2))
        """,
            )

            // Empty table
            execute(
                """
            local k, v = next({})
            assert(k == nil and v == nil, 'k=' .. tostring(k) .. ' v=' .. tostring(v))
        """,
            )

            // Iterate through table with next
            execute(
                """
            local t = {a = 1, b = 2, c = 3}
            local count = 0
            local k = nil
            repeat
                k = next(t, k)
                if k then count = count + 1 end
            until k == nil
            assert(count == 3, 'count should be 3 but is ' .. count)
        """,
            )
        }

    @Test
    fun testSelect() =
        runTest {
            // select('#', ...) returns count of arguments
            execute("local count = select('#', 10, 20, 30); assert(count == 3, 'count should be 3 but is ' .. tostring(count))")
            execute("local count = select('#'); assert(count == 0, 'count should be 0 but is ' .. tostring(count))")

            // select(n, ...) returns arguments starting from n
            assertLuaString("return select(2, 'a', 'b', 'c')", "b")

            // Multiple return values
            execute("local a, b, c = select(2, 10, 20, 30, 40); assert(a == 20 and b == 30 and c == 40)")

            // select with index 1 returns all
            execute("local a, b = select(1, 'x', 'y'); assert(a == 'x' and b == 'y')")

            // Negative indices count from end
            execute("local a = select(-1, 3, 5, 7); assert(a == 7)")
            execute("local a, b = select(-2, 3, 5, 7); assert(a == 5 and b == 7)")
        }

    @Test
    fun testRawget() =
        runTest {
            // Basic rawget without metamethods
            execute(
                """
            local t = {a = 10, b = 20}
            assert(rawget(t, 'a') == 10)
            assert(rawget(t, 'b') == 20)
            assert(rawget(t, 'c') == nil)
        """,
            )

            // rawget bypasses __index metamethod
            execute(
                """
            local t = {}
            local mt = {__index = function() return 'from_meta' end}
            setmetatable(t, mt)
            assert(t.x == 'from_meta')
            assert(rawget(t, 'x') == nil)
        """,
            )

            // Numeric indices
            execute(
                """
            local t = {10, 20, 30}
            assert(rawget(t, 1) == 10)
            assert(rawget(t, 2) == 20)
            assert(rawget(t, 3) == 30)
        """,
            )
        }

    @Test
    fun testRawset() =
        runTest {
            // Basic rawset without metamethods
            execute(
                """
            local t = {}
            rawset(t, 'a', 10)
            rawset(t, 'b', 20)
            assert(t.a == 10 and t.b == 20)
        """,
            )

            // rawset bypasses __newindex metamethod
            execute(
                """
            local t = {}
            local other = {}
            local mt = {__newindex = other}
            setmetatable(t, mt)
            t.x = 100  -- goes to 'other'
            assert(t.x == nil and other.x == 100)
            rawset(t, 'y', 200)  -- bypasses metamethod
            assert(t.y == 200 and other.y == nil)
        """,
            )

            // rawset returns the table
            execute(
                """
            local t = {}
            local result = rawset(t, 'a', 5)
            assert(result == t)
        """,
            )
        }

    @Test
    fun testRawlen() =
        runTest {
            // String length
            execute("assert(rawlen('hello') == 5)")
            execute("assert(rawlen('') == 0)")
            execute("assert(rawlen('Lua 5.4') == 7)")

            // Table length (array part only)
            execute("assert(rawlen({10, 20, 30}) == 3)")
            execute("assert(rawlen({}) == 0)")
            execute("assert(rawlen({1, 2, 3, 4, 5}) == 5)")

            // rawlen bypasses __len metamethod
            execute(
                """
            local t = {10, 20, 30}
            local mt = {__len = function() return 999 end}
            setmetatable(t, mt)
            assert(#t == 999)
            assert(rawlen(t) == 3)
        """,
            )

            // Tables with holes
            execute("assert(rawlen({1, 2, nil, 4}) >= 2)") // Length is undefined with holes
        }

    @Test
    fun testRawequal() =
        runTest {
            // Equal values
            execute("assert(rawequal(10, 10))")
            execute("assert(rawequal('hello', 'hello'))")
            execute("assert(rawequal(true, true))")
            execute("assert(rawequal(nil, nil))")

            // Different values
            execute("assert(not rawequal(10, 20))")
            execute("assert(not rawequal('a', 'b'))")
            execute("assert(not rawequal(true, false))")
            execute("assert(not rawequal(10, '10'))")

            // Same table reference
            execute(
                """
            local t = {a = 1}
            assert(rawequal(t, t))
        """,
            )

            // Different table objects (even with same content)
            execute(
                """
            local t1 = {a = 1}
            local t2 = {a = 1}
            assert(not rawequal(t1, t2))
        """,
            )

            // rawequal bypasses __eq metamethod
            execute(
                """
            local t1 = {}
            local t2 = {}
            local mt = {__eq = function() return true end}
            setmetatable(t1, mt)
            setmetatable(t2, mt)
            assert(t1 == t2)  -- uses metamethod
            assert(not rawequal(t1, t2))  -- bypasses metamethod
        """,
            )
        }

    @Test
    fun testSetmetatable() =
        runTest {
            // TODO: Test setmetatable()
            skipTest("setmetatable not implemented")
        }

    @Test
    fun testGetmetatable() =
        runTest {
            // TODO: Test getmetatable()
            skipTest("getmetatable not implemented")
        }
}
