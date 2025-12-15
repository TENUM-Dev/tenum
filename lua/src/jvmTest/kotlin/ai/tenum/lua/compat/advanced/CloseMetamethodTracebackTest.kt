package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Test for __close metamethod stack trace formatting.
 * Tests that error messages from __close handlers include "in metamethod 'close'".
 * Based on locals.lua lines 486-493.
 */
class CloseMetamethodTracebackTest : LuaCompatTestBase() {
    @Test
    fun testCloseMetamethodAppearsInTraceback() =
        runTest {
            val code =
                """
                local function func2close(f)
                  return setmetatable({}, {__close = f})
                end
                
                -- error in toclose in vararg function
                local function foo (...)
                  local x123 <close> = func2close(function () error("@x123") end)
                end
                
                local st, msg = xpcall(foo, debug.traceback)
                print("Status:", st)
                print("Message:")
                print(msg)
                print("")
                
                -- Check assertions from locals.lua:492-493
                assert(string.match(msg, "@x123"), "Should contain error message")
                assert(string.find(msg, "in metamethod 'close'"), "Should contain 'in metamethod \\'close\\''")
                
                return true
                """.trimIndent()

            execute(code)
        }

    @Test
    fun testMultipleCloseHandlersWithErrors() =
        runTest {
            val code =
                """
                local function func2close(f)
                  return setmetatable({}, {__close = f})
                end
                
                -- Test from locals.lua:387-420 - multiple __close handlers with errors and collectgarbage
                local function foo ()
                  local x <close> =
                    func2close(function (self, msg)
                      print("x: got msg = " .. tostring(msg))
                      assert(string.find(msg, "@y"))
                      error("@x")
                    end)

                  local x1 <close> =
                    func2close(function (self, msg)
                      print("x1: got msg = " .. tostring(msg))
                      assert(string.find(msg, "@y"))
                    end)

                  local gc <close> = func2close(function ()
                    print("gc: running collectgarbage()")
                    collectgarbage()
                  end)

                  local y <close> =
                    func2close(function (self, msg)
                      print("y: got msg = " .. tostring(msg))
                      assert(string.find(msg, "@z"))  -- error in 'z'
                      error("@y")
                    end)

                  local z <close> =
                    func2close(function (self, msg)
                      print("z: got msg = " .. tostring(msg))
                      assert(msg == nil)
                      error("@z")
                    end)

                  return 200
                end

                print("Calling foo()...")
                local stat, msg = pcall(foo, false)
                print("Result: stat=" .. tostring(stat) .. " msg=" .. tostring(msg))
                assert(string.find(msg, "@x"))
                print("Test passed!")
                
                return true
                """.trimIndent()

            execute(code)
        }
}
