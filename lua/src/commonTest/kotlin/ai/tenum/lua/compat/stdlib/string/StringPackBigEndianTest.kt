package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for big-endian pack with unsigned integers.
 * From tpack.lua line 67.
 */
class StringPackBigEndianTest : LuaCompatTestBase() {
    @Test
    fun testBigEndianPackI9() {
        val result =
            execute(
                """
            local s = string.pack(">I9", 0xAA)
            -- Big-endian: high bytes first, so 8 zero bytes then 0xAA
            local expected = string.rep("\0", 8) .. "\xAA"
            return s == expected
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testBigEndianPackI16() {
        val result =
            execute(
                """
            local s = string.pack(">I16", 0xAA)
            -- Big-endian: 15 zero bytes then 0xAA
            local expected = string.rep("\0", 15) .. "\xAA"
            return s == expected
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testBigEndianUnpackI9() {
        val result =
            execute(
                """
            local s = string.rep("\0", 8) .. "\xAA"
            local v = string.unpack(">I9", s)
            return v
        """,
            )
        assertLuaNumber(result, 0xAA.toDouble())
    }

    @Test
    fun testLittleEndianPackI9() {
        val result =
            execute(
                """
            local s = string.pack("<I9", 0xAA)
            -- Little-endian: 0xAA then 8 zero bytes
            local expected = "\xAA" .. string.rep("\0", 8)
            return s == expected
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testTpackLine67() {
        // Exact test from tpack.lua
        val result =
            execute(
                """
            local pack = string.pack
            local unpack = string.unpack
            
            local i = 9
            local s = "\xAA" .. string.rep("\0", i - 1)
            
            -- These should match
            local packed = pack(">I" .. i, 0xAA)
            local reversed = s:reverse()
            
            return packed == reversed
        """,
            )
        assertLuaBoolean(result, true)
    }
}
