package ai.tenum.lua.compat.stdlib.io

// CPD-OFF: test file with intentional file system test setup duplications

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.LuaVmImpl
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 6.5: IO Library Compatibility Tests
 *
 * Tests file I/O operations using Okio filesystem.
 * Based on Lua 5.4 io library specification.
 */
class IOLibCompatTest : LuaCompatTestBase() {
    private fun createVmWithFileSystem(): Pair<LuaVmImpl, FakeFileSystem> {
        val fs = FakeFileSystem()
        val vm = LuaVmImpl(fileSystem = fs)
        // vm.debugEnabled = true  // Enable debug output
        return Pair(vm, fs)
    }

    @Test
    fun testIoOpenRead() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            // Create a test file
            fs.write("test.txt".toPath()) {
                writeUtf8("Hello, World!")
            }

            // Test that we can open and check type
            vm.execute(
                """
            local file = io.open("test.txt", "r")
            assert(file ~= nil, "File should open")
            assert(io.type(file) == "file", "Should be a file handle")
        """,
            )

            // Test that __index method exists
            val result2 =
                vm.execute(
                    """
            local file = io.open("test.txt", "r")
            local mt = getmetatable(file)
            if mt == nil then return "no metatable" end
            local idx = mt.__index
            if idx == nil then return "no __index" end
            local rd = idx.read
            if rd == nil then return "no read" end
            return "ok"
        """,
                )

            assertTrue(result2 is LuaString, "Expected string but got ${result2::class.simpleName}")
            assertEquals("ok", (result2 as LuaString).value)
        }

    @Test
    fun testIoOpenNonExistent() =
        runTest {
            val (vm, _) = createVmWithFileSystem()

            val result =
                vm.execute(
                    """
            local file = io.open("nonexistent.txt", "r")
            return file == nil
        """,
                )

            assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
            assertEquals(true, (result as LuaBoolean).value)
        }

    @Test
    fun testIoReadAll() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test.txt".toPath()) {
                writeUtf8("Hello, World!")
            }

            val result =
                vm.execute(
                    """
            local file = io.open("test.txt", "r")
            local content = file:read("*a")
            file:close()
            return content
        """,
                )

            assertTrue(result is LuaString, "Expected string but got ${result::class.simpleName}")
            assertEquals("Hello, World!", (result as LuaString).value)
        }

    @Test
    fun testIoReadLine() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test.txt".toPath()) {
                writeUtf8("First line\nSecond line\nThird line")
            }

            val result =
                vm.execute(
                    """
            local file = io.open("test.txt", "r")
            local line = file:read("*l")
            file:close()
            return line
        """,
                )

            assertTrue(result is LuaString, "Expected string but got ${result::class.simpleName}")
            assertEquals("First line", (result as LuaString).value)
        }

    @Test
    fun testIoWrite() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            vm.execute(
                """
            local file = io.open("output.txt", "w")
            file:write("Hello, ")
            file:write("World!")
            file:close()
        """,
            )

            val content = fs.read("output.txt".toPath()) { readUtf8() }
            assertEquals(content, "Hello, World!", "File should contain written content")
        }

    @Test
    fun testIoAppend() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            // Create initial file
            fs.write("output.txt".toPath()) {
                writeUtf8("Initial")
            }

            vm.execute(
                """
            local file = io.open("output.txt", "a")
            file:write(" Appended")
            file:close()
        """,
            )

            val content = fs.read("output.txt".toPath()) { readUtf8() }
            assertEquals(content, "Initial Appended", "File should have appended content")
        }

    @Test
    fun testIoClose() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test.txt".toPath()) {
                writeUtf8("Test")
            }

            val result =
                vm.execute(
                    """
            local file = io.open("test.txt", "r")
            file:close()
            assert(io.type(file) == "closed file", "File should be closed")
            return true
        """,
                )

            assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
            assertEquals(true, (result as LuaBoolean).value)
        }

    @Test
    fun testIoLines() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test.txt".toPath()) {
                writeUtf8("Line 1\nLine 2\nLine 3")
            }

            val result =
                vm.execute(
                    """
            local lines = {}
            for line in io.lines("test.txt") do
                table.insert(lines, line)
            end
            return #lines
        """,
                )

            assertLuaNumber(result, 3.0)
        }

    @Test
    fun testIoFileLines() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test.txt".toPath()) {
                writeUtf8("A\nB\nC")
            }

            // Test that lines() returns an iterator function
            // (Generic for-loop support requires FORPREP/FORLOOP opcodes)
            val result =
                vm.execute(
                    """
            local file = io.open("test.txt", "r")
            local iter = file:lines()
            local line1 = iter()
            local line2 = iter()
            local line3 = iter()
            local line4 = iter()
            file:close()
            return line1 == "A" and line2 == "B" and line3 == "C" and line4 == nil
        """,
                )

            assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
            assertEquals(true, (result as LuaBoolean).value)
        }

    @Test
    fun testIoType() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test.txt".toPath()) {
                writeUtf8("Test")
            }

            val result =
                vm.execute(
                    """
            local file = io.open("test.txt", "r")
            local t1 = io.type(file)
            file:close()
            local t2 = io.type(file)
            local t3 = io.type("not a file")
            return t1 == "file" and t2 == "closed file" and t3 == nil
        """,
                )

            assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
            assertEquals(true, (result as LuaBoolean).value)
        }

    @Test
    fun testIoFlush() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            vm.execute(
                """
            local file = io.open("test.txt", "w")
            file:write("Data")
            file:flush()
            file:close()
        """,
            )

            val content = fs.read("test.txt".toPath()) { readUtf8() }
            assertTrue(content == "Data", "Data should be flushed to file")
        }

    @Test
    fun testIoReadNumber() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("numbers.txt".toPath()) {
                writeUtf8("42.5")
            }

            val result =
                vm.execute(
                    """
            local file = io.open("numbers.txt", "r")
            local num = file:read("*n")
            file:close()
            return num
        """,
                )

            assertLuaNumber(result, 42.5)
        }

    @Test
    fun testIoReadCharacters() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test.txt".toPath()) {
                writeUtf8("ABCDEFGH")
            }

            val result =
                vm.execute(
                    """
            local file = io.open("test.txt", "r")
            local chars = file:read(3)
            file:close()
            return chars
        """,
                )

            assertTrue(result is LuaString, "Expected string but got ${result::class.simpleName}")
            assertEquals("ABC", (result as LuaString).value)
        }

    @Test
    fun testIoMultipleWrites() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            vm.execute(
                """
            local file = io.open("test.txt", "w")
            file:write("A", "B", "C")
            file:close()
        """,
            )

            val content = fs.read("test.txt".toPath()) { readUtf8() }
            assertEquals(content, "ABC", "Multiple writes should concatenate")
        }

    @Test
    fun testIoWriteNumbers() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            vm.execute(
                """
            local file = io.open("test.txt", "w")
            file:write(123, " ", 456)
            file:close()
        """,
            )

            val content = fs.read("test.txt".toPath()) { readUtf8() }
            assertTrue(content.contains("123") && content.contains("456"), "Numbers should be written")
        }

    // ========== io.input/io.output Validation Tests ==========

    @Test
    fun testIoInputRejectsNonFileWithMetatableName() =
        runTest {
            val (vm, _) = createVmWithFileSystem()

            val result =
                vm.execute(
                    """
                local XX = setmetatable({}, { __name = "My Type" })
                local ok, err = pcall(io.input, XX)
                return not ok and string.find(tostring(err), "FILE%*") ~= nil and string.find(tostring(err), "My Type") ~= nil
            """,
                )

            assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
            assertEquals(true, (result as LuaBoolean).value, "Should reject non-FILE* with metatable name")
        }

    @Test
    fun testIoInputRejectsNonFileWithoutMetatableName() =
        runTest {
            val (vm, _) = createVmWithFileSystem()

            val result =
                vm.execute(
                    """
                local XX = {}
                local ok, err = pcall(io.input, XX)
                return not ok and string.find(tostring(err), "FILE%*") ~= nil and string.find(tostring(err), "table") ~= nil
            """,
                )

            assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
            assertEquals(true, (result as LuaBoolean).value, "Should reject non-FILE* without metatable name")
        }

    @Test
    fun testIoOutputRejectsNonFileWithMetatableName() =
        runTest {
            val (vm, _) = createVmWithFileSystem()

            val result =
                vm.execute(
                    """
                local XX = setmetatable({}, { __name = "My Type" })
                local ok, err = pcall(io.output, XX)
                return not ok and string.find(tostring(err), "FILE%*") ~= nil and string.find(tostring(err), "My Type") ~= nil
            """,
                )

            assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
            assertEquals(true, (result as LuaBoolean).value, "Should reject non-FILE* with metatable name")
        }

    @Test
    fun testIoOutputRejectsNonFileWithoutMetatableName() =
        runTest {
            val (vm, _) = createVmWithFileSystem()

            val result =
                vm.execute(
                    """
                local XX = {}
                local ok, err = pcall(io.output, XX)
                return not ok and string.find(tostring(err), "FILE%*") ~= nil and string.find(tostring(err), "table") ~= nil
            """,
                )

            assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
            assertEquals(true, (result as LuaBoolean).value, "Should reject non-FILE* without metatable name")
        }

    @Test
    fun testIoInputAcceptsValidFile() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test.txt".toPath()) {
                writeUtf8("Hello")
            }

            vm.execute(
                """
                local file = io.open("test.txt", "r")
                io.input(file)
                -- Should succeed without error
                file:close()
            """,
            )
        }

    @Test
    fun testIoOutputAcceptsValidFile() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            vm.execute(
                """
                local file = io.open("test.txt", "w")
                io.output(file)
                -- Should succeed without error
                file:close()
            """,
            )
        }
}
