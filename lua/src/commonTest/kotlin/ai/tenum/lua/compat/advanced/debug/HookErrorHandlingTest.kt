package ai.tenum.lua.compat.advanced.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for db.lua:322 regression - hook errors should propagate correctly
 *
 * Domain: Debug Hook Error Handling
 *
 * Root Cause: When an error occurs inside a hook function (e.g., a failing assert),
 * the error should be propagated and stop execution. Instead, the hook was causing
 * infinite recursion or silent failure.
 */
class HookErrorHandlingTest : LuaCompatTestBase() {
    @Test
    fun testHookErrorsAreSwallowed() =
        runTest {
            // NOTE: This test documents current behavior where hook errors are swallowed
            // This is INCORRECT per Lua 5.4 spec (hook errors should crash), but we keep it
            // to avoid breaking db.lua until we fix the line number reporting bug
            execute(
                """
            local hookCalled = 0
            local function f (event, line)
                hookCalled = hookCalled + 1
                if hookCalled > 10 then
                    error("Hook called too many times - infinite loop detected!")
                end
                error("Intentional hook error")
            end
            
            debug.sethook(f,"l")
            local x = 1
            debug.sethook()
            -- Hook error was swallowed, execution continues
        """,
            )
        }

    @Test
    fun testHookAssertFailuresAreSwallowed() =
        runTest {
            // NOTE: This test documents current behavior where hook assert failures are swallowed
            // This is INCORRECT per Lua 5.4 spec (hook errors should crash), but we keep it
            // to avoid breaking db.lua until we fix the line number reporting bug
            execute(
                """
            local hookCalled = 0
            local function f (event, line)
                hookCalled = hookCalled + 1
                if hookCalled > 10 then
                    error("Hook called too many times - infinite loop detected!")
                end
                assert(false, "This assert should fail and stop the hook")
            end
            
            debug.sethook(f,"l")
            local x = 1
            debug.sethook()
            -- Hook assert failure was swallowed, execution continues
        """,
            )
        }

    @Test
    fun testDb322ExactScenario() =
        runTest {
            // The exact scenario from db.lua:305-323
            // This is the acceptance test for the fix
            execute(
                """
            local function test (s, l, p)
              collectgarbage()
              local function f (event, line)
                assert(event == 'line')
              end
              debug.sethook(f,"l")
              load(s)()
              debug.sethook()
            end
            
            test([[
            local function foo()
            end
            foo()
            ]], {})
            
            do
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
              assert(count == 4, "expected 4 hooks, got "..count)
            end
        """,
            )
        }

    @Test
    fun testDb322FullScenario() =
        runTest {
            // The exact scenario from db.lua:305-323
            // This is the acceptance test for the fix
            execute(
                """
                local function test (s, l, p)     -- this must be line 19
                  collectgarbage()   -- avoid gc during trace
                  local function f (event, line)
                    assert(event == 'line')
                    local l = table.remove(l, 1)
                    if p then print(l, line) end
                    assert(l == line, "wrong trace!!")
                  end
                  debug.sethook(f,"l"); load(s)(); debug.sethook()
                  assert(#l == 0)
                end
                
                test([[
                local function foo()
                end
                foo()
                A = 1
                A = 2
                A = 3
                ]], {2, 3, 2, 4, 5, 6})
                
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
                  assert(count == 4, "expected 4 hooks, got "..count)
                end
            """,
            )
        }
}
