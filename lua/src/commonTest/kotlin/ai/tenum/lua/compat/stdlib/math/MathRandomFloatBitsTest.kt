package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test for math.random() float generation with correct bit precision.
 *
 * This tests that math.random() generates floats with the correct number of bits,
 * matching Lua 5.4.8's behavior where random floats should only use the mantissa bits
 * (53 bits for double precision) and no extra bits.
 *
 * From math.lua:855-889 - tests that random floats have exactly the expected precision.
 */
class MathRandomFloatBitsTest : LuaCompatTestBase() {
    @Test
    fun testRandomFloatHasNoExtraBits() =
        runTest {
            // This reproduces the assertion at math.lua:870
            // assert(t * mult % 1 == 0)    -- no extra bits
            val code =
                """
                math.randomseed(42)
                
                -- Get float mantissa bits (53 for double precision)
                local floatbits = 53  -- Standard IEEE 754 double precision
                local randbits = math.min(floatbits, 64)
                local mult = 2^randbits
                
                -- Test a few random floats
                for i = 1, 10 do
                    local t = math.random()
                    
                    -- Check range
                    assert(0 <= t and t < 1, "random() out of range: " .. tostring(t))
                    
                    -- Check that there are no extra bits
                    -- When we multiply by 2^randbits, the result should be a whole number
                    local scaled = t * mult
                    local fractional_part = scaled % 1
                    
                    if fractional_part ~= 0 then
                        error(string.format("Random float has extra bits: t=%.17g, t*mult=%.17g, frac=%.17g", t, scaled, fractional_part))
                    end
                end
                
                return true
                """.trimIndent()

            assertLuaTrue(code, "Random floats should have no extra bits beyond mantissa")
        }

    @Test
    fun testRandomFloatPrecision() =
        runTest {
            // Test that random() generates values with correct precision
            val code =
                """
                math.randomseed(12345)
                
                -- For IEEE 754 double, we have 53 bits of mantissa
                local floatbits = 53
                local randbits = math.min(floatbits, 64)
                
                -- The multiplier should convert our [0,1) float to an integer
                local mult = 2^randbits
                
                local count = 0
                for i = 1, 100 do
                    local t = math.random()
                    
                    -- Scale to integer range
                    local scaled = t * mult
                    
                    -- This should be exact (no fractional part)
                    if scaled % 1 == 0 then
                        count = count + 1
                    end
                end
                
                -- All 100 should have exact scaling
                return count == 100
                """.trimIndent()

            assertLuaTrue(code, "All random floats should scale exactly to integers")
        }
}
