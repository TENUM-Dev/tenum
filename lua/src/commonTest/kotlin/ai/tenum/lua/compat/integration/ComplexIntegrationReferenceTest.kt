package ai.tenum.lua.compat.integration

import ai.tenum.lua.compat.LuaCompatTestBase
import okio.Path.Companion.toPath
import kotlin.test.Test

/**
 * Phase 8.4: Complex Integration Tests (Lua 5.4 Reference)
 *
 * These tests document complex features that work in official Lua 5.4.2
 * but may have limitations in LuaK2 due to implementation constraints.
 *
 * Tests are disabled by default but documented for future enhancement.
 */
class ComplexIntegrationReferenceTest : LuaCompatTestBase() {
    /**
     * REFERENCE: This test works in Lua 5.4.2
     * Verified with: Write-Output '...' | lua54
     *
     * LuaK2 Limitation: string.gmatch not implemented
     * Future: Phase 9+ - Pattern matching library
     */
    @Test
    fun testStringPatternMatchingWithGmatch() {
        val result =
            execute(
                """
            local text = "Hello World Lua Programming"
            local words = {}
            
            for word in string.gmatch(text, "%w+") do
                table.insert(words, word)
            end
            
            local result = table.concat(words, "-")
            return string.upper(result)
        """,
            )

        assertLuaString(result, "HELLO-WORLD-LUA-PROGRAMMING")
    }

    /**
     * REFERENCE: This test works in Lua 5.4.2
     *
     * ✅ ENABLED: Phase 9.1 - Enhanced function declaration syntax implemented
     * function M.name() syntax now fully supported
     */
    @Test
    fun testModuleSystemWithDotSyntax() {
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

    /**
     * NOTE: Floor division operator // IS IMPLEMENTED in LuaK2
     * This test is kept for reference but should actually pass.
     *
     * Verified working in: OperatorsCompatTest.testFloorDivision
     * Implemented: TokenType.FLOOR_DIVIDE, OpCode.IDIV, // operator
     *
     * This test can be removed or un-ignored - it's a duplicate.
     */
    @Test
    fun testFloorDivisionOperator() {
        val result =
            execute(
                """
            local function divmod(a, b)
                return a // b, a % b
            end
            
            local q, r = divmod(17, 5)
            return q * 10 + r
        """,
            )

        // divmod(17,5) = 3, 2
        // Total: 3 * 10 + 2 = 32
        assertLuaNumber(result, 32.0)
    }

    /**
     * REFERENCE: This test works in Lua 5.4.2
     *
     * LuaK2 Issue: Complex pcall with function tables
     * May be related to function storage in tables
     * Future: Investigate and fix
     */
    @Test
    fun testPcallWithFunctionTables() {
        val result =
            execute(
                """
            local tasks = {}
            local completed = 0
            
            local function addTask(name, priority, action)
                table.insert(tasks, {
                    name = name,
                    priority = priority,
                    action = action
                })
            end
            
            local function runTasks()
                table.sort(tasks, function(a, b)
                    return a.priority > b.priority
                end)
                
                for _, task in ipairs(tasks) do
                    local success, result = pcall(task.action)
                    if success then
                        completed = completed + 1
                    end
                end
                
                return completed
            end
            
            addTask("Task1", 3, function() return 1 + 1 end)
            addTask("Task2", 1, function() return 2 * 2 end)
            addTask("Task3", 5, function() return 3 ^ 2 end)
            addTask("Task4", 2, function() error("fail") end)
            
            return runTasks()
        """,
            )

        // 4 tasks, 1 fails, so 3 completed
        assertLuaNumber(result, 3.0)
    }

    /**
     * REFERENCE: This test works in Lua 5.4.2
     *
     * ✅ ENABLED: Phase 9.1 - Proper _ENV system enables module loading
     * require() with FakeFileSystem now works correctly
     */
    @Test
    fun testRequireWithFileSystem() {
        val fs = okio.fakefilesystem.FakeFileSystem()
        vm =
            ai.tenum.lua.vm
                .LuaVmImpl(fs)

        fs.write("mymath.lua".toPath()) {
            writeUtf8(
                """
                local M = {}
                
                M.square = function(x)
                    return x * x
                end
                
                M.cube = function(x)
                    return x * x * x
                end
                
                M.sum = function(...)
                    local total = 0
                    for _, v in ipairs({...}) do
                        total = total + v
                    end
                    return total
                end
                
                return M
            """,
            )
        }

        val result =
            execute(
                """
            local mymath = require('mymath')
            local a = mymath.square(3)
            local b = mymath.cube(2)
            local c = mymath.sum(1, 2, 3, 4, 5)
            return a + b + c
        """,
            )

        // square(3) = 9, cube(2) = 8, sum(1..5) = 15
        // Total: 9 + 8 + 15 = 32
        assertLuaNumber(result, 32.0)
    }

    /**
     * REFERENCE: This test works in Lua 5.4.2
     *
     * Tests const attribute error handling with load()
     * Const violations are compile-time errors, so they must be caught via load()
     * which returns nil on compilation failure, not via pcall of inline functions
     */
    @Test
    fun testConstAttributeErrorInPcall() {
        val result =
            execute(
                """
            local f, err = load("local x <const> = 10; x = 20")
            
            return f == nil and string.find(err, "const") ~= nil
        """,
            )

        assertLuaBoolean(result, true)
    }

    /**
     * REFERENCE: This test works in Lua 5.4.2
     *
     * LuaK2 Issue: table.insert in __close handler
     * May be related to table access during scope cleanup
     * Future: Investigate scope cleanup timing
     */
    @Test
    fun testCloseAttributeWithTableInsert() {
        val result =
            execute(
                """
            local closed = {}
            
            local function makeResource(id)
                local res = {id = id}
                setmetatable(res, {
                    __close = function(self)
                        table.insert(closed, self.id)
                    end
                })
                return res
            end
            
            do
                local r1 <close> = makeResource(1)
                local r2 <close> = makeResource(2)
                local r3 <close> = makeResource(3)
            end
            
            -- Should close in reverse order: 3, 2, 1
            return closed[1] * 100 + closed[2] * 10 + closed[3]
        """,
            )

        // Closes in reverse: 3, 2, 1
        // Result: 3*100 + 2*10 + 1 = 321
        assertLuaNumber(result, 321.0)
    }

    /**
     * REFERENCE: This test works in Lua 5.4.2
     *
     * LuaK2 Limitation: Custom iterator functions
     * Generic for with custom iterators may have edge cases
     * Future: Validate iterator protocol implementation
     */
    @Test
    fun testCustomIteratorFunction() {
        val result =
            execute(
                """
            local function range(n)
                local i = 0
                return function()
                    i = i + 1
                    if i <= n then
                        return i
                    end
                end
            end
            
            local sum = 0
            for value in range(10) do
                sum = sum + value
            end
            
            return sum
        """,
            )

        // 1+2+3+...+10 = 55
        assertLuaNumber(result, 55.0)
    }

    /**
     * REFERENCE: This test works in Lua 5.4.2
     *
     * LuaK2 Limitation: Complex varargs with multiple functions
     * May have edge cases with vararg propagation
     * Future: Comprehensive varargs validation
     */
    @Test
    fun testComplexVarargsWithStats() {
        val result =
            execute(
                """
            local function stats(...)
                local args = table.pack(...)
                local sum = 0
                local min = args[1]
                local max = args[1]
                
                for i = 1, args.n do
                    sum = sum + args[i]
                    if args[i] < min then min = args[i] end
                    if args[i] > max then max = args[i] end
                end
                
                return sum, min, max
            end
            
            local sum, min, max = stats(3, 7, 2, 9, 1, 5)
            
            return sum + min + max
        """,
            )

        // stats(3,7,2,9,1,5) = 27, 1, 9
        // Total: 27 + 1 + 9 = 37
        assertLuaNumber(result, 37.0)
    }

    /**
     * REFERENCE: This test works in Lua 5.4.2
     *
     * ✅ ENABLED: Phase 9.1 - Method definition syntax implemented
     * function t:method() syntax now fully supported with implicit self
     */
    @Test
    fun testObjectOrientedVector() {
        val result =
            execute(
                """
            local Vector = {}
            Vector.__index = Vector
            
            function Vector:new(x, y)
                local v = {x = x, y = y}
                setmetatable(v, Vector)
                return v
            end
            
            function Vector.__add(a, b)
                return Vector:new(a.x + b.x, a.y + b.y)
            end
            
            function Vector.__mul(a, scalar)
                return Vector:new(a.x * scalar, a.y * scalar)
            end
            
            local v1 = Vector:new(3, 4)
            local v2 = Vector:new(1, 2)
            local v3 = v1 + v2
            local v4 = v3 * 2
            
            return v4.x + v4.y
        """,
            )

        // v3 = (3+1, 4+2) = (4, 6)
        // v4 = v3 * 2 = (8, 12)
        // sum = 20
        assertLuaNumber(result, 20.0)
    }
}
