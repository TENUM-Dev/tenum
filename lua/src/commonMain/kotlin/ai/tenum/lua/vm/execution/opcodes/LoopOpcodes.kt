@file:Suppress("NOTHING_TO_INLINE")

package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.ExecutionEnvironment
import ai.tenum.lua.vm.execution.FunctionNameSource
import ai.tenum.lua.vm.execution.InferredFunctionName

/**
 * Loop operation helpers for the Lua VM.
 * These functions implement loop opcodes (FORPREP, FORLOOP, TFORCALL, TFORLOOP).
 */
object LoopOpcodes {
    /**
     * FORPREP: Prepare numeric for loop.
     * R[A] := R[A] - R[A+2] (init - step), then PC += sBx
     * Sets up the loop counter before entering the loop body.
     *
     * In Lua 5.4, if init, limit, and step are all integers, the loop uses integer arithmetic.
     */
    inline fun executeForPrep(
        instr: Instruction,
        env: ExecutionEnvironment,
        currentPc: Int,
    ): Int {
        val a = instr.a

        val initVal = env.registers[a]
        val limitVal = env.registers[a + 1]
        val stepVal = env.registers[a + 2]

        // Check if all three values are integers
        val useIntegerLoop = initVal is LuaLong && limitVal is LuaLong && stepVal is LuaLong

        if (useIntegerLoop) {
            // Integer for-loop: preserve integer types
            val init = (initVal as LuaLong).value
            val limit = (limitVal as LuaLong).value
            val step = (stepVal as LuaLong).value

            val startIndex = init - step
            env.registers[a] = LuaLong(startIndex)
            env.registers[a + 1] = LuaLong(limit)
            env.registers[a + 2] = LuaLong(step)

            env.debug(
                "  FORPREP (integer): init=$init, limit=$limit, step=$step -> index=$startIndex, jump to PC=${currentPc + instr.b + 1}",
            )
        } else {
            // Floating-point for-loop: coerce to numbers
            val init = coerceNumberForLoop(initVal, "initial value", env, currentPc)
            val limit = coerceNumberForLoop(limitVal, "limit", env, currentPc)
            val step = coerceNumberForLoop(stepVal, "step", env, currentPc)

            val startIndex = init - step
            env.registers[a] = LuaDouble(startIndex)
            env.registers[a + 1] = LuaDouble(limit)
            env.registers[a + 2] = LuaDouble(step)

            env.debug("  FORPREP (float): init=$init, limit=$limit, step=$step -> index=$startIndex, jump to PC=${currentPc + instr.b + 1}")
        }

        val newPc = currentPc + instr.b
        return newPc
    }

    /**
     * FORLOOP: Numeric for loop iteration.
     * R[A] += R[A+2] (index += step)
     * If loop continues: R[A+3] = R[A], PC += sBx
     * Otherwise fall through to exit loop.
     *
     * In Lua 5.4, integer loops preserve integer types throughout.
     */
    inline fun executeForLoop(
        instr: Instruction,
        env: ExecutionEnvironment,
        currentPc: Int,
    ): Int? {
        val a = instr.a

        val indexVal = env.registers[a]
        val limitVal = env.registers[a + 1]
        val stepVal = env.registers[a + 2]

        // Check if this is an integer loop (all values are LuaLong)
        val useIntegerLoop = indexVal is LuaLong && limitVal is LuaLong && stepVal is LuaLong

        return if (useIntegerLoop) {
            // Integer for-loop
            val step = (stepVal as LuaLong).value
            val index = (indexVal as LuaLong).value + step
            env.registers[a] = LuaLong(index)

            val limit = (limitVal as LuaLong).value

            // Continue condition:
            //   if step > 0 then index <= limit
            //   else          index >= limit
            val shouldLoop =
                (step > 0 && index <= limit) ||
                    (step < 0 && index >= limit)

            if (shouldLoop) {
                // Copy index to visible loop variable (A+3)
                env.registers[a + 3] = LuaLong(index)

                // pc += B (jump back into loop body)
                val newPc = currentPc + instr.b
                env.debug("  FORLOOP (integer): index=$index, limit=$limit, step=$step -> continue (jump to PC=${newPc + 1})")
                newPc
            } else {
                env.debug("  FORLOOP (integer): index=$index, limit=$limit, step=$step -> exit loop")
                null // fall through: loop exits
            }
        } else {
            // Floating-point for-loop
            val step = coerceNumberForLoop(stepVal, "step", env, currentPc)
            val index = coerceNumberForLoop(indexVal, "initial value", env, currentPc) + step
            env.registers[a] = LuaDouble(index)

            val limit = coerceNumberForLoop(limitVal, "limit", env, currentPc)

            // Continue condition:
            //   if step > 0 then index <= limit
            //   else          index >= limit
            val shouldLoop =
                (step > 0 && index <= limit) ||
                    (step < 0 && index >= limit)

            if (shouldLoop) {
                // Copy index to visible loop variable (A+3)
                env.registers[a + 3] = LuaDouble(index)

                // pc += B (jump back into loop body)
                val newPc = currentPc + instr.b
                env.debug("  FORLOOP (float): index=$index, limit=$limit, step=$step -> continue (jump to PC=${newPc + 1})")
                newPc
            } else {
                env.debug("  FORLOOP (float): index=$index, limit=$limit, step=$step -> exit loop")
                null // fall through: loop exits
            }
        }
    }

    /**
     * TFORCALL: Call iterator function for generic for loop.
     * R[A+3], ..., R[A+2+C] := R[A](R[A+1], R[A+2])
     * Calls iterator function with state and control variables, stores results in loop variables.
     */
    inline fun executeTForCall(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        val func = env.registers[instr.a]
        val state = env.registers[instr.a + 1]
        val control = env.registers[instr.a + 2]

        env.debug("  TFORCALL: func=R[${instr.a}], state=R[${instr.a + 1}], control=R[${instr.a + 2}]")
        env.debug("    func=$func")
        env.debug("    state=$state")
        env.debug("    control=$control")

        // Set function name as "for iterator" for debug.getinfo
        env.setPendingInferredName(InferredFunctionName("for iterator", FunctionNameSource.ForIterator))

        val results = env.callFunction(func, listOf(state, control))

        env.debug("    results=$results (${results.size} values)")

        // Fill all loop variable slots with results, using nil for missing values
        for (i in 0 until instr.c) {
            env.registers[instr.a + 3 + i] = results.getOrElse(i) { LuaNil }
            env.debug("    R[${instr.a + 3 + i}] = ${results.getOrElse(i) { LuaNil }}")
        }
    }

    /**
     * TFORLOOP: Generic for loop iteration control.
     * If R[A+1] != nil: R[A] = R[A+1], continue loop
     * Otherwise: PC += sBx (exit loop)
     */
    inline fun executeTForLoop(
        instr: Instruction,
        env: ExecutionEnvironment,
        currentPc: Int,
    ): Int? {
        // TFORLOOP: instr.a points to control variable
        // Register layout: a-2=func, a-1=state, a=control, a+1=first loop var, a+2=second loop var...
        // Check if first loop variable (at a+1) is nil
        val firstLoopVar = env.registers[instr.a + 1]
        env.debug("  TFORLOOP: check R[${instr.a + 1}] = $firstLoopVar")
        return if (firstLoopVar != LuaNil) {
            // Not nil: copy first loop var to control variable (a) and continue to loop body
            env.setRegister(instr.a, firstLoopVar)
            env.debug("    Not nil: R[${instr.a}] = $firstLoopVar, continue loop")
            null
        } else {
            // Nil: exit loop by jumping forward
            val newPc = currentPc + instr.b
            env.debug("    Nil: exit loop, jump to PC=$newPc")
            newPc
        }
    }

    inline fun coerceNumberForLoop(
        value: LuaValue<*>,
        role: String,
        env: ExecutionEnvironment,
        pc: Int,
    ): Double =
        when (value) {
            is LuaLong -> value.value.toDouble()
            is LuaDouble -> value.value
            is LuaString -> value.value.toDoubleOrNull() ?: badForLoop(role, value, env, pc)
            else -> badForLoop(role, value, env, pc)
        }

    inline fun badForLoop(
        role: String,
        value: LuaValue<*>,
        env: ExecutionEnvironment,
        pc: Int,
    ): Nothing {
        val typeName = getTypeName(value, env)
        env.error("bad 'for' $role (number expected, got $typeName)", pc)
    }

    /**
     * Get the type name for a value, respecting __name metatable field.
     * This is used for error messages to show custom type names like "FILE*".
     */
    fun getTypeName(
        value: LuaValue<*>,
        env: ExecutionEnvironment,
    ): String {
        val metaName = (value.metatable as? LuaTable)?.get(LuaString("__name")) as? LuaString
        return metaName?.value ?: value.type().name.lowercase()
    }
}
