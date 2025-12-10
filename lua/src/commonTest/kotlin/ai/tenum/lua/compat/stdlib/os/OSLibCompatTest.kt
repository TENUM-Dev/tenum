package ai.tenum.lua.compat.stdlib.os

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.LuaVmImpl
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OSLibCompatTest : LuaCompatTestBase() {
    private fun createVmWithFileSystem(): Pair<LuaVmImpl, FakeFileSystem> {
        val fs = FakeFileSystem()
        val vm = LuaVmImpl(fileSystem = fs)
        return Pair(vm, fs)
    }

    // ============================================
    // Time Functions
    // ============================================

    @Test
    fun testOsClock() {
        val result =
            execute(
                """
            local t1 = os.clock()
            local sum = 0
            for i = 1, 1000 do sum = sum + i end
            local t2 = os.clock()
            return t2 >= t1
        """,
            )

        assertTrue(result is LuaBoolean && result.value, "t2 should be >= t1")
    }

    @Test
    fun testOsTime() {
        val result = execute("return os.time()")
        assertTrue(result is LuaNumber, "os.time() should return a number")
        assertTrue(result.toDouble() > 0, "timestamp should be positive")
    }

    @Test
    fun testOsTimeWithTable() {
        val result =
            execute(
                """
            local t = os.time({year=2025, month=11, day=13, hour=12, min=30, sec=45})
            return t > 0
        """,
            )
        assertTrue(result is LuaBoolean && result.value)
    }

    @Test
    fun testOsDate() {
        val result =
            execute(
                """
            return os.date("%Y-%m-%d")
        """,
            )
        assertTrue(result is LuaString)
        // Format should be YYYY-MM-DD
        assertTrue(result.value.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun testOsDateWithTimestamp() {
        val result =
            execute(
                """
            local t = os.time({year=2025, month=11, day=13, hour=0, min=0, sec=0})
            return os.date("%Y-%m-%d", t)
        """,
            )
        assertTrue(result is LuaString)
        assertEquals("2025-11-13", result.value)
    }

    @Test
    fun testOsDateTable() {
        execute(
            """
            local t = os.date("*t", os.time({year=2025, month=11, day=13, hour=12, min=30, sec=45}))
            assert(t.year == 2025, "year should be 2025")
            assert(t.month == 11, "month should be 11")
            assert(t.day == 13, "day should be 13")
            assert(t.hour == 12, "hour should be 12")
            assert(t.min == 30, "min should be 30")
            assert(t.sec == 45, "sec should be 45")
        """,
        )
    }

    @Test
    fun testOsDifftime() {
        val result =
            execute(
                """
            local t1 = os.time({year=2025, month=11, day=13, hour=12, min=0, sec=0})
            local t2 = os.time({year=2025, month=11, day=13, hour=13, min=0, sec=0})
            return os.difftime(t2, t1)
        """,
            )
        assertTrue(result is LuaNumber)
        assertEquals(3600.0, result.value.toDouble()) // 1 hour = 3600 seconds
    }

    // ============================================
    // Environment Variables
    // ============================================

    @Test
    fun testOsGetenv() {
        val result =
            execute(
                """
            return os.getenv("PATH")
        """,
            )
        // PATH should exist on all platforms
        assertTrue(result is LuaString || result is LuaNil)
    }

    @Test
    fun testOsGetenvNonexistent() {
        val result =
            execute(
                """
            return os.getenv("NONEXISTENT_VAR_12345")
        """,
            )
        assertTrue(result is LuaNil)
    }

    @Test
    fun testOsPlatform() {
        val result =
            execute(
                """
            return os.platform()
        """,
            )
        assertTrue(result is LuaString, "os.platform() should return a string")
        // Platform should be something like "JVM", "JS", "Native", etc.
        assertTrue((result as LuaString).value.isNotEmpty(), "Platform name should not be empty")
    }

    @Test
    fun testOsOs() {
        val result =
            execute(
                """
            return os.os()
        """,
            )
        assertTrue(result is LuaString, "os.os() should return a string")
        // OS should be something like "Windows", "Linux", "macOS", etc.
        assertTrue((result as LuaString).value.isNotEmpty(), "OS name should not be empty")
    }

    // ============================================
    // File System Operations
    // ============================================

    @Test
    fun testOsRemove() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test_remove.txt".toPath()) {
                writeUtf8("test")
            }

            vm.execute(
                """
            local success = os.remove("test_remove.txt")
            assert(success == true, "os.remove should succeed")
        """,
            )
        }

    @Test
    fun testOsRemoveNonexistent() =
        runTest {
            val (vm, _) = createVmWithFileSystem()

            vm.execute(
                """
            local success, err = os.remove("nonexistent_file_12345.txt")
            assert(success == nil or success == false, "Should fail")
            assert(type(err) == "string", "Should return error message")
        """,
            )
        }

    @Test
    fun testOsRename() =
        runTest {
            val (vm, fs) = createVmWithFileSystem()

            fs.write("test_old.txt".toPath()) {
                writeUtf8("test")
            }

            vm.execute(
                """
            local f = io.open("test_old.txt", "r")
            local content = f:read("*a")
            f:close()
            assert(content == "test", "content before rename")
            
            local success = os.rename("test_old.txt", "test_new.txt")
            assert(success == true, "rename should succeed")
            
            local f2 = io.open("test_new.txt", "r")
            local content2 = f2:read("*a")
            f2:close()
            assert(content2 == "test", "content should be preserved")
            os.remove("test_new.txt")
        """,
            )
        }

    @Test
    fun testOsRenameNonexistent() =
        runTest {
            val (vm, _) = createVmWithFileSystem()

            vm.execute(
                """
            local success, err = os.rename("nonexistent_12345.txt", "new.txt")
            assert(success == nil or success == false, "Should fail")
            assert(type(err) == "string", "Should return error message")
        """,
            )
        }

    @Test
    fun testOsTmpname() {
        execute(
            """
            local tmp1 = os.tmpname()
            local tmp2 = os.tmpname()
            assert(type(tmp1) == "string", "tmpname should return string")
            assert(type(tmp2) == "string", "tmpname should return string")
            assert(tmp1 ~= tmp2, "Each call should return unique name")
        """,
        )
    }

    // ============================================
    // System Execution (Platform-specific)
    // ============================================

    @Test
    fun testOsExecute() {
        // Simple command that should work on all platforms
        val result =
            execute(
                """
            local success = os.execute("")
            return success
        """,
            )
        // Empty command should return exit status or nil
        assertTrue(result is LuaNumber || result is LuaBoolean || result is LuaNil)
    }

    @Test
    fun testOsExecuteWithCommand() {
        // This test is platform-specific and may not work on all platforms
        // Just verify it returns something reasonable
        val result =
            execute(
                """
            local success, exitType, exitCode = os.execute("echo test")
            return success
        """,
            )
        // Should return boolean or number
        assertTrue(result is LuaBoolean || result is LuaNumber || result is LuaNil)
    }

    // ============================================
    // Exit (tested carefully to not terminate test runner)
    // ============================================

    @Test
    fun testOsExitExists() {
        // Just verify os.exit exists and is callable
        // We can't actually call it or it would terminate the test runner
        val result =
            execute(
                """
            return type(os.exit)
        """,
            )
        assertTrue(result is LuaString)
        assertEquals("function", result.value)
    }

    // ============================================
    // Integration Tests
    // ============================================

    @Test
    fun testOsDateFormats() {
        execute(
            """
            local t = os.time({year=2025, month=1, day=1, hour=0, min=0, sec=0})
            assert(os.date("%Y", t) == "2025", "Year format")
            assert(os.date("%m", t) == "01", "Month format")
            assert(os.date("%d", t) == "01", "Day format")
            assert(os.date("%H", t) == "00", "Hour format")
            assert(os.date("%M", t) == "00", "Minute format")
            assert(os.date("%S", t) == "00", "Second format")
        """,
        )
    }

    @Test
    fun testOsTimeRoundtrip() {
        val result =
            execute(
                """
            local original = {year=2025, month=11, day=13, hour=12, min=30, sec=45}
            local timestamp = os.time(original)
            local reconstructed = os.date("*t", timestamp)
            return reconstructed.year == original.year and
                   reconstructed.month == original.month and
                   reconstructed.day == original.day and
                   reconstructed.hour == original.hour and
                   reconstructed.min == original.min and
                   reconstructed.sec == original.sec
        """,
            )
        assertTrue(result is LuaBoolean && result.value, "Time roundtrip should preserve values")
    }
}
