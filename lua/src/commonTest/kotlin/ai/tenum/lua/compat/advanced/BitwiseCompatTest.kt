package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaString
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PHASE 7.3: Advanced Features - Bitwise Operations
 *
 * Tests bitwise operation library (Lua 5.3+).
 * Based on: bitwise.lua
 *
 * Coverage:
 * - Bitwise operators (&, |, ~, <<, >>)
 * - bit32 library (Lua 5.2 compatibility)
 * - Bitwise operations on integers
 */
class BitwiseCompatTest : LuaCompatTestBase() {
    @Test
    fun testBitwiseAnd() =
        runTest {
            // basic AND tests
            assertLuaBoolean("return 0x0F & 0x0C == 0x0C", true)
            assertLuaBoolean("return 0xF0 & 0x0F == 0x00", true)
        }

    @Test
    fun testBitwiseOr() =
        runTest {
            // basic OR tests
            assertLuaBoolean("return 0x0C | 0x03 == 0x0F", true)
            assertLuaBoolean("return 0xF0 | 0x0F == 0xFF", true)
        }

    @Test
    fun testBitwiseXor() =
        runTest {
            // basic XOR tests
            assertLuaBoolean("return 0xF0 ~ 0x0F == 0xFF", true)
            assertLuaBoolean("return 0xAA ~ 0xFF == 0x55", true)
        }

    @Test
    fun testBitwiseNot() =
        runTest {
            // unary NOT
            assertLuaBoolean("return ~0 == -1", true)
            assertLuaNumber("return ~0xFF", -256.0)
        }

    @Test
    fun testLeftShift() =
        runTest {
            // left shift
            assertLuaNumber("return 1 << 3", 8.0)
            assertLuaNumber("return 0x12345678 << 4", 0x12345678L.shl(4).toDouble())
        }

    @Test
    fun testRightShift() =
        runTest {
            // right shift
            assertLuaNumber("return 8 >> 3", 1.0)
            assertLuaNumber("return 0x12345678 >> 4", (0x12345678L.shr(4)).toDouble())
        }

    @Test
    fun testBitwiseWithNegatives() =
        runTest {
            // negative operands
            assertLuaBoolean("return (-4 | 3) == -1", true)
            assertLuaBoolean("return (-1 & 0xFFFFFFFF) == 0xFFFFFFFF", true)
        }

    @Test
    fun testBitwiseWithLargeNumbers() =
        runTest {
            // basic large-ish shifts (stay within signed 64-bit where possible)
            assertLuaBoolean("return (0x12345678 << 8) == 0x1234567800", true)
            assertLuaBoolean("return (0x12345678 >> 8) == 0x00123456", true)

            // error case: non-numeric operand should raise
            assertThrowsError("return 4 & 'a'", "bitwise operation")
        }

    @Test
    fun testReplicateBitwiseLuaFailure() =
        runTest {
            vm.debugEnabled = true
            // Minimal check reproducing the original assertion — should now succeed
            val code = """
            local a = 0xF0F0F0F0F0F0F0F0
            return (a >> 4) == (~a)
        """

            assertLuaBoolean(code, true)
        }

    @Test
    fun testConstantFoldingShifts_MaxInteger() =
        runTest {
            vm.debugEnabled = true
            val code = "local code = string.format(\"return -1 >> %d\", math.maxinteger); return load(code)()"
            assertLuaNumber(code, 0.0)
        }

    @Test
    fun testConstantFoldingShifts_MinInteger() =
        runTest {
            val code = "local code = string.format(\"return -1 >> %d\", math.mininteger); return load(code)()"
            assertLuaNumber(code, 0.0)
        }

    @Test
    fun testConstantFoldingShifts_MaxIntegerLeft() =
        runTest {
            val code = "local code = string.format(\"return -1 << %d\", math.maxinteger); return load(code)()"
            assertLuaNumber(code, 0.0)
        }

    @Test
    fun testConstantFoldingShifts_MinIntegerLeft() =
        runTest {
            vm.debugEnabled = true
            val code = "local code = string.format(\"return -1 << %d\", math.mininteger); return load(code)()"
            assertLuaNumber(code, 0.0)
        }

    @Test
    fun testIntegrationAssertion_Repro() =
        runTest {
            val code =
                """
                local numbits = string.packsize('j') * 8; 
                return (-1 >> 1) == ((1 << (numbits - 1)) - 1) and (1 << 31) == 0x80000000
                """.trimIndent()
            assertLuaBoolean(code, true)
        }

    @Test
    fun testIntegrationAssertion_DebugValues() =
        runTest {
            val code = """
                local numbits = string.packsize('j') * 8;
                return tostring(-1 >> 1) .. ',' .. tostring((1 << (numbits - 1)) - 1) .. ',' .. tostring(1 << 31)
            """
            assertLuaString(code, "9223372036854775807,9223372036854775807,2147483648")
        }

    @Test
    fun testIntegrationAssertion_DebugDirect() =
        runTest {
            val code = "return tostring(1 << 63) .. ',' .. tostring((1 << 63) - 1)"
            assertLuaString(code, "-9223372036854775808,9223372036854775807")
        }

    @Test
    fun testIntegration_OutOfRange_Debug() =
        runTest {
            val code =
                """
                local s = "0xffffffffffffffff.0";
                local t = tonumber(s); local i = math.tointeger(t);
                local ok, msg = pcall(function() return s | 0 end);
                return tostring(t)..','..tostring(i)..','..tostring(ok)..','..tostring(msg or '')
                """.trimIndent()
            // Expect tonumber to be nil or tointeger to be nil and pcall to be false (error)
            val result = execute(code)
            // Print result for debugging; assert that pcall returned false
            assertTrue(result is LuaString)
            val s = result.value
            // Ensure pcall returned false and include debug string
            assertTrue(s.contains("false"), "Unexpected result for out-of-range conversion: $s")
        }

    @Test
    @Ignore
    fun testIntegration_Bwcoercion_OutOfRange() =
        runTest {
            val code =
                "require \"bwcoercion\"; local s = \"0xffffffffffffffff.0\"; " +
                    "local t = tonumber(s); local i = math.tointeger(t); " +
                    "local ok = pcall(function() return s | 0 end); " +
                    "return tostring(t)..','..tostring(i)..','..tostring(ok)"
            val result = execute(code)
            assertTrue(result is LuaString)
            val s = result.value
            // We expect pcall to be false (operation should error); include the string for debugging if not
            assertTrue(s.contains("false"), "Unexpected result for out-of-range conversion: $s")
        }

    @Test
    @Ignore
    fun testIntegration_CoercionSequence_Check() =
        runTest {
            val code = """require "bwcoercion"; 
            local cases = { 
                'return "0xffffffffffffffff" | 0', 
                'return "0xfffffffffffffffe" & "-1"', 
                'return " \t-0xfffffffffffffffe" & "-1"', 'return " -45  \t " >> "  -2  "', 
                'return "1234.0" << "5.0"',
                'return "0xffff.0" ~ "0xAAAA"',
                'return ~"0x0.000p4"'
            }
            for i, v in ipairs(cases) do
                local ok, res = pcall(function() return load(v)() end)
                if not ok then return tostring(i)..":"..tostring(res) end
            end
            return "OK"
        """
            val result = execute(code)
            assertTrue(result is LuaString)
            val s = result.value
            assertEquals("OK", s)
        }
}
