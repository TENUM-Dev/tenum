package ai.tenum.lua.compat.advanced.errors

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests for parser and syntax error messages.
 *
 * Coverage:
 * - Parser error message format
 * - Error messages with token information
 * - Token lexemes in error messages
 * - Invalid character handling
 * - Load failure behavior
 */
class ParserErrorMessageTest : LuaCompatTestBase() {
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
            
            function checksyntax (prog, extra, token, line)
              local msg = doit(prog)
              if not string.find(token, "^<%a") and not string.find(token, "^char%(") then
                token = "'"..token.."'"
              end
              token = string.gsub(token, "(%p)", "%%%1")
              local pt = string.format("^%%[string \".*\"%%]:%d: .- near %s$", line, token)
              local match = string.find(msg, pt)
              assert(match, "Pattern should match. Pattern: " .. pt .. " Message: " .. tostring(msg))
            end
            """.trimIndent(),
            "BeforeTest",
        )
    }

    @Test
    fun testParserErrorMessageFormat() =
        runTest {
            // Test that load() returns Lua-compatible error messages
            // Format: [string "..."]:LINE: MESSAGE near TOKEN
            execute(
                """
            local f, err = load([[
  local a = {4

]])
            -- Error should start with [string "..."]
            assert(string.find(err, "^%[string"), "Error should start with '[string': " .. tostring(err))
            
            -- Error should contain line number
            assert(string.find(err, ":3:"), "Error should contain ':3:' for line 3: " .. tostring(err))
            
            -- Error should end with "near <eof>" or "near <token>"
            assert(string.find(err, "near"), "Error should contain 'near': " .. tostring(err))
        """,
            )
        }

    @Test
    fun testParserErrorWithToken() =
        runTest {
            // Test error message contains the token where error occurred
            execute(
                """
            local f, err = load([[
  local a = {4

]])
            -- Should mention the token near which error occurred
            assert(string.find(err, "<eof>") or string.find(err, "near"), 
                   "Error should mention token: " .. tostring(err))
        """,
            )
        }

    @Test
    fun testErrorsLuaChecksyntaxPattern() =
        runTest {
            // Exact test from errors.lua line 65-68
            // Tests that parser errors match Lua's standard format
            execute(
                """
            local function doit(s)
                local f, msg = load(s)
                if not f then return msg end
                local cond, msg = pcall(f)
                return (not cond) and msg
            end
            
            local function checksyntax(prog, extra, token, line)
                local msg = doit(prog)
                
                if not string.find(token, "^<%a") and not string.find(token, "^char%(") then
                    token = "'"..token.."'"
                end
                token = string.gsub(token, "(%p)", "%%%1")
                
                local pt = string.format("^%%[string \".*\"%%]:%d: .- near %s$", line, token)
                
                local match = string.find(msg, pt)
                assert(match, "Pattern should match. Pattern: " .. pt .. " Message: " .. tostring(msg))
            end
            
            checksyntax([[
  local a = {4

]], "'}' expected (to close '{' at line 1)", "<eof>", 3)
        """,
            )
        }

    @Test
    fun testErrorTokensShowLexemeInQuotes() =
        runTest {
            // Test from errors.lua lines 609-614
            // Tests that error messages show actual token lexemes in quotes
            // This ensures literal values (numbers, strings, identifiers) appear
            // as 'lexeme' instead of generic <type> in error messages
            execute(
                """
            local function doit(s)
                local f, msg = load(s)
                if not f then return msg end
                local cond, msg = pcall(f)
                return (not cond) and msg
            end
            
            local function checksyntax(prog, extra, token, line)
                local msg = doit(prog)
                
                if not string.find(token, "^<%a") and not string.find(token, "^char%(") then
                    token = "'"..token.."'"
                end
                token = string.gsub(token, "(%p)", "%%%1")
                
                local pt = string.format("^%%[string \".*\"%%]:%d: .- near %s$", line, token)
                
                local match = string.find(msg, pt)
                assert(match, "Pattern should match. Pattern: " .. pt .. " Message: " .. tostring(msg))
            end
            
            -- Test identifier token shows lexeme
            checksyntax("syntax error", "", "error", 1)
            
            -- Test number literal shows lexeme (line 610 from errors.lua)
            checksyntax("1.000", "", "1.000", 1)
            
            -- Test long string literal shows lexeme with delimiters
            checksyntax("[[a]]", "", "[[a]]", 1)
            
            -- Test short string literal shows lexeme with quotes
            checksyntax("'aa'", "", "'aa'", 1)
            
            -- Test operator tokens show lexeme
            checksyntax("while << do end", "", "<<", 1)
            checksyntax("for >> do end", "", ">>", 1)
        """,
            )
        }

    @Test
    fun testInvalidNonPrintableCharInChunk() =
        runTest {
            // Test from errors.lua:617-620 - invalid non-printable chars should be reported with <\NUM> notation
            execute(
                """
                checksyntax("a${'\u0001'}a = 1", "", "<\\1>", 1)
                checksyntax("a${'\u0002'}a = 1", "", "<\\2>", 1)
                checksyntax("a${'\u007F'}a = 1", "", "<\\127>", 1)
                """,
            )
        }

    @Test
    fun testLoadFailureDoesNotSetGlobals() =
        runTest {
            // Test from errors.lua:622-624 - load() failure should not execute code or set globals
            execute(
                """
                I = load("a=9+")  -- syntax error: incomplete expression
                aaa = 3
                
                -- I should be nil (load failed), aaa should be 3 (was set after)
                assert(aaa == 3, "aaa should be 3")
                assert(not I, "I should be nil/false since load failed")
            """,
            )
        }

    @Test
    fun testRepeatedLoadFailures() =
        runTest {
            // Test from errors.lua:627-631 - repeated load failures should not cause issues
            execute(
                """
                local function doit (s)
                  local f, msg = load(s)
                  if not f then return msg end
                  local cond, msg = pcall(f)
                  return (not cond) and msg
                end
                
                -- Run many load failures to ensure stability
                local lim = 100
                for i=1,lim do
                  doit('a = ')  -- incomplete statement
                  doit('a = 4+nil')  -- runtime error
                end
            """,
            )
        }

    @Test
    fun testParserErrorMessagesAreLowercase() =
        runTest {
            // Test from constructs.lua:10 - the checkload function tests parser error messages
            // Parser error messages must contain lowercase "expected" to be compatible with Lua 5.4
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- Test from constructs.lua:403 - parser errors should contain lowercase "expected"
                checkload("for x do", "expected")
            """,
            )
        }

    @Test
    fun testForLoopExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- For loop: '=' or 'in' expected
                checkload("for x do", "'=' or 'in' expected")
                
                -- Numeric for: ',' expected after start value
                checkload("for i = 1 2", "',' expected")
                
                -- Numeric for: 'do' expected
                checkload("for i = 1, 10", "'do' expected")
                
                -- Generic for: 'in' expected
                checkload("for k, v pairs(t)", "'=' or 'in' expected")
                
                -- Generic for: 'do' expected
                checkload("for k, v in pairs(t)", "'do' expected")
                
                -- For loop: 'end' expected
                checkload("for i = 1, 10 do", "'end' expected")
            """,
            )
        }

    @Test
    fun testIfStatementExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- If: 'then' expected
                checkload("if x", "'then' expected")
                
                -- Elseif: 'then' expected
                checkload("if x then elseif y", "'then' expected")
                
                -- If: 'end' expected
                checkload("if x then", "'end' expected")
            """,
            )
        }

    @Test
    fun testWhileLoopExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- While: 'do' expected
                checkload("while x", "'do' expected")
                
                -- While: 'end' expected
                checkload("while x do", "'end' expected")
            """,
            )
        }

    @Test
    fun testRepeatLoopExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- Repeat: 'until' expected
                checkload("repeat", "'until' expected")
            """,
            )
        }

    @Test
    fun testFunctionExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg, 1, true))
                end
                
                -- Function: <name> expected
                checkload("function", "<name> expected")
                
                -- Function: '(' expected
                checkload("function foo", "'(' expected")
                
                -- Function: <name> or '...' expected
                checkload("function foo(", "<name> or '...' expected")
                
                -- Function: 'end' expected
                checkload("function foo()", "'end' expected")
                
                -- Function parameters: <name> or '...' expected
                checkload("function foo(x,", "<name> or '...' expected")
                
                -- Local function: <name> expected
                checkload("local function", "<name> expected")
                
                -- Local function: '(' expected
                checkload("local function foo", "'(' expected")
            """,
            )
        }

    @Test
    fun testDoBlockExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- Do block: 'end' expected
                checkload("do", "'end' expected")
            """,
            )
        }

    @Test
    fun testLocalVariableExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- Local: <name> expected
                checkload("local", "<name> expected")
                
                -- Local with comma: <name> expected
                checkload("local x,", "<name> expected")
                
                -- Local with attribute: <name> expected after '<'
                checkload("local x <", "<name> expected")
                
                -- Local with attribute: '>' expected
                checkload("local x <const", "'>' expected")
            """,
            )
        }

    @Test
    fun testTableConstructorExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- Table: '}' expected
                checkload("x = {1", "'}' expected")
                
                -- Table field: ']' expected
                checkload("x = {[1", "']' expected")
                
                -- Table field: '=' expected
                checkload("x = {[1]", "'=' expected")
            """,
            )
        }

    @Test
    fun testExpressionExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- Parenthesized expression: ')' expected
                checkload("x = (1", "')' expected")
                
                -- Index access: ']' expected
                checkload("x = t[1", "']' expected")
                
                -- Function call: ')' expected
                checkload("x = f(1", "')' expected")
                
                -- Method call: <name> expected
                checkload("x = t:", "<name> expected")
                
                -- Method call: function arguments expected
                checkload("x = t:method", "function arguments expected")
                
                -- Field access: <name> expected
                checkload("x = t.", "<name> expected")
            """,
            )
        }

    @Test
    fun testAssignmentExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- Assignment: '=' expected
                checkload("x, y", "'=' expected")
            """,
            )
        }

    @Test
    fun testLabelAndGotoExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg))
                end
                
                -- Goto: <name> expected
                checkload("goto", "<name> expected")
                
                -- Label: <name> expected
                checkload("::", "<name> expected")
                
                -- Label: '::' expected after name
                checkload("::label", "'::' expected")
            """,
            )
        }

    @Test
    fun testFunctionExpressionExpectedMessages() =
        runTest {
            execute(
                """
                local function checkload(s, msg)
                  assert(string.find(select(2, load(s)), msg, 1, true))
                end
                
                -- Function expression: '(' expected
                checkload("x = function", "'(' expected")
                
                -- Function expression: <name> or '...' expected
                checkload("x = function(", "<name> or '...' expected")
                
                -- Function expression: 'end' expected
                checkload("x = function()", "'end' expected")
            """,
            )
        }
}
