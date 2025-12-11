package ai.tenum.lua.compat.stdlib.math

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * TDD Test: math.fmod should preserve integer type when both arguments are integers
 *
 * According to Lua 5.4.8 behavior (math.lua:713-732):
 * - math.fmod(integer, integer) should return an integer
 * - math.fmod(float, integer) should return a float
 * - math.fmod(integer, float) should return a float
 * - math.fmod(float, float) should return a float
 */
class MathFmodTypePreservationTest : LuaCompatTestBase() {
    @Test
    fun testMathFmodIntegerInteger_ReturnsInteger() =
        runTest {
            val result =
                """
                local mi = math.fmod(2, 5)
                return math.type(mi) == 'integer'
                """.trimIndent()
            assertLuaTrue(result, "math.fmod(integer, integer) should return integer type")
        }

    @Test
    fun testMathFmodFloatInteger_ReturnsFloat() =
        runTest {
            val result =
                """
                local mf = math.fmod(2 + 0.0, 5)
                return math.type(mf) == 'float'
                """.trimIndent()
            assertLuaTrue(result, "math.fmod(float, integer) should return float type")
        }

    @Test
    fun testMathFmodIntegerFloat_ReturnsFloat() =
        runTest {
            val result =
                """
                local mf = math.fmod(2, 5.0)
                return math.type(mf) == 'float'
                """.trimIndent()
            assertLuaTrue(result, "math.fmod(integer, float) should return float type")
        }

    @Test
    fun testMathFmodBothTypes_ValuesMatch() =
        runTest {
            val result =
                """
                local mi = math.fmod(2, 5)
                local mf = math.fmod(2 + 0.0, 5)
                return mi == mf and math.type(mi) == 'integer' and math.type(mf) == 'float'
                """.trimIndent()
            assertLuaTrue(result, "math.fmod should preserve type but give same value")
        }
}
