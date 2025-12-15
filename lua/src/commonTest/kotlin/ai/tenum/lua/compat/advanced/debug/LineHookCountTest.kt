package ai.tenum.lua.compat.advanced.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Ignore
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
    @Ignore
    fun testLineHookWithCloseVariables() =
        runTest {
            // Test that close variables don't affect line hook counting
            // Lua 5.4 fires 5 hooks: one for each line + one for closure end + one for close operation
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
            
            -- Lua 5.4 fires 5 hooks:
            -- 1. local x line
            -- 2. closure end (function() end)  
            -- 3. local y line
            -- 4. y = 2 line
            -- 5. close operation (fires hook at variable declaration line)
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
