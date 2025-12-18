package ai.tenum.lua.vm.execution

import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.CloseContext
import ai.tenum.lua.vm.ExecutionFlowState
import ai.tenum.lua.vm.OpCode
import ai.tenum.lua.vm.VmDebugSink

/**
 * Handler for segment continuation logic in RETURN processing.
 *
 * This class handles two types of segment continuations:
 * 1. ContinueSegment - Process next segment in close resume chain
 * 2. ContinueOuterFrame - Continue outer frame with TBC after segments complete
 *
 * These operations involve complex frame rebuilding, register restoration,
 * and close state management that were previously inline in the main loop.
 */
class SegmentContinuationHandler(
    private val debugSink: VmDebugSink,
    private val globals: MutableMap<String, LuaValue<*>>,
    private val closeContext: CloseContext,
    private val flowState: ExecutionFlowState,
    private val vmCapabilities: VmCapabilities,
) {
    /**
     * Process a ContinueSegment action - rebuild next segment's frame and update state.
     */
    fun processContinueSegment(action: ReturnLoopAction.ContinueSegment): ExecutionContextUpdate {
        println(
            "[NO-CALLER] activeCloseState=true segments=${closeContext.activeCloseResumeState?.ownerSegments?.size}",
        )
        debugSink.debug {
            "[SEGMENT VALUES] Got ${action.segmentReturnValues.size} values"
        }

        // Rebuild next segment's frame
        val segmentFrame =
            ExecutionFrame(
                proto = action.nextSegment.proto,
                initialArgs = emptyList(),
                upvalues = action.nextSegment.upvalues,
                initialPc = action.nextSegment.pcToResume,
                existingRegisters = action.nextSegment.registers.toMutableList(),
                existingVarargs = action.nextSegment.varargs,
                existingToBeClosedVars = action.nextSegment.toBeClosedVars,
                existingOpenUpvalues = mutableMapOf(),
            )

        // CRITICAL: For mid-RETURN frames, capturedReturns is the single source of truth
        if (action.nextSegment.isMidReturn) {
            segmentFrame.capturedReturns = action.nextSegment.capturedReturns
            segmentFrame.isMidReturn = true
        } else {
            segmentFrame.capturedReturns = null
            segmentFrame.isMidReturn = false

            // Store previous segment's return values as CALL result
            val prevCallInstr =
                if (action.nextSegment.pcToResume > 0) {
                    action.nextSegment.proto.instructions[action.nextSegment.pcToResume - 1]
                } else {
                    null
                }

            if (prevCallInstr != null && prevCallInstr.opcode == OpCode.CALL) {
                val storage = ResultStorage(ExecutionEnvironment(segmentFrame, globals, vmCapabilities))
                storage.storeResults(
                    targetReg = prevCallInstr.a,
                    encodedCount = prevCallInstr.c,
                    results = action.segmentReturnValues,
                    opcodeName = "SEGMENT-CALL-RESUME",
                )
                debugSink.debug {
                    "[Segment CALL-Resume] Stored ${action.segmentReturnValues.size} values"
                }
            }
        }

        // Update active close state with remaining segments
        if (action.remainingSegments.isNotEmpty()) {
            debugSink.debug {
                "[Segment Orchestrator] ${action.remainingSegments.size} segments remaining"
            }
            closeContext.setActiveCloseResumeState(
                CloseResumeState(
                    pendingCloseContinuation = null,
                    ownerSegments = action.remainingSegments,
                    errorArg = closeContext.activeCloseResumeState!!.errorArg,
                    pendingReturnValues = action.segmentReturnValues,
                    closeOwnerFrameStack = closeContext.activeCloseResumeState!!.closeOwnerFrameStack,
                ),
            )
        } else {
            closeContext.setActiveCloseResumeState(null)
        }

        // Update execution context to next segment
        flowState.setActiveExecutionFrame(segmentFrame)
        val env = ExecutionEnvironment(segmentFrame, globals, vmCapabilities)

        // Restore close context state for this segment
        if (action.nextSegment.pendingCloseVar != null) {
            closeContext.setPendingCloseVar(
                action.nextSegment.pendingCloseVar,
                action.nextSegment.pendingCloseStartReg,
            )
        }

        return ExecutionContextUpdate.fromFrame(
            frame = segmentFrame,
            proto = action.nextSegment.proto,
            env = env,
        )
    }

    /**
     * Process a ContinueOuterFrame action - rebuild outer frame and store return values.
     */
    fun processContinueOuterFrame(action: ReturnLoopAction.ContinueOuterFrame): ExecutionContextUpdate {
        val outerFrames = closeContext.activeCloseResumeState?.closeOwnerFrameStack ?: emptyList()
        println(
            "[SEGMENT-COMPLETE] outerFrames.size=${outerFrames.size} frames=${outerFrames.map {
                "${it.proto.name}(mid=${it.isMidReturn},tbc=${it.toBeClosedVars.size})"
            }}",
        )
        closeContext.setActiveCloseResumeState(null)

        // Rebuild the outer frame's execution context
        val outerFrame =
            ExecutionFrame(
                proto = action.outerFrame.proto,
                initialArgs = emptyList(),
                upvalues = action.outerFrame.upvalues,
                initialPc = action.outerFrame.pcToResume,
                existingRegisters = action.outerFrame.registers,
                existingVarargs = action.outerFrame.varargs,
                existingToBeClosedVars = action.outerFrame.toBeClosedVars,
            )

        // Update flow state
        flowState.setActiveExecutionFrame(outerFrame)
        val env = ExecutionEnvironment(outerFrame, globals, vmCapabilities)

        // Store results from previous frame
        val callPc = action.outerFrame.pcToResume - 1
        if (callPc >= 0 && callPc < action.outerFrame.proto.instructions.size) {
            val instr = action.outerFrame.proto.instructions[callPc]
            if (instr.opcode == OpCode.CALL || instr.opcode == OpCode.TAILCALL) {
                ResultStorage(env).storeResults(instr.a, instr.c, action.finalReturnValues, "SEGMENT-FINAL-RETURN")
            }
        }

        return ExecutionContextUpdate.fromFrame(
            frame = outerFrame,
            proto = action.outerFrame.proto,
            env = env,
        )
    }
}
