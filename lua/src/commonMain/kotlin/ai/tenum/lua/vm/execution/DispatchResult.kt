package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

/**
 * Result of opcode dispatch - indicates control flow changes
 */
sealed class DispatchResult {
    /** Continue to next instruction normally */
    object Continue : DispatchResult()

    /** Skip next instruction (LOADBOOL with skip) */
    object SkipNext : DispatchResult()

    /** Jump to specific PC */
    data class Jump(
        val newPc: Int,
    ) : DispatchResult()

    /** Return from function */
    data class Return(
        val values: List<LuaValue<*>>,
    ) : DispatchResult()

    /** Tail call trampoline - replace execution context */
    data class TailCallTrampoline(
        val newProto: Proto,
        val newArgs: List<LuaValue<*>>,
        val newUpvalues: List<Upvalue>,
        val savedFunc: LuaCompiledFunction,
    ) : DispatchResult()
}
