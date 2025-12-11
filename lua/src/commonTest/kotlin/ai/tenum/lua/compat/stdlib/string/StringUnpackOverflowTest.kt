package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for overflow detection in string.unpack.
 * From tpack.lua lines 89-90 - checking that values that don't fit produce errors.
 */
class StringUnpackOverflowTest : LuaCompatTestBase() {
    @Test
    fun testUnpackI8Overflow() {
        // Unpacking i8 (signed 8-byte) with value > max signed 64-bit should error
        val result =
            execute(
                """
            local status, err = pcall(string.unpack, "<I8", "\x00\x00\x00\x00\x00\x00\x00\x01")
            -- I8 is unsigned, so 2^56 should be fine
            return status
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testUnpackI9OverflowLittleEndian() {
        // From tpack.lua line 89: unpack("<I9", "\x00"*8 .. "\x01") should error "does not fit"
        val result =
            execute(
                """
            local status, err = pcall(string.unpack, "<I9", string.rep("\x00", 8) .. "\x01")
            if status then
                return false, "expected error but got success"
            end
            if not string.find(err, "does not fit") then
                return false, "wrong error message: " .. tostring(err)
            end
            return true
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testUnpackI9OverflowBigEndian() {
        // From tpack.lua line 90: unpack(">i9", "\x01" .. "\x00"*8) should error "does not fit"
        val result =
            execute(
                """
            local status, err = pcall(string.unpack, ">i9", "\x01" .. string.rep("\x00", 8))
            if status then
                return false, "expected error but got success"
            end
            if not string.find(err, "does not fit") then
                return false, "wrong error message: " .. tostring(err)
            end
            return true
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testUnpackI16Overflow() {
        // i16 with value in byte 9+ should error
        val result =
            execute(
                """
            local status, err = pcall(string.unpack, "<I16", string.rep("\x00", 8) .. "\x01" .. string.rep("\x00", 7))
            if status then
                return false, "expected error but got success"
            end
            if not string.find(err, "does not fit") then
                return false, "wrong error message: " .. tostring(err)
            end
            return true
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testUnpackWithinRange() {
        // Values that fit should work fine
        val result =
            execute(
                """
            -- This should succeed - all significant bytes are zero
            local v = string.unpack("<I9", "\xAA" .. string.rep("\x00", 8))
            return v == 0xAA
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testTpackLine89() {
        // Exact test from tpack.lua
        val result =
            execute(
                """
            local function checkerror(msg, f, ...)
                local status, err = pcall(f, ...)
                return not status and string.find(err, msg) ~= nil
            end
            
            local i = 9
            local result = checkerror("does not fit", string.unpack, "<I" .. i, string.rep("\x00", i - 1) .. "\x01")
            return result
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testTpackLine90() {
        // Exact test from tpack.lua
        val result =
            execute(
                """
            local function checkerror(msg, f, ...)
                local status, err = pcall(f, ...)
                return not status and string.find(err, msg) ~= nil
            end
            
            local i = 9
            local result = checkerror("does not fit", string.unpack, ">i" .. i, "\x01" .. string.rep("\x00", i - 1))
            return result
        """,
            )
        assertLuaBoolean(result, true)
    }

    @Test
    fun testTpackLine129() {
        // Line 129: checkerror("16%-byte integer", unpack, "i16", string.rep('\3', 16))
        // This tests that unpacking an i16 format should fail with error message containing "16-byte integer"
        execute(
            """
            local unpack = string.unpack
            
            local function checkerror(msg, f, ...)
                local status, err = pcall(f, ...)
                print("checkerror: msg='" .. msg .. "' status=" .. tostring(status) .. " err=" .. tostring(err))
                if status then
                    error("Expected error but got success")
                end
                if not string.find(err, msg) then
                    error("Pattern '" .. msg .. "' not found in error: '" .. tostring(err) .. "'")
                end
                assert(not status and string.find(err, msg))
            end
            
            -- Line 129 test
            checkerror("16%-byte integer", unpack, "i16", string.rep('\3', 16))
            print("Line 129 passed!")
        """,
        )
    }
}
