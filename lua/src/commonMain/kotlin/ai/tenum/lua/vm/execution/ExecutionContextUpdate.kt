package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

// CPD-OFF: Field overlap with ExecutionPreparation is intentional - both represent execution state
// but serve different purposes (initial setup vs. runtime updates)

/**
 * Unified result type for execution context updates.
 *
 * This data class is used by various execution helpers (CloseResumeOrchestrator,
 * SegmentContinuationHandler) to return updated execution context after processing.
 * It eliminates duplication between similar result types.
 */
data class ExecutionContextUpdate(
    val execFrame: ExecutionFrame,
    val currentProto: Proto,
    val registers: MutableList<LuaValue<*>>,
    val constants: List<LuaValue<*>>,
    val instructions: List<Instruction>,
    val pc: Int,
    val openUpvalues: MutableMap<Int, Upvalue>,
    val toBeClosedVars: MutableList<Pair<Int, LuaValue<*>>>,
    val varargs: List<LuaValue<*>>,
    val currentUpvalues: List<Upvalue>,
    val env: ExecutionEnvironment,
    val needsEnvRecreation: Boolean = false,
) {
    companion object {
        /**
         * Create ExecutionContextUpdate from an ExecutionFrame.
         * This factory method eliminates duplication across multiple call sites.
         */
        fun fromFrame(
            frame: ExecutionFrame,
            proto: Proto,
            env: ExecutionEnvironment,
            needsEnvRecreation: Boolean = false,
        ) = ExecutionContextUpdate(
            execFrame = frame,
            currentProto = proto,
            registers = frame.registers,
            constants = proto.constants,
            instructions = proto.instructions,
            pc = frame.pc,
            openUpvalues = frame.openUpvalues,
            toBeClosedVars = frame.toBeClosedVars,
            varargs = frame.varargs,
            currentUpvalues = frame.upvalues,
            env = env,
            needsEnvRecreation = needsEnvRecreation,
        )
    }
}
