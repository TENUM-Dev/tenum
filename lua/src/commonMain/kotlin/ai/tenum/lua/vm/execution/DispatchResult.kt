package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

/**
 * Saved execution context for a caller when trampolining into a callee.
 * Holds all state needed to restore the caller after the callee returns.
 *
 * Note: openUpvalues and toBeClosedVars are NOT saved here because they are
 * global state that must persist across stack frames (upvalues can be shared
 * between closures created in different frames).
 */
data class ExecContext(
    val proto: Proto,
    val execFrame: ExecutionFrame,
    val registers: MutableList<LuaValue<*>>,
    val constants: List<LuaValue<*>>,
    val instructions: List<Instruction>,
    val pc: Int, // PC to resume at after call returns
    val varargs: List<LuaValue<*>>,
    val currentUpvalues: List<Upvalue>,
    val callInstruction: Instruction, // The CALL instruction that triggered this
    val lastLine: Int, // Last line number seen (for LINE hook deduplication)
)

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

    /** Regular call trampoline - push new execution context (non-tail call) */
    data class CallTrampoline(
        val newProto: Proto,
        val newArgs: List<LuaValue<*>>,
        val newUpvalues: List<Upvalue>,
        val savedFunc: LuaCompiledFunction,
        val callInstruction: Instruction, // Original CALL instruction with target reg (a) and result count (c)
    ) : DispatchResult()
}
