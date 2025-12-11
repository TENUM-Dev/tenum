package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test for call hooks with debug.getinfo(2, "f") pattern.
 *
 * This is the pattern used in db.lua:330-398 to track which functions were called.
 * The test verifies that when a call hook fires, debug.getinfo(2, "f") returns
 * the function that was just called (not the hook function itself).
 */
class CallHookGetInfoTest : LuaCompatTestBase() {
    @Test
    fun testCallHookWithGetInfoLevel2() =
        runTest {
            vm.debugEnabled = true
            execute(
                """
                -- Pattern from db.lua:330-398
                -- This test verifies that call hooks can use debug.getinfo(2, "f")
                -- to identify which function was called
                
                a = {}
                
                debug.sethook(function (e,l)
                  if e == "call" then
                      local info = debug.getinfo(2, "f")
                      if info then
                          local f = info.func
                          a[f] = 1
                      end
                  end
                end, "c")
                
                function f(a,b)
                  -- Call native functions to trigger call hooks
                  assert(a ~= nil, "a is required")
                  local info = debug.getinfo(1, "n")
                  return a + b
                end
                
                function g (...)
                  local arg = {...}
                  f(1, 2)
                end
                
                g()
                
                debug.sethook()  -- disable hook
                
                -- Both f and g should have been recorded
                assert(a[f], "f was not recorded in call hook")
                assert(a[g], "g was not recorded in call hook")
                assert(a[assert], "assert was not recorded in call hook")
                assert(a[debug.getinfo], "debug.getinfo was not recorded in call hook")
            """,
            )
        }
}
