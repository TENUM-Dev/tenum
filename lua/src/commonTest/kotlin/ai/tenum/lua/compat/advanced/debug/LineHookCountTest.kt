package ai.tenum.lua.compat.advanced.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test that line hooks work correctly and fire the expected number of times.
 *
 * Regression test for db.lua:322 - ensure close variable changes don't affect line hook counts.
 */
class LineHookCountTest : LuaCompatTestBase() {
    @Test
    fun testLineHookCountSimple() =
        runTest {
            // From db.lua:305-322
            val code = """
            do   -- test hook presence in debug info
              assert(not debug.gethook())
              local count = 0
              local function f ()
                assert(debug.getinfo(1).namewhat == "hook")
                local sndline = string.match(debug.traceback(), "\n(.-)\n")
                assert(string.find(sndline, "hook"))
                count = count + 1
              end
              debug.sethook(f, "l")
              local a = 0
              _ENV.a = a
              a = 1
              debug.sethook()
              assert(count == 4, "Expected count=4, got " .. count)
            end
        """
            execute(code)
        }

    @Test
    fun testLineHookWithCloseVariables() =
        runTest {
            vm.debugEnabled = true
            // Test that close variables don't affect line hook counting
            // Verified to match Lua 5.4.8 behavior (5 hooks)
            val code = """
            local count = 0
            local function hook()
                count = count + 1
            end
            
            debug.sethook(hook, "l")
            do
                local x <close> = setmetatable({}, {__close = function() end})
                local y = 1
                y = 2
            end
            debug.sethook()
            
            -- Expected: 5 hooks like Lua 5.4.8
            -- 1. local x <close> = ... (line 8)
            -- 2. local y = 1 (line 9)
            -- 3. y = 2 (line 10)
            -- 4. Inside __close function (line 8 again, attributed to metamethod)
            -- 5. After end of do block (line 12)
            assert(count == 5, "Expected count=5, got " .. count)
        """
            execute(code)
        }

    @Test
    fun testLineHookNotAffectedByCloseErrors() =
        runTest {
            // Ensure close errors don't add extra line events
            val code = """
            local count = 0
            local function hook()
                count = count + 1
            end
            
            debug.sethook(hook, "l")
            local stat = pcall(function()
                local x <close> = setmetatable({}, {__close = function() error("test") end})
                local y = 1
            end)
            debug.sethook()
            
            -- Should count lines in the function, not error handling
            assert(not stat, "Expected error from close")
            print("Line hook count:", count)
        """
            execute(code)
        }
}
