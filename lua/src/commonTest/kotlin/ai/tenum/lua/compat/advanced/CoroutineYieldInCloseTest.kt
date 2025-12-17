package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaString
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Tests for coroutine yield/resume behavior within to-be-closed variable __close metamethods.
 *
 * Issue: When a __close metamethod yields in a coroutine, resuming the coroutine should
 * continue execution and properly return the function's return values. Currently, after
 * resuming from a yield in __close, the return values are lost.
 *
 * Related: locals.lua:853 - coroutines with yielding close handlers
 */
class CoroutineYieldInCloseTest : LuaCompatTestBase() {
    @Test
    fun testSimpleYieldInCloseReturnsCorrectValues() {
        val result =
            execute(
                """
                print("[SCRIPT START]")
                local function func2close(f)
                  return setmetatable({}, {__close = f})
                end
                
                print("[SCRIPT] Creating coroutine")
                local co = coroutine.wrap(function ()
                  print("[COROUTINE] Starting wrapped function")
                  local x <close> = func2close(function ()
                    print("[CLOSE] About to yield")
                    coroutine.yield("yielding")
                    print("[CLOSE] Resumed after yield")
                  end)
                  
                  print("[COROUTINE] About to return result")
                  return "result"
                end)
                
                print("[SCRIPT] Calling co() first time")
                local r1 = co()
                print("[SCRIPT] r1 type=" .. type(r1) .. " value=" .. tostring(r1))
                print("[SCRIPT] Checking r1 == 'yielding': " .. tostring(r1 == "yielding"))
                assert(r1 == "yielding", "First resume should yield 'yielding'")
                
                print("[SCRIPT] First assert passed! Calling co() second time")
                local r2 = co()
                print("[SCRIPT] r2 type=" .. type(r2) .. " value=" .. tostring(r2))
                assert(r2 == "result", "Second resume should return 'result', got: " .. tostring(r2))
                
                return "OK"
                """.trimIndent(),
            )

        (result as? LuaString)?.value shouldBe "OK"
    }

    @Test
    fun testYieldInCloseWithMultipleReturnValues() {
        val result =
            execute(
                """
                local function func2close(f)
                  return setmetatable({}, {__close = f})
                end
                
                local co = coroutine.wrap(function ()
                  local x <close> = func2close(function ()
                    coroutine.yield("y1")
                  end)
                  
                  return 10, 20, 30
                end)
                
                assert(co() == "y1")
                
                local a, b, c = co()
                assert(a == 10 and b == 20 and c == 30, 
                       string.format("Expected 10,20,30 got %s,%s,%s", tostring(a), tostring(b), tostring(c)))
                
                return "OK"
                """.trimIndent(),
            )

        (result as? LuaString)?.value shouldBe "OK"
    }

    @Test
    fun testNestedCloseHandlersWithYields() {
        val result =
            execute(
                """
                local function func2close(f)
                  return setmetatable({}, {__close = f})
                end
                
                local trace = {}
                local co = coroutine.wrap(function ()
                  local outer <close> = func2close(function ()
                    trace[#trace + 1] = "outer1"
                    coroutine.yield("outer")
                    trace[#trace + 1] = "outer2"
                  end)
                  
                  do
                    local inner <close> = func2close(function ()
                      trace[#trace + 1] = "inner1"
                      coroutine.yield("inner")
                      trace[#trace + 1] = "inner2"
                    end)
                  end
                  
                  return "done"
                end)
                
                assert(co() == "inner", "First yield should be from inner")
                assert(co() == "outer", "Second yield should be from outer")
                assert(co() == "done", "Third should return 'done'")
                
                -- Check execution order
                assert(trace[1] == "inner1" and trace[2] == "inner2" and 
                       trace[3] == "outer1" and trace[4] == "outer2",
                       "Trace order incorrect: " .. table.concat(trace, ", "))
                
                return "OK"
                """.trimIndent(),
            )

        result shouldBe LuaString.of("OK")
    }

    @Test
    fun testYieldInCloseWithPcall() {
        vm.debugEnabled = true
        val result =
            execute(
                """
                local function func2close(f)
                  return setmetatable({}, {__close = f})
                end
                
                local trace = {}
                local co = coroutine.wrap(function ()
                  trace[#trace + 1] = "start"
                  
                  local x <close> = func2close(function (_, msg)
                    assert(msg == nil)
                    trace[#trace + 1] = "x1"
                    coroutine.yield("x")
                    trace[#trace + 1] = "x2"
                  end)
                  
                  return pcall(function ()
                    do
                      local z <close> = func2close(function (_, msg)
                        assert(msg == nil)
                        trace[#trace + 1] = "z1"
                        coroutine.yield("z")
                        trace[#trace + 1] = "z2"
                      end)
                    end
                    
                    trace[#trace + 1] = "between"
                    
                    local y <close> = func2close(function(_, msg)
                      assert(msg == nil)
                      trace[#trace + 1] = "y1"
                      coroutine.yield("y")
                      trace[#trace + 1] = "y2"
                    end)
                    
                    return 10, 20, 30
                  end)
                end)
                local result = co()
                assert(result == "z", "First yield should be 'z' but is " .. tostring(result))
                local result = co()
                assert(result == "y", "Second yield should be 'y' but is " .. tostring(result))
                local result = co()
                assert(result == "x", "Third yield should be 'x' but is " .. tostring(result))
                
                local results = {co()}
                assert(#results == 4, "Should have 4 return values, got " .. #results)
                assert(results[1] == true and results[2] == 10 and 
                       results[3] == 20 and results[4] == 30,
                       "Wrong return values from pcall")
                
                local expected = {"start", "z1", "z2", "between", "y1", "y2", "x1", "x2"}
                for i = 1, #expected do
                  assert(trace[i] == expected[i], 
                         string.format("trace[%d]: expected '%s', got '%s'", 
                                       i, expected[i], tostring(trace[i])))
                end
                
                return "OK"
                """.trimIndent(),
            )

        result shouldBe LuaString.of("OK")
    }
}
