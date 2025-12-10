package ai.tenum.lua.vm

// CPD-OFF: test file with intentional compilation test setup duplications

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test that load() properly returns multiple values (nil, error_message) on syntax errors
 */
class LoadMultipleReturnsTest : LuaCompatTestBase() {
    @Test
    fun testBytecodeForLocalDeclarationWithMultiReturn() {
        // Check that `local st, err = load(...)` generates correct bytecode
        val code =
            """
            local st, err = load("test")
            """.trimIndent()

        val lexer = Lexer(code)
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        val ast = parser.parse()
        val compiler = Compiler()
        val proto = compiler.compile(ast)

        // Find the CALL instruction
        val callInstr = proto.instructions.firstOrNull { it.opcode.name == "CALL" }
        assertNotNull(callInstr, "Should have a CALL instruction")

        // For `local st, err = load(...)`, the CALL should have c=3 (capture 2 results)
        // c=1 means 0 results, c=2 means 1 result, c=3 means 2 results
        assertEquals(
            3,
            callInstr.c,
            "CALL instruction should have c=3 to capture 2 results for two local variables, got c=${callInstr.c}",
        )
    }

    @Test
    fun testLoadWithGotoError() {
        // Test the actual runtime behavior
        val result =
            vm.execute(
                """
                local st, err = load([[
                    ::A:: a = 1
                    ::A::
                ]])
                -- Check that we got two values
                assert(st == nil, "st should be nil, got: " .. tostring(st))
                assert(type(err) == "string", "err should be a string, got: " .. type(err))
                assert(string.find(err, "label"), "err should mention 'label', got: " .. tostring(err))
                return true
                """.trimIndent(),
            )

        assertTrue(result.toString() == "true", "Test should pass")
    }

    @Test
    fun testLoadWithGotoErrorInNestedFunction() {
        // Test the actual runtime behavior in a nested function (like errors.lua:74)
        // THIS IS THE FAILING CASE
        val result =
            vm.execute(
                """
                local function checksyntax(prog, msg, line)
                    local st, err = load(prog)
                    assert(st == nil, "st should be nil")
                    assert(type(err) == "string", "err should be string, got: " .. type(err))
                    assert(string.find(err, "line " .. line), "should find 'line " .. line .. "' in: " .. tostring(err))
                    assert(string.find(err, msg, 1, true), "should find '" .. msg .. "' in: " .. tostring(err))
                end
                
                checksyntax([[
                    ::A:: a = 1
                    ::A::
                ]], "label 'A' already defined", 1)
                
                return true
                """.trimIndent(),
            )

        assertTrue(result.toString() == "true", "Test should pass")
    }

    @Test
    fun testErrorsLuaExactCode() {
        // Run the EXACT code from errors.lua:72-81
        val result =
            vm.execute(
                """
                do   -- testing errors in goto/break
                  local function checksyntax (prog, msg, line)
                    local st, err = load(prog)
                    assert(string.find(err, "line " .. line))
                    assert(string.find(err, msg, 1, true))
                  end
                
                  checksyntax([[
                    ::A:: a = 1
                    ::A::
                  ]], "label 'A' already defined", 1)
                end
                return true
                """.trimIndent(),
            )

        assertTrue(result.toString() == "true", "Test should pass")
    }

    @Test
    fun testGotoNoVisibleLabelError() {
        // Test the "no visible label" error for goto (from errors.lua:84-87)
        val result =
            vm.execute(
                """
                local function checksyntax(prog, msg, line)
                    local st, err = load(prog)
                    assert(st == nil, "st should be nil")
                    assert(type(err) == "string", "err should be string, got: " .. type(err))
                    assert(string.find(err, "line " .. line), "should find 'line " .. line .. "' in: " .. tostring(err))
                    assert(string.find(err, msg, 1, true), "should find '" .. msg .. "' in: " .. tostring(err))
                end
                
                checksyntax([[
                    a = 1
                    goto A
                    do ::A:: end
                ]], "no visible label 'A'", 2)
                
                return true
                """.trimIndent(),
            )

        assertTrue(result.toString() == "true", "Test should pass")
    }
}
