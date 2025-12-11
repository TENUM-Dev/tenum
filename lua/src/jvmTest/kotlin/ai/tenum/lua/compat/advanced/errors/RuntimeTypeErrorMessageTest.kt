package ai.tenum.lua.compat.advanced.errors

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests for runtime type error messages.
 *
 * Coverage:
 * - Arithmetic operation errors
 * - Bitwise operation errors
 * - Comparison errors
 * - Type coercion errors
 * - Variable name hints in errors (global, local, upvalue, field, method)
 * - Integer conversion errors
 * - Division by zero
 * - Numeric for loop errors
 * - String function argument errors
 * - Custom type names in errors
 */
class RuntimeTypeErrorMessageTest : LuaCompatTestBase() {
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
    fun testArithmeticErrorMessage() =
        runTest {
            // Test from errors.lua line 126
            // Arithmetic on non-numeric types should produce error with "arithmetic" in message
            execute("""checkmessage("a = {} + 1", "arithmetic")""")
        }

    @Test
    fun testBitwiseErrorMessage() =
        runTest {
            // Test from errors.lua line 127
            // Bitwise operation on non-integer types should produce error with "bitwise operation"
            execute("""checkmessage("a = {} | 1", "bitwise operation")""")
        }

    @Test
    fun testComparisonErrorMessage() =
        runTest {
            // Test from errors.lua line 128-129
            // Comparison of incompatible types should produce error with "attempt to compare"
            execute(
                """
                checkmessage("a = {} < 1", "attempt to compare")
                checkmessage("a = {} <= 1", "attempt to compare")
            """,
            )
        }

    @Test
    fun testCallNumberErrorMessage() =
        runTest {
            // Test from errors.lua line 130
            // Attempting to call a number should produce error mentioning the variable name
            // NOTE: Lua 5.4 says "global 'bbbb'", but due to how our compiler generates bytecode,
            // we may see "field 'bbbb'" instead. Both are acceptable as long as we include context.
            execute(
                """
            local function checkmessage_custom(prog, pattern)
                local m = doit(prog)
                -- Accept either "global 'bbbb'" or "field 'bbbb'" as both indicate the variable
                local hasGlobal = string.find(m, "global 'bbbb'", 1, true)
                local hasField = string.find(m, "field 'bbbb'", 1, true)
                assert(hasGlobal or hasField, "Expected 'global \\'bbbb\\'' or 'field \\'bbbb\\'' in error message, got: " .. tostring(m))
            end
            
            checkmessage_custom("aaa=1; bbbb=2; aaa=math.sin(3)+bbbb(3)", "bbbb")
        """,
            )
        }

    @Test
    fun testFieldNamedEnvErrorMessage() =
        runTest {
            // Test from errors.lua line 138
            // When accessing a nil field named "_ENV", error should say "field 'x'" not "global 'x'"
            // Bug in Lua 5.4.6 fixed - field named "_ENV" should not be confused with _ENV upvalue
            execute("""checkmessage("a = {_ENV = {}}; print(a._ENV.x + 1)", "field 'x'")""")
        }

    @Test
    fun testStringFieldAccessErrorMessage() =
        runTest {
            // Test from errors.lua line 141
            // When accessing a field on a string, error should say "field 'x'" not "global 'x'"
            // Bug in Lua 5.4.0-5.4.6 fixed in 5.4.7 - string with name "_ENV" should not be confused with _ENV upvalue
            execute("""checkmessage("print(('_ENV').x + 1)", "field 'x'")""")
        }

    @Test
    fun testCallNilLocalErrorMessage() =
        runTest {
            // Test from errors.lua line 147
            // Calling a nil local variable should say "local 'a'"
            execute("""checkmessage("local a; a(13)", "local 'a'")""")
        }

    @Test
    fun testMetamethodErrorMessage() =
        runTest {
            // Test from errors.lua line 148-151
            // When metamethod is not callable, error should mention "metamethod 'add'"
            execute(
                """
            checkmessage([[
              local a = setmetatable({}, {__add = 34})
              a = a + 1
            ]], "metamethod 'add'")
        """,
            )
        }

    @Test
    fun testComparisonMetamethodNotCallable() =
        runTest {
            // Test from errors.lua line 153-155
            // When comparison metamethod exists but is not callable, error should mention "metamethod 'lt'"
            execute(
                """
            checkmessage([[
              local a = setmetatable({}, {__lt = {}})
              a = a > a
            ]], "metamethod 'lt'")
        """,
            )
        }

    @Test
    fun testIndexNilGlobalErrorMessage() =
        runTest {
            // Test from errors.lua line 166
            // When indexing a nil global variable, error should mention "global 'aaa'" not "field 'bbb'"
            execute("""checkmessage("aaa.bbb:ddd(9)", "global 'aaa'")""")
        }

    @Test
    fun testMethodCallOnNonTableErrorMessage() =
        runTest {
            // Test from errors.lua line 167
            // SELF opcode should error when trying to index a non-table value for method call
            // Lua 5.4: "attempt to index a number value (field 'bbb')"
            execute(
                """
            -- When aaa.bbb = 1 (number), calling aaa.bbb:ddd() should error
            -- Error should mention "field 'bbb'" to indicate which field contained the non-indexable value
            checkmessage("local aaa={bbb=1}; aaa.bbb:ddd(9)", "field 'bbb'")
        """,
            )
        }

    @Test
    fun testSetTableUpvalueErrorMessage() =
        runTest {
            // Test from errors.lua line 174
            // SETTABLE opcode should include name hint when indexing nil upvalue
            // Lua 5.4: "attempt to index a nil value (upvalue 'a')"
            execute("""checkmessage("local a,b,cc; (function () a.x = 1 end)()", "upvalue 'a'")""")
        }

    @Test
    fun testGetTableUpvalueErrorMessage() =
        runTest {
            // GETTABLE opcode should include name hint when indexing nil upvalue
            // Lua 5.4: "attempt to index a nil value (upvalue 'a')"
            execute("""checkmessage("local a,b,cc; (function () local x = a.field end)()", "upvalue 'a'")""")
        }

    @Test
    fun testArithmeticGlobalErrorMessage() =
        runTest {
            vm.debugEnabled = true
            // Test from errors.lua line 176
            // ADD opcode should include name hint when performing arithmetic on nil global
            // Lua 5.4: "attempt to perform arithmetic on a nil value (global 'a')"
            execute("""checkmessage("local _ENV = {x={}}; a = a + 1", "global 'a'")""")
        }

    @Test
    fun testArithmeticLocalErrorMessage() =
        runTest {
            // Test from errors.lua line 178
            // Arithmetic on nil local variable should say "local 'aaa'"
            execute("""checkmessage("BB=1; local aaa={}; x=aaa+BB", "local 'aaa'")""")
        }

    @Test
    fun testDivisionGlobalErrorMessage() =
        runTest {
            // Test from errors.lua line 179
            // Division with nil global should say "global 'aaa'"
            execute("""checkmessage("aaa={}; x=3.3/aaa", "global 'aaa'")""")
        }

    @Test
    fun testMultiplicationGlobalErrorMessage() =
        runTest {
            // Test from errors.lua line 180
            // Multiplication with nil global should say "global 'BB'"
            execute("""checkmessage("aaa=2; BB=nil;x=aaa*BB", "global 'BB'")""")
        }

    @Test
    fun testUnaryMinusGlobalErrorMessage() =
        runTest {
            // Test from errors.lua line 181
            // Unary minus on table global should say "global 'aaa'"
            execute("""checkmessage("aaa={}; x=-aaa", "global 'aaa'")""")
        }

    @Test
    fun testShortCircuitLocalErrorMessage() =
        runTest {
            // Test from errors.lua line 184-185
            // Short circuit evaluation should preserve name hints for locals
            execute(
                """
                checkmessage("aaa=1; local aaa,bbbb=2,3; aaa = math.sin(1) and bbbb(3)", "local 'bbbb'")
                checkmessage("aaa=1; local aaa,bbbb=2,3; aaa = bbbb(1) or aaa(3)", "local 'bbbb'")
            """,
            )
        }

    @Test
    fun testTailCallFieldErrorMessage() =
        runTest {
            // Test from errors.lua line 158-159
            // Tail calls should preserve name hints
            execute(
                """
                checkmessage("local a={}; return a.bbbb(3)", "field 'bbbb'")
                checkmessage("aaa={}; do local aaa=1 end; return aaa:bbbb(3)", "method 'bbbb'")
            """,
            )
        }

    @Test
    fun testUpvalueArithmeticErrorMessage() =
        runTest {
            // Test from errors.lua line 169
            // Arithmetic on nil upvalue should say "upvalue 'b'"
            execute("""checkmessage("local a,b,c; (function () a = b+1.1 end)()", "upvalue 'b'")""")
        }

    @Test
    fun testUpvalueIndexErrorMessage() =
        runTest {
            // Test from errors.lua line 172-173
            // Indexing nil upvalue should say "upvalue 'cc'" or "upvalue 'a'"
            execute("""checkmessage("local a,b,cc; (function () a = cc[1] end)()", "upvalue 'cc'")""")
        }

    @Test
    fun testLengthOperatorErrorMessages() =
        runTest {
            // Test from errors.lua line 161-162
            // Length operator on non-indexable values
            execute(
                """
                checkmessage("aaa = #print", "length of a function value")
                checkmessage("aaa = #3", "length of a number value")
            """,
            )
        }

    @Test
    fun testConcatenationErrorMessages() =
        runTest {
            // Test from errors.lua line 313-315
            // Concatenation type errors
            execute(
                """
                checkmessage([[x = print .. "a"]], "concatenate")
                checkmessage([[x = "a" .. false]], "concatenate")
                checkmessage([[x = {} .. 2]], "concatenate")
            """,
            )
        }

    @Test
    fun testComparisonTypeErrorMessages() =
        runTest {
            // Test from errors.lua line 194-197
            // Type errors in comparisons with descriptive messages
            execute(
                """
                checkmessage("print(print < 10)", "function with number")
                checkmessage("print(print < print)", "two function values")
                checkmessage("print('10' < 10)", "string with number")
                checkmessage("print(10 < '23')", "number with string")
            """,
            )
        }

    @Test
    fun testFloatToIntegerConversionErrors() =
        runTest {
            // Test from errors.lua line 200-214
            // Float values that can't be converted to integers for bitwise operations
            execute(
                """
                checkmessage("local a = 2.0^100; x = a << 2", "local a")
                checkmessage("local a = 1 >> 2.0^100", "has no integer representation")
                checkmessage("local a = 10.1 << 2.0^100", "has no integer representation")
                checkmessage("local a = 2.0^100 & 1", "has no integer representation")
                checkmessage("local a = 2.0^100 & 1e100", "has no integer representation")
                checkmessage("local a = 2.0 | 1e40", "has no integer representation")
                checkmessage("local a = 2e100 ~ 1", "has no integer representation")
            """,
            )
        }

    @Test
    fun testStringFunctionIntegerConversionErrors() =
        runTest {
            vm.debugEnabled = true
            // Test from errors.lua line 208-209
            // String functions requiring integer arguments
            execute(
                """
                checkmessage("string.sub('a', 2.0^100)", "has no integer representation")
                checkmessage("string.rep('a', 3.3)", "has no integer representation")
            """,
            )
        }

    @Test
    fun testBitwiseNotIntegerConversionErrors() =
        runTest {
            // Test from errors.lua line 210-211, 213-214
            // Bitwise NOT requires integer conversion
            execute(
                """
                checkmessage("return ~-3e40", "has no integer representation")
                checkmessage("return ~-3.009", "has no integer representation")
                checkmessage("return 3.009 & 1", "has no integer representation")
                checkmessage("return 34 >> {}", "table value")
            """,
            )
        }

    @Test
    fun testBitwiseFieldAccessIntegerConversionErrors() =
        runTest {
            // Test from errors.lua line 292: checkcompt("field 'huge'", "return math.huge << 1")
            // Bitwise operations on non-integer-representable field values should include field name
            execute(
                """
                checkmessage("return math.huge << 1", "field 'huge'")
                checkmessage("return 2.0^63 & 1", "has no integer representation")
                checkmessage("return 1 | 2.0^63", "has no integer representation")
                checkmessage("return 2.3 ~ 0.0", "has no integer representation")
                
                -- Test math.lua line 293 exact case - this is what was failing
                local intbits = 64
                local code = ("return 1 | 2.0^%d"):format(intbits - 1)
                print("Testing line 293 code: " .. code)
                checkmessage(code, "has no integer representation")
            """,
            )
        }

    @Test
    fun testHasIntegerRepresentation() =
        runTest {
            // Unit test for hasIntegerRepresentation boundary behavior
            vm.execute(
                """
                -- Test boundary values
                local function test(value, shouldSucceed, desc)
                    local ok, result = pcall(function() return value & 1 end)
                    if shouldSucceed then
                        assert(ok, desc .. ": expected success but got error: " .. tostring(result))
                    else
                        assert(not ok, desc .. ": expected error but succeeded with: " .. tostring(result))
                        assert(string.find(result, "has no integer representation", 1, true), desc .. ": expected 'has no integer representation' in error")
                    end
                end
                
                -- Values that SHOULD work
                test(0.0, true, "0.0")
                test(1.0, true, "1.0")
                test(-1.0, true, "-1.0")
                test(2.0^62, true, "2.0^62")
                test(-2.0^62, true, "-2.0^62")
                
                -- Values that should FAIL
                test(0.5, false, "0.5 (fractional)")
                test(1.1, false, "1.1 (fractional)")
                test(math.huge, false, "math.huge")
                test(-math.huge, false, "-math.huge")
                test(0/0, false, "NaN")
                test(2.0^63, false, "2.0^63 (out of range)")
                test(2.0^100, false, "2.0^100")
                test(1e40, false, "1e40")
                
                -- Test math.lua line 293 exact case
                local intbits = 64
                test(2.0^(intbits - 1), false, "2.0^63 from intbits-1")
                
                print("All hasIntegerRepresentation tests passed!")
                """.trimIndent(),
                "testHasIntegerRepresentation",
            )
        }

    @Test
    fun testDivisionByZeroErrors() =
        runTest {
            // Test from errors.lua line 215-216
            // Division and modulo by zero
            execute(
                """
                checkmessage("aaa = 24 // 0", "divide by zero")
                checkmessage("aaa = 1 % 0", "'n%0'")
            """,
            )
        }

    @Test
    fun testNumericForLoopErrors() =
        runTest {
            // Test from errors.lua line 225-233
            // Type errors in numeric for loop components
            execute(
                """
                checkmessage("for i = {}, 10 do end", "table")
                checkmessage("for i = {}, 10 do end", "initial value")
                checkmessage("for i = 1, 'x', 10 do end", "limit")
                checkmessage("for i = 1, {} do end", "limit")
                checkmessage("for i = 1, 10, print do end", "step")
            """,
            )
        }

    @Test
    fun testMethodCallWrongSelfType() =
        runTest {
            // Test from errors.lua line 323
            // Method call on wrong type (bad self)
            execute(
                """
            aaa = {}
            setmetatable(aaa, {__index = string})
            checkmessage("aaa:sub()", "bad self")
        """,
            )
        }

    @Test
    fun testStringSubArgumentTypeError() =
        runTest {
            vm.debugEnabled = true
            // Test from errors.lua line 324-325
            // Type errors in string.sub arguments
            execute(
                """
                checkmessage("string.sub('a', {})", "#2")
                checkmessage("('a'):sub{}", "#1")
            """,
            )
        }

    @Test
    fun testCallbackFunctionNameInError() =
        runTest {
            // Test from errors.lua line 327-328
            // Function names in error messages for callbacks
            execute(
                """
                checkmessage("table.sort({1,2,3}, table.sort)", "'table.sort'")
                checkmessage("string.gsub('s', 's', setmetatable)", "'setmetatable'")
            """,
            )
        }

    @Test
    fun testGlobalVariableCallErrors() =
        runTest {
            // Test from errors.lua line 131-133
            // Calling non-functions with context about the variable
            execute(
                """
                checkmessage("aaa=1; bbbb=2; aaa=math.sin(3)+bbbb(3)", "global 'bbbb'")
                checkmessage("aaa={}; do local aaa=1 end aaa:bbbb(3)", "method 'bbbb'")
                checkmessage("local a={}; a.bbbb(3)", "field 'bbbb'")
            """,
            )
        }

    @Test
    fun testIndexedCallNoNameHint() =
        runTest {
            // Test from errors.lua line 134-136
            // When calling through array index, no name hint should be given
            execute(
                """
                assert(not string.find(doit("aaa={13}; local bbbb=1; aaa[bbbb](3)"), "'bbbb'"))
                checkmessage("aaa={13}; local bbbb=1; aaa[bbbb](3)", "number")
            """,
            )
        }

    @Test
    fun testConcatenationWithTableValue() =
        runTest {
            // Test from errors.lua line 136
            // Concatenation error message includes type
            execute("""checkmessage("aaa=(1)..{}", "a table value")""")
        }

    @Test
    fun testMethodCallOnNilGlobal() =
        runTest {
            // Test from errors.lua line 168
            // Method call on nil global variable
            execute("""checkmessage("local aaa={bbb={}}; aaa.bbb:ddd(9)", "method 'ddd'")""")
        }

    @Test
    fun testShortCircuitOperatorNoNameHint() =
        runTest {
            vm.debugEnabled = true
            execute("""assert(not string.find(doit("aaa={}; x=(aaa or aaa)+(aaa and aaa)"), "'aaa'"))""")
        }

    @Test
    fun testShortCircuitOperator2NoNameHint() =
        runTest {
            vm.debugEnabled = true
            execute(
                """
                local msg = doit("aaa={}; (aaa or aaa)()")
                assert(not string.find(msg, "'aaa'"), "Did not expect 'aaa' in error message, got: " .. tostring(msg))
                """.trimIndent(),
            )
        }

    @Test
    fun testMetatableIndexNonTable() =
        runTest {
            // Test from errors.lua line 222
            // Type error for object in metatable __index
            // When __index is not a table or function, indexing should fail with proper error
            execute("""checkmessage("local a = setmetatable({}, {__index = 10}).x", "attempt to index a number value")""")
        }

    @Test
    fun testGlobalFunctionArgumentValidation() =
        runTest {
            // Test from errors.lua line 274-275
            // Global functions should validate argument types and include function name in error
            execute("""checkmessage("(io.write or print){}", "io.write")""")
            execute("""checkmessage("(collectgarbage or print){}", "collectgarbage")""")
        }

    @Test
    fun testArithmeticErrorWithoutDebugInfo() =
        runTest {
            // Test from errors.lua:289 - arithmetic on table with stripped debug info
            // Error message should be: ?:-1: attempt to perform arithmetic on a table value
            execute(
                """
                local function checkerr(msg, f, ...)
                    local st, err = pcall(f, ...)
                    assert(not st and string.find(err, msg), 
                           "Expected error matching '" .. msg .. "', got: " .. tostring(err))
                end
                
                -- Create function that adds 2 to a table
                local f = function () local a; a = {}; return a + 2 end
                -- Strip debug info
                f = assert(load(string.dump(f, true)))
                -- Should error with "?:-1:" prefix and "table value" in message
                checkerr("^%?:%-1:.*table value", f)
            """,
            )
        }

    @Test
    fun testIndexNumberWithGlobalHint() =
        runTest {
            // Test from errors.lua:300 - indexing a number should include "global 'aaa'" hint
            execute(
                """
                -- Set aaa to a number, then try to index it
                aaa = 9
                x = 1
                y = 2
                checkmessage("local t = {d = x and aaa[x or y]}", "global 'aaa'")
            """,
            )
        }

    @Test
    fun testIndexNumberFormLocalWithGlobalHint() =
        runTest {
            // Test from errors.lua:306-314 - indexing a number should include "global 'aaa'" hint
            execute(
                """
                checkmessage([[aaa=9
                repeat until 3==3
                local x=math.sin(math.cos(3))
                if math.sin(1) == x then return math.sin(1) end   -- tail call
                local a,b = 1, {
                  {x='a'..'b'..'c', y='b', z=x},
                  {1,2,3,4,5} or 3+3<=3+3,
                  3+1>3+1,
                  {d = x and aaa[x or y]}}
                ]], "global 'aaa'")
            """,
            )
        }

    @Test
    fun testArithmeticOnNilGlobalSmall() =
        runTest {
            // Test from errors.lua:300 - arithmetic on nil global should include "global 'bbb'" hint
            // This tests the case with many assignments before the error (RK limit)
            vm.debugEnabled = true
            execute(
                """
                local t = {}
                for i = 1, 255 do
                  t[i] = "aaa = x" .. i
                end
                local s = table.concat(t, "; ")
                t = nil
                checkmessage(s.."; aaa = bbb + 1", "global 'bbb'")
            """,
            )
        }

    @Test
    fun testArithmeticOnNilGlobal() =
        runTest {
            // Test from errors.lua:300 - arithmetic on nil global should include "global 'bbb'" hint
            // This tests the case with many assignments before the error (RK limit)
            vm.debugEnabled = true
            execute(
                """
                local t = {}
                for i = 1, 1000 do
                  t[i] = "aaa = x" .. i
                end
                local s = table.concat(t, "; ")
                t = nil
                checkmessage(s.."; aaa = bbb + 1", "global 'bbb'")
            """,
            )
        }

    @Test
    fun testCustomTypeNameInErrorMessage() =
        runTest {
            // Test from errors.lua:249 - io.input should report custom type names
            // When a table has __name metamethod, error messages should use that name
            execute(
                """
                local function doit(s)
                    local f, msg = load(s)
                    if not f then return msg end
                    local cond, msg = pcall(f)
                    return (not cond) and msg
                end
                
                local function checkmessage(prog, msg)
                    local m = doit(prog)
                    assert(string.find(m, msg, 1, true), "Expected '" .. msg .. "' in error message, got: " .. tostring(m))
                end
                
                -- Create a table with custom type name
                _G.XX = setmetatable({}, {__name = "My Type"})
                
                -- io.input should validate FILE* type and report custom type name in error
                checkmessage("io.input(XX)", "(FILE* expected, got My Type)")
            """,
            )
        }

    @Test
    fun testFileMetatableGcMethodNoValue() =
        runTest {
            // Test from errors.lua:340 - __gc() called without self should report "got no value"
            // When calling getmetatable(io.stdin).__gc() without passing self,
            // it should say "bad argument #1 to '__gc' (FILE* expected, got no value)"
            execute(
                """
                checkmessage("getmetatable(io.stdin).__gc()", "no value")
            """,
            )
        }

    @Test
    fun testIOInputHasFileStarName() =
        runTest {
            // io.input() should return a table with FILE* type name
            execute(
                """
                local function doit(s)
                    local f, msg = load(s)
                    if not f then return msg end
                    local cond, msg = pcall(f)
                    return (not cond) and msg
                end
                
                -- Test using the same pattern as errors.lua:241
                local m = doit("math.sin(io.input())")
                print("DEBUG: Error message from doit = " .. tostring(m))
                assert(string.find(m, "FILE*", 1, true), "Should contain 'FILE*', got: " .. tostring(m))
            """,
            )
        }

    @Test
    fun testNamedTypesInMathErrors() =
        runTest {
            // Custom types with __name should appear in math function error messages
            execute(
                """
                -- Create a custom type with __name
                local obj = {}
                setmetatable(obj, {__name = "MyType"})
                
                -- math.sin should report the custom type name in error
                local ok, err = pcall(math.sin, obj)
                assert(not ok, "Should fail")
                assert(string.find(err, "MyType", 1, true), "Should contain custom type name 'MyType', got: " .. tostring(err))
                
                -- For reference: regular table without __name should say "table"
                local tbl = {}
                local ok2, err2 = pcall(math.sin, tbl)
                assert(not ok2, "Should fail")
                assert(string.find(err2, "table", 1, true), "Should contain 'table', got: " .. tostring(err2))
            """,
            )
        }

    @Test
    fun testBitwiseFieldAccessErrorMessage() =
        runTest {
            // Test from math.lua line 288
            // When accessing math.huge in a bitwise operation, error should say "field 'huge'" not "upvalue '_ENV'"
            // This test should FAIL until we fix isGlobalsTable() to find _ENV at ANY upvalue position
            execute("""checkmessage("return math.huge << 1", "field 'huge'")""")
        }
}