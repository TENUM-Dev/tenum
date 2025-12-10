package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.vm.execution.BinaryMetamethodDispatcher
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * String operation helpers for the Lua VM.
 * These functions implement string opcodes (LEN, CONCAT).
 */
object StringOpcodes {
    /**
     * LEN: Length operator with metamethod support.
     * R[A] := #R[B]
     * Works on strings and tables, checks __len metamethod first.
     */
    inline fun executeLen(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val value = env.registers[instr.b]
        // Check for __len metamethod on the value
        val meta = env.getMetamethod(value, "__len")
        if (meta != null && meta is LuaFunction) {
            env.setMetamethodCallContext("__len")
            val result = env.callFunction(meta, listOf(value))
            env.setRegister(instr.a, result.firstOrNull() ?: LuaNil)
        } else {
            // Standard length operation
            env.setRegister(
                instr.a,
                when (value) {
                    is LuaString -> LuaDouble(value.value.length.toDouble())
                    is LuaTable -> LuaDouble(value.length().toDouble())
                    else -> {
                        // Generate error with name hint
                        val nameHint = env.getRegisterNameHint(instr.b)
                        val typeStr = value.type().name.lowercase()
                        val errorMsg =
                            if (nameHint != null) {
                                "attempt to get length of a $typeStr value ($nameHint)"
                            } else {
                                "attempt to get length of a $typeStr value"
                            }
                        env.luaError(errorMsg)
                    }
                },
            )
        }
        env.debug("  R[${instr.a}] = #R[${instr.b}] (${env.registers[instr.a]})")
    }

    /**
     * CONCAT: String concatenation with metamethod support.
     * R[A] := RK[B] .. RK[C]
     * Both operands must be strings or numbers (or have __concat metamethod).
     */
    inline fun executeConcat(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, "__concat", env)
        if (metaResult != null) {
            env.setRegister(instr.a, metaResult)
        } else {
            // Lua requires both operands to be strings or numbers
            val canConcat =
                (left is LuaString || left is LuaNumber) &&
                    (right is LuaString || right is LuaNumber)
            if (!canConcat) {
                val badType =
                    when {
                        left !is LuaString && left !is LuaNumber -> left.type().name.lowercase()
                        else -> right.type().name.lowercase()
                    }
                throw RuntimeException("attempt to concatenate a $badType value")
            }

            val leftStr = env.toString(left)
            val rightStr = env.toString(right)
            env.setRegister(instr.a, LuaString(leftStr + rightStr))
        }
        env.debug("  R[${instr.a}] = RK[${instr.b}] .. RK[${instr.c}] (${env.registers[instr.a]})")
    }
}
