package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.LuaVmImpl
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for load/loadfile/dofile/require functions
 * Uses FakeFileSystem for filesystem-based tests
 */
class BasicFunctionsLoadTest : LuaCompatTestBase() {
    @Test
    fun testLoadValidCode() {
        assertLuaNumber("return load('return 42')()", 42.0)
    }

    @Test
    fun testLoadInvalidCode() {
        assertLuaBoolean("local f, err = load('return @#$'); return f == nil and type(err) == 'string'", true)
    }

    @Test
    fun testLoadReturnsFunction() {
        assertLuaString("local f = load('return 1+1'); return type(f)", "function")
    }

    @Test
    fun testLoadExecutesWhenCalled() {
        assertLuaNumber(
            """
            local f = load('x = 10')
            f()
            return x
        """,
            10.0,
        )
    }

    @Test
    fun testLoadfileSuccess() {
        val fs = FakeFileSystem()
        fs.createDirectories("test".toPath())
        fs.write("test.lua".toPath()) {
            writeUtf8("return 'Hello from file'")
        }

        val testVm = LuaVmImpl(fileSystem = fs)
        val result =
            testVm.execute(
                """
            local f = loadfile('test.lua')
            return f()
        """,
            )

        assertTrue(result is LuaString, "Expected string but got ${result::class.simpleName}")
        assertEquals("Hello from file", result.value)
    }

    @Test
    fun testLoadfileMissingFile() {
        val fs = FakeFileSystem()
        val testVm = LuaVmImpl(fileSystem = fs)

        val result =
            testVm.execute(
                """
            local f, err = loadfile('nonexistent.lua')
            return f == nil and type(err) == 'string'
        """,
            )

        assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
        assertEquals(true, result.value)
    }

    @Test
    fun testDofileSuccess() {
        val fs = FakeFileSystem()
        fs.write("test.lua".toPath()) {
            writeUtf8("return 99")
        }

        val testVm = LuaVmImpl(fileSystem = fs)
        val result = testVm.execute("return dofile('test.lua')")

        assertTrue(result is LuaNumber, "Expected number but got ${result::class.simpleName}")
        assertEquals(LuaNumber.of(99), result)
    }

    @Test
    fun testDofileExecutesImmediately() {
        val fs = FakeFileSystem()
        fs.write("test.lua".toPath()) {
            writeUtf8("y = 20")
        }

        val testVm = LuaVmImpl(fileSystem = fs)
        testVm.debugEnabled = true
        val result =
            testVm.execute(
                """
            dofile('test.lua')
            return y
        """,
            )

        assertTrue(result is LuaNumber, "Expected number but got ${result::class.simpleName}")
        assertEquals(LuaNumber.of(20), result)
    }

    @Test
    fun testRequireLoadsModule() {
        val fs = FakeFileSystem()
        fs.write("mymodule.lua".toPath()) {
            writeUtf8("return { value = 42 }")
        }

        val testVm = LuaVmImpl(fileSystem = fs)
        val result =
            testVm.execute(
                """
            local m = require('mymodule')
            return m.value
        """,
            )

        assertTrue(result is LuaNumber, "Expected number but got ${result::class.simpleName}")
        assertEquals(LuaNumber.of(42), result)
    }

    @Test
    fun testRequireCachesModule() {
        val fs = FakeFileSystem()
        fs.write("counter.lua".toPath()) {
            writeUtf8(
                """
                local count = 0
                count = count + 1
                return { value = count }
            """,
            )
        }

        val testVm = LuaVmImpl(fileSystem = fs)
        val result =
            testVm.execute(
                """
            local m1 = require('counter')
            local m2 = require('counter')
            return m1.value == 1 and m2.value == 1
        """,
            )

        assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
        assertEquals(true, result.value)
    }

    @Test
    fun testRequireSearchPath() {
        val fs = FakeFileSystem()
        fs.createDirectories("libs".toPath())
        fs.write("libs/init.lua".toPath()) {
            writeUtf8("return 'found'")
        }

        val testVm = LuaVmImpl(fileSystem = fs)
        // Default search paths are "./?.lua" and "./?/init.lua"
        val result = testVm.execute("return require('libs')")

        assertTrue(result is LuaString, "Expected string but got ${result::class.simpleName}")
        assertEquals("found", result.value)
    }

    @Test
    fun testRequireModuleNotFound() {
        val fs = FakeFileSystem()
        val testVm = LuaVmImpl(fileSystem = fs)

        assertFailsWith<RuntimeException> {
            testVm.execute("require('nonexistent')")
        }
    }

    @Test
    fun testRequireReturnsTrue() {
        val fs = FakeFileSystem()
        fs.write("noreturn.lua".toPath()) {
            writeUtf8("x = 5") // Module doesn't return anything
        }

        val testVm = LuaVmImpl(fileSystem = fs)
        val result =
            testVm.execute(
                """
            local m = require('noreturn')
            return m == true
        """,
            )

        assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
        assertEquals(true, result.value)
    }

    @Test
    fun testLoadWithChunkname() {
        // Chunkname is used for error messages (not tested here, but parameter should be accepted)
        assertLuaNumber("local f = load('return 7', 'mychunk'); return f()", 7.0)
    }

    @Test
    fun testDofileMissingFile() {
        val fs = FakeFileSystem()
        val testVm = LuaVmImpl(fileSystem = fs)

        assertFailsWith<RuntimeException> {
            testVm.execute("dofile('missing.lua')")
        }
    }
}
