package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for backward goto with <close> variables.
 * Ensures __close metamethods are called when jumping out of scope via backward goto.
 */
class GotoBackwardCloseTest : LuaCompatTestBase() {
    @Test
    fun testBackwardGotoClosesVariable() =
        runTest {
            // From goto.lua:255
            val result =
                execute(
                    """
                local X
                goto L1

                ::L2:: goto L3

                ::L1:: do
                    local a <close> = setmetatable({}, {__close = function () X = true end})
                    assert(X == nil)
                    if a then goto L2 end   -- jumping back out of scope of 'a'
                end

                ::L3:: assert(X == true)   -- checks that 'a' was correctly closed
                return X
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(true, result.value, "__close should be called when backward goto exits scope")
        }

    @Test
    fun testSimpleBackwardGotoWithClose() =
        runTest {
            val result =
                execute(
                    """
                local closed = false
                ::loop::
                do
                    local obj <close> = setmetatable({}, {
                        __close = function() closed = true end
                    })
                    if closed then return true end  -- second iteration
                    goto loop  -- jump back, should close obj
                end
            """,
                )
            assertTrue(result is LuaBoolean)
            assertEquals(true, result.value, "__close should be called when backward goto exits scope")
        }

    @Test
    fun testBackwardGotoMultipleCloseVars() =
        runTest {
            val result =
                execute(
                    """
                local order = ""
                local count = 0
                ::start::
                if count > 0 then return order end
                count = count + 1
                do
                    local a <close> = setmetatable({}, {
                        __close = function() order = order .. "a" end
                    })
                    local b <close> = setmetatable({}, {
                        __close = function() order = order .. "b" end
                    })
                    goto start  -- should close b, then a
                end
            """,
                )
            assertTrue(result is ai.tenum.lua.runtime.LuaString)
            assertEquals("ba", result.value, "Variables should close in reverse order (LIFO)")
        }
}
