package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Coerces a value to number, attempting string-to-number conversion per Lua semantics.
 * Returns the original value if no coercion is possible.
 */
@PublishedApi
internal inline fun coerceToNumber(value: LuaValue<*>): LuaValue<*> =
    when {
        value is LuaNumber -> value
        value is LuaString -> value.coerceToNumber()
        else -> value
    }

/**
 * Extracts double value from a LuaNumber (handles both LuaLong and LuaDouble).
 */
@PublishedApi
internal inline fun toDouble(num: LuaNumber): Double =
    if (num is LuaLong) {
        num.value.toDouble()
    } else {
        (num as LuaDouble).value
    }

/**
 * Throws an arithmetic error for invalid operand types.
 */
@PublishedApi
internal inline fun throwArithmeticError(
    operand: LuaValue<*>,
    originalValue: LuaValue<*>,
    env: ExecutionEnvironment,
): Nothing {
    val typeName = LoopOpcodes.getTypeName(originalValue, env)
    throw RuntimeException("attempt to perform arithmetic on a $typeName value")
}

/**
 * Get the value from either a register or constant pool based on RK encoding.
 * Lua bytecode uses bit 8 of operands: if set, value is in constants, else in registers.
 *
 * Note: When register indices exceed 255, bit 8 is set accidentally, making them
 * look like RK constants. If the decoded constant index is out of bounds, treat as register.
 *
 * This function consolidates the getRKValue() logic that was duplicated across:
 * - ArithmeticOpcodes.kt
 * - BitwiseOpcodes.kt
 * - StringOpcodes.kt
 */
@PublishedApi
internal inline fun getRKValue(
    operand: Int,
    env: ExecutionEnvironment,
): LuaValue<*> =
    if ((operand and 256) != 0) {
        val constIndex = operand and 255
        if (constIndex < env.constants.size) {
            env.constants[constIndex]
        } else {
            // Constant index out of bounds - treat as register instead
            // This handles register indices >255 that have bit 8 set
            // Use constIndex (0-255) as the register index, not the full operand
            env.registers[constIndex]
        }
    } else {
        env.registers[operand]
    }

/**
 * Handle arithmetic operation error with proper register hints.
 * Consolidates error handling logic that was duplicated across ADD, SUB, MUL, etc.
 */
inline fun handleArithmeticError(
    e: RuntimeException,
    left: ai.tenum.lua.runtime.LuaValue<*>,
    right: ai.tenum.lua.runtime.LuaValue<*>,
    leftOperand: Int,
    rightOperand: Int,
    pc: Int,
    env: ExecutionEnvironment,
): Nothing {
    val hint =
        when {
            left !is ai.tenum.lua.runtime.LuaNumber -> env.getRegisterHintWithRuntime(leftOperand, pc)
            right !is ai.tenum.lua.runtime.LuaNumber -> env.getRegisterHintWithRuntime(rightOperand, pc)
            else -> null
        }
    val errorMsg =
        if (hint != null) {
            "${e.message} ($hint)"
        } else {
            e.message
        }
    env.error(errorMsg ?: "arithmetic error", pc)
}

/**
 * Checks for and invokes a metamethod for binary operations.
 * Returns the metamethod result if found, null otherwise.
 * Consolidates metamethod checking logic duplicated across ArithmeticOpcodes and BitwiseOpcodes.
 */
@PublishedApi
internal inline fun checkBinaryMetamethod(
    env: ExecutionEnvironment,
    left: LuaValue<*>,
    right: LuaValue<*>,
    metamethodName: String,
): LuaValue<*>? {
    val meta = env.getMetamethod(left, metamethodName) ?: env.getMetamethod(right, metamethodName)
    if (meta != null && meta is ai.tenum.lua.runtime.LuaFunction) {
        env.setMetamethodCallContext(metamethodName)
        val result = env.callFunction(meta, listOf(left, right))
        return result.firstOrNull() ?: ai.tenum.lua.runtime.LuaNil
    }
    return null
}
