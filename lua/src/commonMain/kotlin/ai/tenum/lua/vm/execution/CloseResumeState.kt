package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.CallFrame

/**
 * Represents a single frame segment in the close-yield resumption pipeline.
 * Each segment corresponds to a frame that needs to complete its RETURN or CLOSE
 * instruction after __close handlers finish.
 *
 * The segment stack models the unwind-and-resume path: after completing inner segments,
 * we rebuild each outer segment and re-enter dispatch so its RETURN/CLOSE executes
 * with proper TBC processing.
 */
data class OwnerSegment(
    /** The proto of the frame to resume */
    val proto: Proto,
    /** PC to resume at (should point to RETURN or CLOSE instruction) */
    val pcToResume: Int,
    /** Register snapshot for this frame */
    val registers: MutableList<LuaValue<*>>,
    /** Upvalue snapshot */
    val upvalues: List<Upvalue>,
    /** Varargs snapshot */
    val varargs: List<LuaValue<*>>,
    /** TBC variables for this frame (shared reference, not copied) */
    val toBeClosedVars: MutableList<Pair<Int, LuaValue<*>>>,
    /** Captured return values if this frame's RETURN already captured them */
    val capturedReturns: List<LuaValue<*>>?,
    /** Start register for CLOSE operation */
    val pendingCloseStartReg: Int,
    /** Currently processing TBC variable */
    val pendingCloseVar: Pair<Int, LuaValue<*>>?,
    /** Exec stack for this frame */
    val execStack: List<ExecContext>,
    /** Debug call stack */
    val debugCallStack: List<CallFrame>,
    /** Whether this segment is mid-RETURN (vs mid-CLOSE) */
    val isMidReturn: Boolean,
)

/**
 * Encapsulates all state needed to resume a coroutine after yielding from a __close metamethod.
 *
 * New architecture: instead of a single owner frame, we maintain an ordered stack of
 * owner segments (innermost to outermost). Resumption walks this stack, rebuilding each
 * frame and letting its RETURN/CLOSE execute. This correctly handles multi-frame chains
 * including native boundaries (pcall).
 */
data class CloseResumeState(
    /**
     * Continuation of the __close metamethod that yielded.
     * This allows resuming the metamethod where it left off.
     */
    val pendingCloseContinuation: ResumptionState?,
    /**
     * Stack of owner frame segments to process, ordered innermost to outermost.
     * Each segment will be rebuilt and resumed in sequence until all complete.
     */
    val ownerSegments: List<OwnerSegment>,
    /**
     * Error argument to pass to subsequent __close handlers (for error chaining).
     */
    val errorArg: LuaValue<*>,
)
