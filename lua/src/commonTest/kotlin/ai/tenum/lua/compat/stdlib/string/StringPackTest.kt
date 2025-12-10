package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for string.pack, string.unpack, and string.packsize functions.
 *
 * These functions handle binary data packing/unpacking with format strings.
 *
 * Format string codes:
 * - b: signed byte
 * - B: unsigned byte
 * - h: signed short (2 bytes)
 * - H: unsigned short (2 bytes)
 * - l: signed long (4 bytes)
 * - L: unsigned long (4 bytes)
 * - j: lua_Integer (typically 8 bytes)
 * - J: lua_Unsigned (typically 8 bytes)
 * - i[n]: signed integer with n bytes (1-16)
 * - I[n]: unsigned integer with n bytes (1-16)
 * - f: float (4 bytes)
 * - d: double (8 bytes)
 * - n: lua_Number
 * - s[n]: string with length prefix
 * - z: zero-terminated string
 * - c[n]: fixed-size string
 * - <: little endian
 * - >: big endian
 * - =: native endian
 * - ![n]: set alignment
 * - x: padding byte
 * - X: padding and alignment
 */
class StringPackTest : LuaCompatTestBase() {
    @Test
    fun testPacksizeBasicFormats() {
        val result =
            execute(
                """
            local b = string.packsize("b")
            local h = string.packsize("h")
            local i = string.packsize("i")
            local l = string.packsize("l")
            local j = string.packsize("j")
            local f = string.packsize("f")
            local d = string.packsize("d")
            
            -- Basic validation
            assert(b == 1, "byte should be 1")
            assert(h == 2, "short should be 2")
            assert(l == 4, "long should be 4")
            assert(j == 8, "lua_Integer should be 8")
            assert(f == 4, "float should be 4")
            assert(d == 8, "double should be 8")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackByte() {
        val result =
            execute(
                """
            -- Unsigned byte (0-255)
            local s = string.pack("B", 0xFF)
            local v = string.unpack("B", s)
            assert(v == 0xFF, "unsigned byte failed")
            
            -- Signed byte (-128 to 127)
            s = string.pack("b", 0x7F)
            v = string.unpack("b", s)
            assert(v == 0x7F, "signed byte positive failed")
            
            s = string.pack("b", -0x80)
            v = string.unpack("b", s)
            assert(v == -0x80, "signed byte negative failed")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackShort() {
        val result =
            execute(
                """
            -- Unsigned short
            local s = string.pack("H", 0xFFFF)
            local v = string.unpack("H", s)
            assert(v == 0xFFFF, "unsigned short failed")
            
            -- Signed short
            s = string.pack("h", 0x7FFF)
            v = string.unpack("h", s)
            assert(v == 0x7FFF, "signed short positive failed")
            
            s = string.pack("h", -0x8000)
            v = string.unpack("h", s)
            assert(v == -0x8000, "signed short negative failed")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackLong() {
        val result =
            execute(
                """
            -- Unsigned long
            local s = string.pack("L", 0xFFFFFFFF)
            local v = string.unpack("L", s)
            assert(v == 0xFFFFFFFF, "unsigned long failed")
            
            -- Signed long
            s = string.pack("l", 0x7FFFFFFF)
            v = string.unpack("l", s)
            assert(v == 0x7FFFFFFF, "signed long positive failed")
            
            s = string.pack("l", -0x80000000)
            v = string.unpack("l", s)
            assert(v == -0x80000000, "signed long negative failed")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackSizedInteger() {
        val result =
            execute(
                """
            -- Test various integer sizes
            for i = 1, 8 do
                local fmt = "i" .. i
                local size = string.packsize(fmt)
                assert(size == i, "size mismatch for " .. fmt)
                
                -- Test packing -1 (all FF bytes)
                local s = string.pack(fmt, -1)
                assert(#s == i, "packed length mismatch")
                assert(s == string.rep("\xff", i), "packed bytes mismatch for -1")
                
                local v = string.unpack(fmt, s)
                assert(v == -1, "unpacked value mismatch")
            end
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackLittleEndian() {
        val result =
            execute(
                """
            -- Little endian unsigned byte at start
            local s = string.pack("<I4", 0xAA)
            -- Should be 0xAA followed by 3 zeros
            assert(s:byte(1) == 0xAA, "first byte should be 0xAA")
            assert(s:byte(2) == 0, "second byte should be 0")
            assert(s:byte(3) == 0, "third byte should be 0")
            assert(s:byte(4) == 0, "fourth byte should be 0")
            
            local v = string.unpack("<I4", s)
            assert(v == 0xAA, "unpacked value mismatch")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackBigEndian() {
        val result =
            execute(
                """
            -- Big endian unsigned byte at end
            local s = string.pack(">I4", 0xAA)
            -- Should be 3 zeros followed by 0xAA
            assert(s:byte(1) == 0, "first byte should be 0")
            assert(s:byte(2) == 0, "second byte should be 0")
            assert(s:byte(3) == 0, "third byte should be 0")
            assert(s:byte(4) == 0xAA, "fourth byte should be 0xAA")
            
            local v = string.unpack(">I4", s)
            assert(v == 0xAA, "unpacked value mismatch")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackLargeHexNumber() {
        // This is from tpack.lua line 74
        val result =
            execute(
                """
            local lnum = 0x13121110090807060504030201
            local s = string.pack("<j", lnum)
            local v = string.unpack("<j", s)
            assert(v == lnum, "large hex number pack/unpack failed")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackFloat() {
        val result =
            execute(
                """
            local s = string.pack("f", 3.14)
            local v = string.unpack("f", s)
            -- Float precision is limited, so check approximate equality
            assert(math.abs(v - 3.14) < 0.01, "float pack/unpack failed")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackDouble() {
        val result =
            execute(
                """
            local s = string.pack("d", 3.14159265358979)
            local v = string.unpack("d", s)
            assert(v == 3.14159265358979, "double pack/unpack failed")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackMultipleValues() {
        val result =
            execute(
                """
            local s = string.pack("bhi", 10, 20, 30)
            local b, h, i = string.unpack("bhi", s)
            assert(b == 10, "first value mismatch")
            assert(h == 20, "second value mismatch")
            assert(i == 30, "third value mismatch")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackString() {
        val result =
            execute(
                """
            -- Fixed-size string
            local s = string.pack("c5", "hello")
            assert(#s == 5, "packed string length mismatch")
            local v = string.unpack("c5", s)
            assert(v == "hello", "fixed-size string mismatch")
            
            -- Zero-terminated string
            s = string.pack("z", "test")
            local v2 = string.unpack("z", s)
            assert(v2 == "test", "zero-terminated string mismatch")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackStringWithLength() {
        // 's' format: string with size_t (8-byte) length prefix
        val result =
            execute(
                """
            local s = "hello"
            local packed = string.pack("s", s)
            -- 8 bytes for length + 5 bytes for string = 13 bytes
            assert(#packed == 13, "packed 's' length mismatch, got " .. #packed)
            local unpacked = string.unpack("s", packed)
            assert(unpacked == s, "unpacked 's' string mismatch")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackStringWithCustomSize() {
        // 's2' format: string with 2-byte length prefix
        val result =
            execute(
                """
            local s = "test"
            local packed = string.pack("s2", s)
            -- 2 bytes for length + 4 bytes for string = 6 bytes
            assert(#packed == 6, "packed 's2' length mismatch")
            local unpacked = string.unpack("s2", packed)
            assert(unpacked == s, "unpacked 's2' string mismatch")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPackUnpackLargeString() {
        // Test 's' format with large string (from tpack.lua line 200)
        val result =
            execute(
                """
            local s = string.rep("abc", 1000)
            local packed = string.pack("s", s)
            local unpacked = string.unpack("s", packed)
            assert(unpacked == s, "large string mismatch")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testUnpackWithNegativeIndex() {
        // Test negative indices for unpack starting position (from tpack.lua line 306-310)
        val result =
            execute(
                """
            local x = string.pack("i4i4i4i4", 1, 2, 3, 4)
            assert(#x == 16, "packed string should be 16 bytes")
            
            -- Test -4 (should start at position 13, 1-based)
            local i, p = string.unpack("!4 i4", x, -4)
            assert(i == 4, "Expected value 4, got " .. tostring(i))
            assert(p == 17, "Expected next position 17, got " .. tostring(p))
            
            -- Test -7 (with alignment, rounds to same position as -4)
            i, p = string.unpack("!4 i4", x, -7)
            assert(i == 4, "Expected value 4 from -7, got " .. tostring(i))
            assert(p == 17, "Expected next position 17 from -7, got " .. tostring(p))
            
            -- Test -#x (start from beginning)
            i, p = string.unpack("!4 i4", x, -#x)
            assert(i == 1, "Expected value 1 from -16, got " .. tostring(i))
            assert(p == 5, "Expected next position 5 from -16, got " .. tostring(p))
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testUnpackOutOfBoundsError() {
        // Test that unpack throws error when position is out of bounds (from tpack.lua line 317)
        val result =
            execute(
                """
            local x = string.pack("i4i4i4i4", 1, 2, 3, 4)
            
            -- Test c0 at valid positions (should return empty string)
            for i = 1, #x + 1 do
                local s = string.unpack("c0", x, i)
                assert(s == "", "c0 should return empty string")
            end
            
            -- Test c0 beyond string bounds (should error)
            local ok, err = pcall(string.unpack, "c0", x, #x + 2)
            assert(not ok, "Should have thrown error for out of bounds")
            assert(string.find(err, "out of string"), "Error should mention 'out of string'")
            
            return 1
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testStringPackInvalidFormats() {
        // From tpack.lua line 124-127: Test that various invalid formats throw errors
        // Using exact same structure as tpack.lua with local aliases

        execute(
            """
            local pack = string.pack
            local NB = 16
            
            local function checkerror(msg, f, ...)
                local status, err = pcall(f, ...)
                print("checkerror: status=" .. tostring(status) .. " err=" .. tostring(err) .. " msg=" .. tostring(msg))
                if status then
                    error("Expected error but got success")
                end
                if not string.find(err, msg) then
                    error("Pattern '" .. msg .. "' not found in error: " .. tostring(err))
                end
                assert(not status and string.find(err, msg))
            end
            
            -- Line 124
            checkerror("out of limits", pack, "i0", 0)
            print("Line 124 passed")
            
            -- Line 125
            checkerror("out of limits", pack, "i" .. NB + 1, 0)
            print("Line 125 passed")
            
            -- Line 126
            checkerror("out of limits", pack, "!" .. NB + 1, 0)
            print("Line 126 passed")
            
            -- Line 127
            checkerror("%(17%) out of limits %[1,16%]", pack, "Xi" .. NB + 1)
            print("Line 127 passed")
        """,
        )
    }
}
