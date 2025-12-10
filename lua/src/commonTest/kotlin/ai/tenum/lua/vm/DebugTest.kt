package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test to demonstrate VM debug logging feature
 */
class DebugTest {
    @Test
    fun testDebugLogging() {
        val vm = LuaVmImpl()

        // Collect debug messages
        val debugMessages = mutableListOf<String>()
        vm.enableDebug { message -> debugMessages.add(message) }

        // Execute simple code
        val code =
            """
            local x = 5
            local y = 3
            return x + y
            """.trimIndent()

        val result = vm.execute(code)

        // Verify result
        assertEquals(LuaNumber.of(8.0), result)

        // Verify debug messages were captured
        assertTrue(debugMessages.isNotEmpty(), "Debug messages should be captured")
        assertTrue(debugMessages.any { it.contains("Executing Lua chunk") }, "Should have execution start message")
        assertTrue(debugMessages.any { it.contains("Lexer:") }, "Should have lexer message")
        assertTrue(debugMessages.any { it.contains("Parser:") }, "Should have parser message")
        assertTrue(debugMessages.any { it.contains("Compiler:") }, "Should have compiler message")
        assertTrue(debugMessages.any { it.contains("LOADI") || it.contains("LOADK") }, "Should have instruction traces")

        // Print messages for manual verification
        println("\n=== Debug Messages ===")
        debugMessages.forEach { println(it) }
    }

    @Test
    fun testDebugCanBeDisabled() {
        val vm = LuaVmImpl()

        // Enable then disable
        vm.enableDebug()
        vm.disableDebug()

        // Execute code - should not produce debug output
        val result = vm.execute("return 42")

        assertEquals(LuaNumber.of(42.0), result)
    }
}
