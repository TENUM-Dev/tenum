package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Integration tests for the debug library.
 *
 * PHASE 6.4: Advanced Features - Debug Library
 *
 * This suite contains cross-cutting integration tests that verify
 * multiple debug library components working together.
 *
 * Domain-specific tests are in:
 * - FunctionInfoCompatTest - debug.getinfo
 * - LocalVariablesCompatTest - debug.getlocal/setlocal
 * - UpvaluesCompatTest - debug.getupvalue/setupvalue/upvalueid/upvaluejoin
 * - TracebackCompatTest - debug.traceback
 * - HooksCompatTest - debug.sethook/gethook
 * - UserValuesCompatTest - debug.setuservalue/getuservalue
 *
 * STATUS: 13/26 tests passing (stub implementation)
 *
 * VM REQUIREMENTS for full implementation:
 * 1. Stack frame metadata: Track call stack with function info + line numbers
 * 2. Local variable names: Store in Proto, expose at runtime
 * 3. Upvalue names: Store in Proto, expose in closures
 * 4. Execution hooks: Line/call/return event system in VM
 * 5. Source mapping: Map bytecode PC to source line numbers
 */
class DebugLibCompatTest : LuaCompatTestBase() {
    // ========== Long String Parsing with Line Endings ==========

    @Test
    fun testLongStringWithCarriageReturnLineEndings() =
        runTest {
            // Test from literals.lua:252 - the exact failing case
            // When string.gsub replaces \n with \r and then load() is called,
            // the resulting string has actual \r bytes (char code 13) in it
            // The bug: lexer fails to parse code with \r inside long strings and escape sequences

            execute(
                """
                local function dostring(x) return assert(load(x), "")() end
                
                local prog = [[local a = 1        -- a comment
local b = 2
x = [=[
hi
]=]
y = "\
hello\r\n\
"
return require"debug".getinfo(1).currentline]]
                
                -- Replace all \n with \r - this creates a string with \r bytes (13)
                local progr = string.gsub(prog, "\n", "\r")
                
                -- This should work - Lua 5.4 treats \r as a valid line ending
                dostring(progr)
            """,
            )
        }

    @Test
    fun testActiveLines() =
        runTest {
            // Test activelines from debug.getinfo (option 'L')
            // This should return a table mapping line numbers to true
            execute(
                """local testline = 2
                local function test()  -- line 2
                    local x = 1        -- line 3
                    return x + 1       -- line 4
                end                    -- line 5
                
                local b = debug.getinfo(test, "SfL")
                
                -- Check that linedefined and lastlinedefined are correct
                assert(b.linedefined == testline, "linedefined should be " .. testline .. ", got " .. tostring(b.linedefined))
                assert(b.lastlinedefined == b.linedefined + 3, "lastlinedefined should be " .. (b.linedefined + 3) .. ", got " .. tostring(b.lastlinedefined))
                
                -- Check that activelines table exists
                assert(b.activelines ~= nil, "activelines should not be nil")
                assert(type(b.activelines) == "table", "activelines should be a table, got " .. type(b.activelines))
                
                -- Check that linedefined+1 (line 3) is in activelines
                assert(b.activelines[b.linedefined + 1] == true, "line " .. (b.linedefined + 1) .. " should be active")
                
                -- Check that lastlinedefined (line 5) is in activelines
                assert(b.activelines[b.lastlinedefined] == true, "line " .. b.lastlinedefined .. " should be active")
            """,
            )
        }

    @Test
    fun testDebugInfoAllConditions() =
        runTest {
            // Isolated test for the exact failing assertion from db.lua:42
            // Tests all 6 conditions that must be true
            // Function must span exactly 11 lines (linedefined to lastlinedefined)
            execute(
                """
                local testline = 3
                local function test()
                  local x = 1
                  local y = 2
                  if x then
                    y = 3
                  else
                    y = 4
                  end
                  y = y + 1
                  return y
                end
                
                local b = debug.getinfo(test, 'SfL')
                
                -- Detailed condition checking
                print('Checking conditions:')
                print('1. b.name == nil:', b.name == nil, '(got:', b.name, ')')
                print('2. b.what == "Lua":', b.what == 'Lua', '(got:', b.what, ')')
                print('3. b.linedefined == testline:', b.linedefined == testline, '(', b.linedefined, '==', testline, ')')
                print('4. b.lastlinedefined == b.linedefined + 10:', b.lastlinedefined == b.linedefined + 10, '(', b.lastlinedefined, '==', b.linedefined + 10, ')')
                print('5. b.func == test:', b.func == test)
                print('6. not string.find(b.short_src, "%%["):', not string.find(b.short_src, '%['), '(short_src:', b.short_src, ')')
                
                -- The actual assertion from db.lua:42
                assert(b.name == nil and b.what == "Lua" and b.linedefined == testline and
                       b.lastlinedefined == b.linedefined + 10 and
                       b.func == test and not string.find(b.short_src, "%["),
                       "debug.getinfo conditions failed")
            """,
            )
        }
}
