package ai.tenum.lua.compat.integration

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.compat.executeTestFile
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * PHASE 8.1: Integration - Official Test Suite
 *
 * Runs official Lua 5.4.8 test files directly.
 * Based on: all.lua (test orchestrator)
 *
 * Coverage:
 * - Complete test files from lua-5.4.8-tests
 * - Integration testing
 * - Regression testing
 */
class OfficialTestSuiteCompatTest : LuaCompatTestBase() {
    @Test
    @Ignore
    fun test_all_lua() = runTest(timeout = 60.seconds) { executeTestFile("all.lua") }

    @Test
    fun test_api_lua() = runTest(timeout = 60.seconds) { executeTestFile("api.lua") }

    @Test
    fun test_attrib_lua() = runTest(timeout = 60.seconds) { executeTestFile("attrib.lua") }

    @Test
    fun test_big_lua() = runTest(timeout = 60.seconds) { executeTestFile("big.lua") }

    @Test
    fun test_bitwise_lua() = runTest(timeout = 60.seconds) { executeTestFile("bitwise.lua") }

    @Test
    fun test_bwcoercion_lua() = runTest(timeout = 60.seconds) { executeTestFile("bwcoercion.lua") }

    @Test
    fun test_calls_lua() =
        runTest(timeout = 60.seconds) {
            executeTestFile(
                "calls.lua",
                // Bytecode is different
                475..493,
            )
        }

    @Test
    fun test_closure_lua() =
        runTest(timeout = 60.seconds) {
            executeTestFile(
                "closure.lua",
            )
        }

    @Test
    @Ignore
    fun test_code_lua() = runTest(timeout = 60.seconds) { executeTestFile("code.lua") }

    @Test
    fun test_constructs_lua() = runTest(timeout = 60.seconds) { executeTestFile("constructs.lua") }

    @Test
    @Ignore // TODO: Fix - failing at eqtab check for empty table after coroutine resume
    fun test_coroutine_lua() = runTest(timeout = 60.seconds) { executeTestFile("coroutine.lua") }

    @Test
    @Ignore
    fun test_cstack_lua() = runTest(timeout = 60.seconds) { executeTestFile("cstack.lua") }

    @Test
    fun test_db_lua() =
        runTest(timeout = 60.seconds) {
            executeTestFile(
                "db.lua",
                // Different instruction count
                594..609,
                // We use the kotlin gc
                903..916,
                // Current state
                1002..1055,
            )
        }

    @Test
    fun test_errors_lua() =
        runTest(timeout = 60.seconds) {
            executeTestFile(
                "errors.lua",
                // Ignore Parser limits
                650..701,
            )
        }

    @Test
    @Ignore
    fun test_events_lua() = runTest(timeout = 60.seconds) { executeTestFile("events.lua") }

    @Test
    @Ignore
    fun test_files_lua() = runTest(timeout = 60.seconds) { executeTestFile("files.lua") }

    @Test
    @Ignore // Not implmented by tenum lua
    fun test_gc_lua() = runTest(timeout = 60.seconds) { executeTestFile("gc.lua") }

    @Test
    @Ignore
    fun test_gengc_lua() = runTest(timeout = 60.seconds) { executeTestFile("gengc.lua") }

    @Test
    @Ignore
    fun test_goto_lua() = runTest(timeout = 60.seconds) { executeTestFile("goto.lua") }

    @Test
    @Ignore
    fun test_heavy_lua() = runTest(timeout = 60.seconds) { executeTestFile("heavy.lua") }

    @Test
    fun test_literals_lua() =
        runTest(timeout = 60.seconds) {
            executeTestFile(
                "literals.lua",
                229..231, // we us kotlin string internalization
                296..320, // no localisation for now
            )
        }

    @Test
    @Ignore
    fun test_locals_lua() = runTest(timeout = 60.seconds) { executeTestFile("locals.lua") }

    @Test
    fun test_main_lua() = runTest(timeout = 60.seconds) { executeTestFile("main.lua") }

    @Test
    @Ignore
    fun test_math_lua() = runTest(timeout = 60.seconds) { executeTestFile("math.lua") }

    @Test
    @Ignore
    fun test_nextvar_lua() = runTest(timeout = 60.seconds) { executeTestFile("nextvar.lua") }

    @Test
    @Ignore
    fun test_pm_lua() = runTest(timeout = 60.seconds) { executeTestFile("pm.lua") }

    @Test
    fun test_sort_lua() =
        runTest(timeout = 60.seconds) {
            executeTestFile(
                "sort.lua",
            )
        }

    @Test
    fun test_strings_lua() =
        runTest(timeout = 60.seconds) {
            executeTestFile(
                "strings.lua",
                // ignore internalized string tests
                186..195,
                // ignore strange formating
                199..217,
            )
        }

    @Test
    fun test_tpack_lua() = runTest(timeout = 60.seconds) { executeTestFile("tpack.lua") }

    @Test
    fun test_tracegc_lua() = runTest(timeout = 60.seconds) { executeTestFile("tracegc.lua") }

    @Test
    @Ignore
    fun test_utf8_lua() = runTest(timeout = 60.seconds) { executeTestFile("utf8.lua") }

    @Test
    fun test_vararg_lua() = runTest(timeout = 60.seconds) { executeTestFile("vararg.lua") }

    @Test
    fun test_verybig_lua() = runTest(timeout = 60.seconds) { executeTestFile("verybig.lua") }
}
