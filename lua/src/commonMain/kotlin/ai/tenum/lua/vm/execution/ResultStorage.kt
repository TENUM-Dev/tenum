package ai.tenum.lua.vm.execution

import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue

/**
 * Domain object for storing function call results into registers.
 *
 * Encapsulates the critical pattern of:
 * 1. Storing a fixed or variable number of results
 * 2. Padding with nil for fixed counts
 * 3. Managing frame.top (CRITICAL for SETLIST correctness)
 *
 * The frame.top manipulation is extremely subtle:
 * - Fixed count (C>0): Reset top=0 to prevent stale vararg top from affecting SETLIST
 * - Variable count (C=0): Set top=targetReg+resultCount to mark where results end
 *
 * This pattern appears in CALL opcode and coroutine resume.
 */
class ResultStorage(
    private val env: ExecutionEnvironment,
) {
    private val registers = env.registers
    private val frame = env.frame

    /**
     * Store function call results into registers.
     *
     * @param targetReg First register to store results (typically A for CALL)
     * @param encodedCount C field from instruction (0 means variable, >0 means encodedCount-1 results)
     * @param results List of values to store
     * @param opcodeName Name of opcode for tracing (e.g., "CALL", "RESUME")
     */
    fun storeResults(
        targetReg: Int,
        encodedCount: Int,
        results: List<LuaValue<*>>,
        opcodeName: String,
    ) {
        val count = encodedCount - 1
        if (count >= 0) {
            // Fixed count: store specific number of results, pad with nil
            for (i in 0 until count) {
                val value = results.getOrElse(i) { LuaNil }
                env.setRegister(targetReg + i, value)
                env.trace(targetReg + i, registers[targetReg + i], "$opcodeName-res")
            }
            // CRITICAL: Clear any extra registers that contained return values beyond what we requested
            // This prevents multi-return functions from leaking values into unused registers
            // For example: `local a,b = 3 and f()` where f() returns (1,2,3) should only capture 1 value
            if (results.size > count) {
                for (i in count until results.size) {
                    env.setRegister(targetReg + i, LuaNil)
                    env.trace(targetReg + i, LuaNil, "$opcodeName-clear")
                }
            }
            // CRITICAL: Reset top after capturing specific number of results
            // This prevents stale top value (set by previous VARARG) from
            // affecting subsequent instructions like SETLIST
            frame.top = 0
        } else {
            // Variable count (C=0): store all results and update top
            results.forEachIndexed { i, value ->
                env.setRegister(targetReg + i, value)
                env.trace(targetReg + i, value, "$opcodeName-res-all")
            }
            frame.top = targetReg + results.size
        }
    }
}
