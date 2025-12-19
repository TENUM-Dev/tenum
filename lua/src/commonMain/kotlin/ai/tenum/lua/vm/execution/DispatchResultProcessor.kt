package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.VmDebugSink

/**
 * Result of processing a RETURN dispatch result.
 * Tells the loop what action to take next.
 */
sealed class ReturnLoopAction {
    /**
     * Unwind to caller - restore caller's state and continue execution.
     */
    data class UnwindToCaller(
        val callerContext: ExecContext,
        val returnValues: List<LuaValue<*>>,
    ) : ReturnLoopAction()

    /**
     * Process next segment in close resume chain.
     */
    data class ContinueSegment(
        val nextSegment: OwnerSegment,
        val segmentReturnValues: List<LuaValue<*>>,
        val remainingSegments: List<OwnerSegment>,
    ) : ReturnLoopAction()

    /**
     * Continue outer frame with TBC after segments complete.
     */
    data class ContinueOuterFrame(
        val outerFrame: OwnerSegment,
        val finalReturnValues: List<LuaValue<*>>,
    ) : ReturnLoopAction()

    /**
     * Exit executeProto with return values.
     */
    data class ExitProto(
        val returnValues: List<LuaValue<*>>,
    ) : ReturnLoopAction()
}

/**
 * Result of processing a CALL trampoline dispatch result.
 * Provides the new execution state without modifying the loop.
 */
data class CallTrampolineAction(
    val newProto: Proto,
    val newFrame: ExecutionFrame,
    val newUpvalues: List<Upvalue>,
    val luaFunc: LuaFunction,
)

/**
 * Result of processing a TAILCALL trampoline dispatch result.
 * Provides the state changes for TCO without modifying the loop.
 */
data class TailCallAction(
    val newProto: Proto,
    val newFrame: ExecutionFrame,
    val newUpvalues: List<Upvalue>,
    val luaFunc: LuaFunction,
    val newTailCallDepth: Int,
)

/**
 * Helper for processing DispatchResult actions.
 * Returns what the loop should do, but doesn't modify loop state directly.
 */
class DispatchResultProcessor(
    private val debugSink: VmDebugSink,
) {
    /**
     * Process a RETURN result to determine loop action.
     *
     * @param dispatchResult The return result from opcode dispatch
     * @param execStack The trampoline execution stack
     * @param execFrame Current execution frame
     * @param activeCloseState Current close resume state (if any)
     * @return The action the loop should take
     */
    fun processReturn(
        dispatchResult: DispatchResult.Return,
        execStack: ArrayDeque<ExecContext>,
        execFrame: ExecutionFrame,
        activeCloseState: CloseResumeState?,
    ): ReturnLoopAction {
        // Check if there's a caller waiting (trampolined call stack)
        if (execStack.isNotEmpty()) {
            val callerContext = execStack.removeLast()
            debugSink.debug {
                "[RETURN unwind] Restoring caller, TBC list size=${callerContext.execFrame.toBeClosedVars.size}"
            }
            return ReturnLoopAction.UnwindToCaller(callerContext, dispatchResult.values)
        }

        // No caller on stack - check if there are remaining segments to process
        if (activeCloseState != null && activeCloseState.ownerSegments.isNotEmpty()) {
            // Get return values from the just-completed segment
            val segmentReturnValues =
                activeCloseState.pendingReturnValues
                    ?: execFrame.capturedReturns
                    ?: dispatchResult.values

            val nextSegment = activeCloseState.ownerSegments.first()
            val remainingSegments = activeCloseState.ownerSegments.drop(1)

            debugSink.debug {
                "[Segment Orchestrator] First segment completed, processing next segment: proto=${nextSegment.proto.name}"
            }

            return ReturnLoopAction.ContinueSegment(
                nextSegment = nextSegment,
                segmentReturnValues = segmentReturnValues,
                remainingSegments = remainingSegments,
            )
        }

        // No remaining segments - check for outer frames with TBC
        val outerFrames = activeCloseState?.closeOwnerFrameStack ?: emptyList()
        val nonSegmentFrame = outerFrames.firstOrNull { !it.isMidReturn }

        val finalReturn =
            activeCloseState?.pendingReturnValues
                ?: execFrame.capturedReturns
                ?: dispatchResult.values

        if (nonSegmentFrame != null) {
            debugSink.debug {
                "[SEGMENT FINAL] Continuing outer frame: proto=${nonSegmentFrame.proto.name} pc=${nonSegmentFrame.pcToResume}"
            }
            return ReturnLoopAction.ContinueOuterFrame(nonSegmentFrame, finalReturn)
        }

        // No outer frames with TBC - return to coroutine caller
        debugSink.debug {
            "[SEGMENT FINAL] No outer frame, returning ${finalReturn.size} values to coroutine caller."
        }
        return ReturnLoopAction.ExitProto(finalReturn)
    }

    /**
     * Process a CallTrampoline to prepare the new execution state.
     *
     * @param dispatchResult The call trampoline result
     * @return The new execution state for the callee
     */
    fun processCallTrampoline(dispatchResult: DispatchResult.CallTrampoline): CallTrampolineAction {
        debugSink.debug { "  Trampolining into regular call" }

        val funcVal = dispatchResult.newProto
        val args = dispatchResult.newArgs
        val calleeUpvalues = dispatchResult.newUpvalues

        // Create new execution frame for callee
        val execFrame =
            ExecutionFrame(
                proto = funcVal,
                initialArgs = args,
                upvalues = calleeUpvalues,
                initialPc = 0,
                existingRegisters = null, // Fresh registers for callee
                existingVarargs =
                    if (funcVal.hasVararg && args.size > funcVal.parameters.size) {
                        args.subList(funcVal.parameters.size, args.size)
                    } else {
                        emptyList()
                    },
            )

        return CallTrampolineAction(
            newProto = funcVal,
            newFrame = execFrame,
            newUpvalues = calleeUpvalues,
            luaFunc = dispatchResult.savedFunc,
        )
    }

    /**
     * Process a TailCallTrampoline to prepare the TCO state.
     *
     * @param dispatchResult The tail call trampoline result
     * @param currentRegisters Current register array to reuse
     * @param currentCallDepth Current tail call depth
     * @return The new execution state for TCO
     */
    fun processTailCallTrampoline(
        dispatchResult: DispatchResult.TailCallTrampoline,
        currentRegisters: MutableList<LuaValue<*>>,
        currentCallDepth: Int,
    ): TailCallAction {
        val funcVal = dispatchResult.newProto
        val args = dispatchResult.newArgs
        val calleeUpvalues = dispatchResult.newUpvalues

        // TCO: Move arguments from `args` to the beginning of the current register set.
        val calleeNumParams = funcVal.parameters.size
        currentRegisters.fill(LuaNil)

        for (i in 0 until calleeNumParams) {
            currentRegisters[i] = args.getOrElse(i) { LuaNil }
        }

        // Recalculate varargs for the new frame
        val varargs =
            if (funcVal.hasVararg && args.size > calleeNumParams) {
                args.subList(calleeNumParams, args.size)
            } else {
                emptyList()
            }

        // Create NEW execution frame for the tail-called function
        val execFrame =
            ExecutionFrame(
                proto = funcVal,
                initialArgs = args,
                upvalues = calleeUpvalues,
                initialPc = 0,
                existingRegisters = currentRegisters,
                existingVarargs = varargs,
            )

        // Calculate new tail call depth
        val newTailCallDepth = currentCallDepth + 1

        return TailCallAction(
            newProto = funcVal,
            newFrame = execFrame,
            newUpvalues = calleeUpvalues,
            luaFunc = dispatchResult.savedFunc,
            newTailCallDepth = newTailCallDepth,
        )
    }
}
