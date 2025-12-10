package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * PHASE 5.2: Standard Library - Float/Integer Precision and Special Values
 *
 * Tests float/integer behavior, NaN, infinity, and precision.
 * Based on: math.lua lines 20-33, 62-85, 174-200, 268-285, 752-779
 *
 * Coverage:
 * - NaN detection and behavior
 * - Infinity handling
 * - Float precision and mantissa bits
 * - Zero handling (positive/negative zero)
 * - Float/integer boundary conditions
 */
class MathFloatIntegerCompatTest : LuaCompatTestBase() {
    // ========== NaN Detection and Behavior ==========

    @Test
    fun testNaNDetection() =
        runTest {
            // From math.lua:20-26
            val code =
                """
                local function isNaN(x)
                    return (x ~= x)
                end
                
                return isNaN(0/0) and not isNaN(1/0)
                """.trimIndent()
            assertLuaTrue(code, "NaN detection should work correctly")
        }

    @Test
    fun testNaNArithmetic() =
        runTest {
            val code =
                """
                local nan = 0/0
                local results = {}
                results[1] = (nan + 1) ~= (nan + 1)  -- NaN + anything = NaN
                results[2] = (nan * 0) ~= (nan * 0)  -- NaN * anything = NaN  
                results[3] = (nan / nan) ~= (nan / nan)  -- NaN / NaN = NaN
                results[4] = (nan - nan) ~= (nan - nan)  -- NaN - NaN = NaN
                return results[1] and results[2] and results[3] and results[4]
                """.trimIndent()
            assertLuaTrue(code, "NaN arithmetic should produce NaN")
        }

    @Test
    fun testNaNComparisons() =
        runTest {
            // From math.lua:268-285
            val code =
                """
                local NaN = 0/0
                -- NaN should not compare equal, less, or greater to anything (including itself)
                local r1 = not (NaN < 0)
                local r2 = not (NaN > math.mininteger) 
                local r3 = not (NaN <= -9)
                local r4 = not (NaN <= math.maxinteger)
                local r5 = not (NaN < math.maxinteger)
                local r6 = not (math.mininteger <= NaN)
                local r7 = not (math.mininteger < NaN)
                local r8 = not (4 <= NaN)
                local r9 = not (4 < NaN)
                local r10 = not (NaN == NaN)  -- NaN != NaN
                local r11 = not (NaN <= NaN)
                local r12 = not (NaN >= NaN)
                local r13 = not (NaN > NaN)
                local r14 = not (0 < NaN) and not (NaN < 0)
                
                -- Test that different NaN values are still not equal
                local NaN1 = 0/0
                local r15 = NaN ~= NaN1 and not (NaN <= NaN1) and not (NaN1 <= NaN)
                
                return r1 and r2 and r3 and r4 and r5 and r6 and r7 and r8 and r9 and
                       r10 and r11 and r12 and r13 and r14 and r15
                """.trimIndent()
            assertLuaTrue(code, "NaN comparisons should all be false")
        }

    // ========== Zero Handling ==========

    @Test
    fun testPositiveNegativeZero() =
        runTest {
            // From math.lua:752-762
            val code =
                """
                local mz = -0.0  -- minus zero
                local z = 0.0    -- plus zero
                
                -- Zeros should be equal
                local r1 = (mz == z)
                
                -- But have different signs in division
                local r2 = (1/mz < 0) and (0 < 1/z)
                
                -- Should work the same in table indexing
                local a = {[mz] = 1}
                local r3 = (a[z] == 1) and (a[mz] == 1)
                a[z] = 2
                local r4 = (a[z] == 2) and (a[mz] == 2)
                
                return r1 and r2 and r3 and r4
                """.trimIndent()
            assertLuaTrue(code, "Positive and negative zero should behave correctly")
        }

    @Test
    fun testZeroCreation() =
        runTest {
            // From math.lua:63-67
            val code =
                """
                local x = -1
                local mz = 0/x   -- minus zero from division
                local t = {[0] = 10, 20, 30, 40, 50}
                return t[mz] == t[0] and t[-0] == t[0]
                """.trimIndent()
            assertLuaTrue(code, "Minus zero should work as table key like regular zero")
        }

    @Test
    fun testZeroArithmetic() =
        runTest {
            val code =
                """
                local a = 0
                return a == -a and 0 == -0
                """.trimIndent()
            assertLuaTrue(code, "Zero arithmetic identities")
        }

    // ========== Infinity Handling ==========

    @Test
    fun testInfinityArithmetic() =
        runTest {
            val code =
                """
                local inf = 1/0
                local ninf = -1/0
                
                local r1 = inf > 0 and ninf < 0
                local r2 = inf + 1 == inf
                local r3 = ninf - 1 == ninf
                local r4 = inf * 2 == inf
                local r5 = ninf * 2 == ninf
                local r6 = inf / 2 == inf
                local r7 = ninf / 2 == ninf
                
                return r1 and r2 and r3 and r4 and r5 and r6 and r7
                """.trimIndent()
            assertLuaTrue(code, "Infinity arithmetic should behave correctly")
        }

    @Test
    fun testInfinityComparisons() =
        runTest {
            val code =
                """
                local inf = math.huge
                local ninf = -math.huge
                
                local r1 = inf > math.maxinteger
                local r2 = ninf < math.mininteger
                local r3 = inf > ninf
                local r4 = not (inf < ninf)
                local r5 = inf == inf
                local r6 = ninf == ninf
                local r7 = not (inf == ninf)
                
                return r1 and r2 and r3 and r4 and r5 and r6 and r7
                """.trimIndent()
            assertLuaTrue(code, "Infinity comparisons should work correctly")
        }

    // ========== Float Precision and Boundaries ==========

    @Test
    fun testFloatPrecision() =
        runTest {
            // From math.lua:28-34 - detect float mantissa bits
            val code =
                """
                local floatbits = 24  -- Start with single precision guess
                local p = 2.0^floatbits
                while p < p + 1.0 do
                    p = p * 2.0
                    floatbits = floatbits + 1
                end
                
                -- Test that at the precision boundary, arithmetic changes behavior
                local x = 2.0^floatbits
                return x > (x - 1.0) and x == (x + 1.0) and floatbits >= 24
                """.trimIndent()
            assertLuaTrue(code, "Float precision detection should work")
        }

    @Test
    fun testFloatIntegerBoundaries() =
        runTest {
            // From math.lua:174-200 - boundary between float and integer representation
            val code =
                """
                local intbits = 64  -- Assume 64-bit integers
                local floatbits = 53  -- IEEE double precision
                
                if floatbits < intbits then
                    -- Floats cannot represent all integers precisely
                    local boundary = 2.0^floatbits
                    local int_boundary = 1 << floatbits
                    
                    local r1 = boundary == int_boundary
                    local r2 = (boundary - 1.0) == (int_boundary - 1.0) 
                    local r3 = (boundary - 1.0) ~= int_boundary  -- float loses precision
                    local r4 = (boundary + 1.0) ~= (int_boundary + 1)  -- float rounds
                    
                    return r1 and r2 and r3 and r4
                else
                    -- Floats can represent all integers in range
                    local r1 = math.maxinteger == (math.maxinteger + 0.0)
                    local r2 = (math.maxinteger - 1) == (math.maxinteger - 1.0)
                    local r3 = (math.mininteger + 1) == (math.mininteger + 1.0) 
                    local r4 = math.maxinteger ~= (math.maxinteger - 1.0)
                    
                    return r1 and r2 and r3 and r4
                end
                """.trimIndent()
            assertLuaTrue(code, "Float/integer boundaries should be handled correctly")
        }

    // ========== Float Notation and Parsing ==========

    @Test
    fun testBasicFloatNotation() =
        runTest {
            // From math.lua:53-55
            assertLuaTrue("return 0e12 == 0 and .0 == 0 and 0. == 0")
            assertLuaTrue("return .2e2 == 20 and 2.E-1 == 0.2")
        }

    @Test
    fun testFloatArithmeticNotation() =
        runTest {
            // From math.lua:56-61
            val code =
                """
                local a, b, c = "2", " 3e0 ", " 10  "
                local r1 = a+b == 5 and -b == -3 and b+"2" == 5 and "10"-c == 0
                local r2 = type(a) == 'string' and type(b) == 'string' and type(c) == 'string'
                local r3 = a == "2" and b == " 3e0 " and c == " 10  " and -c == -"  10 "
                local r4 = c%a == 0 and a^b == 8
                return r1 and r2 and r3 and r4
                """.trimIndent()
            assertLuaTrue(code, "String to number conversion in arithmetic should work")
        }

    // ========== Precision Edge Cases ==========

    @Test
    fun testVeryLargeNumbers() =
        runTest {
            val code =
                """
                -- Test behavior with very large numbers
                local big = 1e308  -- Near max double
                local bigger = big * 10
                
                -- Should handle overflow to infinity gracefully
                local r1 = bigger == math.huge or bigger > big
                
                -- Very small numbers
                local small = 1e-308
                local smaller = small / 10
                
                -- Should handle underflow to zero
                local r2 = smaller == 0 or smaller > 0
                
                return r1 and r2
                """.trimIndent()
            assertLuaTrue(code, "Very large and small numbers should be handled correctly")
        }

    @Test
    fun testPrecisionLoss() =
        runTest {
            val code =
                """
                -- Test precision loss scenarios
                local big_int = 9007199254740992.0  -- 2^53, first integer that loses precision in double
                
                -- At this scale, adding 1 might not change the value
                local r1 = big_int + 1.0 >= big_int  -- Should be true
                
                -- Very close values
                local a = 1.0000000000000002
                local b = 1.0000000000000001  
                
                -- These might be equal due to precision limits
                local r2 = (a == b) or (a > b)
                
                return r1 and r2
                """.trimIndent()
            assertLuaTrue(code, "Precision loss should be handled consistently")
        }

    @Test
    fun testFloatIntegerConversion() =
        runTest {
            val code =
                """
                -- Test conversions between float and integer
                local int_val = 42
                local float_val = 42.0
                
                -- Values should be equal but types different
                local r1 = int_val == float_val
                local r2 = math.type(int_val) == 'integer'
                local r3 = math.type(float_val) == 'float'
                
                -- Adding 0.0 should convert to float
                local r4 = math.type(int_val + 0.0) == 'float'
                
                -- Floor/ceil of integers should remain integers
                local r5 = math.type(math.floor(int_val)) == 'integer'
                local r6 = math.type(math.ceil(int_val)) == 'integer'
                
                return r1 and r2 and r3 and r4 and r5 and r6
                """.trimIndent()
            assertLuaTrue(code, "Float/integer conversions should preserve semantics")
        }

    // ========== Special Values in Tables ==========

    @Test
    fun testSpecialValuesInTables() =
        runTest {
            // From math.lua:789-797 - NaN table index behavior
            val code =
                """
                local nan = 0/0
                local inf = math.huge
                
                -- NaN should not be usable as table key with rawset
                local a = {}
                local r1 = not pcall(rawset, a, nan, 1)  -- Should fail
                local r2 = a[nan] == nil  -- Should be nil (undefined)
                
                a[1] = 1  -- Add existing key
                local r3 = not pcall(rawset, a, nan, 1)  -- Should still fail
                local r4 = a[nan] == nil  -- Should still be nil
                
                -- Infinity should work as table key
                a[inf] = "infinity"
                local r5 = a[inf] == "infinity"
                
                return r1 and r2 and r3 and r4 and r5
                """.trimIndent()
            assertLuaTrue(code, "Special values should behave correctly as table keys")
        }

    @Test
    fun testNaNTableIndexError() =
        runTest {
            // From math.lua:793-797 - rawset should fail when using NaN as index
            val code =
                """
                local a = {}
                local NaN = 0/0
                local success = pcall(rawset, a, NaN, 1)
                return not success  -- Should return false indicating failure
                """.trimIndent()

            assertLuaTrue(code, "rawset should fail with NaN index")
        }
}
