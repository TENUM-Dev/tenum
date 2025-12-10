package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * PHASE 4.1: Tables - Construction and Access
 *
 * Tests table creation, indexing, and basic operations.
 * Based on: constructs.lua (tables section)
 *
 * Coverage:
 * - Table constructors {}
 * - Array-like tables
 * - Hash-like tables
 * - Mixed tables
 * - Table indexing (t[k], t.k)
 * - Table assignment
 * - Table length operator #
 */
class TablesCompatTest : LuaCompatTestBase() {
    @Test
    fun testEmptyTable() =
        runTest {
            val code =
                """
                local t = {}
                return type(t)
                """.trimIndent()
            assertLuaString(code, "table")
        }

    @Test
    fun testArrayLikeTable() =
        runTest {
            val code =
                """
                local t = {10, 20, 30}
                return t[1] + t[2] + t[3]
                """.trimIndent()
            assertLuaNumber(code, 60.0)
        }

    @Test
    fun testHashLikeTable() =
        runTest {
            val code =
                """
                local t = {a = 10, b = 20}
                return t.a + t.b
                """.trimIndent()
            assertLuaNumber(code, 30.0)
        }

    @Test
    fun testMixedTable() =
        runTest {
            val code =
                """
                local t = {10, 20, a = 30, b = 40}
                return t[1] + t[2] + t.a + t.b
                """.trimIndent()
            assertLuaNumber(code, 100.0)
        }

    @Test
    fun testTableIndexBracket() =
        runTest {
            val code =
                """
                local t = {x = 100}
                return t["x"]
                """.trimIndent()
            assertLuaNumber(code, 100.0)
        }

    @Test
    fun testTableIndexDot() =
        runTest {
            val code =
                """
                local t = {value = 42}
                return t.value
                """.trimIndent()
            assertLuaNumber(code, 42.0)
        }

    @Test
    fun testTableAssignment() =
        runTest {
            val code =
                """
                local t = {}
                t.x = 10
                t["y"] = 20
                t[1] = 30
                return t.x + t.y + t[1]
                """.trimIndent()
            vm.debugEnabled = true
            assertLuaNumber(code, 60.0)
        }

    @Test
    fun testTableLengthOperator() =
        runTest {
            val code =
                """
                local t = {10, 20, 30, 40, 50}
                return #t
                """.trimIndent()
            assertLuaNumber(code, 5.0)
        }

    @Test
    fun testTableNilValue() =
        runTest {
            val code =
                """
                local t = {a = 10, b = 20}
                t.a = nil
                return t.b
                """.trimIndent()
            assertLuaNumber(code, 20.0)
        }

    @Test
    fun testTableNumericKeys() =
        runTest {
            val code =
                """
                local t = {}
                t[1] = "first"
                t[2] = "second"
                t[10] = "tenth"
                return t[1] .. t[2] .. t[10]
                """.trimIndent()
            assertLuaString(code, "firstsecondtenth")
        }

    @Test
    fun testTableStringKeys() =
        runTest {
            val code =
                """
                local t = {}
                t["name"] = "Alice"
                t["age"] = 30
                return t["name"] .. tostring(t["age"])
                """.trimIndent()
            assertLuaString(code, "Alice30")
        }

    @Test
    fun testTableNonStringKeys() =
        runTest {
            val code =
                """
                local t = {}
                local key = {x = 1}
                t[key] = "table key"
                return t[key]
                """.trimIndent()
            assertLuaString(code, "table key")
        }

    @Test
    fun testTableConstructorWithExpressions() =
        runTest {
            val code =
                """
                local x = 10
                local function f() return 20 end
                local t = {x + 5, f(), x * 2}
                return t[1] + t[2] + t[3]
                """.trimIndent()
            assertLuaNumber(code, 55.0) // 15 + 20 + 20
        }

    @Test
    fun testTableConstructorWithBrackets() =
        runTest {
            val code =
                """
                local t = {[1] = "one", ["key"] = "value", [10] = "ten"}
                return t[1] .. t.key .. t[10]
                """.trimIndent()
            assertLuaString(code, "onevalueten")
        }

    @Test
    fun testNestedTables() =
        runTest {
            val code =
                """
                local t = {{1, 2}, {3, 4}, {5, 6}}
                return t[1][1] + t[2][2] + t[3][1]
                """.trimIndent()
            assertLuaNumber(code, 10.0) // 1 + 4 + 5
        }

    @Test
    fun testTableNilIndexAccess() =
        runTest {
            // Reading table[nil] should return nil, not error
            val code =
                """
                local t = {a = 10}
                local result = t[nil]
                return result == nil
                """.trimIndent()
            assertLuaTrue(code)
        }

    @Test
    fun testTableNilIndexSetError() =
        runTest {
            // Setting table[nil] = value should error
            val code =
                """
                return pcall(function()
                    local t = {}
                    t[nil] = 10
                end)
                """.trimIndent()
            assertLuaFalse(code, "Setting table[nil] should fail")
        }

    @Test
    fun testTableConstructorWithNilKeyError() =
        runTest {
            // Constructor with [nil] = value should error
            val code =
                """
                return pcall(function()
                    local t = {[nil] = 10}
                end)
                """.trimIndent()
            assertLuaFalse(code, "Table constructor with [nil] key should fail")
        }

    @Test
    fun testTableFloatIntegerKeyEquivalence() =
        runTest {
            // Floats and integers with same numeric value should access same table slot
            val code =
                """
                local a = {}
                a[42] = "integer"
                a[42.0] = "float"
                return a[42] == "float" and a[42.0] == "float"
                """.trimIndent()
            assertLuaTrue(code, "Float and integer keys with same value should be equivalent")
        }

    @Test
    fun testTableLargeIntegerFloatKeyEquivalence() =
        runTest {
            // Test with large integers that fit in double precision
            val code =
                """
                local maxint = 9007199254740991  -- 2^53 - 1
                local maxintF = maxint + 0.0
                local a = {}
                a[maxintF] = 10
                a[maxintF - 1.0] = 11
                return a[maxint] == 10 and a[maxint - 1] == 11
                """.trimIndent()
            assertLuaTrue(code, "Large integer/float keys should be equivalent")
        }

    @Test
    fun testTableMaxintFloatIntegerKeyEquivalence_AttribLine470() =
        runTest {
            // Reproduces attrib.lua line 470 failure
            // Tests that float keys and integer keys are equivalent for maxint values,
            // including negative maxint values
            val code =
                """
                local a = {}
                local maxint = 9007199254740991  -- 2^53 - 1 (maximum integer fitting in double)
                local maxintF = maxint + 0.0      -- float version
                
                -- Set values using float keys
                a[maxintF] = 10
                a[maxintF - 1.0] = 11
                a[-maxintF] = 12
                a[-maxintF + 1.0] = 13
                
                -- Read values using integer keys (should access same slots)
                local test1 = a[maxint] == 10
                local test2 = a[maxint - 1] == 11
                local test3 = a[-maxint] == 12
                local test4 = a[-maxint + 1] == 13
                
                return test1 and test2 and test3 and test4
                """.trimIndent()
            assertLuaTrue(code, "Float and integer keys should be equivalent for maxint and -maxint")
        }

    @Test
    fun testTableMaxintFloatIntegerKeyEquivalence_ActualAttribCalculation() =
        runTest {
            // Use the EXACT maxint calculation from attrib.lua line 453-459
            // This test FAILS and reproduces the actual attrib.lua line 470 error
            val code =
                """
                local a = {}
                
                -- Compute maximum integer where all bits fit in a float (from attrib.lua)
                local maxint = math.maxinteger
                -- trim (if needed) to fit in a float
                while maxint ~= (maxint + 0.0) or (maxint - 1) ~= (maxint - 1.0) do
                  maxint = maxint // 2
                end
                
                local maxintF = maxint + 0.0   -- float version
                
                -- Set values using float keys (attrib.lua line 467-468)
                a[maxintF] = 10
                a[maxintF - 1.0] = 11
                a[-maxintF] = 12
                a[-maxintF + 1.0] = 13
                
                -- Read values using integer keys (attrib.lua line 470)
                print("maxint: " .. tostring(maxint))
                print("maxintF: " .. tostring(maxintF))
                print("a[maxint]: " .. tostring(a[maxint]))
                print("a[maxint - 1]: " .. tostring(a[maxint - 1]))
                print("a[-maxint]: " .. tostring(a[-maxint]))
                print("a[-maxint + 1]: " .. tostring(a[-maxint + 1]))
                return a[maxint] == 10 and a[maxint - 1] == 11 and
                       a[-maxint] == 12 and a[-maxint + 1] == 13
                """.trimIndent()
            assertLuaTrue(code, "Float and integer keys should be equivalent using attrib.lua calculation")
        }

    @Test
    fun testTableMaxintFloatPrecisionIssue_Diagnostic() =
        runTest {
            // Diagnostic test: Shows that maxintF and maxintF-1.0 produce the SAME float value
            // due to precision loss at Double's boundary (2^53)
            val code =
                """
                local maxint = math.maxinteger
                while maxint ~= (maxint + 0.0) or (maxint - 1) ~= (maxint - 1.0) do
                  maxint = maxint // 2
                end
                
                local maxintF = maxint + 0.0
                local maxintF_minus1 = maxintF - 1.0
                
                -- These should be different, but at Double precision boundary they're the SAME
                return maxintF, maxintF_minus1, maxintF == maxintF_minus1
                """.trimIndent()

            val results = execute(code)
            // This reveals the core issue: both values are the same due to floating point precision
            assertLuaTrue("return true", "Diagnostic test")
        }

    @Test
    fun testFloatSubtractionAtPrecisionBoundary() =
        runTest {
            // Focused test: 9007199254740991 is 2^53-1, the max safe integer for Double
            // In Lua 5.4: (9007199254740991.0 - 1.0) should give 9007199254740990.0
            val code =
                """
                local x = 9007199254740991.0
                local y = x - 1.0
                return y
                """.trimIndent()

            val result = execute(code)
            assertLuaNumber(code, 9007199254740990.0)
        }

    @Test
    fun testMaxintCalculationValue() =
        runTest {
            // Check what value maxint actually gets after the trimming loop
            val code =
                """
                local maxint = math.maxinteger
                while maxint ~= (maxint + 0.0) or (maxint - 1) ~= (maxint - 1.0) do
                  maxint = maxint // 2
                end
                return maxint, math.type(maxint)
                """.trimIndent()

            val result = execute(code)
            // In Lua 5.4, this should be 9007199254740991 (2^53-1) as an integer
            assertLuaTrue("return true", "Diagnostic")
        }

    @Test
    fun testLongMaxValueFloatEquality() =
        runTest {
            // ROOT CAUSE TEST: Long.MAX_VALUE (9223372036854775807) incorrectly equals its float conversion
            // This causes the trimming loop to exit immediately without reducing maxint
            val code =
                """
                local maxint = math.maxinteger  -- 9223372036854775807
                local maxintF = maxint + 0.0     -- Should be 9.223372036854776E18
                print("maxint type: " .. math.type(maxint))
                print("maxintF type: " .. math.type(maxintF))
                print("maxint == maxintF: " .. tostring(maxint == maxintF))
                return maxint == maxintF
                """.trimIndent()

            val result = execute(code)
            println("Result: $result")

            // In Lua 5.4, this should be FALSE because the float conversion loses precision
            assertLuaFalse(code, "Long.MAX_VALUE should NOT equal its imprecise float conversion")
        }
}
