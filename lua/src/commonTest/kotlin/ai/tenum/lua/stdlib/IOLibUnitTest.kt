package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Direct unit tests for IOLib internals
 */
class IOLibUnitTest {
    @Test
    fun testLuaFileHandleCreatesTableWithMetatable() {
        val fs = FakeFileSystem()
        fs.write("test.txt".toPath()) {
            writeUtf8("Hello")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val table = handle.toLuaTable()

        // Check metatable exists
        assertNotNull(table.metatable, "Metatable should be set")

        val metatable = table.metatable as? LuaTable
        assertNotNull(metatable, "Metatable should be a table")

        // Check __index exists
        val index = metatable[LuaString("__index")]
        assertNotNull(index, "__index should exist")

        val indexTable = index as? LuaTable
        assertNotNull(indexTable, "__index should be a table")

        // Check read method exists
        val readMethod = indexTable[LuaString("read")]
        assertTrue(readMethod is LuaNativeFunction, "read should be a function")
    }
}
