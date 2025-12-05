package ai.tenum.cli.commands

import com.github.ajalt.clikt.core.main
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LuaCommandTest {
    private fun runLua(fs: FakeFileSystem, vararg args: String): Pair<String, String> {
        val originalOut = System.out
        val originalErr = System.err
        val outBaos = ByteArrayOutputStream()
        val errBaos = ByteArrayOutputStream()
        System.setOut(PrintStream(outBaos))
        System.setErr(PrintStream(errBaos))
        try {
            val cmd = Lua()
            cmd.main(args.toList().toTypedArray())
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
        return outBaos.toString().trim() to errBaos.toString().trim()
    }

    @Test
    fun testInlineChunkReturnNumber() {
        val fs = FakeFileSystem()
        val (out, err) = runLua(fs, "-e", "return 42")
        assertEquals("42", out)
        assertTrue(err.isEmpty(), "Expected no stderr: $err")
    }

    @Test
    fun testScriptArgs() {
        val fs = FakeFileSystem()
        fs.write("script.lua".toPath()) { writeUtf8("return arg[2]") }
        val (out, err) = runLua(fs, "script.lua", "first", "second")
        assertEquals("second", out)
        assertTrue(err.isEmpty(), "Expected no stderr: $err")
    }

    @Test
    fun testPreloadLibrarySetsGlobal() {
        val fs = FakeFileSystem()
        // Module that defines global table and value
        fs.write("mylib.lua".toPath()) { writeUtf8("mylib = {}; mylib.val = 99") }
        val (out, err) = runLua(fs, "-l", "mylib", "-e", "return mylib.val")
        assertEquals("99", out)
        assertTrue(err.isEmpty(), "Expected no stderr: $err")
    }

    @Test
    fun testMissingScriptShowsError() {
        val fs = FakeFileSystem()
        val (out, err) = runLua(fs, "nosuch.lua")
        assertTrue(out.isEmpty())
        assertTrue(err.startsWith("lua: cannot open nosuch.lua"), "Unexpected stderr: $err")
    }
}
