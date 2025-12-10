package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNumber
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for packing large hex numbers with bitwise masking.
 * From tpack.lua lines 94-101.
 */
class StringPackLargeHexTest : LuaCompatTestBase() {
    @Test
    fun testLargeHexLiteral() {
        val result =
            execute(
                """
            local lnum = 0x13121110090807060504030201
            return lnum
        """,
            )
        // Should be able to parse and return this large hex number
        // This is approximately 5.77e24
        assertTrue(result is LuaNumber)
    }

    @Test
    fun testBitwiseMask() {
        val result =
            execute(
                """
            local lnum = 0x13121110090807060504030201
            local i = 1
            local n = lnum & (~(-1 << (i * 8)))
            -- For i=1: mask is 0xFF, so n should be 0x01
            return n
        """,
            )
        assertLuaNumber(result, 0x01.toDouble())
    }

    @Test
    fun testBitwiseMaskI2() {
        val result =
            execute(
                """
            local lnum = 0x13121110090807060504030201
            local i = 2
            local n = lnum & (~(-1 << (i * 8)))
            -- For i=2: mask is 0xFFFF, so n should be 0x0201
            return n
        """,
            )
        assertLuaNumber(result, 0x0201.toDouble())
    }

    @Test
    fun testPackLargeHexI1() {
        val result =
            execute(
                """
            local lstr = "\1\2\3\4\5\6\7\8\9\10\11\12\13"
            local lnum = 0x13121110090807060504030201
            local i = 1
            local n = lnum & (~(-1 << (i * 8)))
            local s = string.sub(lstr, 1, i)
            local packed = string.pack("<i" .. i, n)
            return packed == s
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testPackLargeHexI2() {
        val result =
            execute(
                """
            local lstr = "\1\2\3\4\5\6\7\8\9\10\11\12\13"
            local lnum = 0x13121110090807060504030201
            local i = 2
            local n = lnum & (~(-1 << (i * 8)))
            local s = string.sub(lstr, 1, i)
            local packed = string.pack("<i" .. i, n)
            return packed == s
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testPackLargeHexI8() {
        val result =
            execute(
                """
            local lstr = "\1\2\3\4\5\6\7\8\9\10\11\12\13"
            local lnum = 0x13121110090807060504030201
            local i = 8
            local n = lnum & (~(-1 << (i * 8)))
            local s = string.sub(lstr, 1, i)
            local packed = string.pack("<i" .. i, n)
            
            -- Debug: check each byte
            local match = true
            for j = 1, i do
                local pb = string.byte(packed, j)
                local sb = string.byte(s, j)
                if pb ~= sb then
                    match = false
                    break
                end
            end
            
            return match
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testTpackLine99() {
        // Exact test from tpack.lua line 99
        val result =
            execute(
                """
            local lstr = "\1\2\3\4\5\6\7\8\9\10\11\12\13"
            local lnum = 0x13121110090807060504030201
            local sizeLI = 8
            
            for i = 1, sizeLI do
                local n = lnum & (~(-1 << (i * 8)))
                local s = string.sub(lstr, 1, i)
                local packed = string.pack("<i" .. i, n)
                if packed ~= s then
                    return false, "i=" .. i
                end
            end
            
            return true
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testPackI9Format() {
        // i9 format: 9-byte integer (preserves 64-bit precision + extra byte)
        val result =
            execute(
                """
            local lnum = 0x13121110090807060504030201
            local s = string.pack("<i9", lnum)
            local v = string.unpack("<i9", s)
            return v == lnum and 1 or 0
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackJ8UnpackI9() {
        // Pack with j (8 bytes), unpack with i9 (9 bytes with extra zero)
        val result =
            execute(
                """
            local lnum = 0x13121110090807060504030201
            local s = string.pack("<j", lnum)
            local extended = s .. "\0"
            local v = string.unpack("<i9", extended)
            return v == lnum and 1 or 0
        """,
            )
        assertLuaNumber(result, 1.0)
    }
}
