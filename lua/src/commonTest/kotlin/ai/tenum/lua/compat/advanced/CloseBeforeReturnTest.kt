package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that __close metamethods are called BEFORE return values are evaluated.
 *
 * From locals.lua:255 - ensures that closing functions do not corrupt returning values.
 * The __close metamethod should execute before return values are collected, but the return
 * values themselves should be evaluated before __close is called.
 */
class CloseBeforeReturnTest : LuaCompatTestBase() {
    @Test
    fun testCloseBeforeReturnDoesNotCorruptReturnValues() =
        runTest {
            // From locals.lua:241-255
            // This test ensures that __close metamethods are called after return value
            // expressions are evaluated, but the return values themselves are not affected
            // by any mutations the __close metamethod makes.
            val result =
                execute(
                    """
                local function func2close (f, x, y)
                  local obj = setmetatable({}, {__close = f})
                  if x then
                    return x, obj, y
                  else
                    return obj
                  end
                end
                
                local X = false
                
                local x, closescope = func2close(function (_, msg)
                  assert(msg == nil)
                  X = true
                end, 100)
                
                -- closing functions do not corrupt returning values
                local function foo (x)
                  local _ <close> = closescope
                  return x, X, 23
                end
                
                local a, b, c = foo(1.5)
                -- b should be false (the value of X when return expression was evaluated)
                -- even though X becomes true after __close is called
                return a == 1.5 and b == false and c == 23 and X == true
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(true, result.value, "Return values should not be corrupted by __close metamethods")
        }

    @Test
    fun testCloseBeforeReturnSimple() =
        runTest {
            vm.debugEnabled = true
            val result =
                execute(
                    """
                local value = 10
                local function foo()
                    local obj <close> = setmetatable({}, {
                        __close = function() value = 20 end
                    })
                    return value
                end
                local result = foo()
                -- result should be 10 (evaluated before __close)
                -- value should be 20 (modified by __close)
                return result == 10 and value == 20
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(true, result.value)
        }

    @Test
    fun testCloseBeforeReturnMultipleValues() =
        runTest {
            val result =
                execute(
                    """
                local x, y, z = 1, 2, 3
                local function foo()
                    local obj <close> = setmetatable({}, {
                        __close = function()
                            x, y, z = 10, 20, 30
                        end
                    })
                    return x, y, z
                end
                local a, b, c = foo()
                -- a, b, c should be 1, 2, 3 (evaluated before __close)
                -- x, y, z should be 10, 20, 30 (modified by __close)
                return a == 1 and b == 2 and c == 3 and x == 10 and y == 20 and z == 30
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(true, result.value)
        }

    @Test
    fun testCloseBeforeReturnWithUpvalue() =
        runTest {
            val result =
                execute(
                    """
                local function makeFunc()
                    local captured = 100
                    return function()
                        local obj <close> = setmetatable({}, {
                            __close = function() captured = 200 end
                        })
                        return captured
                    end, function() return captured end
                end
                
                local func, getCapture = makeFunc()
                local result = func()
                -- result should be 100 (evaluated before __close)
                -- captured should be 200 (modified by __close)
                return result == 100 and getCapture() == 200
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(true, result.value)
        }

    @Test
    fun testCloseBeforeReturnMultipleCloseVars() =
        runTest {
            val result =
                execute(
                    """
                local value = 0
                local function foo()
                    local a <close> = setmetatable({}, {
                        __close = function() value = value + 1 end
                    })
                    local b <close> = setmetatable({}, {
                        __close = function() value = value + 10 end
                    })
                    return value
                end
                local result = foo()
                -- result should be 0 (evaluated before any __close)
                -- value should be 11 (first b's __close adds 10, then a's adds 1)
                return result == 0 and value == 11
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(true, result.value)
        }
}
