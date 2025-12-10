package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Stack operation helpers for the Lua VM.
 * These functions implement basic stack manipulation opcodes.
 */
object StackOpcodes {
    /**
     * MOVE: Copy value from one register to another.
     * R[A] := R[B]
     */
    inline fun executeMove(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        env.setRegister(instr.a, env.registers[instr.b])
        env.debug("  R[${instr.a}] = R[${instr.b}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "MOVE")
        env.debug("[REGISTERS after MOVE] ${env.registers.slice(0..10).mapIndexed { i, v -> "R[$i]=$v" }.joinToString(", ")}")
    }

    /**
     * LOADK: Load constant into register.
     * R[A] := K[B]
     */
    inline fun executeLoadK(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val const = env.constants[instr.b]
        env.setRegister(instr.a, const)
        env.debug("  R[${instr.a}] = K[${instr.b}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "LOADK")
    }

    /**
     * LOADI: Load integer immediate into register.
     * R[A] := sBx (sign-extended)
     */
    inline fun executeLoadI(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val intValue = instr.b.toLong()
        env.setRegister(instr.a, LuaLong(intValue))
        env.debug("  R[${instr.a}] = $intValue (immediate)")
        env.trace(instr.a, env.registers[instr.a], "LOADI")
    }

    /**
     * LOADBOOL: Load boolean constant into register.
     * R[A] := (bool)B
     * if (C) pc++
     *
     * @return true if next instruction should be skipped
     */
    inline fun executeLoadBool(
        instr: Instruction,
        env: ExecutionEnvironment,
    ): Boolean {
        env.setRegister(instr.a, LuaBoolean.of(instr.b != 0))
        env.debug("  R[${instr.a}] = ${env.registers[instr.a]}")
        val skip = instr.c != 0
        if (skip) {
            env.debug("  Skip next instruction")
        }
        return skip
    }

    /**
     * LOADNIL: Load nil into range of registers.
     * R[A]..R[B] := nil
     */
    inline fun executeLoadNil(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        for (i in instr.a..instr.b) {
            env.registers[i] = LuaNil
        }
        env.debug("  R[${instr.a}..${instr.b}] = nil")
    }

    /**
     * GETGLOBAL: Get global variable.
     * R[A] := _ENV[K[B]]
     */
    inline fun executeGetGlobal(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val name = env.constants[instr.b] as LuaString
        // GETGLOBAL opcode assumes _ENV is at upvalue[0] (compiler ensures this when emitting GETGLOBAL)
        // For functions with other upvalues before _ENV, compiler emits GETTABLE instead
        val envTable = env.currentUpvalues.getOrNull(0)?.get()
        env.setRegister(
            instr.a,
            if (envTable is LuaTable) {
                envTable[name]
            } else {
                // Fallback for compatibility (shouldn't happen with proper _ENV)
                env.globals[name.value] ?: LuaNil
            },
        )
        env.debug("  R[\${instr.a}] = G['\${name.value}'] (\${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "GETGLOBAL")
    }

    /**
     * SETGLOBAL: Set global variable.
     * _ENV[K[B]] := R[A]
     */
    inline fun executeSetGlobal(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val name = env.constants[instr.b] as LuaString
        // SETGLOBAL opcode assumes _ENV is at upvalue[0] (compiler ensures this when emitting SETGLOBAL)
        // For functions with other upvalues before _ENV, compiler emits SETTABLE instead
        val envTable = env.currentUpvalues.getOrNull(0)?.get()
        if (envTable is LuaTable) {
            envTable[name] = env.registers[instr.a]
        } else {
            // Fallback for compatibility (shouldn't happen with proper _ENV)
            env.globals[name.value] = env.registers[instr.a]
        }
        env.debug("  G['\${name.value}'] = R[\${instr.a}] (\${env.registers[instr.a]})")
    }
}
