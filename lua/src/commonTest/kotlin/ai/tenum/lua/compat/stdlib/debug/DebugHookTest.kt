package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Unit tests for debug.sethook/gethook functionality
 *
 * Domain: Debug Hooks - Basic Functionality
 */
class DebugHookTest : LuaCompatTestBase() {
    @Test
    fun testLineHookCountBasic() =
        runTest {
            execute(
                """
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
            assert(count == 4, "expected 4 hooks, got "..count)
        """,
            )
        }

    @Test
    fun testLineHookFiresInMainThread() =
        runTest {
            execute(
                """
            local count = 0
            debug.sethook(function() count = count + 1 end, "l")
            local x = 1
            debug.sethook()
            assert(count > 0, "expected hooks to fire, got "..count)
        """,
            )
        }

    @Test
    fun testDebugSethookAcceptsArguments() =
        runTest {
            execute(
                """
            local called = false
            local function hook() called = true end
            debug.sethook(hook, "l")
            local x = 1
            debug.sethook()
            assert(called, "hook should have been called")
        """,
            )
        }

    @Test
    fun testHookConfigurationIsStored() =
        runTest {
            execute(
                """
            local function hook() end
            debug.sethook(hook, "l", 0)
            local h, mask, count = debug.gethook()
            assert(h ~= nil, "hook function should be set")
            assert(mask == "l", "mask should be 'l', got: "..tostring(mask))
        """,
            )
        }

    @Test
    fun testHookAfterLoad() =
        runTest {
            execute(
                """
            local count = 0
            local function f (event, line)
              count = count + 1
            end
            debug.sethook(f,"l")
            load("local x = 1")()
            debug.sethook()
            assert(count > 0, "expected hooks after load(), got "..count)
        """,
            )
        }

    @Test
    fun testHookInsideLoadedChunk() =
        runTest {
            execute(
                """
            local lines = {}
            local function f (event, line)
              table.insert(lines, line)
            end
            debug.sethook(f,"l")
            load("local x = 1\nlocal y = 2")()
            debug.sethook()
            assert(#lines > 0, "expected hooks inside loaded chunk, got "..#lines.." hooks")
        """,
            )
        }

    @Test
    fun testHookWithCollectgarbage() =
        runTest {
            execute(
                """
            collectgarbage()
            local count = 0
            local function f (event, line)
              count = count + 1
            end
            debug.sethook(f,"l")
            load("local x = 1")()
            debug.sethook()
            assert(count > 0, "expected hooks after collectgarbage+load, got "..count)
        """,
            )
        }

    @Test
    fun testHookInNestedFunction() =
        runTest {
            execute(
                """
            local count = 0
            local function f (event, line)
              count = count + 1
            end
            debug.sethook(f,"l")
            load([[
                local function foo()
                end
                foo()
            ]])()
            debug.sethook()
            assert(count > 0, "expected hooks in nested function, got "..count)
        """,
            )
        }

    @Test
    fun testHookWithinTestFunction() =
        runTest {
            execute(
                """
            local function test()
              collectgarbage()
              local count = 0
              local function f (event, line)
                assert(event == 'line')
                count = count + 1
              end
              debug.sethook(f,"l")
              load("local x = 1\nlocal y = 2")()
              debug.sethook()
              assert(count > 0, "expected hooks in test function, got "..count)
            end
            test()
        """,
            )
        }
}
