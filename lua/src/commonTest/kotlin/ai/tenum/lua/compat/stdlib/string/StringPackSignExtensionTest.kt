package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for sign extension in string.pack with signed integers.
 * Based on tpack.lua lines 58-61 - the loop that tests negative numbers.
 */
class StringPackSignExtensionTest : LuaCompatTestBase() {
    @Test
    fun testPackMinusOneSignExtension() {
        // Test that packing -1 as signed integer produces all 0xFF bytes
        val result =
            execute(
                """
            local s1 = string.pack("i1", -1)
            local s2 = string.pack("i2", -1)
            local s4 = string.pack("i4", -1)
            local s8 = string.pack("i8", -1)
            
            -- Check that -1 produces all 0xFF bytes (sign extension)
            local check1 = (s1 == "\xFF")
            local check2 = (s2 == "\xFF\xFF")
            local check4 = (s4 == "\xFF\xFF\xFF\xFF")
            local check8 = (s8 == "\xFF\xFF\xFF\xFF\xFF\xFF\xFF\xFF")
            
            return check1 and check2 and check4 and check8
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testUnpackMinusOneSignExtension() {
        // Test that unpacking all 0xFF bytes produces -1
        val result =
            execute(
                """
            local v1 = string.unpack("i1", "\xFF")
            local v2 = string.unpack("i2", "\xFF\xFF")
            local v4 = string.unpack("i4", "\xFF\xFF\xFF\xFF")
            local v8 = string.unpack("i8", "\xFF\xFF\xFF\xFF\xFF\xFF\xFF\xFF")
            
            return v1 == -1 and v2 == -1 and v4 == -1 and v8 == -1
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testPackUnsignedSmallNumber() {
        // Test little-endian unsigned: 0xAA should be "\xAA\x00"
        val result =
            execute(
                """
            local s1 = string.pack("<I1", 0xAA)
            local s2 = string.pack("<I2", 0xAA)
            local s4 = string.pack("<I4", 0xAA)
            
            -- Little-endian: low byte first
            local check1 = (s1 == "\xAA")
            local check2 = (s2 == "\xAA\x00")
            local check4 = (s4 == "\xAA\x00\x00\x00")
            
            return check1 and check2 and check4
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testPackBigEndianUnsigned() {
        // Test big-endian unsigned: 0xAA should be "\x00\xAA"
        val result =
            execute(
                """
            local s1 = string.pack(">I1", 0xAA)
            local s2 = string.pack(">I2", 0xAA)
            local s4 = string.pack(">I4", 0xAA)
            
            -- Big-endian: high byte first
            local check1 = (s1 == "\xAA")
            local check2 = (s2 == "\x00\xAA")
            local check4 = (s4 == "\x00\x00\x00\xAA")
            
            return check1 and check2 and check4
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testUnpackUnsignedSmallNumber() {
        val result =
            execute(
                """
            local v1 = string.unpack("<I1", "\xAA")
            local v2 = string.unpack("<I2", "\xAA\x00")
            local v4 = string.unpack("<I4", "\xAA\x00\x00\x00")
            
            return v1 == 0xAA and v2 == 0xAA and v4 == 0xAA
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testPacksizeForDifferentSizes() {
        val result =
            execute(
                """
            local s1 = string.packsize("i1")
            local s2 = string.packsize("i2")
            local s4 = string.packsize("i4")
            local s8 = string.packsize("i8")
            
            return s1 == 1 and s2 == 2 and s4 == 4 and s8 == 8
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testTpackLine58To61() {
        // This is the exact loop from tpack.lua lines 58-68
        val result =
            execute(
                """
            local pack = string.pack
            local packsize = string.packsize
            local unpack = string.unpack
            local NB = 16
            
            for i = 1, NB do
              -- small numbers with signal extension ("\xFF...")
              local s = string.rep("\xFF", i)
              assert(pack("i" .. i, -1) == s)
              assert(packsize("i" .. i) == #s)
              assert(unpack("i" .. i, s) == -1)
            end
            
            return true
        """,
            )
        assertLuaBoolean(result, true)
    }
}
