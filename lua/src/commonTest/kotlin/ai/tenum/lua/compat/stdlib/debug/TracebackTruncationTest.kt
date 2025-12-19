package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Focused test for db.lua:946 traceback truncation assertion failure.
 *
 * This issue is caused by the traceback formatter not properly truncating long tracebacks.
 * Lua 5.4 limits tracebacks to 10 first frames + "..." marker + 11 last frames (22 total).
 */
class TracebackTruncationTest : LuaCompatTestBase() {
    @Test
    fun testTracebackTruncationBasic() =
        runTest {
            // Test that long tracebacks are properly truncated
            // Using coroutine.wrap like db.lua to ensure consistent stack handling
            execute(
                """
            local function countlines(s)
                return select(2, string.gsub(s, "\n", ""))
            end
            
            local function deep(lvl, n)
                if lvl == 0 then
                    return (debug.traceback("message", n))
                else
                    return (deep(lvl-1, n))
                end
            end
            
            local function checkdeep(total, start)
                local s = deep(total, start)
                local rest = string.match(s, "^message\nstack traceback:\n(.*)$")
                local cl = countlines(rest)
                assert(cl <= 10 + 11 + 1, "depth " .. total .. ": got " .. cl .. " lines, expected max 22")
                
                local brk = string.find(rest, "%.%.%.\t%(skip")
                if brk then
                    local rest1 = string.sub(rest, 1, brk)
                    local rest2 = string.sub(rest, brk, #rest)
                    assert(countlines(rest1) == 10 and countlines(rest2) == 11,
                           "truncated traceback should have 10 first + 11 last lines")
                else
                    assert(cl == total - start + 2, "non-truncated: expected " .. (total - start + 2) .. ", got " .. cl)
                end
            end
            
            -- Test case 1: depth 11, start 1 - no truncation
            coroutine.wrap(checkdeep)(11, 1)
            
            -- Test case 2: depth 21, start 1 - no truncation (21 frames still <= 22)
            coroutine.wrap(checkdeep)(21, 1)
            
            -- Test case 3: depth 31, start 1 - should truncate
            coroutine.wrap(checkdeep)(31, 1)
        """,
            )
        }

    @Test
    fun testTracebackTruncationInCoroutineWrap() =
        runTest {
            // Test the exact scenario from db.lua:946
            execute(
                """
            local function countlines(s)
                return select(2, string.gsub(s, "\n", ""))
            end
            
            local function deep(lvl, n)
                if lvl == 0 then
                    return (debug.traceback("message", n))
                else
                    return (deep(lvl-1, n))
                end
            end
            
            local function checkdeep(total, start)
                local s = deep(total, start)
                local rest = string.match(s, "^message\nstack traceback:\n(.*)$")
                local cl = countlines(rest)
                -- at most 10 lines in first part, 11 in second, plus '...'
                assert(cl <= 10 + 11 + 1, string.format("checkdeep(%d, %d): got %d lines", total, start, cl))
                local brk = string.find(rest, "%.%.%.\t%(skip")
                if brk then   -- does message have '...'?
                    local rest1 = string.sub(rest, 1, brk)
                    local rest2 = string.sub(rest, brk, #rest)
                    assert(countlines(rest1) == 10, string.format("checkdeep(%d, %d): first part has %d lines", total, start, countlines(rest1)))
                    assert(countlines(rest2) == 11, string.format("checkdeep(%d, %d): second part has %d lines", total, start, countlines(rest2)))
                else
                    assert(cl == total - start + 2, string.format("checkdeep(%d, %d): expected %d lines, got %d", total, start, total - start + 2, cl))
                end
            end
            
            -- Test a few cases, including the one that's failing
            for d = 1, 31, 10 do
                for l = 1, d do
                    coroutine.wrap(checkdeep)(d, l)
                end
            end
        """,
            )
        }
}
