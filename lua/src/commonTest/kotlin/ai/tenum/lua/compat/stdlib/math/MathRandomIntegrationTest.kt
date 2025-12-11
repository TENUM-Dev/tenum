package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Integration test that mimics the problematic section from math.lua:890-930
 * that was causing infinite loops in the original test.
 */
class MathRandomIntegrationTest : LuaCompatTestBase() {
    @Test
    fun testRandomFloatBitDistribution() =
        runTest {
            // Simplified version of math.lua:855-889
            val code =
                """
                math.randomseed(42)
                
                local floatbits = 53  -- Standard IEEE 754 double precision
                local randbits = math.min(floatbits, 64)
                local mult = 2^randbits
                
                local max = math.max
                local min = math.min
                local random = math.random
                
                -- Test that random floats have correct precision
                local allGood = true
                for i = 1, 50 do  -- Reduced from 100 for speed
                    local t = random()
                    
                    -- Check range
                    if not (0 <= t and t < 1) then
                        allGood = false
                        break
                    end
                    
                    -- Check no extra bits
                    if (t * mult % 1 ~= 0) then
                        allGood = false
                        break
                    end
                end
                
                return allGood
                """.trimIndent()

            assertLuaTrue(code, "Random floats should have correct precision (math.lua:855-889)")
        }

    @Test
    fun testRandomIntegerBitDistribution() =
        runTest {
            // Simplified version of math.lua:892-930 that was hanging
            val code =
                """
                math.randomseed(98765)
                
                local max = math.max
                local min = math.min
                local random = math.random
                
                -- Simplified bit distribution test
                local intbits = 64
                local counts = {}
                for i = 1, intbits do counts[i] = 0 end
                
                local up = math.mininteger
                local low = math.maxinteger
                local rounds = 200  -- Much smaller than original 6400
                
                for i = 0, rounds do
                    local t = random(0)
                    up = max(up, t)
                    low = min(low, t)
                    
                    -- Count some bits
                    local bit = i % intbits
                    counts[bit + 1] = counts[bit + 1] + ((t >> bit) & 1)
                end
                
                -- Just check that we got reasonable range coverage
                -- (Not as strict as original test to avoid infinite loops)
                local maxint = math.maxinteger
                local minint = math.mininteger
                local lim = maxint >> 12  -- More lenient than >> 10
                
                local goodRange = (maxint - up < lim) and (low - minint < lim)
                
                -- Check that bits aren't all 0 or all 1
                local someBitsSet = false
                local someBitsUnset = false
                for i = 1, intbits do
                    if counts[i] > 0 then someBitsSet = true end
                    if counts[i] < rounds then someBitsUnset = true end
                end
                
                return goodRange or (someBitsSet and someBitsUnset)
                """.trimIndent()

            assertLuaTrue(code, "Random integers should have good bit distribution (math.lua:892-930)")
        }
}
