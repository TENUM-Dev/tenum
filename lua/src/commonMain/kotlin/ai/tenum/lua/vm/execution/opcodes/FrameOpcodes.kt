package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.ExecutionEnvironment
import ai.tenum.lua.vm.execution.ExecutionFrame

/**
 * Frame-aware opcode operations that manipulate execution frame state.
 * These opcodes operate on frame-specific state like top, varargs, openUpvalues, toBeClosedVars.
 *
 * NOTE: VARARG and SETLIST cannot be extracted here because they need access to local mutable
 * variables (varargs can be reassigned in TAILCALL, and both need local registers reference).
 */
object FrameOpcodes {
    /**
     * CLOSE: Close upvalues and execute __close metamethods.
     * Mode 0: Close upvalues only (no __close)
     * Mode 1: Mark register as to-be-closed
     * Mode 2: Execute __close for all to-be-closed vars >= register
     */
    inline fun executeClose(
        instr: Instruction,
        frame: ExecutionFrame,
        env: ExecutionEnvironment,
        callCloseFn: (LuaFunction, LuaValue<*>) -> Unit,
    ) {
        val a = instr.a
        val mode = instr.b

        env.debug("[CLOSE] reg=$a mode=$mode toBeClosedVars.size=${frame.toBeClosedVars.size}")

        when (mode) {
            // 0: non-<close> locals, used to close upvalues only (e.g. closures in loops)
            0 -> {
                // intentionally no __close logic here
            }

            // 1: declaration of a <close> local: track this lifetime, but do NOT call __close yet
            1 -> {
                frame.markToBeClosedVar(a)
                env.debug("[CLOSE] mark <close> var at reg=$a")
            }

            // 2: scope exit for <close> locals:
            //    run __close on all tracked vars with reg >= a, in reverse declaration order.
            2 -> {
                env.debug("[CLOSE] scope-exit close for regs >= $a")

                // Work on a snapshot since we'll mutate the original
                val snapshot = frame.toBeClosedVars.toList()

                for ((reg, capturedValue) in snapshot.asReversed()) {
                    if (reg < a) continue // only those in scope
                    // Remove this lifetime from tracking
                    frame.toBeClosedVars.removeAll { it.first == reg && it.second === capturedValue }

                    val mt = capturedValue.metatable as? LuaTable
                    val closeFun =
                        mt?.get(
                            LuaString("__close"),
                        ) as? LuaFunction
                    if (closeFun != null) {
                        env.debug("[CLOSE] calling __close for reg=$reg")
                        // Normal flow: err == nil
                        callCloseFn(closeFun, capturedValue)
                    }
                }
            }

            else -> {
                // Shouldn't happen; treat unknown mode like "just upvalues"
                env.debug("[CLOSE] unknown mode=$mode, treating as mode=0")
            }
        }

        // ALWAYS close upvalues >= this register, regardless of mode
        // This ensures fresh upvalues are created for each loop iteration
        frame.closeUpvaluesFrom(a)
        env.debug("[CLOSE] closed upvalues >= reg=$a")
        env.debug("[REGISTERS after CLOSE] ${frame.registers.slice(0..10).mapIndexed { i, v -> "R[$i]=$v" }.joinToString(", ")}")
        env.debug("[TO-BE-CLOSED after CLOSE] ${frame.toBeClosedVars.map { (reg, v) -> "reg=$reg value=$v" }.joinToString(", ")}")
    }
}
