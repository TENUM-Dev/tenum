package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

/**
 * State required for handling dispatch results.
 */
data class DispatchHandlerState(
    var currentProto: Proto,
    var execFrame: ExecutionFrame,
    var registers: MutableList<LuaValue<*>>,
    var constants: List<LuaValue<*>>,
    var instructions: List<Instruction>,
    var pc: Int,
    var openUpvalues: MutableMap<Int, Upvalue>,
    var toBeClosedVars: MutableList<Pair<Int, LuaValue<*>>>,
    var varargs: List<LuaValue<*>>,
    var currentUpvalues: List<Upvalue>,
    var env: ExecutionEnvironment,
    var lastLine: Int,
    var pendingInferredName: InferredFunctionName?,
)
