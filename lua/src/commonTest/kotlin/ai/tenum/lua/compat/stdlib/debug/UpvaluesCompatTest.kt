package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for upvalue introspection and manipulation.
 *
 * Covers:
 * - debug.getupvalue - get upvalue names and values
 * - debug.setupvalue - modify upvalue values
 * - debug.upvalueid - get unique upvalue identifiers
 * - debug.upvaluejoin - share upvalues between closures
 * - Upvalue behavior with string.gmatch iterators
 * - Upvalue behavior after loading binary code
 */
class UpvaluesCompatTest : LuaCompatTestBase() {
    // ========== debug.getupvalue / debug.setupvalue ==========

    @Test
    fun testGetUpvalue() =
        runTest {
            assertLuaString(
                """
            local x = 10
            local function f()
                return x
            end
            local name, value = debug.getupvalue(f, 1)
            return name
        """,
                "x",
            )
        }

    @Test
    fun testGetUpvalueValue() =
        runTest {
            assertLuaNumber(
                """
            local x = 42
            local function f()
                return x
            end
            local name, value = debug.getupvalue(f, 1)
            return value
        """,
                42.0,
            )
        }

    @Test
    fun testSetUpvalue() =
        runTest {
            assertLuaNumber(
                """
            local x = 10
            local function f()
                return x
            end
            debug.setupvalue(f, 1, 20)
            return f()
        """,
                20.0,
            )
        }

    @Test
    fun testGetUpvalueInvalidIndex() =
        runTest {
            assertLuaNil(
                """
            local function f() end
            return debug.getupvalue(f, 100)
        """,
            )
        }

    @Test
    fun testUpvalueModification() =
        runTest {
            assertLuaNumber(
                """
            local function makeCounter()
                local count = 0
                return function()
                    count = count + 1
                    return count
                end
            end
            local counter = makeCounter()
            counter()  -- count is now 1
            debug.setupvalue(counter, 1, 10)
            return counter()  -- should return 11
        """,
                11.0,
            )
        }

    // ========== debug.upvalueid ==========

    @Test
    fun testUpvalueIdReturnsUserdata() =
        runTest {
            assertLuaString(
                """
            local function f()
                return debug
            end
            local id = debug.upvalueid(f, 1)
            return type(id)
        """,
                "userdata",
            )
        }

    @Test
    fun testUpvalueIdSameUpvalueSameId() =
        runTest {
            assertLuaBoolean(
                """
            local x = 10
            local function f1() return x end
            local function f2() return x end
            local id1 = debug.upvalueid(f1, 1)
            local id2 = debug.upvalueid(f2, 1)
            -- Same upvalue should have same ID
            return id1 == id2
        """,
                true,
            )
        }

    @Test
    fun testUpvalueIdDifferentUpvaluesDifferentIds() =
        runTest {
            assertLuaBoolean(
                """
            local x = 10
            local y = 20
            local function f1() return x end
            local function f2() return y end
            local id1 = debug.upvalueid(f1, 1)
            local id2 = debug.upvalueid(f2, 1)
            -- Different upvalues should have different IDs
            return id1 ~= id2
        """,
                true,
            )
        }

    @Test
    fun testUpvalueIdInvalidIndex() =
        runTest {
            assertLuaNil(
                """
            local function f() end
            return debug.upvalueid(f, 100)
        """,
            )
        }

    @Test
    fun testUpvalueIdSharedUpvaluesWithDifferentOrder() =
        runTest {
            assertLuaBoolean(
                """
            -- This test matches closure.lua:256 scenario
            -- foo1 and foo2 share upvalues 'a' and 'b' but in different orders
            local foo1, foo2
            do
                local a, b = 3, 5
                foo1 = function() return a+b end  -- upvalues: a(1), b(2)
                foo2 = function() return b+a end  -- upvalues: b(1), a(2)
            end
            -- foo1's 1st upvalue (a) should be same as foo2's 2nd upvalue (a)
            local id1_1 = debug.upvalueid(foo1, 1)
            local id2_2 = debug.upvalueid(foo2, 2)
            assert(id1_1 == id2_2, "Expected same upvalue IDs for 'a' but got " .. tostring(id1_1) .. " and " .. tostring(id2_2))
            return id1_1 == id2_2
        """,
                true,
            )
        }

    @Test
    fun testUpvalueIdForStringGmatch() =
        runTest {
            // Test closure.lua:262 - gmatch iterator should have upvalues
            assertLuaBoolean(
                """
            local iter = string.gmatch("x", "x")
            local id = debug.upvalueid(iter, 1)
            return id ~= nil
        """,
                true,
            )
        }

    @Test
    fun testGetUpvalueNameForNativeFunction() =
        runTest {
            // Test db.lua:591 - upvalues of C functions are always "called" "" (the empty string)
            assertLuaString(
                """
            local iter = string.gmatch("x", "x")
            return debug.getupvalue(iter, 1)
        """,
                "",
            )
        }

    // ========== debug.upvaluejoin ==========

    @Test
    fun testUpvaluejoinBasic() =
        runTest {
            assertLuaNumber(
                """
            -- Create separate scopes for each function to ensure different upvalues
            local function makeFunc1()
                local a, b = 3, 5
                return function() return a + b end
            end
            local function makeFunc2()
                local x, y = 10, 20
                return function() return x + y end
            end
            local foo1 = makeFunc1()
            local foo2 = makeFunc2()
            assert(foo1() == 8 and foo2() == 30)
            -- Make foo1's 1st upvalue point to foo2's 1st upvalue (x=10)
            debug.upvaluejoin(foo1, 1, foo2, 1)
            -- Now foo1 should return x + b = 10 + 5 = 15
            return foo1()
        """,
                15.0,
            )
        }

    @Test
    fun testUpvaluejoinInvalidIndex() =
        runTest {
            assertLuaBoolean(
                """
            local x = 10
            local function f() return x end
            local ok = pcall(debug.upvaluejoin, f, 100, f, 1)
            return not ok
        """,
                true,
            )
        }

    @Test
    fun testUpvaluejoinNotClosure() =
        runTest {
            assertLuaBoolean(
                """
            local x = 10
            local function f() return x end
            local ok = pcall(debug.upvaluejoin, print, 1, f, 1)
            return not ok
        """,
                true,
            )
        }

    // ========== Binary Loading ==========

    @Test
    fun testSetupvalueAfterLoadBinary() =
        runTest {
            // Test from calls.lua:361-370
            // Load function from binary, setup _ENV upvalue, then call
            execute(
                """
                -- Global variable for testing
                XX = 123
                
                -- Create function with local x and access to global XX
                local x
                local function h()
                    local y = x  -- use 'x', so that it becomes 1st upvalue
                    return XX    -- global name
                end
                
                -- Dump and reload as binary
                local d = string.dump(h)
                x = load(d, "", "b")
                
                -- Check that _ENV is the second upvalue (after x)
                local upname = debug.getupvalue(x, 2)
                assert(upname == '_ENV', "Second upvalue should be _ENV, got: " .. tostring(upname))
                
                -- Setup _ENV to point to global environment
                debug.setupvalue(x, 2, _G)
                
                -- Call function - should access XX from global environment
                local result = x()
                assert(result == 123, "Expected 123, got: " .. tostring(result))
            """,
            )
        }
}
