@file:Suppress("NOTHING_TO_INLINE")

package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.vm.errorhandling.LuaException
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Coroutine operation helpers for the Lua VM.
 * These functions implement coroutine opcodes (YIELD, RESUME).
 */
object CoroutineOpcodes {
    /**
     * YIELD: Yield from coroutine.
     * This would suspend the current coroutine execution.
     * For now, this is a placeholder.
     * Full implementation requires coroutine context tracking.
     */
    inline fun executeYield(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        // The compiler never emits a YIELD opcode; coroutine.yield is a native function.
        // Fail fast if malformed bytecode hits this path.
        throw LuaException("unsupported opcode YIELD")
    }

    /**
     * RESUME: Resume a coroutine.
     * This would resume a suspended coroutine.
     * For now, this is a placeholder.
     * Full implementation requires coroutine stack management.
     */
    inline fun executeResume(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        // The compiler never emits a RESUME opcode; coroutine.resume is a native function.
        // Fail fast if malformed bytecode hits this path.
        throw LuaException("unsupported opcode RESUME")
    }
}
