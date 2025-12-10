package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Upvalue operation helpers for the Lua VM.
 * These functions implement upvalue access opcodes (GETUPVAL, SETUPVAL).
 */
object UpvalueOpcodes {
    /**
     * GETUPVAL: Get upvalue.
     * R[A] := Upvalue[B]
     */
    inline fun executeGetUpval(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val upvalue = env.currentUpvalues.getOrNull(instr.b)
        env.setRegister(instr.a, upvalue?.get() ?: LuaNil)
        env.debug("  R[${instr.a}] = upval[${instr.b}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "GETUPVAL")
    }

    /**
     * SETUPVAL: Set upvalue.
     * Upvalue[B] := R[A]
     */
    inline fun executeSetUpval(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val upvalue = env.currentUpvalues.getOrNull(instr.b)
        upvalue?.set(env.registers[instr.a])
        env.trace(instr.a, env.registers[instr.a], "SETUPVAL")
    }

    /**
     * CLOSURE: Create a closure with upvalues.
     * R[A] := closure(KPROTO[B])
     */
    fun executeClosure(
        instr: Instruction,
        registers: MutableList<LuaValue<*>>,
        openUpvalues: MutableMap<Int, Upvalue>,
        env: ExecutionEnvironment,
        executeProtoFn: (Proto, List<LuaValue<*>>, List<Upvalue>, LuaCompiledFunction) -> List<LuaValue<*>>,
    ) {
        // Load the function prototype from constants and create a closure
        val funcProto = env.constants[instr.b] as LuaCompiledFunction
        val proto = funcProto.proto

        env.debug("  Creating CLOSURE, currentUpvalues.size=${env.currentUpvalues.size}")
        if (env.debug != {}) {
            env.currentUpvalues.forEachIndexed { i, uv ->
                val regsHash = uv.registers?.hashCode() ?: 0
                val regIdx =
                    try {
                        uv.registerIndex
                    } catch (_: Exception) {
                        -1
                    }
                env.debug("    currentUpval[$i] regIdx=$regIdx regsHash=$regsHash closed=${uv.isClosed}")
            }
        }

        // Create upvalues for the new function
        val newUpvalues = mutableListOf<Upvalue>()
        for (upvalueInfo in proto.upvalueInfo) {
            val upvalue =
                if (upvalueInfo.inStack) {
                    val regIdx = upvalueInfo.index
                    // Upvalue refers to a local in this function's stack
                    // Reuse existing open upvalue for this register, or create new one
                    openUpvalues.getOrPut(regIdx) {
                        Upvalue(
                            registerIndex = regIdx,
                            registers = registers,
                        )
                    }
                } else {
                    // Upvalue refers to an upvalue in the parent function
                    // Get the upvalue from the CURRENT function's upvalues (not funcProto)
                    env.currentUpvalues.getOrElse(upvalueInfo.index) {
                        // If parent upvalue doesn't exist, create a closed nil upvalue
                        Upvalue(closedValue = LuaNil).apply { isClosed = true }
                    }
                }
            newUpvalues.add(upvalue)
        }

        // Create a new function instance with upvalues
        val func = LuaCompiledFunction(proto, newUpvalues)
        func.value = { args ->
            executeProtoFn(func.proto, args, func.upvalues, func)
        }
        registers[instr.a] = func
        // Emit identity logging for closure and its upvalues to help diagnose TCO wiring
        env.trace(instr.a, registers[instr.a], "CLOSURE")
        if (env.debug != {}) {
            val cid = func.hashCode()
            env.debug("  CLOSURE id=$cid stored at R[${instr.a}], upvalues=${newUpvalues.size}")
            newUpvalues.forEachIndexed { ui, uv ->
                try {
                    val regsHash = uv.registers?.hashCode() ?: 0
                    val regIdx =
                        try {
                            uv.registerIndex
                        } catch (_: Exception) {
                            -1
                        }
                    env.debug("    newUpval[$ui] regIdx=$regIdx regsHash=$regsHash closed=${uv.isClosed}")
                } catch (_: Exception) {
                }
            }
        }
    }
}
