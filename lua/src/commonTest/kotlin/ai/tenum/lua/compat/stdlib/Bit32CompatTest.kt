package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

class Bit32CompatTest : LuaCompatTestBase() {
    @Test
    fun testBandBasic() {
        assertLuaNumber("return bit32.band(0xF0F0F0F0, 0x0F0F0F0F)", 0x00000000.toDouble())
    }

    @Test
    fun testBorBasic() {
        assertLuaNumber("return bit32.bor(0xF0, 0x0F)", 0xFF.toDouble())
    }

    @Test
    fun testBxorBasic() {
        assertLuaNumber("return bit32.bxor(0xFFFF, 0x00FF)", 0xFF00.toDouble())
    }

    @Test
    fun testBnot() {
        assertLuaNumber("return bit32.bnot(0xFF)", ((0xFF.inv().toLong()) and 0xFFFFFFFFL).toDouble())
    }

    @Test
    fun testLshiftRshift() {
        assertLuaNumber("return bit32.lshift(1, 4)", 16.0)
        assertLuaNumber("return bit32.rshift(0x10000, 8)", 0x100.toDouble())
    }

    @Test
    fun testRotate() {
        assertLuaNumber("return bit32.lrotate(0x12345678, 4)", 0x23456781.toDouble())
        assertLuaNumber("return bit32.rrotate(0x12345678, 4)", 0x81234567.toDouble())
    }

    @Test
    fun testExtractReplace() {
        assertLuaNumber("return bit32.extract(0x12345678, 0, 4)", 8.0)
        assertLuaNumber("return bit32.replace(0x12345678, 5, 28, 4)", 0x52345678.toDouble())
    }

    @Test
    fun testBtest() {
        assertLuaBoolean("return bit32.btest(0x10, 0x10)", true)
        assertLuaBoolean("return bit32.btest(0x10, 0x01)", false)
        assertLuaBoolean("return bit32.btest()", true)
    }
}
