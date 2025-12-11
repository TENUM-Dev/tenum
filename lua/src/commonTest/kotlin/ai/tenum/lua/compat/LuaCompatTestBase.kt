package ai.tenum.lua.compat

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.LuaVmImpl
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Base class for Lua 5.4.8 compatibility tests
 */
abstract class LuaCompatTestBase {
    var fileSystem: FakeFileSystem = FakeFileSystem()
    var vm: LuaVmImpl = LuaVmImpl(fileSystem)

    @BeforeTest
    fun setUp() {
        fileSystem = FakeFileSystem()

        vm = LuaVmImpl(fileSystem)
        // Enable verbose VM debug output for focused test diagnostics
        // vm.enableDebug()
    }

    @AfterTest
    fun tearDown() {
        // Cleanup if needed
    }

    fun runTest(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            throw e
        }
    }

    /**
     * Execute Lua code and return the result
     */
    fun execute(
        luaCode: String,
        source: String? = null,
    ): LuaValue<*> {
        // Format source according to Lua conventions:
        // - Files should be prefixed with @
        // - Special sources with =
        // - String sources have no prefix
        val formattedSource =
            when {
                source == null -> null
                source.startsWith("@") || source.startsWith("=") -> source
                source.contains(".lua") -> "@$source" // File sources get @ prefix
                else -> source
            }

        return if (formattedSource == null) {
            vm.execute(luaCode)
        } else {
            vm.execute(luaCode, formattedSource)
        }
    }

    /**
     * Execute Lua code and assert it returns true
     */
    protected fun assertLuaTrue(
        code: String,
        message: String = "Expected Lua code to return true",
    ) {
        val result = execute(code)
        assertTrue(result is LuaBoolean && result.value, message)
    }

    /**
     * Execute Lua code and assert it returns false
     */
    protected fun assertLuaFalse(
        code: String,
        message: String = "Expected Lua code to return false",
    ) {
        val result = execute(code)
        assertTrue(result is LuaBoolean && !result.value, message)
    }

    /**
     * Execute Lua code and assert it returns nil
     */
    protected fun assertLuaNil(
        code: String,
        message: String = "Expected Lua code to return nil",
    ) {
        val result = execute(code)
        assertTrue(result is LuaNil, message)
    }

    /**
     * Execute Lua code and assert it returns a specific number
     */
    protected fun assertLuaNumber(
        code: String,
        expected: Double,
        message: String = "Number mismatch",
    ) {
        val result = execute(code)
        assertTrue(result is LuaNumber, "Expected number but got ${result::class.simpleName}")
        // Convert both to Double for comparison (handles both LuaLong and LuaDouble)
        assertEquals(expected, result.toDouble(), message)
    }

    /**
     * Execute Lua code and assert it returns a specific number (with tolerance)
     */
    protected fun assertLuaNumber(
        code: String,
        expected: Double,
        tolerance: Double,
        message: String = "Number mismatch",
    ) {
        val result = execute(code)
        assertTrue(result is LuaNumber, "Expected number but got ${result::class.simpleName}")
        val actual = result.toDouble()
        assertTrue(kotlin.math.abs(actual - expected) <= tolerance, "$message: expected $expected ± $tolerance, got $actual")
    }

    /**
     * Execute Lua code and assert it returns a specific number
     */
    protected fun assertLuaNumber(
        value: LuaValue<*>,
        expected: Double,
        message: String = "Number mismatch",
    ) {
        assertTrue(value is LuaNumber, "Expected number but got ${value::class.simpleName}")
        // Convert both to Double for comparison (handles both LuaLong and LuaDouble)
        assertEquals(expected, value.toDouble(), message)
    }

    protected fun assertLuaBoolean(
        code: String,
        expected: Boolean,
        message: String = "Boolean mismatch",
    ) {
        val result = execute(code)
        assertTrue(result is LuaBoolean, "Expected boolean but got ${result::class.simpleName}")
        assertEquals(expected, result.value, message)
    }

    protected fun assertLuaBoolean(
        value: LuaValue<*>,
        expected: Boolean,
        message: String = "Boolean mismatch",
    ) {
        assertTrue(value is LuaBoolean, "Expected boolean but got ${value::class.simpleName}")
        assertEquals(expected, value.value, message)
    }

    /**
     * Execute Lua code and assert it returns a specific string
     */
    protected fun assertLuaString(
        code: String,
        expected: String,
        message: String = "String mismatch",
    ) {
        val result = execute(code)
        assertTrue(result is LuaString, "Expected string but got ${result::class.simpleName}")
        assertEquals(expected, result.value, message)
    }

    protected fun assertLuaString(
        value: LuaValue<*>,
        expected: String,
        message: String = "String mismatch",
    ) {
        assertTrue(value is LuaString, "Expected string but got ${value::class.simpleName}")
        assertEquals(expected, value.value, message)
    }

    /**
     * Assert that executing the code throws an error
     */
    protected fun assertThrowsError(
        code: String,
        message: String? = null,
    ) {
        try {
            execute(code)
            fail("Expected Lua code to throw an error")
        } catch (e: Exception) {
            if (message != null) {
                assertTrue(
                    e.message?.contains(message) == true,
                    "Expected error message to contain '$message' but was '${e.message}'",
                )
            }
        }
    }

    /**
     * Assert that a Kotlin block throws an error with a specific message
     */
    protected fun assertError(
        expectedMessage: String,
        block: () -> Any,
    ) {
        try {
            block()
            fail("Expected error with message containing '$expectedMessage'")
        } catch (e: Exception) {
            assertTrue(
                e.message?.contains(expectedMessage, ignoreCase = true) == true,
                "Expected error message to contain '$expectedMessage', but got: ${e.message}",
            )
        }
    }

    /**
     * Skip a test with a reason
     */
    protected fun skipTest(reason: String) {
        println("SKIPPED: $reason")
        // In real implementation, use proper test skip mechanism
    }
}
