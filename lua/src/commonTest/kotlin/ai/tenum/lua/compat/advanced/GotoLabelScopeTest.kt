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
}
