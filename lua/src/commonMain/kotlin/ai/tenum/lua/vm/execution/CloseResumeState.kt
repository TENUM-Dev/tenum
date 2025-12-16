package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

/**
 * Encapsulates all state needed to resume a coroutine after yielding from a __close metamethod.
 * 
 * This aggregates:
 * - The __close function continuation (for resuming the metamethod itself)
 * - The owner frame snapshot (the function that triggered CLOSE/RETURN)
 * - The to-be-closed variables list with captured values
 * - Close operation parameters
 * 
 * By keeping this as a cohesive state object, we avoid scattered VM globals and ensure
 * proper restoration of the caller's execution context after __close handlers finish.
 */
data class CloseResumeState(
    /**
     * Continuation of the __close metamethod that yielded.
     * This allows resuming the metamethod where it left off.
     */
    val pendingCloseContinuation: ResumptionState?,
    
    /**
     * Snapshot of the owner frame (the function that triggered CLOSE/RETURN)
     * before entering the __close handler.
     */
    val ownerProto: Proto,
    val ownerPc: Int,
    val ownerRegisters: MutableList<LuaValue<*>>,
    val ownerUpvalues: List<Upvalue>,
    val ownerVarargs: List<LuaValue<*>>,
    
    /**
     * The register index where close operations start (a in CLOSE a).
     */
    val startReg: Int,
    
    /**
     * Remaining to-be-closed variables with their captured values.
     * Using captured values ensures values don't drift if registers are overwritten.
     * Format: List of (regIndex, capturedValue) pairs.
     */
    val pendingTbcList: List<Pair<Int, LuaValue<*>>>,
    
    /**
     * The specific TBC variable currently being closed (if any).
     */
    val pendingCloseVar: Pair<Int, LuaValue<*>>?,
    
    /**
     * Error argument to pass to subsequent __close handlers (for error chaining).
     */
    val errorArg: LuaValue<*>,
    
    /**
     * Captured return values from the owner frame (if this was triggered by RETURN).
     * If non-null, these should be returned after all __close handlers complete.
     */
    val capturedReturnValues: List<LuaValue<*>>?
)
