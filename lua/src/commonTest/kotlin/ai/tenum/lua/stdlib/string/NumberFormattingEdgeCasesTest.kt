package ai.tenum.lua.stdlib.string

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for number-to-string formatting edge cases around 2^53 boundary.
 *
 * Key behavior:
 * - Numbers < 2^53 that are exact integers: format as integers
 * - Numbers >= 2^53: format in scientific notation (precision loss zone)
 * - tostring() and string.format("%s", ...) must produce identical output
 */
class NumberFormattingEdgeCasesTest : LuaCompatTestBase() {
    @Test
    fun testTostringAt2Pow53Boundary() =
        runTest {
            val luaResult =
                execute(
                    """
            local results = {}
            -- Test values around 2^53 boundary
            local vals = {
                2^52,      -- Below boundary
                2^53 - 1,  -- Last exact integer
                2^53,      -- Boundary (9007199254740992)
                2^53 + 1,  -- Above boundary (loses precision)
                2^53 + 2,
            }
            for i, val in ipairs(vals) do
                results[i] = tostring(val)
            end
            return table.concat(results, "|")
        """,
                )

            val result = (luaResult as LuaString).value
            val parts = result.split("|")
            // Numbers with >14 significant digits use scientific notation
            assertTrue(parts[0].contains("e", ignoreCase = true), "2^52 should use scientific notation (16 digits), got: ${parts[0]}")
            assertTrue(parts[1].contains("e", ignoreCase = true), "2^53-1 should use scientific notation (16 digits), got: ${parts[1]}")
            // At and above 2^53: also scientific notation
            assertTrue(parts[2].contains("e", ignoreCase = true), "2^53 should use scientific notation, got: ${parts[2]}")
            assertTrue(parts[3].contains("e", ignoreCase = true), "2^53+1 should use scientific notation, got: ${parts[3]}")
            assertTrue(parts[4].contains("e", ignoreCase = true), "2^53+2 should use scientific notation, got: ${parts[4]}")
        }

    @Test
    fun testStringFormatSMatchesTostring() =
        runTest {
            val luaResult =
                execute(
                    """
            -- Test that string.format("%s", num) == tostring(num)
            local testVals = {
                2^52,
                2^53 - 1,
                2^53,
                2^53 + 1,
                2^60,
                1e14,
                99999999999999,
            }
            
            for _, val in ipairs(testVals) do
                local ts = tostring(val)
                local sf = string.format("%s", val)
                assert(ts == sf, string.format(
                    "Mismatch for %.0f: tostring='%s' vs format='%s'",
                    val, ts, sf
                ))
            end
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    @Test
    fun testLargeIntegersBelow2Pow53FormatAsIntegers() =
        runTest {
            val luaResult =
                execute(
                    """
            local val = 99999999999999  -- 14 digits, < 2^53
            local result = tostring(val)
            assert(not result:find("[eE]"), "Should not use scientific notation")
            return result
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("99999999999999", result)
        }

    @Test
    fun test1e14FormatsAsInteger() =
        runTest {
            val luaResult =
                execute(
                    """
            local val = 100000000000000  -- Exactly 1e14, but < 2^53
            local result = tostring(val)
            -- Should be integer format since it's < 2^53 and exact
            return result
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("100000000000000", result)
        }

    @Test
    fun test2Pow53FormatsWithScientificNotation() =
        runTest {
            val luaResult =
                execute(
                    """
            local val = 2^53
            return tostring(val)
        """,
                )

            val result = (luaResult as LuaString).value
            // Should use scientific notation
            assertTrue(result.contains("e", ignoreCase = true), "2^53 should use scientific notation, got: $result")
            // Should have limited precision (14 significant digits in Lua)
            assertTrue(result.contains("9.007199254741e", ignoreCase = true), "Should match Lua format, got: $result")
        }

    @Test
    fun testStringFormatInDynamicCodeGeneration() =
        runTest {
            // This verifies that 2^53 and 2^53+3 format identically
            // due to precision loss in %.14g formatting
            val luaResult =
                execute(
                    """
            local p53 = 2^53
            local p53plus3 = p53 + 3
            
            -- These should format to the same string
            local s1 = tostring(p53)
            local s2 = tostring(p53plus3)
            assert(s1 == s2, string.format("2^53 formats as '%s' but 2^53+3 formats as '%s'", s1, s2))
            
            -- The formatted string should use scientific notation
            assert(s1:find("[eE]"), string.format("Should use scientific notation, got: '%s'", s1))
            assert(s1 == "9.007199254741e+15", string.format("Expected '9.007199254741e+15', got: '%s'", s1))
            
            -- Verify string.format("%s", ...) produces same result
            assert(string.format("%s", p53) == s1)
            assert(string.format("%s", p53plus3) == s2)
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    @Test
    fun testExactIntegersUnder2Pow53NoScientificNotation() =
        runTest {
            val luaResult =
                execute(
                    """
            -- Values with <=14 significant digits use plain format
            local vals = {42, 1000, 1e10, 2^46, 99999999999999}
            for _, val in ipairs(vals) do
                local str = tostring(val)
                assert(not str:find("[eE]"), 
                    string.format("Value %.0f should not use scientific notation: %s", val, str))
            end
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    @Test
    fun testFloatsUnder2Pow53UseDecimalNotation() =
        runTest {
            val luaResult =
                execute(
                    """
            local vals = {3.14, 0.5, 123.456}
            for _, val in ipairs(vals) do
                local str = tostring(val)
                -- Small floats should not use scientific notation
                assert(not str:find("[eE]"), 
                    string.format("Float %f should use decimal notation: %s", val, str))
            end
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    @Test
    fun testNegativeNumbersAt2Pow53Boundary() =
        runTest {
            val luaResult =
                execute(
                    """
            local results = {}
            local vals = {-2^53, -2^53 - 1}
            for i, val in ipairs(vals) do
                results[i] = tostring(val)
            end
            return table.concat(results, "|")
        """,
                )

            val result = (luaResult as LuaString).value
            val parts = result.split("|")
            // Both should use scientific notation (absolute value >= 2^53)
            assertTrue(parts[0].contains("e", ignoreCase = true), "-2^53 should use scientific notation, got: ${parts[0]}")
            assertTrue(parts[1].contains("e", ignoreCase = true), "-2^53-1 should use scientific notation, got: ${parts[1]}")
        }

    @Test
    fun testPrecisionLossAt2Pow53() =
        runTest {
            // This test documents the precision loss at 2^53
            val luaResult =
                execute(
                    """
            local base = 2^53
            local a = base + 1
            local b = base + 2
            
            -- Due to floating point limits, base + 1 cannot be represented exactly
            -- It rounds to base (even value)
            assert(a == base, "2^53 + 1 should round to 2^53")
            
            -- But base + 2 can be represented
            assert(b ~= base, "2^53 + 2 should be different from 2^53")
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }
}
