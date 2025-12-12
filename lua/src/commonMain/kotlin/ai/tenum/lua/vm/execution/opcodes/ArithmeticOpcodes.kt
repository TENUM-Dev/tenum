@file:Suppress("NOTHING_TO_INLINE")

package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.BinaryMetamethodDispatcher
import ai.tenum.lua.vm.execution.ExecutionEnvironment
import kotlin.math.floor
import kotlin.math.pow

/**
 * Arithmetic operation opcodes: ADD, SUB, MUL, DIV, MOD, POW, IDIV, UNM.
 */
object ArithmeticOpcodes {
    /**
     * Helper to perform binary operation with error handling.
     * Wraps the try-catch pattern used by ADD, SUB, MUL operations.
     */
    @PublishedApi
    internal inline fun performBinaryOpWithErrorHandling(
        left: LuaValue<*>,
        right: LuaValue<*>,
        regB: Int,
        regC: Int,
        pc: Int,
        env: ExecutionEnvironment,
        noinline op: (Double, Double) -> Double,
    ): LuaValue<*> =
        try {
            binaryOp(left, right, env, op)
        } catch (e: RuntimeException) {
            handleArithmeticError(e, left, right, regB, regC, pc, env)
            LuaNil // unreachable, handleArithmeticError throws
        }

    /**
     * ADD: Addition with metamethod support.
     * R[A] := RK[B] + RK[C]
     */
    inline fun executeAdd(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, "__add", env)
        if (metaResult != null) {
            env.setRegister(instr.a, metaResult)
        } else if (left is LuaLong && right is LuaLong) {
            // Integer addition with 64-bit wrap semantics
            env.setRegister(instr.a, LuaLong(left.value + right.value))
        } else {
            // Float addition or type coercion
            env.setRegister(instr.a, performBinaryOpWithErrorHandling(left, right, instr.b, instr.c, pc, env) { a, b -> a + b })
        }
        env.debug("  R[${instr.a}] = R[${instr.b}] + R[${instr.c}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "DIV")
    }

    /**
     * SUB: Subtraction with metamethod support.
     * R[A] := RK[B] - RK[C]
     */
    inline fun executeSub(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, "__sub", env)
        if (metaResult != null) {
            env.setRegister(instr.a, metaResult)
        } else if (left is LuaLong && right is LuaLong) {
            // Integer subtraction with 64-bit wrap semantics
            env.setRegister(instr.a, LuaLong(left.value - right.value))
        } else {
            env.setRegister(instr.a, performBinaryOpWithErrorHandling(left, right, instr.b, instr.c, pc, env) { a, b -> a - b })
        }
        env.debug("  R[${instr.a}] = R[${instr.b}] - R[${instr.c}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "SUB")
    }

    /**
     * MUL: Multiplication with metamethod support.
     * R[A] := RK[B] * RK[C]
     */
    inline fun executeMul(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, "__mul", env)
        if (metaResult != null) {
            env.setRegister(instr.a, metaResult)
        } else if (left is LuaLong && right is LuaLong) {
            // Integer multiplication with 64-bit wrap semantics
            env.setRegister(instr.a, LuaLong(left.value * right.value))
        } else {
            // Float multiplication or type coercion
            env.setRegister(instr.a, performBinaryOpWithErrorHandling(left, right, instr.b, instr.c, pc, env) { a, b -> a * b })
        }
        env.debug("  R[${instr.a}] = R[${instr.b}] * R[${instr.c}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "MUL")
    }

    /**
     * DIV: Float division (always returns float).
     * R[A] := RK[B] / RK[C]
     */
    inline fun executeDiv(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, "__div", env)
        if (metaResult != null) {
            env.setRegister(instr.a, metaResult)
        } else {
            try {
                env.setRegister(instr.a, divOp(left, right))
                if (env.registers[instr.a] is LuaNil) {
                    // divOp returned nil, so left or right wasn't a number
                    val hint =
                        when {
                            left !is LuaNumber -> env.getRegisterHintWithRuntime(instr.b, pc)
                            right !is LuaNumber -> env.getRegisterHintWithRuntime(instr.c, pc)
                            else -> null
                        }
                    val type =
                        when {
                            left !is LuaNumber -> LoopOpcodes.getTypeName(left, env)
                            else -> LoopOpcodes.getTypeName(right, env)
                        }
                    val errorMsg =
                        if (hint != null) {
                            "attempt to perform arithmetic on a $type value ($hint)"
                        } else {
                            "attempt to perform arithmetic on a $type value"
                        }
                    env.error(errorMsg, pc)
                }
            } catch (e: RuntimeException) {
                handleArithmeticError(e, left, right, instr.b, instr.c, pc, env)
            }
        }
        env.debug("  R[${instr.a}] = R[${instr.b}] / R[${instr.c}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "DIV")
    }

    /**
     * MOD: Modulo operation.
     * R[A] := RK[B] % RK[C]
     * Lua modulo: a % b = a - floor(a/b) * b
     *
     * For integers, uses integer arithmetic to preserve precision for large values.
     * Converting large integers to Double loses precision (e.g., 1 << 54).
     *
     * For floats with small integer divisors, Lua uses fmod() which is IEEE remainder.
     */
    inline fun executeMod(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, "__mod", env)
        if (metaResult != null) {
            env.setRegister(instr.a, metaResult)
        } else if (left is LuaLong && right is LuaLong) {
            // Integer modulo with zero check
            if (right.value == 0L) {
                env.luaError("attempt to perform 'n%0'")
            }
            // Use integer modulo: a % b = a - (a floorDiv b) * b
            // In Kotlin: a % b has sign of dividend, but Lua needs sign of divisor
            // Formula: a - (a floorDiv b) * b
            env.setRegister(instr.a, LuaLong(left.value - (left.value.floorDiv(right.value)) * right.value))
        } else {
            // Float modulo or mixed-type modulo - convert to Double and use Kotlin's rem() with sign adjustment
            // Handle string coercion (Lua semantics)
            val leftCoerced =
                if (left is LuaNumber) {
                    left
                } else if (left is ai.tenum.lua.runtime.LuaString) {
                    left.coerceToNumber()
                } else {
                    left
                }
            val rightCoerced =
                if (right is LuaNumber) {
                    right
                } else if (right is ai.tenum.lua.runtime.LuaString) {
                    right.coerceToNumber()
                } else {
                    right
                }

            val leftVal =
                when (leftCoerced) {
                    is LuaLong -> leftCoerced.value.toDouble()
                    is LuaDouble -> leftCoerced.value
                    else -> env.luaError("attempt to perform arithmetic on a ${LoopOpcodes.getTypeName(left, env)} value")
                }
            val rightVal =
                when (rightCoerced) {
                    is LuaLong -> rightCoerced.value.toDouble()
                    is LuaDouble -> rightCoerced.value
                    else -> env.luaError("attempt to perform arithmetic on a ${LoopOpcodes.getTypeName(right, env)} value")
                }

            // Lua's modulo has sign of divisor. Kotlin's % has sign of dividend.
            // Use: r = a % b; if ((r > 0 && b < 0) || (r < 0 && b > 0)) r + b else r
            val r = leftVal % rightVal
            val result = if ((r > 0.0 && rightVal < 0.0) || (r < 0.0 && rightVal > 0.0)) r + rightVal else r
            env.setRegister(instr.a, LuaDouble(result))
        }
        env.debug("  R[${instr.a}] = R[${instr.b}] % R[${instr.c}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "MOD")
    }

    /**
     * POW: Exponentiation.
     * R[A] := RK[B] ^ RK[C]
     * Note: Power operation ALWAYS returns float in Lua 5.4 (even 2^2 = 4.0, not 4)
     */
    inline fun executePow(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        env.setRegister(instr.a, powOpWithMetamethod(env, getRKValue(instr.b, env), getRKValue(instr.c, env)))
        env.debug("  R[${instr.a}] = R[${instr.b}] ^ R[${instr.c}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "POW")
    }

    /**
     * IDIV: Floor division (integer division).
     * R[A] := R[B] // R[C]
     */
    inline fun executeIdiv(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)

        // Check for division by integer zero when both operands are integers
        // (float operations with zero return infinity)
        if (left is LuaLong && right is LuaLong && right.value == 0L) {
            env.luaError("attempt to divide by zero")
        }

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, "__idiv", env)
        if (metaResult != null) {
            env.setRegister(instr.a, metaResult)
        } else {
            env.setRegister(instr.a, floorDivOp(left, right))
        }
        env.debug("  R[${instr.a}] = R[${instr.b}] // R[${instr.c}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "IDIV")
    }

    /**
     * UNM: Unary minus (negation).
     * R[A] := -R[B]
     */
    inline fun executeUnm(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val value = env.registers[instr.b]
        val result =
            unaryOpWithMetamethod(env, value, "__unm") { v ->
                // Try to coerce string to number (Lua semantics)
                val numValue = if (v is ai.tenum.lua.runtime.LuaString) v.coerceToNumber() else v
                when (numValue) {
                    is LuaLong -> LuaLong(-numValue.value)
                    is LuaDouble -> LuaDouble(-numValue.value)
                    else -> LuaNil
                }
            }
        if (result is LuaNil && value !is LuaNumber) {
            val valueType = LoopOpcodes.getTypeName(value, env)
            val hint = env.getRegisterHintWithRuntime(instr.b, pc)
            val errorMsg =
                if (hint != null) {
                    "attempt to perform arithmetic on a $valueType value ($hint)"
                } else {
                    "attempt to perform arithmetic on a $valueType value"
                }
            env.error(errorMsg, pc)
        }
        env.setRegister(instr.a, result)
        env.debug("  R[${instr.a}] = -R[${instr.b}] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "UNM")
    }

    // Helper functions

    inline fun binaryOpWithMetamethod(
        env: ExecutionEnvironment,
        left: LuaValue<*>,
        right: LuaValue<*>,
        metamethodName: String,
        noinline op: (Double, Double) -> Double,
    ): LuaValue<*> {
        // Check for metamethod first
        checkBinaryMetamethod(env, left, right, metamethodName)?.let { return it }

        // Perform standard arithmetic
        return binaryOp(left, right, env, op)
    }

    inline fun binaryOp(
        left: LuaValue<*>,
        right: LuaValue<*>,
        env: ExecutionEnvironment,
        noinline op: (Double, Double) -> Double,
    ): LuaValue<*> {
        val leftNum = coerceToNumber(left)
        val rightNum = coerceToNumber(right)

        return when {
            // Lua 5.4 semantics: if BOTH operands are integer type, result is integer (with wrapping)
            // Do arithmetic directly in Long space to avoid precision loss from Double conversion
            leftNum is LuaLong && rightNum is LuaLong -> {
                // For add/sub/mul with both integers, do the operation directly on Long values
                // This preserves exact integer semantics even for values beyond 2^53 (Double precision limit)
                val result = op(leftNum.value.toDouble(), rightNum.value.toDouble())
                // Return LuaLong directly using the exact Long result (wrapping 64-bit arithmetic)
                LuaLong(result.toLong())
            }
            leftNum is LuaNumber && rightNum is LuaNumber -> {
                // At least one operand is float type (LuaDouble), so result must be float
                val leftVal = toDouble(leftNum)
                val rightVal = toDouble(rightNum)
                LuaDouble(op(leftVal, rightVal))
            }
            leftNum !is LuaNumber -> throwArithmeticError(leftNum, left, env)
            rightNum !is LuaNumber -> throwArithmeticError(rightNum, right, env)
            else -> throw RuntimeException("attempt to perform arithmetic")
        }
    }

    inline fun divOp(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): LuaValue<*> {
        // Division (/) always returns float in Lua 5.4
        // Try to coerce strings to numbers (Lua semantics)
        val leftNum = coerceToNumber(left)
        val rightNum = coerceToNumber(right)

        return when {
            leftNum is LuaNumber && rightNum is LuaNumber -> {
                LuaDouble(toDouble(leftNum) / toDouble(rightNum))
            }
            else -> LuaNil
        }
    }

    inline fun floorDivOp(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): LuaValue<*> {
        // Try to coerce strings to numbers (Lua semantics)
        val leftNum = coerceToNumber(left)
        val rightNum = coerceToNumber(right)

        return when {
            leftNum is LuaLong && rightNum is LuaLong -> {
                // Integer // Integer -> Integer (Lua 5.4 semantics)
                val a = leftNum.value
                val b = rightNum.value
                // Lua floor division: floor(a/b)
                val q = a / b // Kotlin integer division truncates toward zero
                val r = a % b
                // Adjust for floor semantics if signs differ and there's a remainder
                val result = if ((r != 0L) && ((a xor b) < 0)) q - 1 else q
                LuaLong(result)
            }
            leftNum is LuaNumber && rightNum is LuaNumber -> {
                val leftVal = toDouble(leftNum)
                val rightVal = toDouble(rightNum)
                LuaDouble(floor(leftVal / rightVal))
            }
            else -> LuaNil
        }
    }

    inline fun unaryOpWithMetamethod(
        env: ExecutionEnvironment,
        value: LuaValue<*>,
        metamethodName: String,
        noinline op: (LuaValue<*>) -> LuaValue<*>,
    ): LuaValue<*> {
        // Check for metamethod first
        val meta = env.getMetamethod(value, metamethodName)
        if (meta != null && meta is LuaFunction) {
            env.setMetamethodCallContext(metamethodName)
            val result = env.callFunction(meta, listOf(value))
            return result.firstOrNull() ?: LuaNil
        }

        // Perform standard operation
        return op(value)
    }

    /**
     * Power operation with metamethod support.
     * Unlike other arithmetic operations, power ALWAYS returns float in Lua 5.4.
     */
    inline fun powOpWithMetamethod(
        env: ExecutionEnvironment,
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): LuaValue<*> {
        // Check for metamethod first
        val meta = env.getMetamethod(left, "__pow") ?: env.getMetamethod(right, "__pow")
        if (meta != null && meta is LuaFunction) {
            env.setMetamethodCallContext("__pow")
            val result = env.callFunction(meta, listOf(left, right))
            return result.firstOrNull() ?: LuaNil
        }

        // Perform standard power operation (always returns float)
        return powOp(left, right, env)
    }

    /**
     * Power operation that ALWAYS returns float (Lua 5.4 semantics).
     * Even integer ^ integer results in float (e.g., 2^2 = 4.0, not 4).
     */
    inline fun powOp(
        left: LuaValue<*>,
        right: LuaValue<*>,
        env: ExecutionEnvironment,
    ): LuaValue<*> {
        val leftNum = coerceToNumber(left)
        val rightNum = coerceToNumber(right)

        return when {
            leftNum is LuaNumber && rightNum is LuaNumber -> {
                // Power ALWAYS returns float
                LuaDouble(toDouble(leftNum).pow(toDouble(rightNum)))
            }
            leftNum !is LuaNumber -> throwArithmeticError(leftNum, left, env)
            rightNum !is LuaNumber -> throwArithmeticError(rightNum, right, env)
            else -> throw RuntimeException("attempt to perform arithmetic")
        }
    }
}
