package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test
import kotlin.test.assertTrue

class TracebackFinalCFrameTest : LuaCompatTestBase() {
    @Test
    fun testTracebackIncludesFinalCFrame() {
        val result =
            execute(
                """
                local tb = debug.traceback("msg", 0)
                print(tb)
                return tb
                """.trimIndent(),
            )

        val traceback = result.toString()

        // Should include the final [C]: in ? entry
        assertTrue(
            traceback.contains("[C]: in ?"),
            "Traceback should include final '[C]: in ?' entry\nGot: $traceback",
        )
    }

    @Test
    fun testTracebackWithLevel1IncludesFinalCFrame() {
        val result =
            execute(
                """
                local function f()
                    return debug.traceback("msg", 1)
                end
                local tb = f()
                print(tb)
                return tb
                """.trimIndent(),
            )

        val traceback = result.toString()

        // Should include the final [C]: in ? entry even with level 1
        assertTrue(
            traceback.contains("[C]: in ?"),
            "Traceback with level 1 should include final '[C]: in ?' entry\nGot: $traceback",
        )
    }

    @Test
    fun testTracebackInCoroutineDoesNotIncludeFinalCFrame() {
        val result =
            execute(
                """
                local function checkInCoroutine()
                    local tb = debug.traceback("msg", 0)
                    print(tb)
                    -- Coroutine tracebacks should NOT include final [C]: in ?
                    if string.find(tb, "%[C%]: in %?") then
                        error("Coroutine traceback should NOT include [C]: in ?")
                    end
                    return "ok"
                end
                
                return coroutine.wrap(checkInCoroutine)()
                """.trimIndent(),
            )

        assertTrue(
            result.toString() == "ok",
            "Coroutine traceback should NOT include final '[C]: in ?' entry",
        )
    }

    @Test
    fun testDeepTracebackInCoroutineLineCount() {
        // This is the EXACT test from db.lua lines 945-956
        val result =
            execute(
                """
                local function countlines (s)
                  return select(2, string.gsub(s, "\n", ""))
                end

                local function deep (lvl, n)
                  if lvl == 0 then
                    return (debug.traceback("message", n))
                  else
                    return (deep(lvl-1, n))
                  end
                end

                local function checkdeep (total, start)
                  local s = deep(total, start)
                  local rest = string.match(s, "^message\nstack traceback:\n(.*)$")
                  local cl = countlines(rest)
                  -- at most 10 lines in first part, 11 in second, plus '...'
                  assert(cl <= 10 + 11 + 1)
                  local brk = string.find(rest, "%.%.%.\t%(skip")
                  if brk then   -- does message have '...'?
                    local rest1 = string.sub(rest, 1, brk)
                    local rest2 = string.sub(rest, brk, #rest)
                    assert(countlines(rest1) == 10 and countlines(rest2) == 11)
                  else
                    assert(cl == total - start + 2)
                  end
                end

                -- Run the exact loop from db.lua
                for d = 1, 51, 10 do
                  for l = 1, d do
                    -- use coroutines to ensure complete control of the stack
                    coroutine.wrap(checkdeep)(d, l)
                  end
                end
                
                return "ok"
                """.trimIndent(),
            )

        assertTrue(result.toString() == "ok", "DB.lua traceback test failed")
    }
}
