package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * TDD Test: Numeric for loops should use integers when start/stop/step are all integers
 *
 * According to Lua 5.4.8 behavior:
 * - for i = 1, 10 do -- i should be an integer type
 * - for i = 1.0, 10 do -- i should be a float type (because start is float)
 * - for i = 1, 10.0 do -- i should be a float type (because stop is float)
 * - for i = 1, 10, 1.0 do -- i should be a float type (because step is float)
 */
class NumericForLoopTypeTest : LuaCompatTestBase() {
    @Test
    fun testForLoopWithIntegerBounds_LoopVariableIsInteger() =
        runTest {
            val result =
                """
                for i = -6, 6 do
                    if math.type(i) ~= 'integer' then
                        return false
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(result, "for i = -6, 6: loop variable should be integer type")
        }

    @Test
    fun testForLoopWithFloatStart_LoopVariableIsFloat() =
        runTest {
            val result =
                """
                for i = 1.0, 5 do
                    if math.type(i) ~= 'float' then
                        return false
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(result, "for i = 1.0, 5: loop variable should be float type")
        }

    @Test
    fun testForLoopWithFloatStop_LoopVariableIsFloat() =
        runTest {
            val result =
                """
                for i = 1, 5.0 do
                    if math.type(i) ~= 'float' then
                        return false
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(result, "for i = 1, 5.0: loop variable should be float type")
        }

    @Test
    fun testForLoopWithFloatStep_LoopVariableIsFloat() =
        runTest {
            val result =
                """
                for i = 1, 5, 1.0 do
                    if math.type(i) ~= 'float' then
                        return false
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(result, "for i = 1, 5, 1.0: loop variable should be float type")
        }

    @Test
    fun testForLoopAllIntegers_ExplicitStep() =
        runTest {
            val result =
                """
                for i = 1, 10, 2 do
                    if math.type(i) ~= 'integer' then
                        return false
                    end
                end
                return true
                """.trimIndent()
            assertLuaTrue(result, "for i = 1, 10, 2: loop variable should be integer type")
        }
}
