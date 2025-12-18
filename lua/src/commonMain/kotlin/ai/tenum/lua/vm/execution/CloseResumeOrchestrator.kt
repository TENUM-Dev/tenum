package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.CloseContext
import ai.tenum.lua.vm.ExecutionFlowState
import ai.tenum.lua.vm.VmDebugSink

/**
 * Orchestrator for CloseResumeState processing.
 *
 * This class handles the two-phase resumption logic for multi-frame TBC chains:
 * - Phase 1: Resume the __close continuation if present
 * - Phase 2: Orchestrate through owner segments (rebuild frames, update state)
 *
 * This matches Lua 5.4 semantics where yields inside __close metamethods
 * require complex state reconstruction across multiple frames.
 */
class CloseResumeOrchestrator(
    private val debugSink: VmDebugSink,
    private val globals: MutableMap<String, LuaValue<*>>,
    private val closeContext: CloseContext,
    private val flowState: ExecutionFlowState,
    private val vmCapabilities: VmCapabilities,
    private val executeProto: (Proto, List<LuaValue<*>>, List<Upvalue>, LuaFunction?, ExecutionMode) -> List<LuaValue<*>>,
) {
    /**
     * Process CloseResumeState and return updated execution context.
     *
     * @param closeState The CloseResumeState to process
     * @param args Resume arguments
     * @param function Current function being executed
     * @param currentExecFrame Current execution frame (may be replaced)
     * @param currentEnv Current execution environment (may be replaced)
     * @return Updated execution context after processing close resumption
     */
    fun processCloseResumeState(
        closeState: CloseResumeState,
        args: List<LuaValue<*>>,
        function: LuaFunction?,
        currentExecFrame: ExecutionFrame,
    ): ExecutionContextUpdate {
        // Phase 1: Resume the __close continuation if present
        val continuation = closeState.pendingCloseContinuation
        if (continuation != null) {
            executeProto(
                continuation.proto,
                args,
                continuation.upvalues,
                function,
                ExecutionMode.ResumeContinuation(continuation),
            )
        }

        // Phase 2: Orchestrate through owner segments
        if (closeState.ownerSegments.isNotEmpty()) {
            return processOwnerSegments(closeState, currentExecFrame)
        } else {
            // No segments - clear activeCloseResumeState
            closeContext.setActiveCloseResumeState(null)

            // Return current state unchanged
            return ExecutionContextUpdate(
                execFrame = currentExecFrame,
                currentProto = currentExecFrame.proto,
                registers = currentExecFrame.registers,
                constants = currentExecFrame.proto.constants,
                instructions = currentExecFrame.proto.instructions,
                pc = currentExecFrame.pc,
                openUpvalues = currentExecFrame.openUpvalues,
                toBeClosedVars = currentExecFrame.toBeClosedVars,
                varargs = currentExecFrame.varargs,
                currentUpvalues = currentExecFrame.upvalues,
                env = ExecutionEnvironment(currentExecFrame, globals, vmCapabilities),
                needsEnvRecreation = false,
            )
        }
    }

    /**
     * Process owner segments - rebuild frames and update active close state.
     */
    private fun processOwnerSegments(
        closeState: CloseResumeState,
        currentExecFrame: ExecutionFrame,
    ): ExecutionContextUpdate {
        val firstSegment = closeState.ownerSegments.first()
        val isSingleFrame = closeState.ownerSegments.size == 1
        debugSink.debug {
            "[Segment Orchestrator] Processing first segment: proto=${firstSegment.proto.name}, total segments=${closeState.ownerSegments.size}"
        }

        // Rebuild first segment's frame
        val segmentFrame =
            ExecutionFrame(
                proto = firstSegment.proto,
                initialArgs = emptyList(),
                upvalues = firstSegment.upvalues,
                initialPc = firstSegment.pcToResume,
                existingRegisters = firstSegment.registers.toMutableList(),
                existingVarargs = firstSegment.varargs,
                existingToBeClosedVars = firstSegment.toBeClosedVars,
                existingOpenUpvalues = mutableMapOf(),
            )
        // CRITICAL: capturedReturns from segment is the single source of truth for mid-RETURN frames
        segmentFrame.capturedReturns = firstSegment.capturedReturns
        segmentFrame.isMidReturn = firstSegment.isMidReturn

        debugSink.debug {
            "[Segment Orchestrator] Rebuilt frame: pc=${firstSegment.pcToResume}, TBC.size=${firstSegment.toBeClosedVars.size}, capturedReturns=${firstSegment.capturedReturns?.size}, isMidReturn=${firstSegment.isMidReturn}"
        }

        // Update active close state based on single vs multi-frame
        updateActiveCloseState(closeState, isSingleFrame)

        // Set up execution context for first segment
        flowState.setActiveExecutionFrame(segmentFrame)

        // Restore close context state for this segment
        if (firstSegment.pendingCloseVar != null) {
            closeContext.setPendingCloseVar(firstSegment.pendingCloseVar, firstSegment.pendingCloseStartReg)
        }

        // Ensure activeExecutionFrame points to the current live frame after any rebuild
        flowState.setActiveExecutionFrame(segmentFrame)

        return ExecutionContextUpdate.fromFrame(
            frame = segmentFrame,
            proto = segmentFrame.proto,
            env = ExecutionEnvironment(segmentFrame, globals, vmCapabilities),
            needsEnvRecreation = true,
        )
    }

    /**
     * Update active close state based on whether this is a single or multi-frame scenario.
     */
    private fun updateActiveCloseState(
        closeState: CloseResumeState,
        isSingleFrame: Boolean,
    ) {
        if (isSingleFrame) {
            debugSink.debug { "[Segment Orchestrator] Single frame - clearing segments but preserving closeOwnerFrameStack" }
            closeContext.setActiveCloseResumeState(
                CloseResumeState(
                    pendingCloseContinuation = null,
                    ownerSegments = emptyList(),
                    errorArg = closeState.errorArg,
                    pendingReturnValues = null,
                    closeOwnerFrameStack = closeState.closeOwnerFrameStack,
                ),
            )
        } else {
            val remainingSegments = closeState.ownerSegments.drop(1)
            debugSink.debug {
                "[Segment Orchestrator] Multi-frame - storing ${remainingSegments.size} remaining segments"
            }
            closeContext.setActiveCloseResumeState(
                CloseResumeState(
                    pendingCloseContinuation = null,
                    ownerSegments = remainingSegments,
                    errorArg = closeState.errorArg,
                    pendingReturnValues = null,
                    closeOwnerFrameStack = closeState.closeOwnerFrameStack,
                ),
            )
        }
    }
}
