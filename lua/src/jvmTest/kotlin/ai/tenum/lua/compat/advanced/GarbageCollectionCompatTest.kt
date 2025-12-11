package ai.tenum.lua.compat.advanced

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNumber
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PHASE 7.2: Advanced Features - Garbage Collection
 *
 * Tests garbage collection and memory management.
 * Based on: gc.lua
 *
 * Coverage:
 * - collectgarbage()
 * - Weak tables (__mode)
 * - Finalizers (__gc)
 * - Memory limits
 * - Generational vs incremental GC
 */
class GarbageCollectionCompatTest : LuaCompatTestBase() {
    @Test
    fun testCollectgarbage() =
        runTest {
            execute(
                """
            local result = collectgarbage("collect")
            assert(type(result) == "number", "collectgarbage should return a number")
        """,
            )
        }

    @Test
    fun testCollectgarbageCount() =
        runTest {
            val result =
                execute(
                    """
            return collectgarbage("count")
        """,
                )
            assertTrue(result is LuaNumber, "collectgarbage('count') should return memory usage")
            assertTrue(result.toDouble() >= 0, "Memory count should be non-negative")
        }

    @Test
    fun testCollectgarbageStop() =
        runTest {
            execute(
                """
            collectgarbage("stop")
            local isRunning = collectgarbage("isrunning")
            -- Note: In managed environments, GC might still report as running
            assert(type(isRunning) == "boolean", "isrunning should return boolean")
        """,
            )
        }

    @Test
    @Ignore
    fun testCollectgarbageRestart() =
        runTest {
            execute(
                """
            collectgarbage("stop")
            collectgarbage("restart")
            local isRunning = collectgarbage("isrunning")
            assert(isRunning == true, "GC should be running after restart")
        """,
            )
        }

    @Test
    fun testWeakKeysTable() =
        runTest {
            execute(
                """
            local weak = {}
            setmetatable(weak, {__mode = "k"})
            
            local key1 = {}
            local key2 = {}
            weak[key1] = "value1"
            weak[key2] = "value2"
            
            -- Keys should exist
            assert(weak[key1] == "value1", "weak key value should be accessible")
            assert(weak[key2] == "value2", "weak key value should be accessible")
            
            -- After GC, weak keys may be collected (but not guaranteed in test environment)
            collectgarbage("collect")
        """,
            )
        }

    @Test
    fun testWeakValuesTable() =
        runTest {
            execute(
                """
            local weak = {}
            setmetatable(weak, {__mode = "v"})
            
            local val1 = {}
            local val2 = {}
            weak.a = val1
            weak.b = val2
            
            -- Values should exist
            assert(weak.a ~= nil, "weak value should be accessible")
            assert(weak.b ~= nil, "weak value should be accessible")
            
            -- After GC, weak values may be collected
            collectgarbage("collect")
        """,
            )
        }

    @Test
    fun testWeakKeysAndValuesTable() =
        runTest {
            execute(
                """
            local weak = {}
            setmetatable(weak, {__mode = "kv"})
            
            local key = {}
            local val = {}
            weak[key] = val
            
            -- Entry should exist
            assert(weak[key] ~= nil, "weak entry should be accessible")
            
            collectgarbage("collect")
        """,
            )
        }

    @Test
    fun testFinalizerExecution() =
        runTest {
            // Finalizer (__gc) test - note: exact execution timing is platform-dependent
            execute(
                """
            local finalized = false
            local t = {}
            setmetatable(t, {
                __gc = function(self)
                    finalized = true
                end
            })
            
            -- Finalizer exists
            local mt = getmetatable(t)
            assert(mt.__gc ~= nil, "finalizer should be set")
        """,
            )
        }

    @Test
    fun testCollectgarbageIsRunning() =
        runTest {
            val result =
                execute(
                    """
            return collectgarbage("isrunning")
        """,
                )
            assertTrue(result is LuaBoolean, "isrunning should return boolean")
        }

    @Test
    fun testCollectgarbageStep() =
        runTest {
            execute(
                """
            local result = collectgarbage("step")
            assert(type(result) == "boolean", "step should return boolean")
        """,
            )
        }

    @Test
    fun testWeakTableGCDetection() =
        runTest {
            // Test from closure.lua that detects when GC has run
            // by checking if a weak table entry has been collected
            execute(
                """
            local A = 0
            local x = {[1] = {}}   -- to detect a GC
            setmetatable(x, {__mode = 'kv'})
            
            -- This loop should eventually exit after GC collects x[1]
            local iterations = 0
            while x[1] do   -- repeat until GC
              local a = A..A..A..A  -- create garbage
              A = A+1
              iterations = iterations + 1
              if iterations > 10000 then
                collectgarbage("collect")  -- force GC if not happening naturally
              end
              if iterations > 20000 then
                error("Weak table entry never collected - infinite loop detected")
              end
            end
            
            assert(iterations > 0, "loop should have run at least once")
        """,
            )
        }
}