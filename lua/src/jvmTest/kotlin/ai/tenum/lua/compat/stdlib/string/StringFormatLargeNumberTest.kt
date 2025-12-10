package ai.tenum.lua.compat.stdlib.string

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for string.format with large numbers (strings.lua line 263-279, 284-292)
 */
class StringFormatLargeNumberTest : LuaCompatTestBase() {
    @Test
    fun testLongestNumberFormatting() {
        // strings.lua line 263-273
        val result =
            execute(
                """
                local i = 1
                local j = 10000
                while i + 1 < j do   -- binary search for maximum finite float
                  local m = (i + j) // 2
                  if 10^m < math.huge then i = m else j = m end
                end
                
                -- Debug output
                print('i =', i)
                print('j =', j)
                print('10^i < math.huge:', 10^i < math.huge)
                print('10^j == math.huge:', 10^j == math.huge)
                
                local s = string.format('%.99f', -(10^i))
                print('string.len(s) =', string.len(s))
                print('i + 101 =', i + 101)
                print('string.len(s) >= i + 101:', string.len(s) >= i + 101)
                
                return {i = i, j = j, len_s = string.len(s), expected = i + 101, s = s}
                """.trimIndent(),
            )

        println("Result: $result")
    }

    @Test
    fun testHexFormat32BitNegativeMin() {
        // strings.lua line 286: assert(string.sub(string.format("%x", min), -8) == "80000000")
        // Where min = -0x80000000 (-2147483648)
        // %x should treat negative numbers as unsigned (two's complement)
        assertLuaString(
            """
            local min = -0x80000000
            return string.sub(string.format("%x", min), -8)
            """,
            "80000000",
        )
    }
}
