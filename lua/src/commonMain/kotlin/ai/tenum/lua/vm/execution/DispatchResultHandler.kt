package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.CloseContext
import ai.tenum.lua.vm.ExecutionFlowState
import ai.tenum.lua.vm.VmDebugSink
import ai.tenum.lua.vm.callstack.CallStackManager
import ai.tenum.lua.vm.debug.HookEvent

/**
 * Result of handling a dispatch result - indicates what the execution loop should do next.
 */
internal sealed class LoopControl {
    /** Continue to next instruction (pc will be incremented) */
    data object Continue : LoopControl()

    /** Skip next instruction (pc will be incremented by 2 total) */
    data object SkipNext : LoopControl()

    /** Jump to specific PC (pc will be set, then incremented) */
    data class Jump(
        val newPc: Int,
    ) : LoopControl()

    /** Return from current function */
    data class Return(
        val returnValues: List<LuaValue<*>>,
    ) : LoopControl()

    /** Exit the executeProto function entirely */
    data class ExitProto(
        val returnValues: List<LuaValue<*>>,
    ) : LoopControl()

    /** Continue execution after updating context */
    data class ContinueWithContext(
        val update: ExecutionContextUpdate,
    ) : LoopControl()
}

/**
 * State required for handling dispatch results.
 */
internal data class DispatchHandlerState(
    var currentProto: Proto,
    var execFrame: ExecutionFrame,
    var registers: MutableList<LuaValue<*>>,
    var constants: List<LuaValue<*>>,
    var instructions: List<Instruction>,
    var pc: Int,
    var openUpvalues: MutableMap<Int, Upvalue>,
    var toBeClosedVars: MutableList<Pair<Int, LuaValue<*>>>,
    var varargs: List<LuaValue<*>>,
    var currentUpvalues: List<Upvalue>,
    var env: ExecutionEnvironment,
    var lastLine: Int,
    var pendingInferredName: InferredFunctionName?,
)

/**
 * Handles the complex dispatch result processing logic extracted from executeProto.
 * Coordinates between various handlers to process different dispatch result types.
 */
internal class DispatchResultHandler(
    private val debugSink: VmDebugSink,
    private val resultProcessor: DispatchResultProcessor,
    private val segmentContinuationHandler: SegmentContinuationHandler,
    private val trampolineHandler: TrampolineHandler,
    private val callStackManager: CallStackManager,
    private val hookHelper: HookTriggerHelper,
    private val flowState: ExecutionFlowState,
    private val closeContext: CloseContext,
    private val globals: MutableMap<String, LuaValue<*>>,
    private val vmCapabilities: VmCapabilities,
    private val triggerHook: (HookEvent, Int) -> Unit,
) {
    /**
     * Process a dispatch result and return the loop control action.
     */
    fun handle(
        dispatchResult: DispatchResult,
        state: DispatchHandlerState,
        execStack: ArrayDeque<ExecContext>,
        callDepth: Int,
        isCoroutineContext: Boolean,
        onCallDepthIncrement: () -> Unit,
        onCallDepthDecrement: () -> Unit,
    ): LoopControl {
        debugSink.debug { "[DispatchResultHandler] Handling ${dispatchResult::class.simpleName}" }

        return when (dispatchResult) {
            is DispatchResult.Continue -> LoopControl.Continue
            is DispatchResult.SkipNext -> LoopControl.SkipNext
            is DispatchResult.Jump -> LoopControl.Jump(dispatchResult.newPc)
            is DispatchResult.Return -> handleReturn(dispatchResult, state, execStack, onCallDepthDecrement)
            is DispatchResult.CallTrampoline ->
                handleCallTrampoline(
                    dispatchResult,
                    state,
                    execStack,
                    callDepth,
                    isCoroutineContext,
                    onCallDepthIncrement,
                )
            is DispatchResult.TailCallTrampoline -> handleTailCallTrampoline(dispatchResult, state, isCoroutineContext)
        }
    }

    private fun handleReturn(
        dispatchResult: DispatchResult.Return,
        state: DispatchHandlerState,
        execStack: ArrayDeque<ExecContext>,
        onCallDepthDecrement: () -> Unit,
    ): LoopControl {
        debugSink.debug {
            "[REGISTERS after RETURN] ${state.registers.slice(0..minOf(10, state.registers.size - 1)).mapIndexed {
                i: Int,
                v: LuaValue<*>,
                ->
                "R[$i]=$v"
            }.joinToString(", ")}"
        }

        return when (
            val action =
                resultProcessor.processReturn(
                    dispatchResult = dispatchResult,
                    execStack = execStack,
                    execFrame = state.execFrame,
                    activeCloseState = closeContext.activeCloseResumeState,
                )
        ) {
            is ReturnLoopAction.UnwindToCaller -> {
                debugSink.debug { "  Unwinding from trampolined call, restoring caller" }

                // Pop callee's call frame from debug stack
                callStackManager.removeLastFrame()

                // Restore caller's execution state
                state.currentProto = action.callerContext.proto
                state.execFrame = action.callerContext.execFrame
                state.registers = action.callerContext.registers
                state.constants = action.callerContext.constants
                state.instructions = action.callerContext.instructions
                state.pc = action.callerContext.pc
                state.openUpvalues = state.execFrame.openUpvalues
                state.toBeClosedVars = state.execFrame.toBeClosedVars
                state.varargs = action.callerContext.varargs
                state.currentUpvalues = action.callerContext.currentUpvalues
                state.lastLine = action.callerContext.lastLine

                // Recreate execution environment for caller
                state.env = ExecutionEnvironment(state.execFrame, globals, vmCapabilities)

                // Store callee's return values into caller's registers
                val callInstr = action.callerContext.callInstruction
                val storage = ResultStorage(state.env)
                storage.storeResults(
                    targetReg = callInstr.a,
                    encodedCount = callInstr.c,
                    results = action.returnValues,
                    opcodeName = "CALL-RETURN",
                )

                // Decrement call depth (unwinding from callee)
                onCallDepthDecrement()
                LoopControl.Continue
            }
            is ReturnLoopAction.ContinueSegment -> {
                val result = segmentContinuationHandler.processContinueSegment(action)
                LoopControl.ContinueWithContext(result)
            }
            is ReturnLoopAction.ContinueOuterFrame -> {
                val result = segmentContinuationHandler.processContinueOuterFrame(action)
                LoopControl.ContinueWithContext(result)
            }
            is ReturnLoopAction.ExitProto -> {
                println(
                    "[NO-CALLER] activeCloseState=${closeContext.activeCloseResumeState != null} segments=${closeContext.activeCloseResumeState?.ownerSegments?.size} outerFrames=${closeContext.activeCloseResumeState?.closeOwnerFrameStack?.size}",
                )
                LoopControl.ExitProto(action.returnValues)
            }
        }
    }

    private fun handleCallTrampoline(
        dispatchResult: DispatchResult.CallTrampoline,
        state: DispatchHandlerState,
        execStack: ArrayDeque<ExecContext>,
        callDepth: Int,
        isCoroutineContext: Boolean,
        onCallDepthIncrement: () -> Unit,
    ): LoopControl {
        debugSink.debug { "  Trampolining into regular call" }

        // Save current (caller) execution context
        val callerContext =
            ExecContext(
                proto = state.currentProto,
                execFrame = state.execFrame,
                registers = state.registers,
                constants = state.constants,
                instructions = state.instructions,
                pc = state.pc,
                varargs = state.varargs,
                currentUpvalues = state.currentUpvalues,
                callInstruction = dispatchResult.callInstruction,
                lastLine = state.lastLine,
            )
        execStack.addLast(callerContext)

        // Check call depth using trampoline handler
        when (
            val depthCheck =
                trampolineHandler.checkCallDepth(
                    currentDepth = callDepth,
                    currentProto = state.currentProto,
                    captureSnapshot = { callStackManager.captureSnapshot() },
                )
        ) {
            is TrampolineHandler.CallDepthCheck.Overflow -> {
                execStack.removeLast()
                throw depthCheck.exception
            }
            is TrampolineHandler.CallDepthCheck.Ok -> {
                onCallDepthIncrement()
            }
        }

        // Process trampoline using helper
        val action = resultProcessor.processCallTrampoline(dispatchResult)

        // Apply state changes
        state.currentProto = action.newProto
        state.constants = state.currentProto.constants
        state.instructions = state.currentProto.instructions
        state.pc = -1
        state.execFrame = action.newFrame
        state.registers = state.execFrame.registers
        state.openUpvalues = state.execFrame.openUpvalues
        state.toBeClosedVars = state.execFrame.toBeClosedVars
        state.varargs = state.execFrame.varargs
        state.currentUpvalues = action.newUpvalues
        state.env = ExecutionEnvironment(state.execFrame, globals, vmCapabilities)

        // Create and add call frame using trampoline handler
        val frame = trampolineHandler.createTrampolineFrame(action, dispatchResult.newArgs, state.pendingInferredName)
        callStackManager.addFrame(frame)
        state.pendingInferredName = null

        // Trigger CALL and LINE hooks for trampolined function
        state.lastLine = hookHelper.triggerEntryHooks(state.currentProto, isCoroutineContext)

        return LoopControl.Continue
    }

    private fun handleTailCallTrampoline(
        dispatchResult: DispatchResult.TailCallTrampoline,
        state: DispatchHandlerState,
        isCoroutineContext: Boolean,
    ): LoopControl {
        debugSink.debug { "  TCO: Before trampoline, registers.hashCode=${state.registers.hashCode()}" }

        // Log closure identity and upvalue wiring for diagnosis (always safe to log with lazy evaluation)
        debugSink.debug {
            val cid = dispatchResult.newProto.hashCode()
            val upvalInfo =
                dispatchResult.newUpvalues
                    .mapIndexed { i, uv ->
                        try {
                            val regsHash = uv.registers?.hashCode() ?: 0
                            val regIdx =
                                try {
                                    uv.registerIndex
                                } catch (_: Exception) {
                                    -1
                                }
                            "upval[$i] regIdx=$regIdx regsHash=$regsHash closed=${uv.isClosed}"
                        } catch (_: Exception) {
                            "upval[$i] error"
                        }
                    }.joinToString("\n    ")
            "  TCO: trampoline into closure id=$cid\n    $upvalInfo"
        }

        // Clear previous frame resources
        for ((regIdx, upvalue) in state.openUpvalues) {
            if (!upvalue.isClosed) {
                debugSink.debug { "  TCO: Closing upvalue at R[$regIdx] before trampoline" }
                upvalue.close()
            }
        }
        state.openUpvalues.clear()
        state.toBeClosedVars.clear()

        // Calculate tail call depth
        val previousFrame = callStackManager.lastFrame()
        val currentTailDepth =
            if (previousFrame?.isTailCall == true) {
                previousFrame.tailCallDepth
            } else {
                0
            }

        // Process tail call using helper (reuses registers for TCO)
        debugSink.debug { "  TCO: About to fill registers with nil, registers.hashCode=${state.registers.hashCode()}" }
        val action = resultProcessor.processTailCallTrampoline(dispatchResult, state.registers, currentTailDepth)
        debugSink.debug { "  TCO: After fill, registers[0]=${state.registers[0]}, registers.hashCode=${state.registers.hashCode()}" }

        // Apply state changes
        state.currentProto = action.newProto
        state.constants = state.currentProto.constants
        state.instructions = state.currentProto.instructions
        state.pc = -1
        state.execFrame = action.newFrame
        state.execFrame.top = 0
        state.varargs = state.execFrame.varargs
        state.currentUpvalues = action.newUpvalues
        flowState.setActiveExecutionFrame(state.execFrame)
        state.env = ExecutionEnvironment(state.execFrame, globals, vmCapabilities)

        // Create and add tail call frame
        callStackManager.removeLastFrame()
        val tailFrame =
            CallFrame(
                function = action.luaFunc,
                proto = action.newProto,
                pc = -1,
                base = 0,
                registers = action.newFrame.registers,
                isNative = false,
                isTailCall = true,
                tailCallDepth = action.newTailCallDepth,
                inferredFunctionName = state.pendingInferredName,
                varargs = action.newFrame.varargs,
                ftransfer = if (dispatchResult.newArgs.isEmpty()) 0 else 1,
                ntransfer = dispatchResult.newArgs.size,
            )
        callStackManager.addFrame(tailFrame)
        state.pendingInferredName = null

        // Trigger TAILCALL hook and reset lastLine
        triggerHook(HookEvent.TAILCALL, state.currentProto.lineDefined)
        state.lastLine = -1

        return LoopControl.Continue
    }
}
