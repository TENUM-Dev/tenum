package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.BinaryMetamethodDispatcher
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Comparison operation helpers for the Lua VM.
 * These functions implement comparison opcodes (EQ, LT, LE).
 */
object ComparisonOpcodes {
    /**
     * EQ: Equality comparison with metamethod support.
     * In Lua 5.4: if ((R[B] == R[C]) != A) then pc++
     * A is a boolean flag (0 or 1), B and C are REGISTER indices (not RK).
     * Note: __eq metamethod only applies if both operands have the same metatable.
     */
    inline fun executeEq(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val left = env.registers[instr.b]
        val right = env.registers[instr.c]

        // Check for __eq metamethod (both operands must have same metatable)
        val leftMt = left.metatable
        val rightMt = right.metatable
        var result = false
        if (leftMt != null && leftMt === rightMt) {
            val metaMethod = env.getMetamethod(left, "__eq")
            if (metaMethod != null && metaMethod !is LuaNil) {
                if (metaMethod is LuaFunction) {
                    env.setMetamethodCallContext("__eq")
                    val metaResult = env.callFunction(metaMethod, listOf(left, right))
                    result = env.isTruthy(metaResult.firstOrNull() ?: LuaNil)
                } else {
                    val typeStr = metaMethod.type().name.lowercase()
                    env.luaError("attempt to call a $typeStr value (metamethod 'eq')")
                }
            } else {
                result = env.luaEquals(left, right)
            }
        } else {
            result = env.luaEquals(left, right)
        }
        env.setRegister(instr.a, LuaBoolean.of(result))
        env.debug("  R[${instr.a}] = RK[${instr.b}] == RK[${instr.c}] (${env.registers[instr.a]})")
    }

    /**
     * Generates a comparison error message and throws it.
     * Format: "two X values" for same types, "X with Y" for different types.
     */
    @PublishedApi
    internal inline fun throwComparisonError(
        left: ai.tenum.lua.runtime.LuaValue<*>,
        right: ai.tenum.lua.runtime.LuaValue<*>,
        env: ExecutionEnvironment,
    ): Nothing {
        val leftType = LoopOpcodes.getTypeName(left, env)
        val rightType = LoopOpcodes.getTypeName(right, env)
        val message =
            if (leftType == rightType) {
                "attempt to compare two $leftType values"
            } else {
                "attempt to compare $leftType with $rightType"
            }
        env.luaError(message)
    }

    @PublishedApi
    internal inline fun executeComparison(
        instr: Instruction,
        env: ExecutionEnvironment,
        metamethod: String,
        comparisonFn: (ExecutionEnvironment, LuaValue<*>, LuaValue<*>) -> Boolean?,
        debugOp: String,
    ) {
        val left = env.registers[instr.b]
        val right = env.registers[instr.c]

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, metamethod, env)
        val result =
            if (metaResult != null) {
                env.isTruthy(metaResult)
            } else {
                val cmpResult = comparisonFn(env, left, right)
                if (cmpResult == null) {
                    throwComparisonError(left, right, env)
                }
                cmpResult
            }
        env.setRegister(instr.a, LuaBoolean.of(result))
        env.debug("  R[${instr.a}] = RK[${instr.b}] $debugOp RK[${instr.c}] (${env.registers[instr.a]})")
    }

    /**
     * LT: Less than comparison with metamethod support.
     * In Lua 5.4: if ((R[B] < R[C]) != A) then pc++
     * A is a boolean flag (0 or 1), B and C are REGISTER indices (not RK).
     */
    inline fun executeLt(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        executeComparison(instr, env, "__lt", ExecutionEnvironment::luaLessThan, "<")
    }

    /**
     * LE: Less than or equal comparison with metamethod support.
     * In Lua 5.4: if ((R[B] <= R[C]) != A) then pc++
     * A is a boolean flag (0 or 1), B and C are REGISTER indices (not RK).
     */
    inline fun executeLe(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        executeComparison(instr, env, "__le", ExecutionEnvironment::luaLessOrEqual, "<=")
    }
}
