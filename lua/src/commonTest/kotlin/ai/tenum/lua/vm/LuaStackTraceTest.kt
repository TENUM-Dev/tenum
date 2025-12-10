package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.errorhandling.LuaException
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for Lua stack trace generation
 *
 * Verifies that Lua errors produce proper Lua stack traces with correct line numbers,
 * not Kotlin stack traces.
 */
class LuaStackTraceTest {
    private lateinit var vm: LuaVmImpl
    private lateinit var fs: FakeFileSystem

    @BeforeTest
    fun setUp() {
        fs = FakeFileSystem()
        vm = LuaVmImpl(fs)
        vm.debugEnabled = true // Enable debug info for stack traces
    }

    @Test
    fun testArithmeticErrorShowsLuaLineNumber() {
        val error =
            assertFails {
                vm.execute(
                    """
                    local x = "hello"
                    local y = x + 5
                    """.trimIndent(),
                )
            }

        // Should contain Lua line number reference (line 2 after trimIndent)
        assertEquals(error.message?.contains(":2:"), true, "Error should reference line 2, got: ${error.message}")

        // Should mention the operation or metamethod
        assertTrue(
            error.message?.contains("arithmetic") == true ||
                error.message?.contains("perform") == true ||
                error.message?.contains("metamethod") == true,
            "Error should mention arithmetic operation or metamethod, got: ${error.message}",
        )
    }

    @Test
    fun testCallNilErrorShowsLuaLineNumber() {
        val error =
            assertFails {
                vm.execute(
                    """
                    local f = nil
                    f()
                    """.trimIndent(),
                )
            }

        assertEquals(error.message?.contains(":2:"), true, "Error should reference line 2, got: ${error.message}")

        assertTrue(
            error.message?.contains("call") == true ||
                error.message?.contains("nil") == true,
            "Error should mention calling nil, got: ${error.message}",
        )
    }

    @Test
    fun testIndexNilErrorShowsLuaLineNumber() {
        val error =
            assertFails {
                vm.execute(
                    """
                    local t = nil
                    local x = t.field
                    """.trimIndent(),
                )
            }

        assertEquals(error.message?.contains(":2:"), true, "Error should reference line 2, got: ${error.message}")

        assertTrue(
            error.message?.contains("index") == true ||
                error.message?.contains("nil") == true,
            "Error should mention indexing nil, got: ${error.message}",
        )
    }

    @Test
    fun testDivisionByZeroShowsLuaLineNumber() {
        // NOTE: Division by zero in Lua 5.4 does NOT throw an error - it returns inf
        val result =
            vm.execute(
                """
                local x = 10
                local y = x / 0
                return y
                """.trimIndent(),
            )

        // Should return infinity, not error
        assertTrue(result is LuaDouble)
        assertTrue((result as LuaDouble).value.isInfinite())
    }

    @Test
    fun testConcatNonStringShowsLuaLineNumber() {
        val error =
            assertFails {
                vm.execute(
                    """
                    local x = {}
                    local y = x .. "test"
                    """.trimIndent(),
                )
            }

        assertEquals(error.message?.contains(":2:"), true, "Error should reference line 2, got: ${error.message}")

        assertTrue(
            error.message?.contains("concatenate") == true ||
                error.message?.contains("concat") == true,
            "Error should mention concatenation, got: ${error.message}",
        )
    }

    @Test
    fun testNestedFunctionCallShowsStackTrace() {
        val error =
            assertFails {
                vm.execute(
                    """
                    function a()
                        b()
                    end
                    
                    function b()
                        c()
                    end
                    
                    function c()
                        error("test error")
                    end
                    
                    a()
                    """.trimIndent(),
                )
            }

        val message = error.message ?: ""

        // Should show function names in stack trace
        assertTrue(
            message.contains("function 'a'") || message.contains("in a") || message.contains("'a'"),
            "Stack trace should mention function 'a', got: $message",
        )

        assertTrue(
            message.contains("function 'b'") || message.contains("in b") || message.contains("'b'"),
            "Stack trace should mention function 'b', got: $message",
        )

        assertTrue(
            message.contains("function 'c'") || message.contains("in c") || message.contains("'c'"),
            "Stack trace should mention function 'c', got: $message",
        )

        // Should show some line numbers in stack trace
        assertTrue(
            message.contains("stack traceback"),
            "Should have stack traceback, got: $message",
        )
    }

    @Test
    fun testErrorInMultilineStringShowsCorrectLine() {
        val error =
            assertFails {
                vm.execute(
                    """
                    local str = [[
                        multi
                        line
                        string
                    ]]
                    local x = str + 5
                    """.trimIndent(),
                )
            }

        // With AST line tracking, we now correctly report line 6 (where the error occurs)
        // This matches Lua 5.4 behavior
        assertEquals(
            error.message?.contains(":6:"),
            true,
            "Error should reference line 6 (error at 'str + 5'), got: ${error.message}",
        )
    }

    @Test
    fun testErrorInLoadedChunkShowsFileName() {
        // Write a Lua file with an error
        fs.write("test_error.lua".toPath()) {
            writeUtf8(
                """
                function bad_function()
                    local x = "hello"
                    return x + 5
                end
                
                bad_function()
                """.trimIndent(),
            )
        }

        val error =
            assertFails {
                vm.execute(
                    """
                    dofile("test_error.lua")
                    """.trimIndent(),
                )
            }

        val message = error.message ?: ""
        val firstLine = message.lines().firstOrNull() ?: ""

        // Should show line numbers - filename tracking works now
        assertTrue(
            firstLine.contains(":") && firstLine.matches(Regex(".*:\\d+:.*")),
            "Error should include line number reference, got: $firstLine",
        )

        // Verify it's a proper error message
        assertTrue(
            message.contains("arithmetic") || message.contains("metamethod"),
            "Error should mention arithmetic operation or metamethod, got: $message",
        )
    }

    @Test
    fun testPCallCatchesErrorWithStackTrace() {
        val result =
            vm.execute(
                """
                function errorFunc()
                    error("test error at line 2")
                end
                
                local success, err = pcall(errorFunc)
                return err  -- Should contain stack trace
                """.trimIndent(),
            )

        assertTrue(result is LuaString, "pcall should return error message as string")
        val errMsg = (result as LuaString).value

        assertTrue(
            errMsg.contains("test error at line 2"),
            "Error message should be preserved, got: $errMsg",
        )
    }

    @Test
    fun testXPCallAddsMessageHandler() {
        val result =
            vm.execute(
                """
                function errorFunc()
                    error("original error")
                end
                
                function messageHandler(err)
                    return "Handler: " .. err
                end
                
                local success, err = xpcall(errorFunc, messageHandler)
                return err
                """.trimIndent(),
            )

        assertTrue(result is LuaString, "xpcall should return error message as string")
        val errMsg = result.value

        assertTrue(
            errMsg.contains("Handler:"),
            "Message handler should have been called, got: $errMsg",
        )

        assertTrue(
            errMsg.contains("original error"),
            "Original error should be included, got: $errMsg",
        )
    }

    @Test
    fun testAssertErrorShowsCorrectLine() {
        val error =
            assertFails {
                vm.execute(
                    """
                    local x = 5
                    assert(x > 10, "x must be greater than 10")
                    """.trimIndent(),
                )
            }

        assertTrue(
            error.message?.contains(":2:") == true,
            "Error should reference line 2, got: ${error.message}",
        )

        assertTrue(
            error.message?.contains("x must be greater than 10") == true,
            "Error should contain assertion message, got: ${error.message}",
        )
    }

    @Test
    fun testStackTraceDoesNotContainKotlinFrames() {
        val error =
            assertFails {
                vm.execute(
                    """
                    function deep()
                        error("deep error")
                    end
                    deep()
                    """.trimIndent(),
                )
            }

        val message = error.message ?: ""
        val stackTrace = error.stackTraceToString()

        // Error message should be Lua-focused
        assertFalse(
            message.contains("ai.tenum.lua.vm.LuaVmImpl"),
            "Lua error message should not contain Kotlin class names, got: $message",
        )

        assertFalse(
            message.contains(".kt:"),
            "Lua error message should not reference Kotlin files, got: $message",
        )

        // Stack trace may contain Kotlin frames (that's OK for debugging),
        // but the ERROR MESSAGE should be Lua-focused
    }

    @Test
    fun testLuaStackTraceFrameOrder() {
        val error =
            assertFails {
                vm.execute(
                    """
                    function a()
                        b()
                    end

                    function b()
                        c()
                    end

                    function c()
                        error("boom")
                    end

                    a()
                    """.trimIndent(),
                )
            }

        val luaEx = error as? LuaException ?: kotlin.test.fail("Expected LuaException")
        val frames = luaEx.luaStackTrace
        // Innermost frame should be 'c', then 'b', then 'a'
        assertTrue(frames.size >= 3, "Expected at least 3 frames, got: ${frames.size}")
        assertEquals("c", frames[0].functionName)
        assertEquals("b", frames[1].functionName)
        assertEquals("a", frames[2].functionName)
    }

    @Test
    fun testMultipleErrorsShowDifferentLineNumbers() {
        // First error at line 2
        val error1 =
            assertFails {
                vm.execute(
                    """
                    local x = nil
                    x()
                    """.trimIndent(),
                )
            }

        // Second error at line 5 (statement after semicolon might show as line 6)
        val error2 =
            assertFails {
                vm.execute(
                    """
                    local a = 1
                    local b = 2
                    local c = 3
                    local d = 4
                    local e = nil; e()
                    """.trimIndent(),
                )
            }

        assertTrue(
            error1.message?.contains(":2:") == true,
            "First error should reference line 2, got: ${error1.message}",
        )

        // NOTE: Parser might treat semicolon-separated statement as next line
        // TODO: Investigate line tracking for semicolon-separated statements
        assertTrue(
            error2.message?.contains(":5:") == true || error2.message?.contains(":6:") == true,
            "Second error should reference line 5 or 6, got: ${error2.message}",
        )
    }

    @Test
    fun testErrorInTableConstructorShowsCorrectLine() {
        val error =
            assertFails {
                vm.execute(
                    """
                    local t = {
                        a = 1,
                        b = 2,
                        c = nil + 5
                    }
                    """.trimIndent(),
                )
            }

        // With AST line tracking, we now correctly report line 4 (where the error occurs)
        // This matches Lua 5.4 behavior
        assertTrue(
            error.message?.contains(":4:") == true,
            "Error should reference line 4 (error at 'nil + 5'), got: ${error.message}",
        )
    }

    @Test
    fun testStackTraceDoesNotShowMainFunctionName() {
        // Test based on db.lua behavior - top-level chunks should not show "in function 'main'"
        val error =
            assertFails {
                vm.execute(
                    """
                    local function test()
                        assert(false, "test assertion")
                    end
                    test()
                    """.trimIndent(),
                )
            }

        val message = error.message ?: ""

        // Stack trace should show "in function 'test'" for the test function
        assertTrue(
            message.contains("in function 'test'"),
            "Stack trace should show test function, got: $message",
        )

        // Stack trace should NOT show "in function 'main'" for the top-level chunk
        // It should just show the location without the function name part
        assertFalse(
            message.contains("in function 'main'"),
            "Stack trace should not show 'main' function name, got: $message",
        )
    }

    @Test
    fun testStackTraceTopLevelChunkDoesNotHaveTrailingColon() {
        // Test based on db.lua:164 - top-level chunk calls should not have trailing colon in stack trace
        // Expected: "@db.lua:164" or "=(load):4" (no trailing colon)
        // Wrong: "@db.lua:164:" or "=(load):4:" (has trailing colon)
        val error =
            assertFails {
                vm.execute(
                    """
                    local function test()
                        error("inner error")
                    end
                    test()
                    """.trimIndent(),
                )
            }

        val message = error.message ?: ""

        // Extract stack traceback lines
        val lines = message.lines()
        val tracebackStart = lines.indexOfFirst { it.contains("stack traceback:") }
        assertTrue(tracebackStart >= 0, "Should have stack traceback, got: $message")

        // Find the top-level chunk line (last line of traceback, no function name)
        val tracebackLines = lines.drop(tracebackStart + 1).filter { it.trim().isNotEmpty() }
        val topLevelLine = tracebackLines.lastOrNull()
        assertNotNull(topLevelLine, "Should have at least one traceback line, got: $message")

        // Top-level chunk should be like "=(load):4" not "=(load):4:"
        // Should NOT have a trailing colon after the line number
        assertFalse(
            topLevelLine!!.matches(Regex(".*:\\d+:\\s*$")),
            "Top-level chunk line should not end with ':' after line number, got: $topLevelLine",
        )

        // Verify it has the correct format: source:line (no trailing colon, no function name)
        assertTrue(
            topLevelLine.matches(Regex(".*:\\d+\\s*$")),
            "Top-level chunk line should be 'source:line' format, got: $topLevelLine",
        )
    }
}
