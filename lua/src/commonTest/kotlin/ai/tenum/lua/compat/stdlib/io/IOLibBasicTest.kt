package ai.tenum.lua.compat.stdlib.io

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNil
import kotlin.test.Test
import kotlin.test.assertNotEquals

/**
 * Test basic IO library functionality, especially io.stdin/stdout availability.
 */
class IOLibBasicTest : LuaCompatTestBase() {
    @Test
    fun testIOStdinExists() {
        // io.stdin should be accessible (even if mocked for testing)
        val result =
            execute(
                """
            return io.stdin
        """,
            )

        assertNotEquals(LuaNil, result, "io.stdin should not be nil")
    }

    @Test
    fun testIOStdoutExists() {
        // io.stdout should be accessible (even if mocked for testing)
        val result =
            execute(
                """
            return io.stdout
        """,
            )

        assertNotEquals(LuaNil, result, "io.stdout should not be nil")
    }

    @Test
    fun testIOStderrExists() {
        // io.stderr should be accessible (even if mocked for testing)
        val result =
            execute(
                """
            return io.stderr
        """,
            )

        assertNotEquals(LuaNil, result, "io.stderr should not be nil")
    }

    @Test
    fun testStringFormatWithIOStdin() {
        // This is the failing test from strings.lua line 171
        execute(
            """
            local result = string.format("%p", io.stdin)
            assert(result ~= "null", "string.format with io.stdin should not return 'null'")
        """,
        )
    }
}
