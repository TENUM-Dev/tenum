package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaFunction
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
    data class ExitProto(val returnValues: List<LuaValue<*>>) : ReturnLoopAction()
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
}
