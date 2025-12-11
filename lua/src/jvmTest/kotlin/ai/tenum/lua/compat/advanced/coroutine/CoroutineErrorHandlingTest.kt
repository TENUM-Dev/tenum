package ai.tenum.lua.compat.advanced.coroutine

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Test coroutine error handling (coroutine.lua lines 17-18)
 * These functions should throw errors when given invalid arguments
 */
class CoroutineErrorHandlingTest : LuaCompatTestBase() {
    @Test
    fun testResumeWithInvalidArgument() {
        // coroutine.lua line 17: assert(not pcall(coroutine.resume, 0))
        // Should error when given non-coroutine
        assertLuaBoolean(
            """
            local success = pcall(coroutine.resume, 0)
            return success
            """.trimIndent(),
            false,
        )
    }

    @Test
    fun testStatusWithInvalidArgument() {
        // coroutine.lua line 18: assert(not pcall(coroutine.status, 0))
        // Should error when given non-coroutine
        assertLuaBoolean(
            """
            local success = pcall(coroutine.status, 0)
            return success
            """.trimIndent(),
            false,
        )
    }

    @Test
    fun testStatusErrorMessage() {
        // Should give descriptive error message
        assertLuaTrue(
            """
            local success, err = pcall(coroutine.status, 0)
            return not success and type(err) == "string"
            """.trimIndent(),
        )
    }

    @Test
    fun testResumeErrorMessage() {
        // Should give descriptive error message
        assertLuaTrue(
            """
            local success, err = pcall(coroutine.resume, 0)
            return not success and type(err) == "string"
            """.trimIndent(),
        )
    }

    @Test
    fun testCoroutineStackOverflow() {
        // errors.lua lines 362-369: Test that recursive coroutine resume causes stack overflow error
        assertLuaTrue(
            """
            local function f(n)
                local c = coroutine.create(f)
                local a, b = coroutine.resume(c)
                return b
            end
            local result = f()
            return type(result) == "string" and string.find(result, "C stack overflow") ~= nil
            """.trimIndent(),
        )
    }

    @Test
    fun testRecursiveFunctionStackOverflow() {
        // errors.lua lines 487-505: Test that recursive function calls cause stack overflow error
        assertLuaTrue(
            """
            local C = 0
            local function auxy() C = C + 1; auxy() end
            local success, msg = pcall(auxy)
            return not success and type(msg) == "string" and string.find(msg, "stack overflow") ~= nil
            """.trimIndent(),
        )
    }
}