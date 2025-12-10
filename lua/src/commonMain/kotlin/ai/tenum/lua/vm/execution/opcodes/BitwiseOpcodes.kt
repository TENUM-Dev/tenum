package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.BinaryMetamethodDispatcher
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Bitwise operation helpers for the Lua VM.
 * These functions implement bitwise opcodes (BAND, BOR, BXOR, SHL, SHR, BNOT).
 */
object BitwiseOpcodes {
    /**
     * Check if a double value has a valid integer representation.
     * In Lua, a float must be exactly equal to its integer conversion.
     *
     * Note: Kotlin's Double.toLong() saturates at Long.MIN_VALUE and Long.MAX_VALUE.
     * For values outside the Long range, the roundtrip test (value != value.toLong().toDouble())
     * will correctly detect the mismatch because the saturation changes the value.
     *
     * Critical edge case: 2.0^63 (9.223372036854776E18) equals Long.MAX_VALUE.toDouble()
     * due to precision loss, but when converted: 2.0^63.toLong() = Long.MAX_VALUE,
     * and Long.MAX_VALUE.toDouble() = 9.223372036854776E18 = 2.0^63, so they match!
     *
     * However, Long.MAX_VALUE (9223372036854775807) as double is also 9.223372036854776E18.
     * The roundtrip works: 9.223372036854776E18.toLong() = 9223372036854775807,
     * and 9223372036854775807.toDouble() = 9.223372036854776E18 (matches input).
     *
     * For 2.0^63 + anything larger: the toLong() saturates at MAX_VALUE, but then
     * MAX_VALUE.toDouble() won't equal the original larger value, so it fails correctly.
     */
    fun hasIntegerRepresentation(value: Double): Boolean {
        // Check if the value is finite
        if (!value.isFinite()) return false

        // Check if value has a fractional part
        if (value != kotlin.math.floor(value)) return false

        // Check exact bounds: values must be strictly within Long range
        // Due to floating point precision, values near the boundaries may round.
        // - Long.MAX_VALUE is 9223372036854775807, but as double it becomes 9223372036854775808.0
        // - So we reject >= 9223372036854775808.0 (which is 2^63)
        // - Long.MIN_VALUE is exactly representable as -9223372036854775808.0
        // - Values below this (like -2^63-1) also round to -9223372036854775808.0
        // - So we need to accept exactly MIN_VALUE but reject values that would go below
        if (value >= 9223372036854775808.0) { // >= 2^63
            return false
        }

        // For negative values: the tricky part is that values slightly below MIN_VALUE
        // will round TO MIN_VALUE as a double, so roundtrip test is needed
        // But we know that if value < Long.MIN_VALUE as double, it's definitely invalid

        // Roundtrip test: this catches values that round to MIN/MAX but aren't actually valid
        val asLong = value.toLong()
        return value == asLong.toDouble()
    }

    /**
     * Convert a LuaValue to Long, checking for valid integer representation.
     * Throws appropriate error if float cannot be represented as integer.
     */
    fun toLongWithCheck(
        value: LuaValue<*>,
        registerIndex: Int,
        env: ExecutionEnvironment,
        pc: Int,
    ): Long =
        when (value) {
            is LuaLong -> value.value
            is LuaDouble -> {
                if (!hasIntegerRepresentation(value.value)) {
                    val nameHint = env.getRegisterHintWithRuntime(registerIndex, pc)
                    val qualifier = if (nameHint != null) "($nameHint) " else ""
                    env.luaError("number ${qualifier}has no integer representation")
                }
                value.value.toLong()
            }
            else -> {
                val typeName = LoopOpcodes.getTypeName(value, env)
                throw RuntimeException("attempt to perform bitwise operation on a $typeName value")
            }
        }

    /**
     * BAND: Bitwise AND operation.
     * R[A] := RK[B] & RK[C]
     */
    inline fun executeBand(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        executeBinaryBitwise(instr, env, pc, "__band", "&") { a, b -> a and b }
    }

    /**
     * BOR: Bitwise OR operation.
     * R[A] := RK[B] | RK[C]
     */
    inline fun executeBor(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        executeBinaryBitwise(instr, env, pc, "__bor", "|") { a, b -> a or b }
    }

    /**
     * BXOR: Bitwise XOR operation.
     * R[A] := RK[B] ~ RK[C]
     */
    inline fun executeBxor(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        executeBinaryBitwise(instr, env, pc, "__bxor", "~") { a, b -> a xor b }
    }

    /**
     * SHL: Bitwise left shift operation.
     * R[A] := RK[B] << RK[C]
     * Negative shifts perform right logical shift.
     */
    inline fun executeShl(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        executeShiftWithMetamethod(instr, env, pc, "__shl", true, "<<")
    }

    @PublishedApi
    internal inline fun executeShiftWithMetamethod(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
        metamethod: String,
        isLeftShift: Boolean,
        debugOp: String,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)

        // Try metamethod dispatch first
        val metaResult = BinaryMetamethodDispatcher.tryDispatch(left, right, metamethod, env)
        if (metaResult != null) {
            env.setRegister(instr.a, metaResult)
        } else {
            env.setRegister(instr.a, shiftOp(left, right, instr.b, instr.c, pc, env, isLeftShift))
        }
        env.debug("  R[${instr.a}] = RK[${instr.b}] $debugOp RK[${instr.c}] (${env.registers[instr.a]})")
    }

    /**
     * SHR: Bitwise right shift operation (logical).
     * R[A] := RK[B] >> RK[C]
     * Negative shifts perform left shift.
     */
    inline fun executeShr(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        executeShiftWithMetamethod(instr, env, pc, "__shr", false, ">>")
    }

    /**
     * BNOT: Bitwise NOT (complement) operation.
     * R[A] := ~RK[B]
     */
    inline fun executeBnot(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val value = getRKValue(instr.b, env)
        val meta = env.getMetamethod(value, "__bnot")
        if (meta != null && meta is LuaFunction) {
            env.setMetamethodCallContext("__bnot")
            val result = env.callFunction(meta, listOf(value))
            env.setRegister(instr.a, result.firstOrNull() ?: LuaNil)
        } else {
            val intValue = toLongWithCheck(value, instr.b, env, pc)
            env.setRegister(instr.a, LuaLong(intValue.inv()))
        }
        env.debug("  R[${instr.a}] = ~RK[${instr.b}] (${env.registers[instr.a]})")
    }

    // Helper functions

    inline fun executeBinaryBitwise(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
        metamethodName: String,
        debugSymbol: String,
        noinline op: (Long, Long) -> Long,
    ) {
        val left = getRKValue(instr.b, env)
        val right = getRKValue(instr.c, env)
        env.setRegister(instr.a, binaryBitwiseWithMetamethod(env, left, right, instr.b, instr.c, pc, metamethodName, op))
        env.debug("  R[${instr.a}] = R[${instr.b}] $debugSymbol R[${instr.c}] (${env.registers[instr.a]})")
    }

    fun binaryBitwiseWithMetamethod(
        env: ExecutionEnvironment,
        left: LuaValue<*>,
        right: LuaValue<*>,
        leftRegister: Int,
        rightRegister: Int,
        pc: Int,
        metamethodName: String,
        op: (Long, Long) -> Long,
    ): LuaValue<*> {
        // Check for metamethod first
        checkBinaryMetamethod(env, left, right, metamethodName)?.let { return it }

        // Perform standard bitwise operation with integer representation check
        val leftVal = toLongWithCheck(left, leftRegister, env, pc)
        val rightVal = toLongWithCheck(right, rightRegister, env, pc)
        return LuaLong(op(leftVal, rightVal))
    }

    fun shiftOp(
        left: LuaValue<*>,
        right: LuaValue<*>,
        leftRegister: Int,
        rightRegister: Int,
        pc: Int,
        env: ExecutionEnvironment,
        isLeftShift: Boolean,
    ): LuaValue<*> {
        val leftVal = toLongWithCheck(left, leftRegister, env, pc)
        val shiftVal = toLongWithCheck(right, rightRegister, env, pc)

        val numbits = 64L
        val s = shiftVal
        val resLong: Long =
            if (isLeftShift) {
                // Left shift: negative shift performs right logical shift
                if (s < 0L) {
                    if (s == Long.MIN_VALUE) {
                        0L
                    } else {
                        val neg = -s
                        if (neg >= numbits) 0L else (leftVal ushr neg.toInt())
                    }
                } else {
                    if (s >= numbits) 0L else (leftVal shl s.toInt())
                }
            } else {
                // Right shift: negative shift performs left shift
                if (s < 0L) {
                    if (s == Long.MIN_VALUE) {
                        0L
                    } else {
                        val neg = -s
                        if (neg >= numbits) 0L else (leftVal shl neg.toInt())
                    }
                } else {
                    if (s >= numbits) 0L else (leftVal ushr s.toInt())
                }
            }

        return LuaLong(resLong)
    }
}
