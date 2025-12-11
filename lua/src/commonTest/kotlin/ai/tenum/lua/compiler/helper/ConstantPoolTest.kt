package ai.tenum.lua.compiler.helper

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import kotlin.test.Test
import kotlin.test.assertEquals

class ConstantPoolTest {
    @Test
    fun testConstantDeduplication() {
        val pool = ConstantPool()
        val idx1 = pool.getIndex(LuaNumber.of(42))
        val idx2 = pool.getIndex(LuaNumber.of(42))
        val idx3 = pool.getIndex(LuaString("foo"))
        val idx4 = pool.getIndex(LuaString("foo"))
        assertEquals(idx1, idx2, "Duplicate numbers should have same index")
        assertEquals(idx3, idx4, "Duplicate strings should have same index")
        assertEquals(2, pool.build().size, "Pool should only contain unique constants")
    }

    @Test
    fun testConstantOrder() {
        val pool = ConstantPool()
        val idx1 = pool.getIndex(LuaNumber.of(1))
        val idx2 = pool.getIndex(LuaNumber.of(2))
        val idx3 = pool.getIndex(LuaString("bar"))
        assertEquals(0, idx1)
        assertEquals(1, idx2)
        assertEquals(2, idx3)
    }
}
