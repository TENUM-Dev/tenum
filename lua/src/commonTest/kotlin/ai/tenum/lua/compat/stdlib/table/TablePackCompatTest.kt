package ai.tenum.lua.compat.stdlib.table

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Compatibility tests for table.pack()
 * Based on official Lua 5.4.8 test suite (tpack.lua)
 */
class TablePackCompatTest : LuaCompatTestBase() {
    @Test
    fun testTablePackBasic() {
        assertLuaNumber("local t = table.pack(1, 2, 3); return t[1]", 1.0)
    }

    @Test
    fun testTablePackCount() {
        assertLuaNumber("local t = table.pack(1, 2, 3); return t.n", 3.0)
    }

    @Test
    fun testTablePackWithNil() {
        assertLuaNumber("local t = table.pack(1, nil, 3); return t.n", 3.0)
    }

    @Test
    fun testTablePackEmpty() {
        assertLuaNumber("local t = table.pack(); return t.n", 0.0)
    }

    @Test
    fun testTablePackPreservesNil() {
        assertLuaNil("local t = table.pack(1, nil, 3); return t[2]")
    }
}
