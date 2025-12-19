package ai.tenum.lua.compat.advanced.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Regression test for db.lua:322 - line hooks not firing in do blocks
 */
class Db322RegressionTest : LuaCompatTestBase() {
    @Test
    fun testLineHooksInDoBlock() {
        val code =
            """
            do
              local count = 0
              local function f()
                count = count + 1
              end
              
              debug.sethook(f, "l")
              local a = 0
              _ENV.a = a
              a = 1
              debug.sethook()
              
              assert(count == 4, "Expected count=4, got " .. count)
            end
            """.trimIndent()

        execute(code)
    }
}
