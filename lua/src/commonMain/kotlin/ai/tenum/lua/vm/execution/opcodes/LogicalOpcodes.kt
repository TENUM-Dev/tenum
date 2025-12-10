package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Logical operation helpers for the Lua VM.
 * These functions implement logical opcodes (NOT, AND, OR).
 */
object LogicalOpcodes {
    /**
     * NOT: Logical NOT operation.
     * R[A] := not R[B]
     */
    inline fun executeNot(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        env.setRegister(instr.a, LuaBoolean.of(!env.isTruthy(env.registers[instr.b])))
        env.debug("  R[${instr.a}] = not R[${instr.b}] (${env.registers[instr.a]})")
    }

    /**
     * AND: Logical AND operation (returns left if falsy, otherwise right).
     * R[A] := R[B] and R[C]
     * In Lua: a and b returns a if a is falsy, otherwise b
     */
    inline fun executeAnd(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val left = env.registers[instr.b]
        val right = env.registers[instr.c]
        env.setRegister(instr.a, if (!env.isTruthy(left)) left else right)
        env.debug("  R[${instr.a}] = R[${instr.b}] and R[${instr.c}] (${env.registers[instr.a]})")
    }

    /**
     * OR: Logical OR operation (returns left if truthy, otherwise right).
     * R[A] := R[B] or R[C]
     * In Lua: a or b returns a if a is truthy, otherwise b
     */
    inline fun executeOr(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val left = env.registers[instr.b]
        val right = env.registers[instr.c]
        env.setRegister(instr.a, if (env.isTruthy(left)) left else right)
        env.debug("  R[${instr.a}] = R[${instr.b}] or R[${instr.c}] (${env.registers[instr.a]})")
    }
}
