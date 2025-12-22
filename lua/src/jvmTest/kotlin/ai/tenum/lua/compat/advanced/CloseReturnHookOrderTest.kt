package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Ensures return hooks see __close metamethods as "close".
 * Mirrors locals.lua lines ~780-808.
 */
class CloseReturnHookOrderTest : LuaCompatTestBase() {
    @Test
    fun testReturnHookSeesCloseName() =
        runTest {
            val code =
                """
                local function func2close (f, x, y)
                  local obj = setmetatable({}, {__close = f})
                  if x then
                    return x, obj, y
                  else
                    return obj
                  end
                end

                local trace = {}

                local function hook (event)
                  trace[#trace + 1] = event .. " " .. tostring(debug.getinfo(2).name)
                end

                local function foo (...)
                  local x <close> = func2close(function (_,msg)
                    trace[#trace + 1] = "x"
                  end)

                  local y <close> = func2close(function (_,msg)
                    debug.sethook(hook, "r")
                  end)

                  return ...
                end

                local t = {foo(10,20,30)}
                debug.sethook()

                local function checktable (t1, t2)
                  assert(#t1 == #t2)
                  for i = 1, #t1 do
                    assert(t1[i] == t2[i], ("idx %d: %s ~= %s"):format(i, tostring(t1[i]), tostring(t2[i])))
                  end
                end

                checktable(t, {10, 20, 30})
                checktable(trace,
                  {"return sethook", "return close", "x", "return close", "return foo"})
                """.trimIndent()

            execute(code)
        }
}
