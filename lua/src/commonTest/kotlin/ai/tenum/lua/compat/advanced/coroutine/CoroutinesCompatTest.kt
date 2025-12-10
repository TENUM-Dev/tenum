package ai.tenum.lua.compat.advanced.coroutine

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * PHASE 6.3: Advanced - Coroutines
 *
 * Tests coroutines (cooperative multitasking).
 * Based on: coroutine.lua
 *
 * Coverage:
 * - coroutine.create
 * - coroutine.resume, coroutine.yield
 * - coroutine.status
 * - coroutine.running
 * - coroutine.wrap
 * - coroutine.isyieldable
 * - Error handling in coroutines
 */
class CoroutinesCompatTest : LuaCompatTestBase() {
    @Test
    fun testCoroutineCreate() =
        runTest {
            execute(
                """
            local co = coroutine.create(function() return 42 end)
            assert(type(co) == "thread", "coroutine.create should return a thread")
        """,
            )
        }

    @Test
    fun testCoroutineStatus() =
        runTest {
            execute(
                """
            local co = coroutine.create(function() return 42 end)
            assert(coroutine.status(co) == "suspended", "New coroutine should be suspended")
        """,
            )
        }

    @Test
    fun testCoroutineResume() =
        runTest {
            execute(
                """
            local co = coroutine.create(function() return 42 end)
            local ok, result = coroutine.resume(co)
            assert(ok == true, "resume should return true for success")
            assert(result == 42, "resume should return coroutine result")
        """,
            )
        }

    @Test
    fun testCoroutineResumeWithArguments() =
        runTest {
            execute(
                """
            local co = coroutine.create(function(a, b) return a + b end)
            local ok, result = coroutine.resume(co, 10, 20)
            assert(ok == true, "resume should succeed")
            assert(result == 30, "resume should pass arguments to coroutine")
        """,
            )
        }

    @Test
    fun testCoroutineStatusDead() =
        runTest {
            execute(
                """
            local co = coroutine.create(function() return 42 end)
            coroutine.resume(co)
            assert(coroutine.status(co) == "dead", "Completed coroutine should be dead")
        """,
            )
        }

    @Test
    fun testCoroutineWrap() =
        runTest {
            execute(
                """
            local wrapped = coroutine.wrap(function() return 42 end)
            assert(type(wrapped) == "function", "coroutine.wrap should return a function")
            local result = wrapped()
            assert(result == 42, "wrapped coroutine should return result")
        """,
            )
        }

    @Test
    fun testCoroutineRunning() =
        runTest {
            execute(
                """
            local co, ismain = coroutine.running()
            assert(ismain == true, "Main thread should report as main")
        """,
            )
        }

    @Test
    fun testCoroutineIsYieldable() =
        runTest {
            execute(
                """
            local yieldable = coroutine.isyieldable()
            -- Main thread is not yieldable
            assert(yieldable == false, "Main thread should not be yieldable")
        """,
            )
        }

    @Test
    fun testCoroutineErrorHandling() =
        runTest {
            execute(
                """
            local co = coroutine.create(function() error("test error") end)
            local ok, err = coroutine.resume(co)
            assert(ok == false, "resume should return false on error")
            assert(type(err) == "string", "error should be a string")
        """,
            )
        }

    @Test
    fun testCoroutineCannotResumeDeadCoroutine() =
        runTest {
            execute(
                """
            local co = coroutine.create(function() return 42 end)
            coroutine.resume(co)
            local ok, err = coroutine.resume(co)
            assert(ok == false, "Cannot resume dead coroutine")
            assert(string.find(err, "dead") ~= nil, "Error should mention dead coroutine")
        """,
            )
        }

    @Test
    fun testCoroutineYield() =
        runTest {
            // Full yield/resume support requires VM-level continuation management.
            //
            // WHY KOTLIN'S SUSPEND WON'T HELP:
            // - Lua coroutines are STACKFUL: can yield from ANY call depth
            // - Kotlin coroutines are STACKLESS: need explicit `suspend` markers
            // - Lua bytecode has no suspension points - it's just instructions
            // - Can't make VM `suspend` because Lua functions aren't written as suspend functions
            //
            // REAL SOLUTION NEEDED:
            // - Save PC (program counter) when yield is called
            // - Save register/stack state in CoroutineThread
            // - Save call frame stack
            // - On resume: restore PC, registers, and continue execution
            // - This is "stackful coroutines" - requires significant VM refactoring
            //
            // Current: YieldException catches yield attempt, but function restarts from beginning on resume
            skipTest("coroutine.yield requires VM-level PC/register state saving (stackful coroutines)")
        }

    @Test
    fun testPcallCoroutineResumeReturnsAllValues() =
        runTest {
            // db.lua:790 - pcall(coroutine.resume, co) should return (true, true, <yielded value>)
            // Reproduces: @db.lua:790: assertion failed!
            execute(
                """
                local co = coroutine.create(function()
                    local l = {currentline = 100}
                    coroutine.yield(l)
                    return l.currentline + 1
                end)
                
                -- First resume to get to the yield
                local _, l = coroutine.resume(co)
                
                -- Second resume with pcall - should return (true, true, 101)
                local a, b, c = pcall(coroutine.resume, co)
                assert(a == true, "pcall should succeed, got: " .. tostring(a))
                assert(b == true, "coroutine.resume should succeed, got: " .. tostring(b))
                assert(c == l.currentline + 1, "should return yielded value " .. (l.currentline + 1) .. ", got: " .. tostring(c))
            """,
            )
        }
}
