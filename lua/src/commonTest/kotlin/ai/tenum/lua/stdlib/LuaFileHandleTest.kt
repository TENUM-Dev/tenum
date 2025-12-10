package ai.tenum.lua.stdlib

// CPD-OFF: test file with intentional file handle test setup duplications

import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaUserdata
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for LuaFileHandle
 *
 * Tests file operations directly without going through the Lua VM.
 */
class LuaFileHandleTest {
    private lateinit var fs: FakeFileSystem

    @BeforeTest
    fun setup() {
        fs = FakeFileSystem()
    }

    // === READ MODE TESTS ===

    @Test
    fun testReadModeLoadsContent() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Hello, World!")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        assertFalse(handle.isClosed)
    }

    @Test
    fun testReadAllContent() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Hello, World!")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val result = handle.read(listOf(LuaString("*a")))

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaString)
        assertEquals("Hello, World!", (result[0] as LuaString).value)
    }

    @Test
    fun testReadAllWithSelf() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Test content")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val selfTable = handle.toLuaTable()

        // Simulate method call with self as first arg
        val result = handle.read(listOf(selfTable, LuaString("*a")))

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaString)
        assertEquals("Test content", (result[0] as LuaString).value)
    }

    @Test
    fun testReadLine() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Line 1\nLine 2\nLine 3")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val result = handle.read(listOf(LuaString("*l")))

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaString)
        assertEquals("Line 1", (result[0] as LuaString).value)
    }

    @Test
    fun testReadNumber() {
        fs.write("test.txt".toPath()) {
            writeUtf8("42.5")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val result = handle.read(listOf(LuaString("*n")))

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaNumber)
        assertEquals(42.5, (result[0] as LuaNumber).value)
    }

    @Test
    fun testReadCharacters() {
        fs.write("test.txt".toPath()) {
            writeUtf8("ABCDEFGH")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val result = handle.read(listOf(LuaString("3")))

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaString)
        assertEquals("ABC", (result[0] as LuaString).value)
    }

    @Test
    fun testReadDefaultFormat() {
        fs.write("test.txt".toPath()) {
            writeUtf8("First line\nSecond line")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val result = handle.read(emptyList())

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaString)
        assertEquals("First line", (result[0] as LuaString).value)
    }

    @Test
    fun testReadFromClosedFile() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Content")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        handle.close()

        val result = handle.read(listOf(LuaString("*a")))

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaNil)
    }

    // === WRITE MODE TESTS ===

    @Test
    fun testWriteModeCreatesBuffer() {
        val handle = LuaFileHandle("output.txt".toPath(), "w", fs)
        assertFalse(handle.isClosed)
    }

    @Test
    fun testWriteString() {
        val handle = LuaFileHandle("output.txt".toPath(), "w", fs)
        handle.write(listOf(LuaString("Hello")))
        handle.flush()

        val content = fs.read("output.txt".toPath()) { readUtf8() }
        assertEquals("Hello", content)
    }

    @Test
    fun testWriteMultipleStrings() {
        val handle = LuaFileHandle("output.txt".toPath(), "w", fs)
        handle.write(listOf(LuaString("Hello"), LuaString(", "), LuaString("World!")))
        handle.flush()

        val content = fs.read("output.txt".toPath()) { readUtf8() }
        assertEquals("Hello, World!", content)
    }

    @Test
    fun testWriteWithSelf() {
        val handle = LuaFileHandle("output.txt".toPath(), "w", fs)
        val selfTable = handle.toLuaTable()

        // Simulate method call with self as first arg
        handle.write(listOf(selfTable, LuaString("Test")))
        handle.flush()

        val content = fs.read("output.txt".toPath()) { readUtf8() }
        assertEquals("Test", content)
    }

    @Test
    fun testWriteNumber() {
        val handle = LuaFileHandle("output.txt".toPath(), "w", fs)
        handle.write(listOf(LuaNumber.of(42.0)))
        handle.flush()

        val content = fs.read("output.txt".toPath()) { readUtf8() }
        assertTrue(content.contains("42"))
    }

    @Test
    fun testWriteMixedTypes() {
        val handle = LuaFileHandle("output.txt".toPath(), "w", fs)
        handle.write(
            listOf(
                LuaString("Number: "),
                LuaNumber.of(123.0),
                LuaString(" Text"),
            ),
        )
        handle.flush()

        val content = fs.read("output.txt".toPath()) { readUtf8() }
        assertTrue(content.contains("123"))
        assertTrue(content.contains("Text"))
    }

    @Test
    fun testCloseFlushesWrites() {
        val handle = LuaFileHandle("output.txt".toPath(), "w", fs)
        handle.write(listOf(LuaString("Data")))
        handle.close()

        assertTrue(handle.isClosed)
        val content = fs.read("output.txt".toPath()) { readUtf8() }
        assertEquals("Data", content)
    }

    @Test
    fun testWriteToClosedFile() {
        val handle = LuaFileHandle("output.txt".toPath(), "w", fs)
        handle.close()

        val result = handle.write(listOf(LuaString("Data")))

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaNil)
    }

    // === APPEND MODE TESTS ===

    @Test
    fun testAppendModeLoadsExistingContent() {
        fs.write("output.txt".toPath()) {
            writeUtf8("Existing")
        }

        val handle = LuaFileHandle("output.txt".toPath(), "a", fs)
        handle.write(listOf(LuaString(" Appended")))
        handle.flush()

        val content = fs.read("output.txt".toPath()) { readUtf8() }
        assertEquals("Existing Appended", content)
    }

    @Test
    fun testAppendToNonExistentFile() {
        val handle = LuaFileHandle("new.txt".toPath(), "a", fs)
        handle.write(listOf(LuaString("New content")))
        handle.flush()

        val content = fs.read("new.txt".toPath()) { readUtf8() }
        assertEquals("New content", content)
    }

    // === LINES TESTS ===

    @Test
    fun testLines() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Line 1\nLine 2\nLine 3")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val result = handle.lines()

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaNativeFunction)

        val iterator = result[0] as LuaNativeFunction

        // Call iterator 3 times
        val line1 = iterator.function(emptyList())
        assertEquals("Line 1", (line1[0] as LuaString).value)

        val line2 = iterator.function(emptyList())
        assertEquals("Line 2", (line2[0] as LuaString).value)

        val line3 = iterator.function(emptyList())
        assertEquals("Line 3", (line3[0] as LuaString).value)

        // Should return nil after all lines
        val done = iterator.function(emptyList())
        assertTrue(done[0] is LuaNil)
    }

    @Test
    fun testLinesEmptyFile() {
        fs.write("empty.txt".toPath()) {
            writeUtf8("")
        }

        val handle = LuaFileHandle("empty.txt".toPath(), "r", fs)
        val result = handle.lines()

        assertEquals(1, result.size)
        assertTrue(result[0] is LuaNativeFunction)

        val iterator = result[0] as LuaNativeFunction
        val line = iterator.function(emptyList())

        // Empty file has one empty line
        assertTrue(line[0] is LuaString)
        assertEquals("", (line[0] as LuaString).value)
    }

    // === METATABLE TESTS ===

    @Test
    fun testToLuaTableHasMetatable() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Content")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val table = handle.toLuaTable()

        assertNotNull(table.metatable)
        assertTrue(table.metatable is LuaTable)
    }

    @Test
    fun testToLuaTableHasFileHandle() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Content")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val table = handle.toLuaTable()

        val fileHandleValue = table.rawGet(LuaString("__filehandle"))
        assertTrue(fileHandleValue is LuaUserdata)
        assertEquals(handle, (fileHandleValue as LuaUserdata).value)
    }

    @Test
    fun testToLuaTableHasMethods() {
        fs.write("test.txt".toPath()) {
            writeUtf8("Content")
        }

        val handle = LuaFileHandle("test.txt".toPath(), "r", fs)
        val table = handle.toLuaTable()

        val metatable = table.metatable as LuaTable
        val index = metatable[LuaString("__index")] as LuaTable

        assertTrue(index[LuaString("read")] is LuaNativeFunction)
        assertTrue(index[LuaString("write")] is LuaNativeFunction)
        assertTrue(index[LuaString("close")] is LuaNativeFunction)
        assertTrue(index[LuaString("flush")] is LuaNativeFunction)
        assertTrue(index[LuaString("lines")] is LuaNativeFunction)
    }
}
