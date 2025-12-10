package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for dynamic format strings in string.pack/unpack/packsize.
 * This tests the pattern from tpack.lua where format is built with concatenation.
 */
class StringPackDynamicFormatTest : LuaCompatTestBase() {
    @Test
    fun testPacksizeWithDynamicFormat() {
        val result =
            execute(
                """
            local i = 4
            local size = string.packsize("i" .. i)
            return size
        """,
            )
        assertLuaNumber(result, 4.0)
    }

    @Test
    fun testPackWithDynamicFormat() {
        val result =
            execute(
                """
            local i = 2
            local s = string.pack("i" .. i, -1)
            return s == "\xFF\xFF"
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testUnpackWithDynamicFormat() {
        val result =
            execute(
                """
            local i = 2
            local v = string.unpack("i" .. i, "\xFF\xFF")
            return v
        """,
            )
        assertLuaNumber(result, -1.0)
    }

    @Test
    fun testDynamicFormatLoop() {
        // Simplified version of tpack.lua loop
        val result =
            execute(
                """
            for i = 1, 4 do
                local s = string.rep("\xFF", i)
                local packed = string.pack("i" .. i, -1)
                if packed ~= s then
                    return false, "i=" .. i .. " packed=" .. #packed .. " expected=" .. #s
                end
            end
            return true
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testDynamicFormatI9() {
        // Test i9 specifically
        val result =
            execute(
                """
            local size = string.packsize("i9")
            return size
        """,
            )
        assertLuaNumber(result, 9.0)
    }

    @Test
    fun testDynamicFormatI10() {
        // Test i10 specifically
        val result =
            execute(
                """
            local size = string.packsize("i10")
            return size
        """,
            )
        assertLuaNumber(result, 10.0)
    }

    @Test
    fun testDynamicFormatI16() {
        // Test i16 specifically
        val result =
            execute(
                """
            local size = string.packsize("i16")
            return size
        """,
            )
        assertLuaNumber(result, 16.0)
    }

    @Test
    fun testPackI9() {
        val result =
            execute(
                """
            local s = string.pack("i9", -1)
            return #s
        """,
            )
        assertLuaNumber(result, 9.0)
    }
}
