package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

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
)
