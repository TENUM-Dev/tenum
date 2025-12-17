package ai.tenum.lua.vm

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.execution.CloseResumeState
import ai.tenum.lua.vm.execution.ExecContext
import ai.tenum.lua.vm.execution.ExecutionFrame
import ai.tenum.lua.vm.execution.OwnerSegment
import ai.tenum.lua.vm.execution.ResumptionState

/**
 * Stateless service responsible for building and managing coroutine resumption state.
 *
 * This service encapsulates the complex logic of capturing execution state when a coroutine
 * yields and building the appropriate ResumptionState to restore execution when resumed.
 *
 * Key responsibilities:
 * - Building normal resumption state for standard yields
 * - Building close resumption state for yields during __close metamethod execution
 * - Selecting the appropriate owner frame for close resumption
 * - Calculating the correct PC value when resuming
 */
class CoroutineResumptionService {
    /**
     * Builds a complete ResumptionState for a coroutine yield.
     *
     * @param proto Current proto being executed
     * @param pc Current program counter
     * @param registers Current register values
     * @param upvalues Current upvalue list
     * @param varargs Current varargs
     * @param yieldTargetReg Target register for yield results
     * @param yieldExpectedResults Expected number of results
     * @param toBeClosedVars To-be-closed variables at this point
     * @param pendingCloseStartReg Start register for pending close
     * @param pendingCloseVar Current pending close variable
     * @param execStack Execution stack for resumption
     * @param pendingCloseYield Whether this is a yield from __close
     * @param capturedReturnValues Captured return values if any
     * @param debugCallStack Current debug call stack
     * @param closeOwnerFrameStack Frame stack for close ownership tracking
     * @param closeResumeState Optional close resume state for yields in __close
     * @return Complete resumption state
     */
    fun buildResumptionState(
        proto: Proto,
        pc: Int,
        registers: MutableList<LuaValue<*>>,
        upvalues: List<Upvalue>,
        varargs: List<LuaValue<*>>,
        yieldTargetReg: Int,
        yieldExpectedResults: Int,
        toBeClosedVars: List<Pair<Int, LuaValue<*>>>,
        pendingCloseStartReg: Int,
        pendingCloseVar: Pair<Int, LuaValue<*>>?,
        execStack: List<ExecContext>,
        pendingCloseYield: Boolean,
        capturedReturnValues: List<LuaValue<*>>?,
        debugCallStack: List<CallFrame>,
        closeOwnerFrameStack: List<ExecutionFrame>,
        closeResumeState: CloseResumeState? = null,
    ): ResumptionState =
        ResumptionState(
            proto = proto,
            pc = pc,
            registers = registers.toMutableList(),
            upvalues = upvalues.toList(),
            varargs = varargs.toList(),
            yieldTargetRegister = yieldTargetReg,
            yieldExpectedResults = yieldExpectedResults,
            toBeClosedVars = toBeClosedVars.toMutableList(),
            pendingCloseStartReg = pendingCloseStartReg,
            pendingCloseVar = pendingCloseVar,
            execStack = execStack.toList(),
            pendingCloseYield = pendingCloseYield,
            capturedReturnValues = capturedReturnValues,
            debugCallStack = debugCallStack.toList(),
            closeResumeState = closeResumeState,
            closeOwnerFrameStack = closeOwnerFrameStack.toList(),
        )

    /**
     * Builds a CloseResumeState for resuming after a yield during __close execution.
     *
     * @param closeContinuationProto Proto of the __close continuation
     * @param closeContinuationPc PC in the __close continuation
     * @param closeContinuationRegisters Registers of the __close continuation
     * @param closeContinuationUpvalues Upvalues of the __close continuation
     * @param closeContinuationVarargs Varargs of the __close continuation
     * @param closeContinuationExecStack Execution stack of the __close continuation
     * @param ownerProto Proto of the owning frame
     * @param ownerPc PC in the owning frame
     * @param ownerRegisters Registers of the owning frame
     * @param ownerUpvalues Upvalues of the owning frame
     * @param ownerVarargs Varargs of the owning frame
     * @param startReg Start register for close processing
     * @param pendingTbcList Remaining to-be-closed variables
     * @param pendingCloseVar Current variable being closed
     * @param errorArg Error argument if any
     * @param capturedReturnValues Return values if captured
     * @param yieldTargetReg Target register for yield results
     * @param yieldExpectedResults Expected number of results
     * @param debugCallStack Debug call stack
     * @param closeOwnerFrameStack Frame stack for close ownership
     * @return Complete close resume state wrapped in ResumptionState
     */
    fun buildCloseResumeState(
        closeContinuationProto: Proto,
        closeContinuationPc: Int,
        closeContinuationRegisters: MutableList<LuaValue<*>>,
        closeContinuationUpvalues: List<Upvalue>,
        closeContinuationVarargs: List<LuaValue<*>>,
        closeContinuationExecStack: List<ExecContext>,
        ownerProto: Proto,
        ownerPc: Int,
        ownerRegisters: MutableList<LuaValue<*>>,
        ownerUpvalues: List<Upvalue>,
        ownerVarargs: List<LuaValue<*>>,
        startReg: Int,
        pendingTbcList: List<Pair<Int, LuaValue<*>>>,
        pendingCloseVar: Pair<Int, LuaValue<*>>?,
        errorArg: LuaValue<*>,
        capturedReturnValues: List<LuaValue<*>>?,
        yieldTargetReg: Int,
        yieldExpectedResults: Int,
        debugCallStack: List<CallFrame>,
        closeOwnerFrameStack: List<ExecutionFrame>,
    ): ResumptionState {
        // Build owner segments: innermost (immediate owner) + outer frames from closeOwnerFrameStack
        val segments = mutableListOf<OwnerSegment>()
        
        // Innermost segment: the immediate owner frame
        segments.add(
            OwnerSegment(
                proto = ownerProto,
                pcToResume = ownerPc,
                registers = ownerRegisters.toMutableList(),
                upvalues = ownerUpvalues.toList(),
                varargs = ownerVarargs.toList(),
                toBeClosedVars = pendingTbcList.toMutableList(),
                capturedReturns = capturedReturnValues?.toList(),
                pendingCloseStartReg = startReg,
                pendingCloseVar = pendingCloseVar,
                execStack = closeContinuationExecStack.toList(),
                debugCallStack = debugCallStack.toList(),
                isMidReturn = capturedReturnValues != null,
            ),
        )
        
        // Outer segments: frames from closeOwnerFrameStack (caller frames across native boundaries)
        for (frame in closeOwnerFrameStack) {
            segments.add(
                OwnerSegment(
                    proto = frame.proto,
                    pcToResume = frame.pc,
                    registers = frame.registers.toMutableList(),
                    upvalues = frame.upvalues.toList(),
                    varargs = frame.varargs.toList(),
                    toBeClosedVars = frame.toBeClosedVars, // Share, don't copy!
                    capturedReturns = frame.capturedReturns?.toList(),
                    pendingCloseStartReg = 0,
                    pendingCloseVar = null,
                    execStack = emptyList(),
                    debugCallStack = debugCallStack.toList(),
                    isMidReturn = frame.capturedReturns != null,
                ),
            )
        }
        
        val closeState =
            CloseResumeState(
                pendingCloseContinuation =
                    if (closeContinuationProto != ownerProto || closeContinuationPc != ownerPc) {
                        ResumptionState(
                            proto = closeContinuationProto,
                            pc = closeContinuationPc,
                            registers = closeContinuationRegisters.toMutableList(),
                            upvalues = closeContinuationUpvalues.toList(),
                            varargs = closeContinuationVarargs.toList(),
                            yieldTargetRegister = yieldTargetReg,
                            yieldExpectedResults = yieldExpectedResults,
                            toBeClosedVars = mutableListOf(),
                            pendingCloseStartReg = 0,
                            pendingCloseVar = null,
                            execStack = closeContinuationExecStack.toList(),
                            pendingCloseYield = false,
                            capturedReturnValues = null,
                            debugCallStack = debugCallStack.toList(),
                            closeResumeState = null,
                            closeOwnerFrameStack = emptyList(),
                        )
                    } else {
                        null
                    },
                ownerSegments = segments,
                errorArg = errorArg,
            )

        return buildResumptionState(
            proto = closeContinuationProto,
            pc = closeContinuationPc,
            registers = closeContinuationRegisters,
            upvalues = closeContinuationUpvalues,
            varargs = closeContinuationVarargs,
            yieldTargetReg = yieldTargetReg,
            yieldExpectedResults = yieldExpectedResults,
            toBeClosedVars = emptyList(),
            pendingCloseStartReg = 0,
            pendingCloseVar = null,
            execStack = closeContinuationExecStack,
            pendingCloseYield = false,
            capturedReturnValues = null,
            debugCallStack = debugCallStack,
            closeOwnerFrameStack = closeOwnerFrameStack,
            closeResumeState = closeState,
        )
    }

    /**
     * Selects the appropriate owner frame context for close resume.
     *
     * If a pendingCloseOwnerFrame is provided (i.e., we're in a nested close), use it.
     * Otherwise, use the current execution state as the owner.
     *
     * @param pendingCloseOwnerFrame Optional pending close owner frame
     * @param callStack Current call stack
     * @param currentProto Current proto
     * @param currentPc Current PC
     * @param currentRegisters Current registers
     * @param currentUpvalues Current upvalues
     * @param currentVarargs Current varargs
     * @param defaultTbc Default to-be-closed variables
     * @return Owner frame context
     */
    fun selectOwnerFrameContext(
        pendingCloseOwnerFrame: ExecutionFrame?,
        callStack: List<ExecutionFrame>,
        currentProto: Proto,
        currentPc: Int,
        currentRegisters: MutableList<LuaValue<*>>,
        currentUpvalues: List<Upvalue>,
        currentVarargs: List<LuaValue<*>>,
        defaultTbc: List<Pair<Int, LuaValue<*>>>,
    ): OwnerFrameContext =
        if (pendingCloseOwnerFrame != null) {
            OwnerFrameContext(
                proto = pendingCloseOwnerFrame.proto,
                pc = pendingCloseOwnerFrame.pc,
                registers = pendingCloseOwnerFrame.registers,
                upvalues = pendingCloseOwnerFrame.upvalues,
                varargs = pendingCloseOwnerFrame.varargs,
                tbcVars = pendingCloseOwnerFrame.toBeClosedVars.toList(),
            )
        } else {
            OwnerFrameContext(
                proto = currentProto,
                pc = currentPc,
                registers = currentRegisters,
                upvalues = currentUpvalues,
                varargs = currentVarargs,
                tbcVars = defaultTbc,
            )
        }

    /**
     * Calculates the PC value to use when resuming.
     *
     * @param currentPc Current PC value
     * @param incrementPc Whether to increment the PC (normal yield) or keep it (close yield)
     * @return The PC value for resumption
     */
    fun calculateResumePc(
        currentPc: Int,
        incrementPc: Boolean,
    ): Int = if (incrementPc) currentPc + 1 else currentPc
}

/**
 * Result of selecting owner frame context for close resume
 */
data class OwnerFrameContext(
    val proto: Proto,
    val pc: Int,
    val registers: MutableList<LuaValue<*>>,
    val upvalues: List<Upvalue>,
    val varargs: List<LuaValue<*>>,
    val tbcVars: List<Pair<Int, LuaValue<*>>>,
)
