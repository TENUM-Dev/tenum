package ai.tenum.lua.compat.core

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * PHASE 2.3: Control Flow - Goto and Labels
 *
 * Tests goto statements and label declarations.
 * Based on: goto.lua
 *
 * Coverage:
 * - Label declarations (::label::)
 * - goto statements
 * - Forward jumps
 * - Backward jumps
 * - Scope restrictions
 * - Invalid goto scenarios
 */
class GotoCompatTest : LuaCompatTestBase() {
    @Test
    fun testSimpleGoto() =
        runTest {
            val code =
                """
                local x = 1
                goto skip
                x = 100
                ::skip::
                return x
                """.trimIndent()
            assertLuaNumber(code, 1.0)
        }

    @Test
    fun testForwardGoto() =
        runTest {
            val code =
                """
                local x = 0
                goto forward
                x = 50
                ::forward::
                x = x + 10
                return x
                """.trimIndent()
            assertLuaNumber(code, 10.0)
        }

    @Test
    fun testBackwardGoto() =
        runTest {
            val code =
                """
                local x = 0
                ::loop::
                x = x + 1
                if x < 5 then
                    goto loop
                end
                return x
                """.trimIndent()
            assertLuaNumber(code, 5.0)
        }

    @Test
    fun testGotoSkipCode() =
        runTest {
            val code =
                """
                local x = 1
                local y = 2
                goto skip
                x = 100
                y = 200
                ::skip::
                return x + y
                """.trimIndent()
            assertLuaNumber(code, 3.0)
        }

    @Test
    fun testGotoIntoScopeError() =
        runTest {
            // Test that goto to label inside a deeper scope is an error
            // The label is not visible from outside the block
            val code =
                """
                goto skip
                do
                    local x = 1
                    ::skip::
                end
                """.trimIndent()

            try {
                execute(code)
                error("Expected IllegalStateException for goto to invisible label")
            } catch (e: IllegalStateException) {
                // Expected - label is inside block and not visible from goto location
                assertTrue(
                    e.message?.contains("no visible label") == true,
                    "Expected 'no visible label' error, got: ${e.message}",
                )
            }
        }

    @Test
    fun testGotoOutOfScope() =
        runTest {
            val code =
                """
                local x = 0
                do
                    local y = 10
                    x = y
                    goto outside
                end
                x = 100
                ::outside::
                return x
                """.trimIndent()
            assertLuaNumber(code, 10.0)
        }

    @Test
    fun testGotoInLoop() =
        runTest {
            val code =
                """
                local sum = 0
                for i = 1, 10 do
                    if i > 5 then
                        goto done
                    end
                    sum = sum + i
                end
                ::done::
                return sum
                """.trimIndent()
            assertLuaNumber(code, 15.0) // 1+2+3+4+5 = 15
        }

    @Test
    fun testGotoContinuePattern() =
        runTest {
            val code =
                """
                local sum = 0
                local i = 0
                ::continue::
                i = i + 1
                if i > 10 then
                    goto done
                end
                if i % 2 == 0 then
                    goto continue
                end
                sum = sum + i
                goto continue
                ::done::
                return sum
                """.trimIndent()
            assertLuaNumber(code, 25.0) // 1+3+5+7+9 = 25
        }

    @Test
    fun testMultipleLabels() =
        runTest {
            val code =
                """
                local x = 0
                goto l2
                ::l1::
                x = x + 1
                goto l3
                ::l2::
                x = x + 10
                goto l1
                ::l3::
                return x
                """.trimIndent()
            assertLuaNumber(code, 11.0) // 0 + 10 + 1 = 11
        }

    @Test
    fun testDuplicateLabelError() =
        runTest {
            // Test that duplicate labels cause error
            val code =
                """
                ::label::
                local x = 1
                ::label::
                """.trimIndent()

            try {
                execute(code)
                error("Expected IllegalStateException for duplicate label")
            } catch (e: IllegalStateException) {
                // Expected - duplicate label should fail (Lua 5.4 format)
                assertTrue(e.message?.contains("already defined") == true)
            }
        }

    @Test
    fun testSameLabelInDifferentBlocks() =
        runTest {
            // Lua allows same label name in different blocks (labels are block-scoped)
            val code =
                """
                local x = 0
                do
                    ::doagain::
                    x = x + 1
                end
                do
                    ::doagain::
                    x = x + 10
                end
                return x
                """.trimIndent()
            assertLuaNumber(code, 11.0)
        }

    @Test
    fun testSameLabelInNestedBlocksShouldError() =
        runTest {
            // Lua does NOT allow label shadowing - same label name in nested blocks is an error
            val code =
                """
                local x = 0
                do
                    ::label::
                    x = x + 1
                    do
                        ::label::
                        x = x + 10
                    end
                end
                return x
                """.trimIndent()
            assertFailsWith<IllegalStateException> {
                execute(code)
            }
        }

    @Test
    fun testSameLabelInLoopIterations() =
        runTest {
            // Same label name can be used across loop iterations (labels removed each iteration)
            val code =
                """
                local x = 0
                for i = 1, 3 do
                    ::doagain::
                    x = x + i
                end
                return x
                """.trimIndent()
            assertLuaNumber(code, 6.0) // 1 + 2 + 3
        }

    @Test
    fun testBackwardGotoResetsLocal() =
        runTest {
            // Test from goto.lua line 115-127: bug in 5.2 -> 5.3.2
            // When we jump backward to ::L1::, local y should be reset to nil
            // This tests that CLOSE is emitted before backward goto
            val code =
                """
                local x
                ::L1::
                local y
                assert(y == nil)
                y = true
                if x == nil then
                    x = 1
                    goto L1
                else
                    x = x + 1
                end
                assert(x == 2 and y == true)
                return x
                """.trimIndent()
            assertLuaNumber(code, 2.0)
        }

    @Test
    fun testLabelBeforeLocalEmitsLoadnil() =
        runTest {
            // FOCUSED TEST: Label immediately before local should emit LOADNIL at label PC
            // This is the minimal reproduction of the backward goto bug
            val code =
                """
                ::L1::
                local y
                y = 42
                return y
                """.trimIndent()
            assertLuaNumber(code, 42.0)
        }

    @Test
    fun testBackwardGotoToLabelBeforeLocal() =
        runTest {
            // FOCUSED TEST: Backward goto should re-initialize local to nil
            val code =
                """
                local x = 0
                ::L1::
                local y
                if y ~= nil then error("y should be nil") end
                y = true
                x = x + 1
                if x < 2 then goto L1 end
                return x
                """.trimIndent()
            assertLuaNumber(code, 2.0)
        }
}
