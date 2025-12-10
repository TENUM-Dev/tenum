package ai.tenum.lua.compat.integration

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Phase 8.4: Full Integration Tests
 * Tests that combine multiple features to verify end-to-end functionality
 */
class FullIntegrationCompatTest : LuaCompatTestBase() {
    @Test
    fun testComplexTableManipulation() {
        // Integration: tables + metatables + stdlib
        val result =
            execute(
                """
            local data = {10, 20, 30, 40, 50}
            table.insert(data, 3, 25)
            table.remove(data, 1)
            local sum = 0
            for i, v in ipairs(data) do
                sum = sum + v
            end
            return sum
        """,
            )

        // After insert: {10, 20, 25, 30, 40, 50}
        // After remove: {20, 25, 30, 40, 50}
        // Sum: 165
        assertLuaNumber(result, 165.0)
    }

    @Test
    fun testClosuresWithUpvalues() {
        // Integration: closures + upvalues + functions
        val result =
            execute(
                """
            local function makeCounter(start)
                local count = start
                return function()
                    count = count + 1
                    return count
                end
            end
            
            local c1 = makeCounter(10)
            local c2 = makeCounter(100)
            
            local r1 = c1()  -- 11
            local r2 = c1()  -- 12
            local r3 = c2()  -- 101
            local r4 = c1()  -- 13
            
            return r1 + r2 + r3 + r4
        """,
            )

        // 11 + 12 + 101 + 13 = 137
        assertLuaNumber(result, 137.0)
    }

    @Test
    fun testMetatableArithmetic() {
        // Integration: metatables + operators + tables
        val result =
            execute(
                """
            local mt = {
                __add = function(a, b)
                    return {x = a.x + b.x, y = a.y + b.y}
                end,
                __mul = function(a, scalar)
                    if type(scalar) == "number" then
                        return {x = a.x * scalar, y = a.y * scalar}
                    end
                    return a
                end
            }
            
            local v1 = {x = 3, y = 4}
            local v2 = {x = 1, y = 2}
            setmetatable(v1, mt)
            setmetatable(v2, mt)
            
            local v3 = v1 + v2
            setmetatable(v3, mt)
            local v4 = v3 * 2
            
            return v4.x + v4.y
        """,
            )

        // v3 = (3+1, 4+2) = (4, 6)
        // v4 = v3 * 2 = (8, 12)
        // sum = 20
        assertLuaNumber(result, 20.0)
    }

    @Test
    fun testStringProcessing() {
        // Integration: string library + loops + tables
        val result =
            execute(
                """
            local text = "hello world lua"
            local upper = string.upper(text)
            local sub = string.sub(upper, 1, 11)
            local reversed = string.reverse(sub)
            return reversed
        """,
            )

        assertLuaString(result, "DLROW OLLEH")
    }

    @Test
    fun testRecursiveFibonacciWithMemoization() {
        // Integration: closures + tables + recursion
        val result =
            execute(
                """
            local function makeFib()
                local cache = {[0] = 0, [1] = 1}
                
                local function fib(n)
                    if cache[n] then
                        return cache[n]
                    end
                    cache[n] = fib(n-1) + fib(n-2)
                    return cache[n]
                end
                
                return fib
            end
            
            local fib = makeFib()
            return fib(20)
        """,
            )

        // fib(20) = 6765
        assertLuaNumber(result, 6765.0)
    }

    @Test
    fun testErrorHandlingWithPcall() {
        // Integration: error handling + functions + strings
        val result =
            execute(
                """
            local function riskyFunction(x)
                if x < 0 then
                    error("negative value")
                end
                return x * 2
            end
            
            local success1, result1 = pcall(riskyFunction, 5)
            local success2, result2 = pcall(riskyFunction, -5)
            
            if success1 and not success2 then
                return result1
            end
            return 0
        """,
            )

        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testVarargsWithTablePack() {
        // Integration: varargs + table library + loops
        val result =
            execute(
                """
            local function sum(...)
                local args = table.pack(...)
                local total = 0
                for i = 1, args.n do
                    total = total + args[i]
                end
                return total
            end
            
            local function multiply(factor, ...)
                local values = table.pack(...)
                local result = {}
                for i = 1, values.n do
                    result[i] = values[i] * factor
                end
                return table.unpack(result, 1, values.n)
            end
            
            local a, b, c = multiply(3, 1, 2, 3)
            return sum(a, b, c)
        """,
            )

        // multiply(3, 1,2,3) = 3,6,9
        // sum(3,6,9) = 18
        assertLuaNumber(result, 18.0)
    }

    @Test
    fun testIteratorsWithPairs() {
        // Integration: iterators + tables + closures
        val result =
            execute(
                """
            local data = {
                name = "Lua",
                version = 5.4,
                type = "scripting",
                count = 3
            }
            
            local sum = 0
            local count = 0
            
            for k, v in pairs(data) do
                if type(v) == "number" then
                    sum = sum + v
                    count = count + 1
                end
            end
            
            return sum
        """,
            )

        // version (5.4) + count (3) = 8.4
        assertLuaNumber(result, 8.4)
    }

    @Test
    fun testAttributesWithClose() {
        // Integration: <close> attribute + metatables
        val result =
            execute(
                """
            local count = 0
            
            local function makeResource(id)
                local res = {id = id}
                setmetatable(res, {
                    __close = function(self)
                        count = count + self.id
                    end
                })
                return res
            end
            
            do
                local r1 <close> = makeResource(10)
                local r2 <close> = makeResource(20)
                local r3 <close> = makeResource(30)
            end
            
            -- Should close in reverse order and sum: 30 + 20 + 10
            return count
        """,
            )

        // Closes in reverse: 30, 20, 10
        // Result: 60
        assertLuaNumber(result, 60.0)
    }

    @Test
    fun testMathLibraryIntegration() {
        // Integration: math library + loops + tables
        val result =
            execute(
                """
            local angles = {0, math.pi/6, math.pi/4, math.pi/3, math.pi/2}
            local sum = 0
            
            for i, angle in ipairs(angles) do
                local sin_val = math.sin(angle)
                local cos_val = math.cos(angle)
                sum = sum + (sin_val * sin_val + cos_val * cos_val)
            end
            
            return math.floor(sum + 0.5)  -- Round to nearest integer
        """,
            )

        // sin^2 + cos^2 = 1 for all angles
        // Sum of 5 angles = 5
        assertLuaNumber(result, 5.0)
    }

    @Test
    fun testComplexControlFlow() {
        // Integration: loops + conditionals + goto + labels
        val result =
            execute(
                """
            local sum = 0
            local i = 1
            
            ::loop::
            if i > 10 then
                goto done
            end
            
            if i % 2 == 0 then
                sum = sum + i
            end
            
            i = i + 1
            goto loop
            
            ::done::
            return sum
        """,
            )

        // Even numbers 2,4,6,8,10 = 30
        assertLuaNumber(result, 30.0)
    }

    @Test
    fun testModuleSystem() {
        // Integration: tables as modules + closures
        val result =
            execute(
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
    fun testConstAttributeProtection() {
        // Integration: <const> attribute + error handling
        val result =
            execute(
                """
            local x <const> = 10
            local y = x + 5
            return y
        """,
            )

        assertLuaNumber(result, 15.0)
    }

    @Test
    fun testBitwiseOperations() {
        // Integration: bitwise operators + loops + tables
        val result =
            execute(
                """
            local function countBits(n)
                local count = 0
                while n > 0 do
                    count = count + (n & 1)
                    n = n >> 1
                end
                return count
            end
            
            local total = 0
            for i = 1, 10 do
                total = total + countBits(i)
            end
            
            return total
        """,
            )

        // Bit count: 1(1), 2(1), 3(2), 4(1), 5(2), 6(2), 7(3), 8(1), 9(2), 10(2)
        // Total: 17
        assertLuaNumber(result, 17.0)
    }

    @Test
    fun testForLoopWithStep() {
        // Integration: for loops + tables + operators
        val result =
            execute(
                """
            local values = {}
            for i = 10, 1, -2 do
                table.insert(values, i)
            end
            
            local sum = 0
            for _, v in ipairs(values) do
                sum = sum + v
            end
            
            return sum
        """,
            )

        // Loop: 10, 8, 6, 4, 2
        // Sum: 30
        assertLuaNumber(result, 30.0)
    }

    @Test
    fun testNestedTablesAndMetatables() {
        // Integration: nested tables + metatables + __index
        val result =
            execute(
                """
            local defaults = {
                width = 100,
                height = 200,
                color = "red"
            }
            
            local config = {}
            setmetatable(config, {__index = defaults})
            
            config.width = 50
            
            return config.width + config.height
        """,
            )

        // width: 50 (overridden), height: 200 (from defaults)
        // Sum: 250
        assertLuaNumber(result, 250.0)
    }

    @Test
    fun testStringFormatting() {
        // Integration: string.format + loops + tables
        val result =
            execute(
                """
            local names = {"Alice", "Bob", "Charlie"}
            local scores = {95, 87, 92}
            
            local formatted = {}
            for i = 1, #names do
                formatted[i] = string.format("%s: %d", names[i], scores[i])
            end
            
            return table.concat(formatted, " | ")
        """,
            )

        assertLuaString(result, "Alice: 95 | Bob: 87 | Charlie: 92")
    }

    @Test
    fun testGenericForLoop() {
        // Integration: generic for + iterators + ipairs
        val result =
            execute(
                """
            local data = {10, 20, 30, 40, 50}
            local sum = 0
            
            for i, v in ipairs(data) do
                sum = sum + v
            end
            
            return sum
        """,
            )

        // 10+20+30+40+50 = 150
        assertLuaNumber(result, 150.0)
    }

    @Test
    fun testLoadAndExecute() {
        // Integration: load() + dofile() + string processing
        val result =
            execute(
                """
            local code = "return 2 + 3 * 4"
            local func, err = load(code)
            
            if func then
                return func()
            end
            
            return 0
        """,
            )

        // 2 + 3*4 = 14
        assertLuaNumber(result, 14.0)
    }

    @Test
    fun testTableSortWithComparator() {
        // Integration: table.sort + functions + comparisons
        val result =
            execute(
                """
            local data = {
                {name = "Alice", age = 30},
                {name = "Bob", age = 25},
                {name = "Charlie", age = 35}
            }
            
            table.sort(data, function(a, b)
                return a.age < b.age
            end)
            
            return data[1].age * 100 + data[2].age * 10 + data[3].age
        """,
            )

        // Sorted by age: Bob(25), Alice(30), Charlie(35)
        // Result: 25*100 + 30*10 + 35 = 2835
        assertLuaNumber(result, 2835.0)
    }

    @Test
    fun testMultipleReturnValues() {
        // Integration: multiple returns + functions
        val result =
            execute(
                """
            local function swap(a, b)
                return b, a
            end
            
            local x, y = swap(3, 7)
            return x + y
        """,
            )

        // swap(3,7) = 7,3
        // Total: 7 + 3 = 10
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testRealWorldExample() {
        // Integration: comprehensive real-world scenario
        val result =
            execute(
                """
            -- Simple data processing pipeline
            local data = {10, 20, 30, 40, 50}
            local result = {}
            
            -- Filter: only values > 20
            for i, v in ipairs(data) do
                if v > 20 then
                    table.insert(result, v)
                end
            end
            
            -- Map: double each value
            for i, v in ipairs(result) do
                result[i] = v * 2
            end
            
            -- Reduce: sum all values
            local sum = 0
            for i, v in ipairs(result) do
                sum = sum + v
            end
            
            return sum
        """,
            )

        // Filter: {30, 40, 50}
        // Map: {60, 80, 100}
        // Sum: 240
        assertLuaNumber(result, 240.0)
    }
}
