package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test label scoping rules for goto statements.
 * Labels are block-scoped and cannot be jumped to from outside their block.
 */
class GotoLabelScopeTest : LuaCompatTestBase() {
    @Test
    fun testCannotGotoLabelInsideBlock() {
        // This should fail to compile: cannot see label inside block
        val code = """
            local function errmsg(code, m)
              local st, msg = load(code)
              print("st:", st, "msg:", msg)
              assert(not st, "load() should have failed but returned: " .. tostring(st))
              assert(string.find(msg, m), "Error should contain '" .. m .. "', got: " .. msg)
            end
            
            errmsg([[ goto l1; do ::l1:: end ]], "label")
        """

        execute(code)
    }

    @Test
    fun testLoadShouldRejectGotoToLabelInBlock() {
        // Direct test: load() should return nil + error message
        execute(
            """
            local st, msg = load([[ goto l1; do ::l1:: end ]])
            
            -- st should be nil (compilation should fail)
            assert(st == nil, "load() should return nil for invalid goto, but got: " .. tostring(st))
            
            -- msg should contain error about label
            assert(type(msg) == "string", "Error message should be a string, got: " .. type(msg))
            assert(string.find(msg, "label"), "Error message should mention 'label', got: " .. msg)
        """,
        )
    }

    @Test
    fun testCannotGotoLabelThatWasInBlock() {
        // Label defined inside block, then goto after block ends
        // The label should not be visible outside the block
        execute(
            """
            local st, msg = load([[ do ::l1:: end goto l1; ]])
            
            assert(st == nil, "load() should return nil, got: " .. tostring(st))
            assert(string.find(msg, "label"), "Error should mention 'label', got: " .. msg)
        """,
        )
    }

    @Test
    fun testCannotJumpOverLocalVariableDefinition() {
        // Cannot jump over local variable definition
        // This should fail with an error about jumping over 'local'
        execute(
            """
            local st, msg = load([[ goto l1; local aa ::l1:: ::l2:: print(3) ]], "test", "t", _ENV)
            
            print("st:", st, "msg:", msg)
            assert(st == nil, "load() should return nil for jumping over local, got: " .. tostring(st))
            assert(string.find(msg, "local"), "Error should mention 'local', got: " .. msg)
        """,
        )
    }

    @Test
    fun testDirectCompileJumpOverLocal() {
        // Direct compilation test with debug enabled
        val compiler =
            ai.tenum.lua.compiler
                .Compiler(sourceFilename = "test", debugEnabled = true)
        val lexer =
            ai.tenum.lua.lexer
                .Lexer("goto l1; local aa ::l1:: print(3)")
        val tokens = lexer.scanTokens()
        val parser =
            ai.tenum.lua.parser
                .Parser(tokens)
        val ast = parser.parse()

        try {
            val proto = compiler.compile(ast)
            kotlin.test.fail("Should have thrown ParserException, but got proto with ${proto.instructions.size} instructions")
        } catch (e: ai.tenum.lua.parser.ParserException) {
            // Expected
            println("Got expected error: ${e.message}")
            kotlin.test.assertTrue(
                e.message?.contains("local") == true || e.message?.contains("scope") == true,
                "Error should mention 'local' or 'scope', got: ${e.message}",
            )
        }
    }
}
