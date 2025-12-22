package ai.tenum.lua.compat.advanced.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Focused test for db.lua:322 - debug.getinfo and traceback inside hooks
 */
class Db322FocusedTest : LuaCompatTestBase() {
    @Test
    fun testDebugGetinfoInHook() =
        runTest {
            // Test that debug.getinfo(1).namewhat returns "hook" when called from inside a hook
            execute(
                """
            do
              local count = 0
              local function f()
                local info = debug.getinfo(1)
                assert(info, "debug.getinfo(1) returned nil")
                assert(info.namewhat == "hook", "Expected namewhat='hook', got '" .. tostring(info.namewhat) .. "'")
                count = count + 1
              end
              debug.sethook(f, "l")
              local a = 0
              _ENV.a = a
              a = 1
              debug.sethook()
              assert(count == 4, "Expected count=4, got " .. count)
            end
        """,
            )
        }

    @Test
    fun testDebugTracebackInHook() =
        runTest {
            // Test that debug.traceback() contains "hook" in the second line when called from inside a hook
            execute(
                """
            do
              local count = 0
              local function f()
                local tb = debug.traceback()
                local sndline = string.match(tb, "\n(.-)\n")
                assert(sndline, "Could not extract second line from traceback")
                assert(string.find(sndline, "hook"), "Second line doesn't contain 'hook': " .. sndline)
                count = count + 1
              end
              debug.sethook(f, "l")
              local a = 0
              _ENV.a = a
              a = 1
              debug.sethook()
              assert(count == 4, "Expected count=4, got " .. count)
            end
        """,
            )
        }

    @Test
    fun testExactDb322Code() =
        runTest {
            // Exact code from db.lua:308-323
            execute(
                """
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
              assert(count == 4)
            end
        """,
            )
        }
}
