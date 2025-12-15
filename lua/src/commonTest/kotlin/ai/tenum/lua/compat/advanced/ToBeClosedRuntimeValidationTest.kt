package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests runtime validation of to-be-closed variables.
 *
 * According to Lua 5.4 semantics:
 * 1. At declaration time: value must be nil, false, or have __close metamethod
 * 2. At close time: if value is not nil/false, __close must still be a callable function
 *
 * This test focuses on close-time validation (case 2).
 * Based on: locals.lua lines 507-525
 */
class ToBeClosedRuntimeValidationTest : LuaCompatTestBase() {
    @Test
    fun testMetamethodRemovedAfterDeclaration() =
        runTest {
            // From locals.lua:507-511
            val code = """
            local function foo()
                local xyz <close> = setmetatable({}, {__close = print})
                getmetatable(xyz).__close = nil  -- remove metamethod after declaration
            end
            local stat, msg = pcall(foo)
            assert(not stat and string.find(msg, "metamethod 'close'"))
        """
            execute(code)
        }

    @Test
    fun testMetamethodInvalidatedAfterDeclaration() =
        runTest {
            // From locals.lua:513-525
            val code = """
            local function func2close(f)
                return setmetatable({}, {__close = f})
            end
            
            local function foo()
                local a1 <close> = func2close(function(_, msg)
                    print("a1 close handler called with msg:", msg)
                    assert(string.find(msg, "number value"), "Expected 'number value' in message")
                    error(12)
                end)
                local a2 <close> = setmetatable({}, {__close = print})
                local a3 <close> = func2close(function(_, msg)
                    print("a3 close handler called with msg:", msg)
                    assert(msg == nil, "Expected nil message for a3")
                    error(123)
                end)
                getmetatable(a2).__close = 4  -- invalidate metamethod (set to non-function)
            end
            local stat, msg = pcall(foo)
            print("pcall result - stat:", stat, "msg:", msg)
            assert(not stat and msg == 12, "Expected error 12, got: " .. tostring(msg))
        """
            execute(code)
        }

    @Test
    fun testDeclarationTimeValidation() =
        runTest {
            // Ensure declaration-time validation still works
            val code = """
            local function foo()
                local x <close> = 99  -- plain number without __close
            end
            local stat, msg = pcall(foo)
            assert(not stat and string.find(msg, "non%-closable value"))
        """
            execute(code)
        }

    @Test
    fun testDeclarationTimeValidationWithTable() =
        runTest {
            // Plain table without __close should fail at declaration
            val code = """
            local function foo()
                local x <close> = {}  -- plain table without __close
            end
            local stat, msg = pcall(foo)
            assert(not stat and string.find(msg, "non%-closable value"))
        """
            execute(code)
        }
}
