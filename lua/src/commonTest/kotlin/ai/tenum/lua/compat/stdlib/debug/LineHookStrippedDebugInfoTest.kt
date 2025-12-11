package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test for line hooks with stripped debug information.
 *
 * This tests the Lua 5.4.0 bug fix: line hooks in stripped code should fire
 * but receive nil as the line parameter (not a number).
 *
 * Test based on db.lua:1004-1019
 */
class LineHookStrippedDebugInfoTest : LuaCompatTestBase() {
    @Test
    fun testLineHookWithStrippedDebugInfo() =
        runTest {
            // Test from db.lua:1004-1019
            // When code is stripped of debug info (string.dump(func, true)),
            // line hooks should still fire but receive nil for the line parameter
            execute(
                """
                local function foo ()
                    local a = 1
                    local b = 2
                    return b
                end

                local s = load(string.dump(foo, true))
                local line = true
                debug.sethook(function (e, l)
                    assert(e == "line")
                    line = l
                end, "l")
                assert(s() == 2); debug.sethook(nil)
                assert(line == nil, "Hook should receive nil for line when debug info is stripped, got: " .. tostring(line))
                """,
            )
        }

    @Test
    fun testLineHookWithDebugInfo() =
        runTest {
            // Verify that WITH debug info, line hooks receive a number (not nil)
            execute(
                """
                local function foo ()
                    local a = 1
                    local b = 2
                    return b
                end

                local s = load(string.dump(foo, false))  -- false = keep debug info
                local line = nil
                local hook_called = false
                debug.sethook(function (e, l)
                    assert(e == "line", "Expected event='line', got: " .. tostring(e))
                    hook_called = true
                    line = l  -- capture the line parameter
                end, "l")
                local result = s()
                debug.sethook(nil)
                
                -- Result should still be correct
                assert(result == 2, "Function should return 2, got: " .. tostring(result))
                
                -- Hook should have been called
                assert(hook_called, "Hook should have been called")
                
                -- Line parameter should be a number when debug info is present
                assert(type(line) == "number", "Hook should receive a number for line when debug info is present, got: " .. type(line))
                assert(line > 0, "Line number should be > 0, got: " .. tostring(line))
                """,
            )
        }
}
