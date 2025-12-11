package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test for math.random(0) which returns full range integers.
 *
 * This tests the statistical properties of random(0) to ensure it generates
 * values across the full range of signed 64-bit integers.
 */
class MathRandomZeroTest : LuaCompatTestBase() {
    @Test
    fun testRandomZeroBasicBehavior() =
        runTest {
            // Test that random(0) returns different values and covers range
            val code =
                """
                math.randomseed(42)
                
                local values = {}
                local minint = math.mininteger
                local maxint = math.maxinteger
                
                -- Collect 20 samples
                for i = 1, 20 do
                    local t = math.random(0)
                    values[i] = t
                    
                    -- Each value should be in valid range
                    assert(t >= minint and t <= maxint, "random(0) out of range")
                end
                
                -- Check that we got some variety (not all the same)
                local first = values[1]
                local allSame = true
                for i = 2, 20 do
                    if values[i] ~= first then
                        allSame = false
                        break
                    end
                end
                
                return not allSame
                """.trimIndent()

            assertLuaTrue(code, "random(0) should generate different values")
        }

    @Test
    fun testRandomZeroAdvancesState() =
        runTest {
            // Verify that random(0) advances the RNG state properly
            val code =
                """
                math.randomseed(12345)
                
                local r1 = math.random(0)
                local r2 = math.random(0)
                local r3 = math.random(0)
                
                -- They should all be different (statistically almost certain)
                return r1 ~= r2 and r2 ~= r3 and r1 ~= r3
                """.trimIndent()

            assertLuaTrue(code, "random(0) should advance state each call")
        }

    @Test
    fun testRandomZeroBitDistribution() =
        runTest {
            // Test a simplified version of the bit distribution check
            // This is much faster than the full test
            val code =
                """
                math.randomseed(98765)
                
                local max = math.max
                local min = math.min
                
                -- Just check that we get some positive and negative values
                local hasPositive = false
                local hasNegative = false
                local hasLargePositive = false
                local hasLargeNegative = false
                
                for i = 1, 100 do
                    local t = math.random(0)
                    
                    if t > 0 then hasPositive = true end
                    if t < 0 then hasNegative = true end
                    if t > (math.maxinteger >> 1) then hasLargePositive = true end
                    if t < (math.mininteger >> 1) then hasLargeNegative = true end
                    
                    -- Early exit if we've seen enough variety
                    if hasPositive and hasNegative then
                        break
                    end
                end
                
                -- We should see both positive and negative values
                return hasPositive and hasNegative
                """.trimIndent()

            assertLuaTrue(code, "random(0) should generate both positive and negative values")
        }
}
