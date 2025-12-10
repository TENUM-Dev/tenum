package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

class StringPackAlignmentTest : LuaCompatTestBase() {
    @Test
    fun testPacksizeWithPaddingX() {
        val result =
            execute(
                """
            -- x is one padding byte
            local size = string.packsize("x")
            return size
        """,
            )
        assertLuaNumber(result, 1.0)
    }

    @Test
    fun testPacksizeWithAlignmentX() {
        val result =
            execute(
                """
            -- X is alignment padding (align to natural boundary)
            -- In "!1 xXi16", the X aligns before the i16
            local size = string.packsize("!1 xXi16")
            -- With !1, maxAlign=1, so i16 is capped to i1
            -- x=1, Xi1 aligns to 1 (no padding), i1 consumed
            return size
        """,
            )
        assertLuaNumber(result, 1.0) // x (1 byte), Xi1 consumes i1
    }

    @Test
    fun testPacksizeWithMaxAlignment() {
        val result =
            execute(
                """
            -- Test the !16 alignment option
            local size = string.packsize("!16 xXi16")
            -- With !16, maxAlign=16, i16 stays i16
            -- x=1, Xi16 aligns to 16 (15 padding), i16 consumed
            return size
        """,
            )
        assertLuaNumber(result, 16.0) // x (1) + Xi16 alignment (15) = 16
    }

    @Test
    fun testPacksizeFromTpackLine22() {
        // This is the exact call from tpack.lua line 22
        val result =
            execute(
                """
            local align = string.packsize("!xXi16")
            return align
        """,
            )
        // With ! alone, maxAlign=8, so i16 is capped to i8
        // x=1, Xi8 aligns to 8 (7 padding), i8 consumed
        assertLuaNumber(result, 8.0) // x (1) + Xi8 alignment (7) = 8
    }

    @Test
    fun testPackWithAlignment() {
        val result =
            execute(
                """
            -- Pack with alignment - real Lua behavior
            -- Xb consumes the 'b' format, so 42 is NOT packed
            local s = string.pack("!2 xXb", 42)
            return #s
        """,
            )
        // With !2, maxAlign=2, x=1 byte, Xb aligns to 1-byte and consumes b
        assertLuaNumber(result, 1.0) // Just the x padding byte
    }

    @Test
    fun testUnpackWithAlignment() {
        val result =
            execute(
                """
            -- Create a proper pack/unpack pair
            local s = string.pack("!2 xb", 42)
            local v = string.unpack("!2 xb", s)
            return v
        """,
            )
        assertLuaNumber(result, 42.0)
    }

    @Test
    fun testAlignmentWithMultipleValues() {
        val result =
            execute(
                """
            -- Test alignment between values
            -- Xi consumes the 'i', so second value is NOT packed
            local s = string.pack("!4 bXi", 1, 0x12345678)
            -- With !4, maxAlign=4, b=1 byte, Xi aligns to 4 and consumes i
            return #s
        """,
            )
        assertLuaNumber(result, 4.0) // b (1 byte) + Xi padding (3 bytes)
    }

    @Test
    fun testPacksizeMatchesPackLength() {
        val result =
            execute(
                """
            -- From tpack.lua line 253 - packsize must equal pack length
            local size = string.packsize(">!8 b Xh i4 i8 c1 Xi8")
            local x = string.pack(">!8 b Xh i4 i8 c1 Xi8", -12, 100, 200, "\xEC")
            print("packsize: " .. size)
            print("pack length: " .. #x)
            return size == #x
        """,
            )
        // Both should be 24:
        // b(1) + Xh(align to 2, h consumed, +1 pad) + i4(align to 4, +2 pad) + i4(4) +
        // i8(align to 8, +0 pad) + i8(8) + c1(1) + Xi8(align to 8, i8 consumed, +7 pad) = 24
        assertLuaBoolean(result, true)
    }

    @Test
    fun testUnpackWithMultipleXAlignments() {
        // From tpack.lua line 258 - regression test for multiple X directives
        val result =
            execute(
                """
            local pack = string.pack
            local unpack = string.unpack
            
            -- Pack data with alignment
            local x = pack(">!8 b Xh i4 i8 c1 Xi8", -12, 100, 200, "\xEC")
            
            -- Unpack with multiple X directives in sequence: Xi8 XI XH
            -- This should NOT cause "data string too short" error
            local a, b, c, d, pos = unpack(">!8 c1 Xh i4 i8 b Xi8 XI XH", x)
            
            -- Verify the unpacked values
            assert(a == "\xF4", "Expected a to be \\xF4")
            assert(b == 100, "Expected b to be 100")
            assert(c == 200, "Expected c to be 200")
            assert(d == -20, "Expected d to be -20")
            assert((pos - 1) == #x, "Expected pos-1 to equal length of x")
            
            return true
        """,
            )
        assertLuaBoolean(result, true)
    }
}
