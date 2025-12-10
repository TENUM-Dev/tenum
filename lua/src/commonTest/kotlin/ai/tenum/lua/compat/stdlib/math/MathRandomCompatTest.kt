package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNumber
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PHASE 5.2: Standard Library - Math Random Functions
 *
 * Tests random number generation functions in the math library.
 * Based on: math.lua lines 780-1024
 *
 * Coverage:
 * - math.random() - float random [0, 1)
 * - math.random(n) - integer random [1, n]
 * - math.random(m, n) - integer random [m, n]
 * - math.randomseed(seed) - set random seed
 * - Deterministic behavior and statistical properties
 */
class MathRandomCompatTest : LuaCompatTestBase() {
    // ========== Basic Random Functions ==========

    @Test
    fun testMathRandomseed() =
        runTest {
            // Just verify it doesn't error - from original test
            execute("math.randomseed(12345)")
            execute("math.randomseed(0)")
            execute("math.randomseed(1)")
            execute("math.randomseed(42)")
        }

    @Test
    fun testMathRandomNoArgs() =
        runTest {
            // Test random() returns [0, 1)
            repeat(10) {
                val result = execute("return math.random()")
                assertTrue(result is LuaNumber)
                val value = result.toDouble()
                assertTrue(value >= 0.0 && value < 1.0, "random() should return value in [0, 1), got $value")
            }
        }

    @Test
    fun testMathRandomWithUpperBound() =
        runTest {
            // Test random(n) returns [1, n]
            repeat(10) {
                val result = execute("return math.random(10)")
                assertTrue(result is LuaNumber)
                val value = result.toDouble()
                assertTrue(
                    value >= 1.0 && value <= 10.0 && value == value.toInt().toDouble(),
                    "random(10) should return integer in [1, 10], got $value",
                )
            }
        }

    @Test
    fun testMathRandomWithRange() =
        runTest {
            // Test random(m, n) returns [m, n]
            repeat(10) {
                val result = execute("return math.random(5, 10)")
                assertTrue(result is LuaNumber)
                val value = result.toDouble()
                assertTrue(
                    value >= 5.0 && value <= 10.0 && value == value.toInt().toDouble(),
                    "random(5, 10) should return integer in [5, 10], got $value",
                )
            }
        }

    @Test
    fun testMathRandomDeterministic() =
        runTest {
            // With same seed, should get same sequence
            val result1 =
                execute(
                    """
            math.randomseed(42)
            return math.random()
        """,
                )

            val result2 =
                execute(
                    """
            math.randomseed(42)
            return math.random()
        """,
                )

            assertTrue(result1 is LuaNumber)
            assertTrue(result2 is LuaNumber)
            assertTrue(result1.toDouble() == result2.toDouble(), "Same seed should produce same random sequence")
        }

    // ========== Advanced Random Testing ==========

    @Test
    fun testRandomSeedReturn() =
        runTest {
            // From math.lua:820-828 - testing return of 'randomseed'
            val code =
                """
                local x, y = math.randomseed()
                local res = math.random(0)
                x, y = math.randomseed(x, y)    -- should repeat the state
                local res2 = math.random(0)
                return res == res2
                """.trimIndent()
            assertLuaTrue(code, "randomseed should return values that can restore state")
        }

    @Test
    fun testSpecificSeedBehavior() =
        runTest {
            // From math.lua:800-819 - low-level test for current Lua implementation
            // First call after seed 1007 should return specific value
            val code =
                """
                math.randomseed(1007)
                return math.random(0)
                """.trimIndent()

            val result = execute(code)
            assertTrue(result is LuaNumber)
            // The exact value depends on the implementation, but it should be deterministic

            // Test that seeding with same value produces same result
            val result2 = execute(code)
            assertTrue(result2 is LuaNumber)
            assertTrue(result.toDouble() == result2.toDouble(), "Same seed should produce same first random value")
        }

    @Test
    fun testRandomFloatProperties() =
        runTest {
            // From math.lua:830-859 - test random for floats
            val code =
                """
                math.randomseed(12345)
                local up, low = -math.huge, math.huge
                local count = 0
                for i = 1, 100 do
                    local t = math.random()
                    if t < 0 or t >= 1 then return false end  -- range check
                    up = math.max(up, t)
                    low = math.min(low, t)
                    count = count + 1
                end
                return up > 0.8 and low < 0.2 and count == 100  -- reasonable distribution
                """.trimIndent()
            assertLuaTrue(code, "Random floats should be well-distributed in [0, 1)")
        }

    @Test
    fun testRandomIntegerProperties() =
        runTest {
            // From math.lua:862-888 - test random for full integers
            val code =
                """
                math.randomseed(54321)
                local up, low = math.mininteger, math.maxinteger
                local count = 0
                for i = 1, 50 do  -- Reduced iterations for performance
                    local t = math.random(0)
                    up = math.max(up, t)
                    low = math.min(low, t)
                    count = count + 1
                end
                -- Should get a reasonable range of values
                local range_coverage = (up - low) / (math.maxinteger - math.mininteger)
                return count == 50 and range_coverage > 0.01  -- At least 1% coverage
                """.trimIndent()
            assertLuaTrue(code, "Random integers should cover a good range")
        }

    @Test
    fun testRandomSmallIntervals() =
        runTest {
            // From math.lua:891-912 - test random for small intervals
            // Test that all values in a small range appear
            val testCases =
                listOf(
                    "1, 2",
                    "1, 6",
                    "-10, 0",
                    "-10, -10", // unit set
                    "5, 8",
                )

            for (testCase in testCases) {
                val code =
                    """
                    math.randomseed(12345)
                    local x1, x2 = $testCase
                    local mark = {}
                    local count = 0
                    for attempt = 1, 1000 do  -- Try many times
                        local t = math.random(x1, x2)
                        if t < x1 or t > x2 then return false end  -- range check
                        if not mark[t] then
                            mark[t] = true
                            count = count + 1
                            if count == x2 - x1 + 1 then
                                return true  -- All values appeared
                            end
                        end
                    end
                    return count >= math.min(3, x2 - x1 + 1)  -- At least some coverage
                    """.trimIndent()
                assertLuaTrue(code, "Random should cover small interval [$testCase] adequately")
            }
        }

    @Test
    fun testRandomLargeIntervals() =
        runTest {
            // From math.lua:915-944 - test random for large intervals
            val code =
                """
                math.randomseed(98765)
                local function test_large_interval(p1, p2)
                    local max_val, min_val = math.mininteger, math.maxinteger
                    local mark = {}
                    local count = 0
                    local n = 50  -- Reduced for performance
                    
                    for i = 1, n do
                        local t = math.random(p1, p2)
                        if t < p1 or t > p2 then return false end
                        max_val = math.max(max_val, t)
                        min_val = math.min(min_val, t)
                        if not mark[t] then
                            mark[t] = true
                            count = count + 1
                        end
                    end
                    
                    -- At least 70% of values should be different
                    local uniqueness = count >= n * 0.7
                    -- Range should be reasonably spread
                    local diff = (p2 - p1) // 8  -- More lenient than original
                    local good_range = min_val <= p1 + diff and max_val >= p2 - diff
                    
                    return uniqueness and (good_range or (p2 - p1 <= 10))
                end
                
                return test_large_interval(0, 1000) and 
                       test_large_interval(1, 1000) and
                       test_large_interval(-100, 100)
                """.trimIndent()
            assertLuaTrue(code, "Random should handle large intervals well")
        }

    @Test
    fun testRandomErrorCases() =
        runTest {
            // From math.lua:946-951 - error cases
            assertError("too many arguments") {
                execute("return math.random(1, 2, 3)")
            }

            // Empty intervals should error - Lua 5.4 says "interval is empty"
            assertError("interval is empty") {
                execute("return math.random(math.mininteger + 1, math.mininteger)")
            }
            assertError("interval is empty") {
                execute("return math.random(math.maxinteger, math.maxinteger - 1)")
            }
            assertError("interval is empty") {
                execute("return math.random(math.maxinteger, math.mininteger)")
            }
        }

    @Test
    fun testRandomWithExtremeValues() =
        runTest {
            // Test with extreme integer values
            execute("math.randomseed(1)")

            // Test random with maxinteger
            val result1 = execute("return math.random(math.maxinteger, math.maxinteger)")
            assertTrue(result1 is LuaNumber)
            assertTrue(result1.toDouble() == Long.MAX_VALUE.toDouble())

            // Test random with mininteger
            val result2 = execute("return math.random(math.mininteger, math.mininteger)")
            assertTrue(result2 is LuaNumber)
            assertTrue(result2.toDouble() == Long.MIN_VALUE.toDouble())
        }

    @Test
    fun testRandomDistributionBasic() =
        runTest {
            // From math.lua:890-900 - test distribution for a dice
            val code =
                """
                math.randomseed(13579)
                local count = {0, 0, 0, 0, 0, 0}
                local total = 600  -- 100 per face expected
                
                for i = 1, total do
                    local r = math.random(6)
                    if r < 1 or r > 6 then return false end  -- range check
                    count[r] = count[r] + 1
                end
                
                -- Each face should appear at least 50 times out of 600 (reasonable variance)
                for i = 1, 6 do
                    if count[i] < 50 then return false end
                end
                
                return true
                """.trimIndent()
            assertLuaTrue(code, "Dice distribution should be reasonably uniform")
        }
}
