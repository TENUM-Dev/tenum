package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Phase 9.1: Enhanced Function Declaration Syntax
 *
 * Tests for function declaration with dot and colon syntax:
 * - function t.name() - dot syntax (assigns function to table field)
 * - function t:method() - colon syntax (adds implicit 'self' parameter)
 */
class EnhancedFunctionSyntaxCompatTest : LuaCompatTestBase() {
    // ========== Dot Syntax Tests (function t.name()) ==========

    @Test
    fun testFunctionDotSyntaxBasic() {
        val result =
            execute(
                """
            local t = {}
            function t.greet()
                return "Hello"
            end
            return t.greet()
        """,
            )
        assertLuaString(result, "Hello")
    }

    @Test
    fun testFunctionDotSyntaxWithParameters() {
        val result =
            execute(
                """
            local math = {}
            function math.square(x)
                return x * x
            end
            return math.square(5)
        """,
            )
        assertLuaNumber(result, 25.0)
    }

    @Test
    fun testFunctionDotSyntaxMultipleParams() {
        val result =
            execute(
                """
            local calc = {}
            function calc.add(a, b)
                return a + b
            end
            function calc.mul(a, b)
                return a * b
            end
            return calc.add(3, 4) + calc.mul(2, 5)
        """,
            )
        // 3 + 4 = 7, 2 * 5 = 10, total = 17
        assertLuaNumber(result, 17.0)
    }

    @Test
    fun testFunctionDotSyntaxWithReturnValue() {
        val result =
            execute(
                """
            local lib = {}
            function lib.double(x)
                return x * 2
            end
            local a = lib.double(5)
            local b = lib.double(a)
            return b
        """,
            )
        // double(5) = 10, double(10) = 20
        assertLuaNumber(result, 20.0)
    }

    @Test
    fun testFunctionDotSyntaxNestedTable() {
        val result =
            execute(
                """
            local a = {}
            a.b = {}
            function a.b.func()
                return 42
            end
            return a.b.func()
        """,
            )
        assertLuaNumber(result, 42.0)
    }

    @Test
    fun testFunctionDotSyntaxModulePattern() {
        vm.debugEnabled = true
        val result =
            execute(
                """
            local M = {}
            
            function M.square(x)
                return x * x
            end
            
            function M.cube(x)
                return x * x * x
            end
            
            function M.sum(...)
                local total = 0
                for i, v in ipairs({...}) do
                    total = total + v
                end
                return total
            end
            
            local a = M.square(3)
            local b = M.cube(2)
            local c = M.sum(1, 2, 3, 4, 5)
            return a + b + c
        """,
            )
        // square(3) = 9, cube(2) = 8, sum(1..5) = 15
        // Total: 9 + 8 + 15 = 32
        assertLuaNumber(result, 32.0)
    }

    @Test
    fun testFunctionDotSyntaxWithLocalVariables() {
        val result =
            execute(
                """
            local counter = {}
            function counter.create()
                local count = 0
                return function()
                    count = count + 1
                    return count
                end
            end
            local c = counter.create()
            c()
            c()
            return c()
        """,
            )
        assertLuaNumber(result, 3.0)
    }

    // ========== Colon Syntax Tests (function t:method()) ==========

    @Test
    fun testFunctionColonSyntaxBasic() {
        val result =
            execute(
                """
            local obj = {value = 10}
            function obj:getValue()
                return self.value
            end
            return obj:getValue()
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testFunctionColonSyntaxImplicitSelf() {
        val result =
            execute(
                """
            local obj = {x = 5, y = 10}
            function obj:sum()
                return self.x + self.y
            end
            return obj:sum()
        """,
            )
        // 5 + 10 = 15
        assertLuaNumber(result, 15.0)
    }

    @Test
    fun testFunctionColonSyntaxWithParameters() {
        val result =
            execute(
                """
            local obj = {value = 10}
            function obj:add(n)
                return self.value + n
            end
            return obj:add(5)
        """,
            )
        // 10 + 5 = 15
        assertLuaNumber(result, 15.0)
    }

    @Test
    fun testFunctionColonSyntaxModifyState() {
        val result =
            execute(
                """
            local obj = {count = 0}
            function obj:increment()
                self.count = self.count + 1
                return self.count
            end
            obj:increment()
            obj:increment()
            return obj:increment()
        """,
            )
        assertLuaNumber(result, 3.0)
    }

    @Test
    fun testFunctionColonSyntaxConstructorPattern() {
        val result =
            execute(
                """
            local Vector = {}
            
            function Vector:new(x, y)
                local v = {x = x, y = y}
                setmetatable(v, {__index = Vector})
                return v
            end
            
            function Vector:magnitude()
                return self.x * self.x + self.y * self.y
            end
            
            local v = Vector:new(3, 4)
            return v:magnitude()
        """,
            )
        // 3*3 + 4*4 = 9 + 16 = 25
        assertLuaNumber(result, 25.0)
    }

    @Test
    fun testFunctionColonSyntaxMultipleMethods() {
        val result =
            execute(
                """
            local obj = {x = 10}
            
            function obj:getX()
                return self.x
            end
            
            function obj:setX(value)
                self.x = value
            end
            
            function obj:doubleX()
                self.x = self.x * 2
                return self.x
            end
            
            obj:setX(5)
            obj:doubleX()
            return obj:getX()
        """,
            )
        // setX(5), doubleX() -> 10, getX() -> 10
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testFunctionColonSyntaxWithVarargs() {
        val result =
            execute(
                """
            local obj = {}
            function obj:sum(...)
                local total = 0
                for i, v in ipairs({...}) do
                    total = total + v
                end
                return total
            end
            return obj:sum(1, 2, 3, 4, 5)
        """,
            )
        // 1+2+3+4+5 = 15
        assertLuaNumber(result, 15.0)
    }

    // ========== Mixed Syntax Tests ==========

    @Test
    fun testMixedDotAndColonSyntax() {
        val result =
            execute(
                """
            local lib = {}
            
            function lib.create(value)
                local obj = {value = value}
                setmetatable(obj, {__index = lib})
                return obj
            end
            
            function lib:getValue()
                return self.value
            end
            
            function lib:double()
                self.value = self.value * 2
                return self.value
            end
            
            local o = lib.create(5)
            o:double()
            return o:getValue()
        """,
            )
        // create(5), double() -> 10, getValue() -> 10
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testOOPVectorExample() {
        val result =
            execute(
                """
            local Vector = {}
            Vector.__index = Vector
            
            function Vector:new(x, y)
                local v = {x = x or 0, y = y or 0}
                setmetatable(v, Vector)
                return v
            end
            
            function Vector:add(other)
                return Vector:new(self.x + other.x, self.y + other.y)
            end
            
            function Vector:scale(factor)
                return Vector:new(self.x * factor, self.y * factor)
            end
            
            function Vector:dot(other)
                return self.x * other.x + self.y * other.y
            end
            
            local v1 = Vector:new(3, 4)
            local v2 = Vector:new(1, 2)
            local v3 = v1:add(v2)
            local v4 = v3:scale(2)
            
            return v4.x + v4.y
        """,
            )
        // v3 = (3+1, 4+2) = (4, 6)
        // v4 = v3 * 2 = (8, 12)
        // sum = 20
        assertLuaNumber(result, 20.0)
    }

    @Test
    fun testMethodChaining() {
        val result =
            execute(
                """
            local obj = {value = 1}
            
            function obj:add(n)
                self.value = self.value + n
                return self
            end
            
            function obj:mul(n)
                self.value = self.value * n
                return self
            end
            
            function obj:get()
                return self.value
            end
            
            return obj:add(2):mul(3):add(4):get()
        """,
            )
        // (1 + 2) * 3 + 4 = 9 + 4 = 13
        assertLuaNumber(result, 13.0)
    }

    @Test
    fun testFunctionColonSyntaxNestedTable() {
        val result =
            execute(
                """
            local a = {}
            a.b = {value = 42}
            
            function a.b:getValue()
                return self.value
            end
            
            return a.b:getValue()
        """,
            )
        assertLuaNumber(result, 42.0)
    }
}
