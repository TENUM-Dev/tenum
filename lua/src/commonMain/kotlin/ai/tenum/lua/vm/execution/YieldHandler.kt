package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.LuaYieldException
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.CallerContext
import ai.tenum.lua.vm.CloseContext
import ai.tenum.lua.vm.CoroutineResumptionService
import ai.tenum.lua.vm.CoroutineYieldContext
import ai.tenum.lua.vm.VmDebugSink
import ai.tenum.lua.vm.callstack.CallStackManager
import ai.tenum.lua.vm.coroutine.CoroutineStateManager

/**
 * Handles yield exception processing and coroutine state saving.
 * Encapsulates the complex logic for:
 * - Determining yield parameters (target register, expected results)
 * - Detecting close-yields (yields from __close metamethods)
 * - Building CloseResumeState for close-yield scenarios
 * - Saving complete coroutine state for resumption
 */
class YieldHandler(
    private val coroutineStateManager: CoroutineStateManager,
    private val resumptionService: CoroutineResumptionService,
    private val debugSink: VmDebugSink,
    private val callStackManager: CallStackManager,
) {
    /**
     * Process a yield exception and save coroutine state for resumption.
     *
     * @param exception The yield exception containing yielded values
     * @param currentProto Current proto being executed
     * @param pc Program counter at yield point
     * @param registers Current register values
     * @param instructions Current proto's instructions
     * @param execFrame Current execution frame
     * @param varargs Current varargs
     * @param execStack Current execution stack
     * @param yieldContext Yield context with pending yield information
     * @param closeContext Close context with to-be-closed variable information
     * @param callerContext Caller context stack
     * @param callStackBase Base index for coroutine call stack filtering
     * @param filterCoroutineFrames Function to filter coroutine frames
     * @param getCapturedReturnValues Function to get captured return values
     */
    fun handleYield(
        exception: LuaYieldException,
        currentProto: Proto,
        pc: Int,
        registers: MutableList<LuaValue<*>>,
        instructions: List<Instruction>,
        execFrame: ExecutionFrame,
        varargs: List<LuaValue<*>>,
        execStack: ArrayDeque<ExecContext>,
        yieldContext: CoroutineYieldContext,
        closeContext: CloseContext,
        callerContext: CallerContext,
        callStackBase: Int,
        filterCoroutineFrames: (List<ExecutionFrame>, Int) -> List<ExecutionFrame>,
        getCapturedReturnValues: () -> List<LuaValue<*>>?,
    ) {
        // The yield was triggered by a CALL instruction, so instructions[pc] has the target register and result count
        val yieldInstr = if (pc < instructions.size) instructions[pc] else null
        val yieldTargetReg = yieldContext.pendingYieldTargetReg ?: yieldInstr?.a ?: 0
        val yieldExpectedResults = yieldContext.pendingYieldEncodedCount ?: yieldInstr?.c ?: 0

        // Filter call stack to only include coroutine's frames (not main thread frames)
        // Save coroutine state for resume
        val currentCoroutine = coroutineStateManager.getCurrentCoroutine()
        val coroutineCallStack = callStackManager.captureSnapshotFrom(callStackBase)
        val isCloseYield = closeContext.pendingCloseVar != null
        val ownerTbc = closeContext.pendingCloseOwnerTbc ?: execFrame.toBeClosedVars

        // CRITICAL: When yielding from __close, capturedReturns are in pendingCloseOwnerFrame
        // This is set in CallOpcodes.executeReturn before calling __close handlers
        // Copy them to closeContext so they're saved with the coroutine state
        val ownerFrame = closeContext.pendingCloseOwnerFrame
        if (ownerFrame != null && ownerFrame.capturedReturns != null) {
            closeContext.setCapturedReturnValues(ownerFrame.capturedReturns!!)
        }

        // When yielding from __close, create CloseResumeState with owner frame snapshot
        val closeResumeState: CloseResumeState? =
            if (isCloseYield) {
                buildCloseResumeState(
                    currentProto = currentProto,
                    pc = pc,
                    registers = registers,
                    execFrame = execFrame,
                    varargs = varargs,
                    execStack = execStack,
                    yieldTargetReg = yieldTargetReg,
                    yieldExpectedResults = yieldExpectedResults,
                    coroutineCallStack = coroutineCallStack,
                    callerContext = callerContext,
                    closeContext = closeContext,
                    callStackBase = callStackBase,
                    filterCoroutineFrames = filterCoroutineFrames,
                    ownerTbc = ownerTbc,
                )
            } else {
                null
            }

        val pendingTbc = ownerTbc.toMutableList()

        // Always resume from the current proto/pc; closeResumeState carries owner segments separately
        val stateProto = currentProto
        val statePc = pc
        val stateRegs = registers
        val stateUpvalues = execFrame.upvalues
        val stateVarargs = varargs

        coroutineStateManager.saveCoroutineState(
            currentCoroutine,
            stateProto,
            statePc,
            stateRegs,
            stateUpvalues,
            stateVarargs,
            exception.yieldedValues,
            yieldTargetReg,
            yieldExpectedResults,
            coroutineCallStack, // Save only coroutine's call stack (without main thread frames)
            pendingTbc,
            closeContext.pendingCloseStartReg,
            closeContext.pendingCloseVar,
            execStack.toList(),
            pendingCloseYield = isCloseYield || yieldContext.pendingYieldStayOnSamePc,
            capturedReturnValues = getCapturedReturnValues(),
            pendingCloseContinuation = closeContext.pendingCloseContinuation,
            pendingCloseErrorArg = closeContext.pendingCloseErrorArg,
            // For close-yield we set stayOnSamePc=true so we must NOT increment the PC here,
            // otherwise we skip over the RETURN/CLOSE that needs to finish after resumption.
            incrementPc = !yieldContext.pendingYieldStayOnSamePc,
            closeResumeState = closeResumeState,
            closeOwnerFrameStack = filterCoroutineFrames(callerContext.snapshot(), callStackBase),
        )
        closeContext.setPendingCloseVar(null, 0)
        exception.stateSaved = true // Mark that state has been saved
    }

    /**
     * Build CloseResumeState for a close-yield scenario.
     * This captures the __close continuation and owner frame state needed to resume after yield.
     */
    private fun buildCloseResumeState(
        currentProto: Proto,
        pc: Int,
        registers: MutableList<LuaValue<*>>,
        execFrame: ExecutionFrame,
        varargs: List<LuaValue<*>>,
        execStack: ArrayDeque<ExecContext>,
        yieldTargetReg: Int,
        yieldExpectedResults: Int,
        coroutineCallStack: List<CallFrame>,
        callerContext: CallerContext,
        closeContext: CloseContext,
        callStackBase: Int,
        filterCoroutineFrames: (List<ExecutionFrame>, Int) -> List<ExecutionFrame>,
        ownerTbc: List<Pair<Int, LuaValue<*>>>,
    ): CloseResumeState {
        // Capture __close continuation (current execution point)
        // This is the __close function's state, NOT the owner's state
        val closeContinuation =
            ResumptionState(
                proto = currentProto,
                pc = pc + 1,
                registers = registers,
                upvalues = execFrame.upvalues,
                varargs = varargs,
                yieldTargetRegister = yieldTargetReg,
                yieldExpectedResults = yieldExpectedResults,
                toBeClosedVars = execFrame.toBeClosedVars, // __close function's TBC (should be empty)
                pendingCloseStartReg = 0, // Not relevant for continuation
                pendingCloseVar = null, // Not relevant for continuation
                execStack = execStack.toList(),
                pendingCloseYield = false,
                capturedReturnValues = closeContext.pendingCloseOwnerFrame?.capturedReturns,
                debugCallStack = coroutineCallStack,
                closeResumeState = null,
                closeOwnerFrameStack = callerContext.snapshot(),
            )

        // Use resumption service to select owner frame and build close resume state
        val callerFrame =
            if (coroutineCallStack.size >= 2) {
                coroutineCallStack[coroutineCallStack.size - 2]
            } else {
                null
            }

        val ownerContext =
            resumptionService.selectOwnerFrameContext(
                pendingCloseOwnerFrame = closeContext.pendingCloseOwnerFrame,
                callStack = emptyList(),
                currentProto = currentProto,
                currentPc = pc,
                currentRegisters = registers,
                currentUpvalues = execFrame.upvalues,
                currentVarargs = varargs,
                defaultTbc = ownerTbc,
            )

        debugSink.debug {
            "[YIELD CloseState] closeOwnerFrameStack.size=${callerContext.size}, pendingCloseOwnerFrame=${closeContext.pendingCloseOwnerFrame != null}"
        }
        debugSink.debug { "[YIELD CloseState] Selected owner: proto=${ownerContext.proto.name} pc=${ownerContext.pc}" }
        debugSink.debug { "[YIELD CloseState] Owner TBC vars: ${ownerContext.tbcVars.size}" }
        debugSink.debug { "[YIELD CloseState] pendingCloseVar=${closeContext.pendingCloseVar}" }

        // Calculate effective owner PC using resumption service
        // The owner should resume AFTER the RETURN instruction so we don't re-execute it
        val rawOwnerPc = closeContext.pendingCloseOwnerFrame?.pc ?: callerFrame?.pc ?: pc
        val effectiveOwnerPc =
            resumptionService.calculateResumePc(
                currentPc = rawOwnerPc,
                incrementPc = true, // Increment to skip past RETURN instruction
            )

        // Build close resume state using resumption service
        val closeResumeStateWrapped =
            resumptionService.buildCloseResumeState(
                closeContinuationProto = currentProto,
                closeContinuationPc = pc + 1,
                closeContinuationRegisters = registers,
                closeContinuationUpvalues = execFrame.upvalues,
                closeContinuationVarargs = varargs,
                closeContinuationExecStack = execStack.toList(),
                ownerProto = ownerContext.proto,
                ownerPc = effectiveOwnerPc,
                ownerRegisters = ownerContext.registers,
                ownerUpvalues = ownerContext.upvalues,
                ownerVarargs = ownerContext.varargs,
                startReg = closeContext.pendingCloseStartReg,
                pendingTbcList = ownerContext.tbcVars,
                pendingCloseVar = closeContext.pendingCloseVar,
                errorArg = closeContext.pendingCloseErrorArg,
                capturedReturnValues = closeContext.pendingCloseOwnerFrame?.capturedReturns,
                yieldTargetReg = yieldTargetReg,
                yieldExpectedResults = yieldExpectedResults,
                debugCallStack = coroutineCallStack,
                closeOwnerFrameStack = filterCoroutineFrames(callerContext.snapshot(), callStackBase),
            )

        return closeResumeStateWrapped.closeResumeState!!
    }
}
