@file:Suppress("NOTHING_TO_INLINE")

package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Control flow operation helpers for the Lua VM.
 * These functions implement control flow opcodes (JMP, TEST, TESTSET).
 *
 * These functions return the new PC value (or null if PC should not be modified).
 */
object ControlFlowOpcodes {
    /**
     * JMP: Unconditional jump.
     * PC += sBx (signed offset in instruction.b)
     * @return new PC value
     */
    inline fun executeJmp(
        instr: Instruction,
        env: ExecutionEnvironment,
        currentPc: Int,
    ): Int {
        val newPc = currentPc + instr.b
        env.debug("  Jump to PC=${newPc + 1}")
        return newPc
    }

    /**
     * TEST: Conditional skip based on truthiness.
     * If isTruthy(R[A]) != C, skip next instruction (usually JMP).
     * C is a boolean flag (0=false, non-zero=true).
     * @return new PC value if skipping, null otherwise
     */
    inline fun executeTest(
        instr: Instruction,
        env: ExecutionEnvironment,
        currentPc: Int,
    ): Int? {
        val value = env.registers[instr.a]
        val shouldSkip = env.isTruthy(value) != (instr.c != 0)
        env.debug("  Test R[${instr.a}] (truthy=${env.isTruthy(value)}, expect=${instr.c != 0}, skip=$shouldSkip)")
        return if (shouldSkip) {
            env.debug("    Skip next instruction")
            currentPc + 1 // Skip next instruction (JMP)
        } else {
            null
        }
    }

    /**
     * TESTSET: Conditional assignment and skip.
     * If isTruthy(R[B]) != C, then R[A] = R[B], else skip next instruction.
     * Used for short-circuit evaluation (and/or operators).
     * @return new PC value if skipping, null otherwise
     */
    inline fun executeTestSet(
        instr: Instruction,
        env: ExecutionEnvironment,
        currentPc: Int,
    ): Int? {
        val value = env.registers[instr.b]
        return if (env.isTruthy(value) != (instr.c != 0)) {
            env.setRegister(instr.a, value)
            env.debug("  R[${instr.a}] = R[${instr.b}] (${env.registers[instr.a]})")
            null
        } else {
            env.debug("  Skip next instruction")
            currentPc + 1 // Skip next instruction
        }
    }
}
