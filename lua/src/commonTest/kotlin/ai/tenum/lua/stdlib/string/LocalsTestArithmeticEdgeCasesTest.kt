package ai.tenum.lua.stdlib.string

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the arithmetic edge cases from locals.lua line 95.
 *
 * CRITICAL INSIGHT: Lua 5.4 distinguishes between integers and floats.
 * - Integer arithmetic (like repeated doubling) keeps values as integers
 * - Integers ALWAYS format as plain numbers, regardless of size
 * - Float arithmetic (like 2^47) produces floats which may use scientific notation
 *
 * The locals.lua test builds p by repeated doubling starting from 4:
 * ```lua
 * local i = 2
 * local p = 4
 * repeat
 *   for j=-3,3 do
 *     assert(load(string.format([[local a=%s; a=a+%s; assert(a ==2^%s)]], j, p-j, i))())
 *     ...
 *   end
 *   p = 2*p; i = i+1
 * until p <= 0
 * ```
 *
 * This keeps p as an INTEGER (not float), so it formats as plain text even at 2^47.
 * If you compute 2^47 directly, it's a FLOAT and uses scientific notation.
 *
 * Our implementation correctly preserves integer vs float types and formats them
 * according to Lua 5.4.8 semantics.
 */
class LocalsTestArithmeticEdgeCasesTest : LuaCompatTestBase() {
    /**
     * Test the addition path: a = j; a = a + (p - j); assert(a == 2^i)
     *
     * This mirrors the first assertion in locals.lua.
     * For i=47, p=2^47:
     * - When j=-3: a starts at -3, adds (2^47 - (-3)) = 2^47 + 3, expects 2^47
     * - The string formatting of (p-j) is critical - it may use scientific notation
     * - When parsed back and computed, floating point rounding must work correctly
     */
    @Test
    fun testAdditionPathAt2Pow47() =
        runTest {
            val luaResult =
                execute(
                    """
            local i = 47
            local p = 2^i
            
            -- Test all j values from -3 to 3
            for j = -3, 3 do
                local a = j
                a = a + (p - j)
                local expected = 2^i
                assert(a == expected, 
                    string.format("add fail i=%d j=%d a=%.17g vs 2^i=%.17g", i, j, a, expected))
            end
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    /**
     * Test the subtraction path: a = -j; a = a - (p - j); assert(a == -2^i)
     *
     * This mirrors the second assertion in locals.lua.
     * For i=47, p=2^47:
     * - When j=-3: a starts at 3, subtracts (2^47 - (-3)) = 2^47 + 3, expects -2^47
     * - Tests negative result handling with large powers of 2
     */
    @Test
    fun testSubtractionPathAt2Pow47() =
        runTest {
            val luaResult =
                execute(
                    """
            local i = 47
            local p = 2^i
            
            -- Test all j values from -3 to 3
            for j = -3, 3 do
                local a = -j
                a = a - (p - j)
                local expected = -2^i
                assert(a == expected,
                    string.format("sub fail i=%d j=%d a=%.17g vs -2^i=%.17g", i, j, a, expected))
            end
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    /**
     * Test the two-register path: a,b=0,-j; a=b-(p-j); assert(a == -2^i)
     *
     * This mirrors the third assertion in locals.lua.
     * For i=47, p=2^47:
     * - When j=-3: b=3, a = 3 - (2^47 - (-3)) = 3 - 2^47 - 3 = -2^47
     * - Tests multi-assignment and subtraction with register operations
     */
    @Test
    fun testTwoRegisterPathAt2Pow47() =
        runTest {
            val luaResult =
                execute(
                    """
            local i = 47
            local p = 2^i
            
            -- Test all j values from -3 to 3
            for j = -3, 3 do
                local a, b = 0, -j
                a = b - (p - j)
                local expected = -2^i
                assert(a == expected,
                    string.format("pair fail i=%d j=%d a=%.17g vs -2^i=%.17g", i, j, a, expected))
            end
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    /**
     * Test dynamic code generation with addition at 2^47.
     *
     * This test verifies that integer arithmetic is preserved through dynamic code generation
     * and string formatting, matching locals.lua behavior.
     */
    @Test
    fun testDynamicCodeGenerationAt2Pow47Addition() =
        runTest {
            val luaResult =
                execute(
                    """
            -- Build p by doubling to match locals.lua behavior
            local i = 2
            local p = 4
            while i < 47 do
                p = 2 * p
                i = i + 1
            end
            -- Now p is 2^47 as an INTEGER
            i = 47
            
            for j = -3, 3 do
                -- This is the exact pattern from locals.lua
                local code = string.format([[local a=%s; a=a+%s; assert(a ==2^%s)]], j, p-j, i)
                local func, err = load(code, '')
                assert(func, "Failed to compile: " .. tostring(err))
                
                local success, result = pcall(func)
                assert(success, 
                    string.format("Dynamic code failed at i=%d j=%d: %s\nCode was: %s", 
                        i, j, tostring(result), code))
            end
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    @Test
    fun testDynamicCodeGenerationAt2Pow47Subtraction() =
        runTest {
            val luaResult =
                execute(
                    """
            -- Build p by doubling to match locals.lua behavior
            local i = 2
            local p = 4
            while i < 47 do
                p = 2 * p
                i = i + 1
            end
            i = 47
            
            for j = -3, 3 do
                local code = string.format([[local a=%s; a=a-%s; assert(a==-2^%s)]], -j, p-j, i)
                local func, err = load(code, '')
                assert(func, "Failed to compile: " .. tostring(err))
                
                local success, result = pcall(func)
                assert(success,
                    string.format("Dynamic code failed at i=%d j=%d: %s\nCode was: %s",
                        i, j, tostring(result), code))
            end
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    @Test
    fun testDynamicCodeGenerationAt2Pow47TwoRegister() =
        runTest {
            val luaResult =
                execute(
                    """
            -- Build p by doubling to match locals.lua behavior
            local i = 2
            local p = 4
            while i < 47 do
                p = 2 * p
                i = i + 1
            end
            i = 47
            
            for j = -3, 3 do
                local code = string.format([[local a,b=0,%s; a=b-%s; assert(a==-2^%s)]], -j, p-j, i)
                local func, err = load(code, '')
                assert(func, "Failed to compile: " .. tostring(err))
                
                local success, result = pcall(func)
                assert(success,
                    string.format("Dynamic code failed at i=%d j=%d: %s\nCode was: %s",
                        i, j, tostring(result), code))
            end
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }

    /**
     * Diagnostic test to show what string representations we're generating
     * for the critical values at i=47.
     */
    @Test
    fun testStringFormattingAt2Pow47() =
        runTest {
            val luaResult =
                execute(
                    """
            local i = 47
            local p = 2^i
            
            print(string.format("i=%d, p=2^%d", i, i))
            print(string.format("p = %.17g", p))
            print(string.format("tostring(p) = %s", tostring(p)))
            
            for j = -3, 3 do
                local diff = p - j
                print(string.format("  j=%d: p-j=%.17g tostring(p-j)=%s", 
                    j, diff, tostring(diff)))
            end
            
            return "OK"
        """,
                )
            val result = (luaResult as LuaString).value
            assertEquals("OK", result)
        }
}
