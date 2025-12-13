package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

class CloseErrorDebugTest : LuaCompatTestBase() {
    @Test
    fun testDebugErrorPropagation() =
        runTest {
            vm.debugEnabled = true
            vm.execute(
                """
                local function func2close(f)
                  return setmetatable({}, {__close = f})
                end
                
                local function foo()
                  local z <close> = func2close(function (self, msg)
                    print("z close: msg type=" .. type(msg) .. " value=" .. tostring(msg))
                    if type(msg) == "number" then
                        print("msg == 4: " .. tostring(msg == 4))
                    end
                    assert(msg == 4, "Expected msg=4, got type=" .. type(msg) .. " value=" .. tostring(msg))
                    error("@z")
                  end)
                
                  error(4)  -- original error
                end
                
                local stat, msg = pcall(foo)
                print("Final msg: " .. tostring(msg))
                assert(string.find(msg, "@z"))
            """,
                source = "test",
            )
        }

    @Test
    fun testDebugErrorChain() =
        runTest {
            vm.debugEnabled = true
            vm.execute(
                """
                local function func2close(f)
                  return setmetatable({}, {__close = f})
                end
                
                local function foo()
                  local y <close> = func2close(function (self, msg)
                    print("y close: msg type=" .. type(msg) .. " value=" .. tostring(msg))
                    assert(string.find(msg, "@z"), "Expected @z in msg")
                    error("@y")
                  end)
                  
                  local z <close> = func2close(function (self, msg)
                    print("z close: msg type=" .. type(msg) .. " value=" .. tostring(msg))
                    assert(msg == 4, "Expected msg=4")
                    error("@z")
                  end)
                
                  error(4)  -- original error
                end
                
                local stat, msg = pcall(foo)
                print("Final msg: " .. tostring(msg))
                assert(string.find(msg, "@y"))
            """,
                source = "test",
            )
        }
}
