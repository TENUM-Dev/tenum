package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phase 8.1: Attribute System Tests
 *
 * Tests for Lua 5.4 local variable attributes:
 * - <const> - constant variables (cannot be reassigned)
 * - <close> - to-be-closed variables (call __close metamethod on scope exit)
 *
 * Based on attrib.lua from official Lua 5.4.8 test suite
 */
class AttributesCompatTest : LuaCompatTestBase() {
    // ============================================
    // <const> Attribute Tests
    // ============================================

    @Test
    fun testConstBasic() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local x <const> = 10
            return x
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testConstCannotReassign() {
        assertFailsWith<RuntimeException> {
            vm.debugEnabled = true
            execute(
                """
                local x <const> = 10
                x = 20
            """,
            )
        }
    }

    @Test
    fun testConstAssignmentCompileTimeError() {
        // Test from locals.lua:189 - const reassignment should be caught at compile time by load()
        val result =
            execute(
                """
                local st, msg = load("local x, y <const>, z = 10, 20, 30; x = 11; y = 12")
                return st == nil and msg ~= nil and string.find(msg, "attempt to assign to const variable 'y'") ~= nil
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "Expected load() to fail with const assignment error for 'y'")
    }

    @Test
    fun testConstFunctionDeclarationError() {
        // Test from locals.lua:192 - function declaration should not overwrite const variable
        val result =
            execute(
                """
                local st, msg = load("local foo <const> = 10; function foo() end")
                return st == nil and msg ~= nil and string.find(msg, "attempt to assign to const variable 'foo'") ~= nil
            """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "Expected load() to fail with const assignment error for 'foo'")
    }

    @Test
    fun testConstMultipleVariables() {
        val result =
            execute(
                """
            local a <const>, b, c <const> = 1, 2, 3
            return a + b + c
        """,
            )
        assertLuaNumber(result, 6.0)
    }

    @Test
    fun testConstCannotReassignInMultiple() {
        assertFailsWith<RuntimeException> {
            execute(
                """
                local a <const>, b, c <const> = 1, 2, 3
                a = 10
            """,
            )
        }
    }

    @Test
    fun testConstScopeRules() {
        val result =
            execute(
                """
            local x <const> = 10
            do
                local x = 20  -- different variable, shadows outer
                x = 30  -- OK, inner x is not const
            end
            return x  -- outer x is still 10
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testConstWithNil() {
        val result =
            execute(
                """
            local x <const> = nil
            return type(x)
        """,
            )
        assertTrue(result is LuaString)
        assertEquals("nil", result.value)
    }

    @Test
    fun testConstInForLoop() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local sum = 0
            for i <const> = 1, 5 do
                sum = sum + i
            end
            return sum
        """,
            )
        assertLuaNumber(result, 15.0)
    }

    // ============================================
    // <close> Attribute Tests
    // ============================================

    @Test
    fun testCloseBasic() {
        val result =
            execute(
                """
            local called = false
            do
                local obj <close> = setmetatable({}, {
                    __close = function(self)
                        called = true
                    end
                })
            end
            return called
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value)
    }

    @Test
    fun testCloseWithValue() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local value = 0
            do
                local obj <close> = setmetatable({x = 42}, {
                    __close = function(self)
                        value = self.x
                    end
                })
            end
            return value
        """,
            )
        assertLuaNumber(result, 42.0)
    }

    @Test
    fun testCloseMultipleVariables() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local order = ""
            do
                local a <close> = setmetatable({}, {
                    __close = function() order = order .. "a" end
                })
                local b <close> = setmetatable({}, {
                    __close = function() order = order .. "b" end
                })
                local c <close> = setmetatable({}, {
                    __close = function() order = order .. "c" end
                })
            end
            return order
        """,
            )
        // Close in reverse order: c, b, a
        assertTrue(result is LuaString)
        assertEquals("cba", result.value)
    }

    @Test
    fun testCloseOnReturn() {
        val result =
            execute(
                """
            local closed = false
            local function f()
                local obj <close> = setmetatable({}, {
                    __close = function() closed = true end
                })
                return 42
            end
            local val = f()
            return closed and val == 42
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value)
    }

    @Test
    fun testCloseOnError() {
        val result =
            execute(
                """
            local closed = false
            local success = pcall(function()
                local obj <close> = setmetatable({}, {
                    __close = function() closed = true end
                })
                error("test error")
            end)
            return closed and not success
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value)
    }

    @Test
    fun testCloseWithNil() {
        // nil and false do not call __close
        val result =
            execute(
                """
            local called = false
            do
                local obj <close> = nil
            end
            return called
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(false, result.value)
    }

    @Test
    fun testCloseWithFalse() {
        // nil and false do not call __close
        val result =
            execute(
                """
            local called = false
            do
                local obj <close> = false
            end
            return called
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(false, result.value)
    }

    @Test
    fun testCloseErrorInClose() {
        val result =
            execute(
                """
            local result = ""
            local ok, err = pcall(function()
                local obj <close> = setmetatable({}, {
                    __close = function()
                        result = result .. "close"
                        error("error in close")
                    end
                })
            end)
            return result == "close" and not ok
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value)
    }

    // ============================================
    // Mixed Attributes Tests
    // ============================================

    @Test
    fun testConstAndClose() {
        val result =
            execute(
                """
            local closed = false
            do
                local obj <const, close> = setmetatable({value = 10}, {
                    __close = function(self) closed = true end
                })
                -- obj cannot be reassigned (const)
            end
            return closed
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value)
    }

    @Test
    fun testConstCloseCannotReassign() {
        assertFailsWith<RuntimeException> {
            execute(
                """
                local obj <const, close> = setmetatable({}, {
                    __close = function() end
                })
                obj = nil
            """,
            )
        }
    }

    // ============================================
    // Scope and Lifetime Tests
    // ============================================

    @Test
    fun testCloseInNestedBlocks() {
        val result =
            execute(
                """
            local order = ""
            do
                local a <close> = setmetatable({}, {
                    __close = function() order = order .. "a" end
                })
                do
                    local b <close> = setmetatable({}, {
                        __close = function() order = order .. "b" end
                    })
                end
                order = order .. "x"
            end
            return order
        """,
            )
        assertTrue(result is LuaString)
        assertEquals("bxa", result.value)
    }

    @Test
    fun testCloseInFunction() {
        val result =
            execute(
                """
            local outer_closed = false
            local function f()
                local inner_closed = false
                do
                    local obj <close> = setmetatable({}, {
                        __close = function() inner_closed = true end
                    })
                end
                return inner_closed
            end
            local inner_result = f()
            return inner_result and not outer_closed
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value)
    }

    @Test
    fun testCloseWithGoto() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local closed = false
            do
                local obj <close> = setmetatable({}, {
                    __close = function() closed = true end
                })
                goto skip
                local x = 1
                ::skip::
            end
            return closed
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value)
    }

    // ============================================
    // Integration Tests
    // ============================================

    @Test
    fun testAttributesWithForLoop() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local total = 0
            for i <const> = 1, 3 do
                local obj <close> = setmetatable({val = i}, {
                    __close = function(self) total = total + self.val end
                })
            end
            return total
        """,
            )

        assertLuaNumber(result, 6.0) // 1 + 2 + 3
    }

    @Test
    fun testRequireBasicsFromAttrib() {
        // From attrib.lua: basic require/package checks
        execute("assert(require'string' == string)")
        execute("assert(require'math' == math)")
        execute("assert(require'table' == table)")
        execute("assert(type(package.path) == 'string')")
        execute("assert(type(package.cpath) == 'string')")
        val result =
            execute(
                """
            assert(type(package.loaded) == "table")
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackageSearchpathBasic() {
        // Test basic searchpath functionality
        val result =
            execute(
                """
            local s, err = package.searchpath("nonexistent", "?.lua;?/init.lua")
            assert(not s)
            assert(type(err) == "string")
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackageSearchpathLoopSimple() {
        // Test with a loop to build path (simpler version)
        val result =
            execute(
                """
            local t = {}
            for i = 1,5 do t[i] = "?" end
            local path = table.concat(t, ";")
            local s, err = package.searchpath("test", path)
            assert(not s)
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackageSearchpathWithStringRep() {
        // Test with string.rep in the loop
        val result =
            execute(
                """
            local t = {}
            for i = 1,5 do 
                t[i] = string.rep("?", i)
            end
            local path = table.concat(t, ";")
            local s, err = package.searchpath("test", path)
            assert(not s)
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackageSearchpathWithModulo() {
        // Test with modulo operator
        val result =
            execute(
                """
            local t = {}
            for i = 1,5 do 
                t[i] = string.rep("?", i%10 + 1)
            end
            local path = table.concat(t, ";")
            local s, err = package.searchpath("test", path)
            assert(not s)
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackageSearchpathWithLengthOp() {
        // Test with # operator on table
        val result =
            execute(
                """
            local t = {}
            for i = 1,5 do 
                t[i] = string.rep("?", i%10 + 1)
            end
            t[#t + 1] = ";"
            local path = table.concat(t, ";")
            local s, err = package.searchpath("test", path)
            assert(not s)
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackageSearchpathWith10Items() {
        // Test with 10 items
        val result =
            execute(
                """
            local t = {}
            for i = 1,10 do 
                t[i] = string.rep("?", i%10 + 1)
            end
            t[#t + 1] = ";"
            local path = table.concat(t, ";")
            local s, err = package.searchpath("test", path)
            assert(not s)
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackageSearchpathLongTemplateFromAttrib() {
        // From attrib.lua: package.searchpath should report long error messages
        val result =
            execute(
                """
            local max = 50
            local t = {}
            for i = 1,max do t[i] = string.rep("?", i%10 + 1) end
            t[#t + 1] = ";"
            local path = table.concat(t, ";")
            local s, err = package.searchpath("xuxu", path)
            assert(not s and string.find(err, string.rep("xuxu", 10)))
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackagePathEmptyFromAttrib() {
        val result =
            execute(
                """
            local oldpath = package.path
            package.path = ""
            local ok = pcall(require, "file_does_not_exist")
            package.path = oldpath
            return ok
        """,
            )
        assertLuaBoolean(result, false)
    }

    @Test
    fun testPreloadFromAttrib() {
        val result =
            execute(
                """
            local p = package
            package = {}
            p.preload.pl = function (...)
                local _ENV = {...}
                function xuxu (x) return x+20 end
                return _ENV
            end
            local pl, ext = require"pl"
            local ok = (pl.xuxu(10) == 30)
            package = p
            return ok
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testAttributesComplexScenario() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local log = ""
            local function process(n)
                local multiplier <const> = 2
                local obj <close> = setmetatable({}, {
                    __close = function() log = log .. n end
                })
                return n * multiplier
            end
            
            local a = process(1)
            local b = process(2)
            local c = process(3)
            
            return a + b + c == 12 and log == "123"
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value)
    }

    @Test
    fun testUnknownAttributeError() =
        runTest {
            // Test that unknown attributes produce proper error message
            // Based on constructs.lua:242 which checks for "unknown attribute 'XXX'"
            val result =
                execute(
                    """
                local f, err = load("local x <XXX> = 10")
                return f == nil and err ~= nil and string.find(err, "unknown attribute 'XXX'") ~= nil
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(true, result.value, "Expected load() to return nil with error containing \"unknown attribute 'XXX'\"")
        }

    @Test
    fun testConstAssignmentErrorMessage() =
        runTest {
            // Test that const assignment errors include variable name
            // Based on constructs.lua:244 which checks for ":1: attempt to assign to const variable 'xxx'"
            val result =
                execute(
                    """
                local f, err = load("local xxx <const> = 20; xxx = 10")
                return f == nil and err ~= nil and string.find(err, "attempt to assign to const variable 'xxx'") ~= nil
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(
                true,
                result.value,
                "Expected load() to return nil with error containing \"attempt to assign to const variable 'xxx'\"",
            )
        }

    @Test
    fun testConstAssignmentInNestedClosure() =
        runTest {
            // Test that const variables cannot be assigned even from nested closures
            // Based on constructs.lua:247 which checks for ":6: attempt to assign to const variable 'xxx'"
            val result =
                execute(
                    """
                local f, err = load([[
                    local xx;
                    local xxx <const> = 20;
                    local yyy;
                    local function foo ()
                      local abc = xx + yyy + xxx;
                      return function () return function () xxx = yyy end end
                    end
                  ]])
                return f == nil and err ~= nil and string.find(err, ":6: attempt to assign to const variable 'xxx'") ~= nil
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(
                true,
                result.value,
                "Expected load() to return nil with error containing \":6: attempt to assign to const variable 'xxx'\"",
            )
        }
}
