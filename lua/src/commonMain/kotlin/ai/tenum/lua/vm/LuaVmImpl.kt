@file:Suppress("NOTHING_TO_INLINE", "REDUNDANT_ELSE_IN_WHEN")

package ai.tenum.lua.vm

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.LuaYieldException
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.callstack.CallStackManager
import ai.tenum.lua.vm.coroutine.CoroutineStateManager
import ai.tenum.lua.vm.debug.DebugContext
import ai.tenum.lua.vm.debug.DebugHook
import ai.tenum.lua.vm.debug.DebugTracer
import ai.tenum.lua.vm.debug.HookConfig
import ai.tenum.lua.vm.debug.HookEvent
import ai.tenum.lua.vm.debug.HookManager
import ai.tenum.lua.vm.errorhandling.ErrorReporter
import ai.tenum.lua.vm.errorhandling.LuaException
import ai.tenum.lua.vm.errorhandling.LuaRuntimeError
import ai.tenum.lua.vm.errorhandling.LuaStackFrame
import ai.tenum.lua.vm.errorhandling.NameHintResolver
import ai.tenum.lua.vm.errorhandling.StackTraceBuilder
import ai.tenum.lua.vm.execution.ChunkExecutor
import ai.tenum.lua.vm.execution.CloseResumeState
import ai.tenum.lua.vm.execution.DispatchResult
import ai.tenum.lua.vm.execution.DispatchResultProcessor
import ai.tenum.lua.vm.execution.ExecContext
import ai.tenum.lua.vm.execution.ExecutionEnvironment
import ai.tenum.lua.vm.execution.ExecutionFrame
import ai.tenum.lua.vm.execution.ExecutionMode
import ai.tenum.lua.vm.execution.ExecutionPreparation
import ai.tenum.lua.vm.execution.ExecutionSnapshot
import ai.tenum.lua.vm.execution.FunctionNameSource
import ai.tenum.lua.vm.execution.HookTriggerHelper
import ai.tenum.lua.vm.execution.InferredFunctionName
import ai.tenum.lua.vm.execution.OpcodeDispatcher
import ai.tenum.lua.vm.execution.ResultStorage
import ai.tenum.lua.vm.execution.ResumptionState
import ai.tenum.lua.vm.execution.ReturnLoopAction
import ai.tenum.lua.vm.execution.StackView
import ai.tenum.lua.vm.execution.VmCapabilities
import ai.tenum.lua.vm.execution.YieldHandler
import ai.tenum.lua.vm.execution.opcodes.CallOpcodes
import ai.tenum.lua.vm.library.LibraryRegistry
import ai.tenum.lua.vm.library.LuaLibraryContext
import ai.tenum.lua.vm.metamethods.MetamethodResolver
import ai.tenum.lua.vm.typeops.TypeComparisons
import ai.tenum.lua.vm.typeops.TypeConversions
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem

/**
 * Full implementation of the Lua VM that executes compiled bytecode
 */
class LuaVmImpl(
    /**
     * Filesystem for load/require operations. Defaults to platform-specific filesystem.
     */
    private val fileSystem: FileSystem = FakeFileSystem(),
) : LuaVm,
    VmCapabilities,
    DebugContext {
    // VmCapabilities implementation
    val globals = mutableMapOf<String, LuaValue<*>>()

    /**
     * Lua registry table - pre-defined table where C code can store Lua values.
     * Used for storing internal state like hook tables.
     */
    private val registryTable = LuaTable()

    override val debug: (String) -> Unit
        get() = { msg -> debug { msg } }
    override val trace: (Int, LuaValue<*>, String) -> Unit
        get() = ::traceRegisterWrite

    override fun getCallStack(): MutableList<CallFrame> = callStackManager.captureSnapshot().toMutableList()

    override fun replaceLastCallFrame(newFrame: CallFrame) {
        callStackManager.replaceLastFrame(newFrame)
    }

    override fun setMetamethodCallContext(metamethodName: String) {
        // Set the pending inferred name with metamethod source
        // Strip the __ prefix if present (e.g., __index -> index, __add -> add)
        val cleanName = metamethodName.removePrefix("__")
        pendingInferredName = InferredFunctionName(cleanName, FunctionNameSource.Metamethod)
    }

    override fun setNextCallIsCloseMetamethod() {
        flowState.setNextCallIsCloseMetamethod(true)
    }

    override fun clearCloseException() {
        closeContext.setPendingException(null)
        closeContext.setCapturedReturnValues(null)
    }

    override fun setCloseException(exception: Exception) {
        closeContext.setPendingException(exception)
    }

    override fun getCloseException(): Exception? = closeContext.pendingCloseException

    fun setCapturedReturnValues(values: List<LuaValue<*>>) {
        closeContext.setCapturedReturnValues(values)
    }

    fun getCapturedReturnValues(): List<LuaValue<*>>? = closeContext.capturedReturnValues

    override fun setPendingCloseVar(
        register: Int,
        value: LuaValue<*>,
    ) {
        closeContext.setPendingCloseVar(register to value, closeContext.pendingCloseStartReg)
    }

    override fun clearPendingCloseVar() {
        closeContext.setPendingCloseVar(null, 0)
        clearPendingCloseErrorArg()
        clearPendingCloseOwnerTbc()
    }

    override fun setPendingCloseStartReg(registerIndex: Int) {
        if (closeContext.pendingCloseVar != null) {
            closeContext.setPendingCloseVar(closeContext.pendingCloseVar!!, registerIndex)
        } else {
            closeContext.setPendingCloseVar(null, registerIndex)
        }
    }

    override fun clearPendingCloseStartReg() {
        if (closeContext.pendingCloseVar != null) {
            closeContext.setPendingCloseVar(closeContext.pendingCloseVar!!, 0)
        }
    }

    override fun setPendingCloseOwnerTbc(vars: MutableList<Pair<Int, LuaValue<*>>>) {
        if (closeContext.pendingCloseOwnerFrame != null) {
            closeContext.setOwnerFrame(closeContext.pendingCloseOwnerFrame!!, vars)
        }
    }

    override fun clearPendingCloseOwnerTbc() {
        // TBC list is cleared when owner frame is cleared
    }

    override fun setPendingCloseOwnerFrame(frame: ExecutionFrame) {
        val tbcList = closeContext.pendingCloseOwnerTbc ?: mutableListOf()
        closeContext.setOwnerFrame(frame, tbcList)
    }

    override fun getPendingCloseOwnerFrame(): ExecutionFrame? = closeContext.pendingCloseOwnerFrame

    override fun setPendingCloseErrorArg(error: LuaValue<*>) {
        closeContext.setCloseErrorArg(error)
    }

    override fun clearPendingCloseErrorArg() {
        closeContext.setCloseErrorArg(LuaNil)
    }

    override fun setYieldResumeContext(
        targetReg: Int,
        encodedCount: Int,
        stayOnSamePc: Boolean,
    ) {
        yieldContext.setYieldResumeContext(targetReg, encodedCount, stayOnSamePc)
    }

    override fun clearYieldResumeContext() {
        yieldContext.clearYieldResumeContext()
    }

    override fun preserveErrorCallStack(callStack: List<CallFrame>) {
        lastErrorCallStack = callStack
    }

    override fun markCurrentFrameAsReturning() {
        // Mark the last frame as returning so it's invisible to debug.getinfo
        val lastFrame = callStackManager.lastFrame()
        if (lastFrame != null) {
            val updated = lastFrame.copy(isReturning = true)
            callStackManager.replaceLastFrame(updated)
        }
    }

    override fun getMetamethod(
        value: LuaValue<*>,
        methodName: String,
    ): LuaValue<*>? = metamethodResolver.getMetamethod(value, methodName)

    override fun getRegisterNameHint(registerIndex: Int): String? {
        // Access current proto and pc from execution context
        // These are captured when executeProto runs
        return try {
            val frame = callStackManager.captureSnapshot().lastOrNull()
            val proto = frame?.proto
            if (frame != null && proto != null) {
                nameHintResolver.getRegisterNameHint(registerIndex, proto, frame.pc)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun getRegisterNameHint(
        registerIndex: Int,
        pc: Int,
    ): String? =
        try {
            val frame = callStackManager.captureSnapshot().lastOrNull()
            val proto = frame?.proto
            if (proto != null) {
                nameHintResolver.getRegisterNameHint(registerIndex, proto, pc)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    override fun luaError(message: String): Nothing {
        // Find the first non-native frame for error location
        val currentStack = callStackManager.captureSnapshot()
        val frame = currentStack.lastOrNull { !it.isNative }
        if (frame != null) {
            errorReporter.luaError(message, frame.proto, frame.pc, currentStack)
        } else {
            throw LuaException(message)
        }
    }

    override fun error(
        message: String,
        pc: Int,
    ): Nothing {
        // If executing in a coroutine, save the call stack for debug.traceback()
        // Do this BEFORE throwing the exception so the stack is captured
        val currentCoroutine = coroutineStateManager.getCurrentCoroutine()
        if (currentCoroutine is LuaCoroutine.LuaFunctionCoroutine) {
            val callStackBase = currentCoroutine.thread.callStackBase
            val fullStack = callStackManager.captureSnapshot()
            val coroutineCallStack = fullStack.drop(callStackBase)
            currentCoroutine.thread.savedCallStack = coroutineCallStack
        }

        // Find the first non-native frame for error location
        val currentStack = callStackManager.captureSnapshot()
        val frame = currentStack.lastOrNull { !it.isNative }
        if (frame != null) {
            errorReporter.luaError(message, frame.proto, pc, currentStack)
        } else {
            throw LuaException(message)
        }
    }

    /**
     * Library registry for standard library modules
     */
    private val libraryRegistry: LibraryRegistry by lazy {
        LibraryRegistry.createDefault(debugTracer)
    }

    /**
     * Loaded modules cache for require()
     */
    private val loadedModules = mutableMapOf<String, LuaValue<*>>()

    /**
     * Package search paths for require()
     */
    private val packagePath =
        mutableListOf(
            "./?.lua",
            "./?/init.lua",
        )

    /**
     * Function call context for name inference (Phase 6.4 - debug.getinfo)
     * These track the inferred name from bytecode analysis before a CALL
     */
    private var pendingInferredName: InferredFunctionName? = null

    /**
     * Last error call stack for debug.traceback.
     * When an error is thrown from a __close metamethod, we capture its call stack here.
     * debug.traceback can use this if called shortly after (e.g., as xpcall message handler).
     * This is cleared after being read once to avoid stale data.
     */
    private var lastErrorCallStack: List<CallFrame>? = null

    /**
     * Encapsulates all __close metamethod handling state.
     * Groups 9 close-related variables into a single cohesive context.
     */
    private val closeContext = CloseContext()

    /**
     * Caller context tracker for close handling across native boundaries.
     * Pushed before any Lua call (including native wrappers like pcall), popped on return.
     * Used in yield-save logic to capture the owner frame even when execStack is not populated.
     */
    private val callerContext = CallerContext()

    /**
     * Service for building coroutine resumption state.
     * Encapsulates yield/resume state management logic.
     */
    private val resumptionService = CoroutineResumptionService()

    /**
     * Filter frames to only include those belonging to the current coroutine.
     * This prevents main chunk or outer native caller frames from being incorrectly
     * used as owner segments when resuming from yield-in-close.
     *
     * @param frames The frames from callerContext.snapshot()
     * @param callStackBase The base index for the current coroutine's call stack
     * @return Filtered list containing only coroutine-owned frames
     */
    private fun filterCoroutineFrames(
        frames: List<ExecutionFrame>,
        callStackBase: Int,
    ): List<ExecutionFrame> {
        if (callStackBase == 0) {
            // No filtering needed - we're in the main thread
            return frames
        }

        // Get the full call stack and build a set of protos that belong to the coroutine
        val fullStack = callStackManager.captureSnapshot()
        val coroutineFrameProtos =
            fullStack
                .drop(callStackBase)
                .mapNotNull { callFrame ->
                    callFrame.proto
                }.toSet()

        // Filter callerContext frames to only those whose proto appears in the coroutine's stack
        // This excludes main chunk and native caller frames that are before callStackBase
        // IMPORTANT: Always keep frames with TBC variables, even if not in current stack
        // (they may be suspended at native boundaries like pcall)
        val filtered =
            frames.filter { execFrame ->
                execFrame.proto in coroutineFrameProtos || execFrame.toBeClosedVars.isNotEmpty()
            }
        println("[FILTER] in=${frames.size} coroutineProtos=${coroutineFrameProtos.size} out=${filtered.size}")
        return filtered
    }

    /**
     * Encapsulates yield/resume context state for coroutine operations.
     * Groups 3 yield-related variables into a single cohesive context.
     */
    private val yieldContext = CoroutineYieldContext()

    /**
     * Encapsulates execution flow state.
     * Groups 3 execution-control variables: currentEnvUpvalue, activeExecutionFrame, nextCallIsCloseMetamethod.
     */
    private val flowState = ExecutionFlowState()

    /**
     * Debug sink for logging and debugging output.
     * Defaults to NOOP to avoid console clutter in production.
     */
    private var debugSink: VmDebugSink = VmDebugSink.NOOP

    /**
     * Original call stack snapshot for hooks.
     * When a hook is executing, this contains the call stack from where the hook was triggered,
     * allowing debug.traceback() to show the complete stack including the triggering location.
     */
    private var hookSnapshot: ExecutionSnapshot? = null

    /**
     * Coroutine state manager
     */
    private val coroutineStateManager = CoroutineStateManager()

    /**
     * Call depth counter for stack overflow detection.
     * Tracks the depth of the trampoline execution stack to prevent unbounded growth.
     * Matches Lua 5.4's LUAI_MAXCCALLS behavior (typically 200 for testing, 1000+ for production).
     * All Lua function calls (including tail calls) use the trampoline loop.
     */
    private var callDepth = 0
    private val maxCallDepth = 1000

    /**
     * Global environment table (_ENV) - wraps the globals map
     * Created once and reused across all chunk executions
     */
    private val globalsTable: LuaTable by lazy {
        val table = LuaTable()
        for ((key, value) in globals) {
            table[LuaString(key)] = value
        }
        // Expose _VERSION for compatibility tests (Lua 5.4)
        table[LuaString("_VERSION")] = LuaString("Lua 5.4")
        // _G should point to the global table itself (Lua 5.1+ standard)
        table[LuaString("_G")] = table
        table
    }

    /**
     * Call stack manager - handles frame lifecycle and provides snapshots for debugging.
     * Separates call stack management from execution logic (DDD: bounded context)
     */
    private val callStackManager = CallStackManager()

    // Debug components
    private val debugTracer = DebugTracer()

    /**
     * Get the current thread's hook state holder (LuaThread for main, CoroutineThread for coroutines)
     */
    private fun getCurrentThreadHookHolder(): ai.tenum.lua.vm.debug.ThreadHookState {
        val coroutine = coroutineStateManager.getCurrentCoroutine()
        return coroutine?.thread ?: coroutineStateManager.mainThread
    }

    private val hookManager =
        HookManager(
            getCallStack = { callStackManager.captureSnapshot() },
            getRegistry = { registryTable },
            getCurrentThread = { getCurrentThreadHookHolder() },
        )

    // Error handling components
    private val nameHintResolver = NameHintResolver()
    private val stackTraceBuilder = StackTraceBuilder()
    private val errorReporter = ErrorReporter(stackTraceBuilder)

    // Metamethod components
    private val metamethodResolver =
        MetamethodResolver(
            callFunction = { func, args -> callFunction(func, args) },
        )

    // Type operation components
    private val typeConversions = TypeConversions()
    private val typeComparisons = TypeComparisons()

    // Chunk execution component
    private val chunkExecutor by lazy {
        ChunkExecutor(
            executeProto = { proto, args, upvalues, function -> executeProto(proto, args, upvalues, function) },
            globalsTable = globalsTable,
            globals = globals,
            debugEnabled = { debugTracer.debugEnabled },
            debug = { messageBuilder -> debug(messageBuilder) },
            callStackManager = callStackManager,
        )
    }

    // Opcode dispatch component
    private val opcodeDispatcher = OpcodeDispatcher(debugTracer)

    // Expose debugEnabled for public access (used by tests)
    var debugEnabled: Boolean
        get() = debugTracer.debugEnabled
        set(value) {
            if (value) debugTracer.enableDebug() else debugTracer.disableDebug()
        }

    var debugLogger: (String) -> Unit
        get() = debugTracer.debugLogger
        set(value) {
            debugTracer.debugLogger = value
        }

    fun enableRegisterTrace(index: Int?) = debugTracer.enableRegisterTrace(index)

    init {
        initStandardLibrary()
    }

    // ===== DebugContext Interface Implementation =====

    /**
     * Get a view of the current call stack for stack inspection operations.
     * All frames (including native) are included because debug.getinfo needs to return them.
     * However, atLevel() will skip native frames when counting levels (Lua semantics).
     *
     * When called from within a hook, returns a combined view:
     * - Observed frames from the snapshot taken when the hook was triggered
     * - Current frames (hook function and any nested calls) from the live stack
     * This prevents nested hook calls from corrupting the observed state while
     * maintaining correct level counting.
     */
    override fun getStackView(): StackView {
        // If we're in a hook, combine snapshot with current live frames
        val snapshot = hookSnapshot
        if (snapshot != null) {
            // Snapshot contains the stack as it was when hook was triggered
            // Live stack now contains: [...snapshot frames..., hook frame, ...nested calls...]
            // We need to combine: snapshot frames + frames added since snapshot

            val snapshotSize = snapshot.frames.size
            val currentStack = callStackManager.captureSnapshot()

            // Get frames that were added after the snapshot (hook and nested calls)
            val newFrames =
                if (currentStack.size > snapshotSize) {
                    currentStack.subList(snapshotSize, currentStack.size)
                } else {
                    emptyList()
                }

            // Combine: snapshot (old state) + new frames (hook and its callees)
            val combinedFrames = snapshot.frames + newFrames
            return StackView(combinedFrames)
        }

        // Call stack is stored oldest-first internally
        // Include all frames: atLevel() handles native frame skipping
        return StackView(callStackManager.captureSnapshot())
    }

    /**
     * Get and clear the last error call stack.
     * Used by debug.traceback to show __close frames in error messages.
     */
    override fun getAndClearLastErrorCallStack(): List<CallFrame>? {
        val stack = lastErrorCallStack
        lastErrorCallStack = null // Clear after reading
        return stack
    }

    /**
     * Execute a hook function with proper context preservation.
     */
    override fun executeHook(
        hookFunc: LuaFunction,
        event: HookEvent,
        line: Int,
        observedStack: List<CallFrame>,
    ) {
        // Capture the execution state as an immutable snapshot
        val snapshot = ExecutionSnapshot.capture(observedStack)
        hookSnapshot = snapshot

        // Set the pending inferred name for the hook call
        pendingInferredName = snapshot.inferredName

        // Call the hook function with event name and line number
        val eventName =
            when (event) {
                HookEvent.CALL -> "call"
                HookEvent.RETURN -> "return"
                HookEvent.LINE -> "line"
                HookEvent.COUNT -> "count"
                HookEvent.TAILCALL -> "tail call" // Lua 5.4 event name
            }

        // For LINE events, pass the line number; for other events, pass nil
        // If line is -1 (stripped debug info), pass nil even for LINE events
        // This matches Lua 5.4 behavior where call/return events don't have meaningful line numbers
        val lineArg = if (event == HookEvent.LINE && line >= 0) LuaNumber.of(line) else LuaNil

        try {
            hookFunc.call(listOf(LuaString(eventName), lineArg))
        } catch (e: Exception) {
            // Hook errors shouldn't crash execution
        } finally {
            // Clear the hook context
            pendingInferredName = null
            hookSnapshot = null
        }
    }

    /**
     * Set debug hook (Phase 6.4)
     * Per-thread in Lua 5.4: if coroutine is null, uses current thread.
     */
    override fun setHook(
        coroutine: ai.tenum.lua.runtime.LuaCoroutine?,
        hook: DebugHook?,
        mask: String,
        count: Int,
    ) {
        val targetThread: ai.tenum.lua.vm.debug.ThreadHookState =
            coroutine?.thread ?: getCurrentThreadHookHolder()
        hookManager.setHook(targetThread, hook, mask, count)
    }

    /**
     * Get current hook configuration (Phase 6.4)
     * Per-thread in Lua 5.4: if coroutine is null, uses current thread.
     */
    override fun getHook(coroutine: ai.tenum.lua.runtime.LuaCoroutine?): HookConfig {
        val targetThread: ai.tenum.lua.vm.debug.ThreadHookState =
            coroutine?.thread ?: getCurrentThreadHookHolder()
        return hookManager.getHook(targetThread)
    }

    /**
     * Get registry table (Phase 6.4)
     */
    override fun getRegistry(): LuaTable = registryTable

    /**
     * Get the current execution snapshot if in a hook, null otherwise.
     */
    override fun getHookSnapshot(): ExecutionSnapshot? = hookSnapshot

    // ===== Backward Compatibility Methods (Deprecated Internally) =====

    /**
     * Set pending inferred function name for next call (used by hook invocation)
     * @deprecated Hook execution now uses ExecutionSnapshot
     */
    override fun setPendingInferredName(name: InferredFunctionName?) {
        pendingInferredName = name
    }

    /**
     * Set the hook call stack snapshot.
     * @deprecated Hook execution now uses ExecutionSnapshot via executeHook()
     */
    fun setHookCallStack(callStack: List<CallFrame>?) {
        // For backward compatibility, convert to/from ExecutionSnapshot
        if (callStack != null) {
            hookSnapshot = ExecutionSnapshot.capture(callStack)
        } else {
            hookSnapshot = null
        }
    }

    /**
     * Get the hook call stack snapshot if available, otherwise return the current call stack.
     * @deprecated Use getStackView() or getHookSnapshot() instead
     */
    fun getCallStackForTraceback(): List<CallFrame> {
        val snapshot = hookSnapshot
        val currentStack = getCallStack()

        return if (snapshot != null && currentStack.isNotEmpty()) {
            // We're in a hook - combine the stacks
            // currentStack[0] is the hook function (most recent)
            // snapshot.frames is the saved stack (oldest-first): [oldest, ..., most recent]
            // Result should be most-recent-first: [hook, main chunk, ...]
            listOf(currentStack[0]) + snapshot.frames.asReversed()
        } else {
            currentStack
        }
    }

    /**
     * Set debug hook (legacy overload)
     * @deprecated Use setHook(coroutine, hook, mask, count) with all parameters
     */
    fun setHook(
        hook: DebugHook?,
        mask: String,
    ) {
        val targetThread = getCurrentThreadHookHolder()
        hookManager.setHook(targetThread, hook, mask, 0)
    }

    // ===== End Backward Compatibility =====

    /**
     * Call debug hook if configured, preventing recursion (Phase 6.4)
     */
    private inline fun triggerHook(
        event: HookEvent,
        line: Int,
    ) = hookManager.triggerHook(event, line)

    /**
     * Enable debug logging with optional custom logger
     */
    fun enableDebug(logger: (String) -> Unit = { message -> println("[LuaVM] $message") }) = debugTracer.enableDebug(logger)

    /**
     * Disable debug logging
     */
    fun disableDebug() = debugTracer.disableDebug()

    /**
     * Internal debug logging helper - inline for zero overhead when disabled.
     * The lambda is only evaluated if debug is enabled.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun debug(messageBuilder: () -> String) = debugTracer.debug(messageBuilder)

    // Helper to emit register write traces when traceRegisterIndex is set
    private fun traceRegisterWrite(
        regIndex: Int,
        value: LuaValue<*>,
        ctx: String,
    ) = debugTracer.traceRegisterWrite(regIndex, value, ctx)

    override fun execute(
        chunk: String,
        source: String,
    ): LuaValue<*> = chunkExecutor.execute(chunk, source)

    /**
     * Close to-be-closed variables during unwinding with proper error handling.
     * Called from exception handlers to ensure __close metamethods are invoked.
     *
     * According to Lua 5.4 semantics:
     * - Each __close receives the current error as its second parameter
     * - If a __close throws a new error, that error REPLACES the current error
     * - The final error (after all __close calls) is the one that gets thrown
     *
     * @param toBeClosedVars List of (register, value) pairs to close
     * @param alreadyClosedRegs Set of registers already closed
     * @param initialErrorValue The initial error value to pass to __close metamethods (nil or error message)
     * @return The final error value after all __close calls (may be different from initialErrorValue)
     */
    private fun closeToBeClosedVars(
        toBeClosedVars: List<Pair<Int, LuaValue<*>>>,
        alreadyClosedRegs: Set<Int>,
        initialErrorValue: LuaValue<*>,
    ): LuaValue<*> {
        var currentError = initialErrorValue

        for ((reg, capturedValue) in toBeClosedVars.reversed()) {
            if (reg in alreadyClosedRegs) continue
            if (capturedValue !is LuaNil && capturedValue != LuaBoolean.FALSE) {
                val metatable = capturedValue.metatable as? LuaTable
                val closeMethod = metatable?.get(LuaString("__close"))
                if (closeMethod is LuaFunction) {
                    try {
                        // Pass the current error to the __close metamethod
                        // Mark this as a __close metamethod call so debug.getinfo can skip it
                        setMetamethodCallContext("__close")
                        flowState.setNextCallIsCloseMetamethod(true)
                        setYieldResumeContext(targetReg = 0, encodedCount = 1, stayOnSamePc = true)
                        callFunctionInternal(closeMethod, listOf(capturedValue, currentError), isCloseMetamethod = true)
                        clearYieldResumeContext()
                    } catch (closeEx: LuaException) {
                        // If __close throws, that becomes the new error
                        // The errorValue from LuaException already has location info for strings
                        currentError = closeEx.errorValue ?: (closeEx.message?.let { LuaString(it) } ?: LuaNil)
                        // Capture the call stack for debug.traceback (converted from LuaStackFrame)
                        // Note: We'll rely on LuaRuntimeError path for proper CallFrame capture
                    } catch (closeEx: LuaRuntimeError) {
                        // For LuaRuntimeError, preserve its call stack which includes the __close frame
                        // This allows debug.traceback to show "in metamethod 'close'" when used as xpcall message handler
                        lastErrorCallStack = closeEx.callStack

                        // For string errors, add location info
                        // This matches Lua 5.4 behavior where error("msg") adds location to the message
                        val rawError = closeEx.errorValue
                        currentError =
                            if (rawError is LuaString) {
                                // Add location info to string errors
                                val currentProto = callStackManager.lastFrame()?.proto
                                val currentPc = callStackManager.lastFrame()?.pc ?: 0
                                val line = currentProto?.let { stackTraceBuilder.getCurrentLine(it, currentPc) }
                                val source = currentProto?.source
                                LuaString(
                                    buildString {
                                        if (source != null || line != null) {
                                            val displaySource = source?.removePrefix("=") ?: "(load)"
                                            append(displaySource)
                                            if (line != null) {
                                                append(":").append(line)
                                            }
                                            append(": ")
                                        }
                                        append(rawError.value)
                                    },
                                )
                            } else {
                                // Non-string errors are passed through unchanged
                                rawError
                            }
                    } catch (closeEx: Exception) {
                        // For other exceptions, convert to string
                        currentError = closeEx.message?.let { LuaString(it) } ?: LuaString("error in __close")
                        lastErrorCallStack = null
                    }
                }
            }
        }

        return currentError
    }

    /**
     * Execute a compiled proto
     */
    private fun executeProto(
        proto: Proto,
        args: List<LuaValue<*>>,
        upvalues: List<Upvalue> = emptyList(),
        function: LuaFunction? = null,
        mode: ExecutionMode = ExecutionMode.FreshCall,
    ): List<LuaValue<*>> {
        // Remember entry call depth so we can fully restore it on exit (even on errors).
        // This prevents leaked callDepth increments when trampolined calls abort, which
        // would otherwise starve error handlers/__close of stack headroom (locals.lua:611).
        val callDepthBase = callDepth

        // Check call depth to prevent stack overflow (Lua 5.4 behavior)
        // Only check for fresh calls, not coroutine resumptions
        if (mode is ExecutionMode.FreshCall) {
            callDepth++
            if (callDepth > maxCallDepth) {
                callDepth = callDepthBase // Reset before throwing
                throw LuaException(
                    errorMessageOnly = "C stack overflow",
                    line = null,
                    source = proto.source,
                    luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                )
            }
        }

        // Prepare execution state using helper
        val currentCoroutine = coroutineStateManager.getCurrentCoroutine()
        val isCoroutineContext = currentCoroutine != null
        val preparation =
            ExecutionPreparation.prepare(
                mode = mode,
                proto = proto,
                args = args,
                upvalues = upvalues,
                callStackManager = callStackManager,
                isCoroutine = isCoroutineContext,
            )

        val resumptionState = (mode as? ExecutionMode.ResumeContinuation)?.state
        var currentProto = preparation.currentProto
        var execFrame = preparation.execFrame
        val initialCallStackSize = preparation.initialCallStackSize

        // Restore close owner frame stack on resume
        if (resumptionState != null && resumptionState.closeOwnerFrameStack.isNotEmpty()) {
            callerContext.restore(resumptionState.closeOwnerFrameStack)
// Restored callerContext

            // Only restore TBC vars if the current frame's TBC list is EMPTY and we have a snapshot
            // This handles the case where nested calls cleared the list, but avoids double-closing
            val topFrame = callerContext.peek()
            val isSameProto = topFrame != null && topFrame.proto == execFrame.proto
            val shouldRestoreTbc = execFrame.toBeClosedVars.isEmpty() && topFrame?.toBeClosedVars?.isNotEmpty() == true
            if (isSameProto && shouldRestoreTbc) {
                debugSink.debug { "[RESUME] Restoring TBC list from stack: ${topFrame.toBeClosedVars.size} vars" }
                execFrame.toBeClosedVars.addAll(topFrame.toBeClosedVars)
            }
        }

        // Track active execution frame for native function calls
        flowState.setActiveExecutionFrame(execFrame)

        // Create hook trigger helper
        val hookHelper = HookTriggerHelper(::triggerHook)

        // Create dispatch result processor
        val resultProcessor = DispatchResultProcessor(debugSink)

        // Create yield handler
        val yieldHandler =
            YieldHandler(
                coroutineStateManager,
                resumptionService,
                debugSink,
                callStackManager,
            )

        // Local aliases for frequently accessed frame state (from preparation)
        var registers = preparation.registers
        var constants = preparation.constants
        var instructions = preparation.instructions
        var pc = preparation.pc
        var openUpvalues = preparation.openUpvalues
        var toBeClosedVars = preparation.toBeClosedVars
        var varargs = preparation.varargs
        var currentUpvalues = preparation.currentUpvalues
        val alreadyClosedRegs = mutableSetOf<Int>() // Track which have been closed in normal flow
        // Create execution environment for opcode handlers
        var env = ExecutionEnvironment(execFrame, globals, this)

        // If resuming from a yield that occurred inside __close, use CloseResumeState
        // NEW ARCHITECTURE: Two-phase resumption orchestrator for multi-frame TBC chains
        if (mode is ExecutionMode.ResumeContinuation && resumptionState != null && resumptionState.closeResumeState != null) {
            val closeState = resumptionState.closeResumeState
            env.clearYieldResumeContext()

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

                // For single-frame case: clear segments but keep closeOwnerFrameStack for outer frames
                // For multi-frame case: store remaining segments for processing after this one completes
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

                // Set up execution context for first segment
                execFrame = segmentFrame
                flowState.setActiveExecutionFrame(segmentFrame)
                registers = segmentFrame.registers
                currentProto = segmentFrame.proto
                constants = segmentFrame.constants
                instructions = segmentFrame.instructions
                pc = segmentFrame.pc
                openUpvalues = segmentFrame.openUpvalues
                toBeClosedVars = segmentFrame.toBeClosedVars
                varargs = segmentFrame.varargs
                env = ExecutionEnvironment(segmentFrame, globals, this)

                // DON'T clear TBC vars - keep them intact so remaining close handlers can run
                // The close chain must be preserved across yield/resume boundaries

                // Restore close context state for this segment
                if (firstSegment.pendingCloseVar != null) {
                    closeContext.setPendingCloseVar(firstSegment.pendingCloseVar, firstSegment.pendingCloseStartReg)
                }
            } else {
                // No segments - clear activeCloseResumeState
                closeContext.setActiveCloseResumeState(null)
            }

            // Ensure activeExecutionFrame points to the current live frame after any rebuild
            flowState.setActiveExecutionFrame(execFrame)
        }

        // Handle coroutine resumption - place resume args as yield return values
        // Skip this if we just finished close handling and reconstructed the frame (indicated by empty TBC list after close handling)
        val justFinishedCloseHandling =
            mode is ExecutionMode.ResumeContinuation &&
                mode.state.pendingCloseYield &&
                mode.state.toBeClosedVars.isEmpty() &&
                mode.state.capturedReturnValues?.isEmpty() != false

        if (mode is ExecutionMode.ResumeContinuation && !justFinishedCloseHandling && resumptionState?.closeResumeState == null) {
            // Storing resume args
            val storage = ResultStorage(env)
            storage.storeResults(
                targetReg = mode.state.yieldTargetRegister,
                encodedCount = mode.state.yieldExpectedResults,
                results = args,
                opcodeName = "RESUME",
            )
        }

        // Create call frame for debugging/error handling (Phase 6.4 + 7.1)
        // For FRESH calls: add frame to stack
        // For RESUME: frame already exists in restored debug frames, DON'T duplicate
        if (mode is ExecutionMode.FreshCall) {
            callStackManager.beginFunctionCall(
                proto = currentProto,
                function = function,
                registers = registers,
                varargs = varargs,
                args = args,
                inferredName = pendingInferredName,
                pc = pc,
                isCloseMetamethod = flowState.nextCallIsCloseMetamethod,
            )
        }

        // Clear call context after using it
        pendingInferredName = null
        flowState.setNextCallIsCloseMetamethod(false)

        // Trigger CALL and LINE hooks for function entry
        var lastLine = hookHelper.triggerEntryHooks(currentProto, preparation.isCoroutineContext)

        // Track current upvalues for this execution context and _ENV
        val previousEnv = flowState.currentEnvUpvalue
        // Find _ENV by name, not position (_ENV is not always at index 0!)
        val envIndex = currentProto.upvalueInfo.indexOfFirst { it.name == "_ENV" }
        flowState.setCurrentEnvUpvalue(if (envIndex >= 0) currentUpvalues.getOrNull(envIndex) else null)

        // Execution stack for trampolined calls (non-tail calls)
        // Each entry represents a caller waiting for callee to return
        val execStack =
            ArrayDeque(
                (mode as? ExecutionMode.ResumeContinuation)?.state?.execStack ?: emptyList(),
            )

        /**
         * Helper function to convert a LuaValue error to a displayable string message.
         */
        fun errorValueToMessage(errorValue: LuaValue<*>): String =
            when (errorValue) {
                is LuaString -> errorValue.value
                is LuaNumber -> errorValue.toDouble().toString()
                else -> errorValue.toString()
            }

        /**
         * Helper function to handle error chaining through __close metamethods.
         * Closes to-be-closed variables and throws a new exception if the error changed.
         */
        fun handleErrorAndCloseToBeClosedVars(originalException: Throwable): Nothing {
            // Extract initial error value from the exception
            val initialError =
                when (originalException) {
                    is LuaRuntimeError -> originalException.errorValue
                    is LuaException -> originalException.errorValue ?: (originalException.message?.let { LuaString(it) } ?: LuaNil)
                    else -> originalException.message?.let { LuaString(it) } ?: LuaNil
                }

            // Mark the current frame as returning BEFORE closing to-be-closed variables
            // This makes the frame invisible to debug.getinfo during __close execution
            // (matching Lua 5.4.8 semantics where the function is conceptually already returned)
            if (toBeClosedVars.isNotEmpty()) {
                markCurrentFrameAsReturning()
            }

            // Close to-be-closed variables and get the final error (possibly changed by __close handlers)
            val finalError = closeToBeClosedVars(toBeClosedVars, alreadyClosedRegs, initialError)

            // Clear the TBC list to prevent double-closing if exception propagates to outer frames
            // This is critical: if a __close handler throws, the var is NOT removed from the list
            // (see ExecutionFrame.executeCloseMetamethods line 246), so we must clear it here
            // to prevent re-processing when the exception unwinds through multiple executeProto frames
            toBeClosedVars.clear()

            // If the error changed during cleanup, throw a new exception with the new error
            if (finalError !== initialError) {
                val errorMsg = errorValueToMessage(finalError)
                when (originalException) {
                    is LuaRuntimeError -> {
                        throw LuaRuntimeError(
                            message = errorMsg,
                            errorValue = finalError,
                            callStack = callStackManager.captureSnapshot(),
                        )
                    }
                    is LuaException -> {
                        throw LuaException(
                            errorMessageOnly = errorMsg,
                            line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                            source = currentProto.source,
                            luaStackTrace = originalException.luaStackTrace,
                            errorValueOverride = finalError,
                        )
                    }
                    else -> {
                        // Should not happen, but handle gracefully
                        throw LuaException(
                            errorMessageOnly = errorMsg,
                            line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                            source = currentProto.source,
                            luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                            errorValueOverride = finalError,
                        )
                    }
                }
            }

            // Error didn't change, re-throw the original exception
            throw originalException
        }

        try {
            debug { "--- Starting execution ---" }
            debug { "Max stack size: ${currentProto.maxStackSize}" }
            debug { "Varargs: ${varargs.size} values" }

            whileLoop@ while (pc < instructions.size) {
                // Keep activeExecutionFrame in sync with the frame currently executing bytecode
                flowState.setActiveExecutionFrame(execFrame)

                // Update current frame PC (for both debug stack and execution frame)
                callStackManager.updateLastFramePc(pc)
                execFrame.pc = pc

                // Trigger LINE hooks using helper
                lastLine = hookHelper.triggerLineHooksAt(currentProto, pc, lastLine)

                val instr = instructions[pc]

                if (debugEnabled) {
                    debug { "PC=$pc: ${instr.opcode} a=${instr.a} b=${instr.b} c=${instr.c}" }
                }

                // Get current frame for dispatch
                val currentFrame = callStackManager.lastFrame()!!

                // Dispatch opcode to handler
                when (
                    val dispatchResult =
                        opcodeDispatcher.dispatch(
                            instr = instr,
                            env = env,
                            registers = registers,
                            execFrame = execFrame,
                            openUpvalues = openUpvalues,
                            varargs = varargs,
                            pc = pc,
                            frame = currentFrame,
                            executeProto = { proto, args, upvalues, func -> executeProto(proto, args, upvalues, func) },
                            callFunction = { func, args -> callFunction(func, args, execFrame) },
                            traceRegisterWrite = { regIndex, value, ctx -> traceRegisterWrite(regIndex, value, ctx) },
                            triggerHook = { event, line -> triggerHook(event, line) },
                            setCallContext = { inferred ->
                                pendingInferredName = inferred
                            },
                        )
                ) {
                    is DispatchResult.Continue -> {
                        // Normal execution continues
                    }
                    is DispatchResult.SkipNext -> {
                        pc++ // Skip next instruction (LOADBOOL with skip)
                    }
                    is DispatchResult.Jump -> {
                        // Loop opcodes return PC-1 (expecting pc++ at end of loop to reach target)
                        pc = dispatchResult.newPc
                    }
                    is DispatchResult.Return -> {
                        debug { "[REGISTERS after RETURN] ${registers.slice(0..10).mapIndexed { i, v -> "R[$i]=$v" }.joinToString(", ")}" }

                        // Process RETURN using helper to determine loop action
                        when (
                            val action =
                                resultProcessor.processReturn(
                                    dispatchResult = dispatchResult,
                                    execStack = execStack,
                                    execFrame = execFrame,
                                    activeCloseState = closeContext.activeCloseResumeState,
                                )
                        ) {
                            is ReturnLoopAction.UnwindToCaller -> {
                                debug { "  Unwinding from trampolined call, restoring caller" }

                                // Pop callee's call frame from debug stack
                                callStackManager.removeLastFrame()

                                // Restore caller's execution state
                                currentProto = action.callerContext.proto
                                execFrame = action.callerContext.execFrame
                                registers = action.callerContext.registers
                                constants = action.callerContext.constants
                                instructions = action.callerContext.instructions
                                pc = action.callerContext.pc
                                openUpvalues = execFrame.openUpvalues
                                toBeClosedVars = execFrame.toBeClosedVars
                                varargs = action.callerContext.varargs
                                currentUpvalues = action.callerContext.currentUpvalues
                                lastLine = action.callerContext.lastLine

                                // Recreate execution environment for caller
                                env = ExecutionEnvironment(execFrame, globals, this)

                                // Store callee's return values into caller's registers
                                val callInstr = action.callerContext.callInstruction
                                val storage = ResultStorage(env)
                                storage.storeResults(
                                    targetReg = callInstr.a,
                                    encodedCount = callInstr.c,
                                    results = action.returnValues,
                                    opcodeName = "CALL-RETURN",
                                )

                                // Decrement call depth (unwinding from callee)
                                callDepth--
                            }
                            is ReturnLoopAction.ContinueSegment -> {
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
                                        val storage = ResultStorage(ExecutionEnvironment(segmentFrame, globals, this))
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
                                currentProto = action.nextSegment.proto
                                execFrame = segmentFrame
                                flowState.setActiveExecutionFrame(segmentFrame)
                                registers = segmentFrame.registers
                                constants = segmentFrame.constants
                                instructions = segmentFrame.instructions
                                pc = segmentFrame.pc
                                openUpvalues = segmentFrame.openUpvalues
                                toBeClosedVars = segmentFrame.toBeClosedVars
                                varargs = segmentFrame.varargs
                                currentUpvalues = execFrame.upvalues
                                env = ExecutionEnvironment(segmentFrame, globals, this)

                                // Restore close context state for this segment
                                if (action.nextSegment.pendingCloseVar != null) {
                                    closeContext.setPendingCloseVar(
                                        action.nextSegment.pendingCloseVar,
                                        action.nextSegment.pendingCloseStartReg,
                                    )
                                }
                            }
                            is ReturnLoopAction.ContinueOuterFrame -> {
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

                                // Restore execution state
                                execFrame = outerFrame
                                flowState.setActiveExecutionFrame(outerFrame)
                                currentProto = action.outerFrame.proto
                                registers = outerFrame.registers
                                constants = currentProto.constants
                                instructions = currentProto.instructions
                                pc = action.outerFrame.pcToResume
                                openUpvalues = outerFrame.openUpvalues
                                toBeClosedVars = outerFrame.toBeClosedVars
                                varargs = action.outerFrame.varargs
                                env = ExecutionEnvironment(outerFrame, globals, this)

                                // Store results from previous frame
                                val callPc = pc - 1
                                if (callPc >= 0 && callPc < instructions.size) {
                                    val instr = instructions[callPc]
                                    if (instr.opcode == OpCode.CALL || instr.opcode == OpCode.TAILCALL) {
                                        ResultStorage(env).storeResults(instr.a, instr.c, action.finalReturnValues, "SEGMENT-FINAL-RETURN")
                                    }
                                }

                                // Continue execution
                                continue
                            }
                            is ReturnLoopAction.ExitProto -> {
                                println(
                                    "[NO-CALLER] activeCloseState=${closeContext.activeCloseResumeState != null} segments=${closeContext.activeCloseResumeState?.ownerSegments?.size} outerFrames=${closeContext.activeCloseResumeState?.closeOwnerFrameStack?.size}",
                                )
                                return action.returnValues
                            }
                        }
                    }
                    is DispatchResult.CallTrampoline -> {
                        debug { "  Trampolining into regular call" }

                        // Save current (caller) execution context
                        val callerContext =
                            ExecContext(
                                proto = currentProto,
                                execFrame = execFrame,
                                registers = registers,
                                constants = constants,
                                instructions = instructions,
                                pc = pc,
                                varargs = varargs,
                                currentUpvalues = currentUpvalues,
                                callInstruction = dispatchResult.callInstruction,
                                lastLine = lastLine,
                            )
                        execStack.addLast(callerContext)

                        // Increment call depth (entering new function)
                        callDepth++
                        if (callDepth > maxCallDepth) {
                            callDepth--
                            execStack.removeLast()
                            throw LuaException(
                                errorMessageOnly = "C stack overflow",
                                line = null,
                                source = currentProto.source,
                                luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                            )
                        }

                        // Process trampoline using helper
                        val action = resultProcessor.processCallTrampoline(dispatchResult)

                        // Apply state changes
                        currentProto = action.newProto
                        constants = currentProto.constants
                        instructions = currentProto.instructions
                        pc = -1
                        execFrame = action.newFrame
                        registers = execFrame.registers
                        openUpvalues = execFrame.openUpvalues
                        toBeClosedVars = execFrame.toBeClosedVars
                        varargs = execFrame.varargs
                        currentUpvalues = action.newUpvalues
                        env = ExecutionEnvironment(execFrame, globals, this)

                        // Push callee's call frame onto debug stack
                        callStackManager.addFrame(
                            CallFrame(
                                function = action.luaFunc,
                                proto = currentProto,
                                pc = 0,
                                base = 0,
                                registers = registers,
                                isNative = false,
                                isTailCall = false,
                                inferredFunctionName = pendingInferredName,
                                varargs = varargs,
                                ftransfer = if (dispatchResult.newArgs.isEmpty()) 0 else 1,
                                ntransfer = dispatchResult.newArgs.size,
                            ),
                        )
                        pendingInferredName = null

                        // Trigger CALL and LINE hooks for trampolined function
                        lastLine = hookHelper.triggerEntryHooks(currentProto, preparation.isCoroutineContext)
                    }
                    is DispatchResult.TailCallTrampoline -> {
                        debug { "  TCO: Before trampoline, registers.hashCode=${registers.hashCode()}" }

                        // Log closure identity and upvalue wiring for diagnosis
                        if (debugEnabled) {
                            val cid = dispatchResult.newProto.hashCode()
                            debug { "  TCO: trampoline into closure id=$cid" }
                            dispatchResult.newUpvalues.forEachIndexed { i, uv ->
                                try {
                                    val regsHash = uv.registers?.hashCode() ?: 0
                                    val regIdx =
                                        try {
                                            uv.registerIndex
                                        } catch (_: Exception) {
                                            -1
                                        }
                                    debug { "    upval[$i] regIdx=$regIdx regsHash=$regsHash closed=${uv.isClosed}" }
                                } catch (_: Exception) {
                                }
                            }
                        }

                        // Clear previous frame resources
                        for ((regIdx, upvalue) in openUpvalues) {
                            if (!upvalue.isClosed) {
                                debug { "  TCO: Closing upvalue at R[$regIdx] before trampoline" }
                                upvalue.close()
                            }
                        }
                        openUpvalues.clear()
                        toBeClosedVars.clear()
                        alreadyClosedRegs.clear()

                        // Calculate tail call depth
                        val previousFrame = callStackManager.lastFrame()
                        val currentTailDepth =
                            if (previousFrame?.isTailCall == true) {
                                previousFrame.tailCallDepth
                            } else {
                                0
                            }

                        // Process tail call using helper (reuses registers for TCO)
                        debug { "  TCO: About to fill registers with nil, registers.hashCode=${registers.hashCode()}" }
                        val action = resultProcessor.processTailCallTrampoline(dispatchResult, registers, currentTailDepth)
                        debug { "  TCO: After fill, registers[0]=${registers[0]}, registers.hashCode=${registers.hashCode()}" }

                        // Apply state changes
                        currentProto = action.newProto
                        constants = currentProto.constants
                        instructions = currentProto.instructions
                        pc = -1
                        execFrame = action.newFrame
                        execFrame.top = 0
                        varargs = action.newFrame.varargs
                        currentUpvalues = action.newUpvalues
                        flowState.setActiveExecutionFrame(execFrame)
                        env = ExecutionEnvironment(execFrame, globals, this)

                        // Remove previous frame and push new tail call frame
                        callStackManager.removeLastFrame()
                        val tailFrame =
                            CallFrame(
                                function = action.luaFunc,
                                proto = currentProto,
                                pc = pc,
                                base = 0,
                                registers = registers,
                                isNative = false,
                                isTailCall = true,
                                tailCallDepth = action.newTailCallDepth,
                                inferredFunctionName = pendingInferredName,
                                varargs = varargs,
                                ftransfer = if (dispatchResult.newArgs.isEmpty()) 0 else 1,
                                ntransfer = dispatchResult.newArgs.size,
                            )
                        callStackManager.addFrame(tailFrame)
                        pendingInferredName = null

                        // Trigger TAILCALL hook and reset lastLine
                        triggerHook(HookEvent.TAILCALL, currentProto.lineDefined)
                        lastLine = -1
                    }
                }
                pc++

                // Trigger COUNT hook
                val currentLineForHook = callStackManager.lastFrame()?.getCurrentLine() ?: -1
                hookManager.checkCountHook(currentLineForHook)
            }

            // If we reach here without a return, check for captured returns first
            // Captured returns are set by RETURN opcode when yielding from __close
            if (execFrame.capturedReturns != null) {
                return execFrame.capturedReturns!!
            }

            // Otherwise return no values
            return emptyList()
        } catch (e: LuaYieldException) {
            // Coroutine yielded - save execution state for resumption
            if (!e.stateSaved) {
                val currentCoroutine = coroutineStateManager.getCurrentCoroutine()
                val callStackBase = (currentCoroutine as? LuaCoroutine.LuaFunctionCoroutine)?.thread?.callStackBase ?: 0

                yieldHandler.handleYield(
                    exception = e,
                    currentProto = currentProto,
                    pc = pc,
                    registers = registers,
                    instructions = instructions,
                    execFrame = execFrame,
                    varargs = varargs,
                    execStack = execStack,
                    yieldContext = yieldContext,
                    closeContext = closeContext,
                    callerContext = callerContext,
                    callStackBase = callStackBase,
                    filterCoroutineFrames = ::filterCoroutineFrames,
                    getCapturedReturnValues = ::getCapturedReturnValues,
                )
            }
            clearYieldResumeContext()
            throw e // Re-throw to be caught by coroutineResume
        } catch (e: LuaRuntimeError) {
            // Preserve the full call stack from LuaRuntimeError BEFORE converting to LuaException
            // This ensures __close metamethod frames with isCloseMetamethod=true are preserved
            // for debug.traceback() even when the simplified LuaException loses them
            preserveErrorCallStack(e.callStack)

            // If executing in a coroutine, save the call stack for debug.traceback()
            val currentCoroutine = coroutineStateManager.getCurrentCoroutine()
            if (currentCoroutine is LuaCoroutine.LuaFunctionCoroutine) {
                // Save the error stack so debug.traceback() can access it after coroutine dies
                val callStackBase = currentCoroutine.thread.callStackBase

                // Filter out yield frames from saved call stack for error tracebacks
                // Yield frames are temporary suspension points and shouldn't appear in error tracebacks
                // However, keep the 'error' function frame as Lua 5.4 includes it
                val coroutineCallStack =
                    e.callStack.drop(callStackBase).filter { frame ->
                        // Keep all non-native frames
                        // For native frames, only filter out coroutine.yield (keep error, etc.)
                        !frame.isNative || (frame.function as? LuaNativeFunction)?.name != "coroutine.yield"
                    }
                currentCoroutine.thread.savedCallStack = coroutineCallStack
            }

            // When level=0 or errorValue is nil, return raw message with no location info
            if (e.level == 0 || e.errorValue is LuaNil) {
                throw LuaException(
                    errorMessageOnly = e.message ?: "",
                    line = null, // No line info for level=0
                    source = null, // No source info for level=0
                    luaStackTrace = emptyList(), // No stack trace for level=0
                    errorValueOverride = e.errorValue, // Preserve original error value (especially for nil)
                )
            }

            // Determine which stack frame to report based on error level
            // level=1 means error location (where error() was called)
            // level=2 means caller of the function that called error()
            // level=3 means caller's caller, etc.
            // Skip native frames when determining the error location
            val reversedStack = e.callStack.asReversed()
            val nonNativeFrames = reversedStack.filter { !it.isNative }
            val targetFrameIndex = e.level - 1

            val (errorLine, errorSource) =
                if (targetFrameIndex >= 0 && targetFrameIndex < nonNativeFrames.size) {
                    val targetFrame = nonNativeFrames[targetFrameIndex]
                    Pair(targetFrame.getCurrentLine(), targetFrame.proto?.source ?: "?")
                } else {
                    // If level is beyond stack depth, use current location
                    Pair(stackTraceBuilder.getCurrentLine(currentProto, pc), currentProto.source)
                }

            // Convert LuaRuntimeError to LuaException (LuaRuntimeError already has stack trace)
            val errorMsg = e.message ?: "runtime error"

            // Build the stack trace (using nonNativeFrames to exclude native functions)
            val stackTrace =
                nonNativeFrames.map { frame ->
                    LuaStackFrame(
                        functionName = frame.proto?.name?.takeIf { it.isNotEmpty() },
                        source = frame.proto?.source ?: "?",
                        line = frame.getCurrentLine(),
                    )
                }

            // For non-string errors (tables, nil, etc.), pass errorValueOverride to preserve the original value
            // For string errors, let LuaException create formatted errorValue automatically
            val luaEx =
                if (e.errorValue is ai.tenum.lua.runtime.LuaString) {
                    LuaException(
                        errorMessageOnly = errorMsg,
                        line = errorLine,
                        source = errorSource,
                        luaStackTrace = stackTrace,
                    )
                } else {
                    LuaException(
                        errorMessageOnly = errorMsg,
                        line = errorLine,
                        source = errorSource,
                        luaStackTrace = stackTrace,
                        errorValueOverride = e.errorValue,
                    )
                }

            // When an assertion fails during compat testing, dump nearby proto info to help debugging
            try {
                val msg = luaEx.message ?: ""
                if (msg.contains("assertion failed") || debugEnabled) {
                    val proto = currentProto
                    val start = maxOf(0, pc - 8)
                    val end = minOf(proto.instructions.size - 1, pc + 8)
                    debugSink.debug { "--- Proto dump for ${proto.name} source=${proto.source} pc=$pc ---" }
                    for (i in start..end) {
                        val ins = proto.instructions[i]
                        debugSink.debug {
                            "  PC=$i: ${ins.opcode} a=${ins.a} b=${ins.b} c=${ins.c}  # line=${stackTraceBuilder.getCurrentLine(proto, i)}"
                        }
                    }
                    debugSink.debug { "--- Constants (${proto.constants.size}) ---" }
                    for ((ci, c) in proto.constants.withIndex()) {
                        if (ci in (0..199)) debugSink.debug { "  K[$ci] = $c" }
                    }
                    debugSink.debug { "--- Line events (${proto.lineEvents.size}) ---" }
                    for (li in proto.lineEvents) debugSink.debug { "  PC=${li.pc} -> line=${li.line} kind=${li.kind}" }
                    debugSink.debug { "--- End proto dump ---" }
                }
            } catch (_: Exception) {
            }

            // Close to-be-closed variables and handle error chaining
            handleErrorAndCloseToBeClosedVars(luaEx)
        } catch (e: LuaException) {
            // Already a LuaException - close to-be-closed vars and handle error chaining
            handleErrorAndCloseToBeClosedVars(e)
        } catch (e: Exception) {
            // Don't catch LuaYieldException - it should propagate to coroutineResume
            if (e is LuaYieldException) {
                println("[EXCEPTION HANDLER] Rethrowing LuaYieldException")
                throw e
            }

            println("[EXCEPTION HANDLER] Converting ${e::class.simpleName} to LuaException")

            // Convert RuntimeException to LuaException, optionally with enhanced diagnostic info
            val extraInfo =
                if (debugEnabled) {
                    try {
                        val instr = if (pc in instructions.indices) instructions[pc] else null
                        "pc=$pc, instr=${instr?.opcode ?: "?"} a=${instr?.a ?: -1} b=${instr?.b ?: -1} c=${instr?.c ?: -1}, registers=${registers.size}, constants=${constants.size}, instructions=${instructions.size}"
                    } catch (inner: Exception) {
                        "pc=$pc, registers=${registers.size}, constants=${constants.size}, instructions=${instructions.size}"
                    }
                } else {
                    null
                }

            val composedMessage =
                when (e) {
                    is RuntimeException -> {
                        val base = e.message ?: "runtime error"
                        if (extraInfo != null) "$base ($extraInfo)" else base
                    }
                    else -> {
                        if (extraInfo != null) {
                            "${e::class.simpleName}: ${e.message} ($extraInfo)"
                        } else {
                            "${e::class.simpleName}: ${e.message}"
                        }
                    }
                }

            val luaEx =
                if (e is RuntimeException) {
                    LuaException(
                        errorMessageOnly = composedMessage,
                        line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                        source = currentProto.source,
                        luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                    )
                } else {
                    // Wrap non-runtime exceptions as LuaException to preserve context
                    LuaException(
                        errorMessageOnly = composedMessage,
                        line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                        source = currentProto.source,
                        luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                    )
                }

            // On error, close all to-be-closed variables in reverse order and handle error chaining
            handleErrorAndCloseToBeClosedVars(luaEx)
        } catch (e: Error) {
            // Convert platform errors to Lua errors with proper stack trace
            val errorType = e::class.simpleName ?: "Error"

            when {
                errorType.contains("StackOverflow") -> {
                    // Convert platform stack overflow to Lua "C stack overflow" error
                    // This catches StackOverflowError on JVM and similar errors on other platforms
                    // This matches Lua 5.4 behavior and lets the platform determine the actual limit
                    throw LuaException(
                        errorMessageOnly = "C stack overflow",
                        line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                        source = currentProto.source,
                        luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                    )
                }
                errorType.contains("OutOfMemory") -> {
                    // Convert out of memory errors to Lua error with stack trace
                    // This helps identify where in Lua code the OOM occurred
                    throw LuaException(
                        errorMessageOnly = "not enough memory",
                        line = stackTraceBuilder.getCurrentLine(currentProto, pc),
                        source = currentProto.source,
                        luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                    )
                }
                else -> throw e
            }
        } finally {
            // Restore call depth to entry value (cleans up leaked increments on error paths)
            callDepth = callDepthBase
            clearYieldResumeContext()

            // Clear active execution frame
            flowState.setActiveExecutionFrame(null)

            // Remove all frames added during this execution (including tail-called frames)
            // This cleans up the call stack after trampolined tail calls
            callStackManager.cleanupFrames(initialCallStackSize)

            // Restore previous _ENV upvalue
            flowState.setCurrentEnvUpvalue(previousEnv)
        }
    }

    fun callFunction(
        func: LuaValue<*>,
        args: List<LuaValue<*>>,
        callerFrame: ExecutionFrame? = null,
    ): List<LuaValue<*>> {
        // Push caller frame onto close owner stack to preserve context across native boundaries
        // IMPORTANT: Share the TBC list reference (don't copy) so that changes made by CLOSE
        // instructions after this call are visible when building CloseResumeState during yields
        // ONLY push if not already at top (avoid duplicates from nested native calls)
        // Use referential equality (not proto equality) since different execution instances of the same function are distinct
        val shouldPush = callerFrame != null && (callerContext.size == 0 || callerContext.peek() !== callerFrame)
        println(
            "[PUSH-FIX] frame=${callerFrame?.proto?.name} hasTBC=${callerFrame?.toBeClosedVars?.isNotEmpty()} tbcCount=${callerFrame?.toBeClosedVars?.size} shouldPush=$shouldPush",
        )
        if (shouldPush) {
            // Create a shallow snapshot - most fields are immutable or shouldn't change
            // TBC list is shared (not copied) so CLOSE instructions update it
            val snapshotFrame =
                ExecutionFrame(
                    proto = callerFrame.proto,
                    initialArgs = emptyList(), // Not used after construction
                    upvalues = callerFrame.upvalues,
                    initialPc = callerFrame.pc,
                    existingRegisters = callerFrame.registers,
                    existingVarargs = callerFrame.varargs,
                    existingToBeClosedVars = callerFrame.toBeClosedVars, // Share, don't copy!
                    existingOpenUpvalues = callerFrame.openUpvalues,
                )
            callerContext.push(snapshotFrame)
        }

        var didYield = false

        try {
            return when (func) {
                is LuaFunction -> {
                    // Call through private callFunction to ensure hooks are triggered
                    callFunctionInternal(func, args)
                }
                else -> {
                    // Check for __call metamethod in the value's metatable
                    val callMeta = metamethodResolver.getMetamethod(func, "__call")
                    if (callMeta != null) {
                        setMetamethodCallContext("__call")
                        // Recursively call through callFunction to support chained __call metamethods
                        callFunction(callMeta, listOf(func) + args, callerFrame)
                    } else {
                        throw RuntimeException("attempt to call a ${func.type().name.lowercase()} value")
                    }
                }
            }
        } catch (e: LuaYieldException) {
            // Don't pop the stack on yield - the owner frame must persist for resume
            // The stack will be saved with coroutine state and restored on resume
            didYield = true
            throw e
        } finally {
            // Pop caller frame only on normal return or non-yield exception
            // Match by proto reference since we pushed a snapshot, not the exact frame
            // IMPORTANT: If the popped frame has TBC vars, transfer them to activeExecutionFrame
            // This ensures outer __close handlers (like x in pcall test) are processed by RETURN
            val shouldPopCallerFrame = !didYield && callerFrame != null && callerContext.size > 0
            val matchesCallerProto = callerContext.peek()?.proto == callerFrame?.proto
            if (shouldPopCallerFrame && matchesCallerProto) {
                val frameToPop = callerContext.peek()
                val activeFrame = flowState.activeExecutionFrame
                debugSink.debug {
                    "[callFunction finally] Popping frame: proto=${frameToPop?.proto?.name} TBC.size=${frameToPop?.toBeClosedVars?.size}"
                }
                debugSink.debug { "[callFunction finally] activeFrame=${activeFrame != null} activeProto=${activeFrame?.proto?.name}" }

                // Transfer TBC vars to active frame so RETURN instruction can process them
                // BUT only if they're different lists (we share the TBC reference when pushing, so check identity)
                if (frameToPop != null && frameToPop.toBeClosedVars.isNotEmpty() && activeFrame != null) {
                    debugSink.debug { "[callFunction finally] Transferring ${frameToPop.toBeClosedVars.size} TBC vars to active frame" }
                    debugSink.debug { "[callFunction finally] Active frame proto: ${activeFrame.proto.name}" }
                    debugSink.debug { "[callFunction finally] Active frame TBC before: ${activeFrame.toBeClosedVars}" }
                    debugSink.debug { "[callFunction finally] Same list? ${frameToPop.toBeClosedVars === activeFrame.toBeClosedVars}" }

                    // Only transfer if they're different list instances
                    // When we push, we share the TBC list reference, so they'll be identical
                    if (frameToPop.toBeClosedVars !== activeFrame.toBeClosedVars) {
                        debugSink.debug { "[callFunction finally] Lists are different, transferring" }
                        activeFrame.toBeClosedVars.addAll(frameToPop.toBeClosedVars)
                    } else {
                        debugSink.debug { "[callFunction finally] Lists are same (shared), no transfer needed" }
                    }
                    debugSink.debug { "[callFunction finally] Active frame TBC after: ${activeFrame.toBeClosedVars}" }
                }

                callerContext.pop()
            }
        }
    }

    override fun callFunction(
        func: LuaValue<*>,
        args: List<LuaValue<*>>,
    ): List<LuaValue<*>> = callFunction(func, args, flowState.activeExecutionFrame)

    /**
     * Call a function with explicit __close error handling.
     * Returns both the captured return values and any exception from __close metamethods.
     * This matches Lua 5.4 semantics where return values are placed on the stack before __close runs.
     *
     * @param func The function to call
     * @param args The arguments to pass
     * @return CallResult with return values and optional __close exception
     */
    fun callFunctionWithCloseHandling(
        func: LuaValue<*>,
        args: List<LuaValue<*>>,
    ): ai.tenum.lua.vm.library.CallResult =
        when (func) {
            is LuaFunction -> {
                // Clear any previous state, but preserve captured return values if this is a __close call
                // __close might be called during RETURN, and we need to preserve the return values
                val savedCaptured = if (flowState.nextCallIsCloseMetamethod) getCapturedReturnValues() else null
                clearCloseException()
                savedCaptured?.let { setCapturedReturnValues(it) }

                var returnValues: List<LuaValue<*>>? = null
                var closeException: Exception? = null

                try {
                    returnValues = callFunctionInternal(func, args)
                    // If successful, check if __close set an exception anyway (shouldn't happen)
                    closeException = getCloseException()
                } catch (e: Exception) {
                    // Exception from __close or function body
                    // Check if we captured return values before __close threw
                    returnValues = getCapturedReturnValues()
                    closeException = e
                }

                // If we have no return values but have captured ones, use them
                if (returnValues == null && getCapturedReturnValues() != null) {
                    returnValues = getCapturedReturnValues()
                }

                ai.tenum.lua.vm.library.CallResult(
                    returnValues = returnValues ?: emptyList(),
                    closeException = closeException,
                )
            }
            else -> {
                // Check for __call metamethod in the value's metatable
                val callMeta = metamethodResolver.getMetamethod(func, "__call")
                if (callMeta != null) {
                    setMetamethodCallContext("__call")
                    // Recursively call through callFunctionWithCloseHandling
                    callFunctionWithCloseHandling(callMeta, listOf(func) + args)
                } else {
                    throw RuntimeException("attempt to call a ${func.type().name.lowercase()} value")
                }
            }
        }

    /**
     * Convert a LuaNumber to a Double value.
     * Helper to eliminate duplication in arithmetic operations.
     */
    private fun LuaNumber.toDoubleValue(): Double = if (this is LuaLong) this.value.toDouble() else (this as LuaDouble).value

    private fun binaryOp(
        left: LuaValue<*>,
        right: LuaValue<*>,
        op: (Double, Double) -> Double,
    ): LuaValue<*> =
        when {
            left is LuaLong && right is LuaLong -> {
                val result = op(left.value.toDouble(), right.value.toDouble())
                // Use LuaNumber.of which handles platform-specific safe integer checks
                LuaNumber.of(result)
            }
            left is LuaNumber && right is LuaNumber -> {
                val leftVal = left.toDoubleValue()
                val rightVal = right.toDoubleValue()
                LuaDouble(op(leftVal, rightVal))
            }
            else -> {
                val leftType = left.type().name.lowercase()
                val rightType = right.type().name.lowercase()
                throw RuntimeException("attempt to perform arithmetic on a $leftType value")
            }
        }

    private fun bitwiseOp(
        left: LuaValue<*>,
        right: LuaValue<*>,
        op: (Long, Long) -> Long,
    ): LuaValue<*> {
        val leftVal =
            when (left) {
                is LuaLong -> left.value
                is LuaDouble -> left.value.toLong()
                else -> return LuaNil
            }
        val rightVal =
            when (right) {
                is LuaLong -> right.value
                is LuaDouble -> right.value.toLong()
                else -> return LuaNil
            }
        return LuaLong(op(leftVal, rightVal))
    }

    private fun divOp(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): LuaValue<*> {
        // Division (/) always returns float in Lua 5.4
        return when {
            left is LuaNumber && right is LuaNumber -> {
                val leftVal = left.toDoubleValue()
                val rightVal = right.toDoubleValue()
                LuaDouble(leftVal / rightVal)
            }
            else -> LuaNil
        }
    }

    private fun floorDiv(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): LuaValue<*> =
        when {
            left is LuaLong && right is LuaLong -> {
                // Integer // Integer -> Integer (Lua 5.4 semantics)
                // Use integer division directly to avoid precision loss
                val a = left.value
                val b = right.value
                // Lua floor division: floor(a/b)
                // For integers, this is: a // b with floor semantics (toward negative infinity)
                val q = a / b // Kotlin integer division truncates toward zero
                val r = a % b
                // Adjust if remainder is non-zero and signs differ
                val result = if (r != 0L && (a xor b) < 0) q - 1 else q
                LuaNumber.of(result)
            }
            left is LuaNumber && right is LuaNumber -> {
                // At least one operand is float -> Float result
                val leftVal = left.toDoubleValue()
                val rightVal = right.toDoubleValue()
                val result = kotlin.math.floor(leftVal / rightVal)
                LuaDouble(result)
            }
            else -> LuaNil
        }

    override fun isTruthy(value: LuaValue<*>): Boolean = typeConversions.isTruthy(value)

    override fun luaEquals(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean = typeComparisons.luaEquals(left, right)

    override fun luaLessThan(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean? = typeComparisons.luaLessThan(left, right)

    override fun luaLessOrEqual(
        left: LuaValue<*>,
        right: LuaValue<*>,
    ): Boolean? = typeComparisons.luaLessOrEqual(left, right)

    override fun toNumber(value: LuaValue<*>): Double = typeConversions.toNumber(value)

    override fun toString(value: LuaValue<*>): String = typeConversions.toString(value)

    private fun initStandardLibrary() {
        // Track native call depth for yield across C boundary detection
        var nativeCallDepth = 0

        // Initialize all registered libraries with full context (including VM reference for debug library)
        val context =
            LuaLibraryContext(
                registerGlobal = { name, value ->
                    globals[name] = value
                    loadedModules[name] = value
                },
                getGlobal = { name -> globals[name] },
                getMetamethod = { value, methodName -> metamethodResolver.getMetamethod(value, methodName) },
                callFunction = { func, args ->
                    val isNativeCall = func is LuaNativeFunction
                    try {
                        if (isNativeCall) {
                            nativeCallDepth++
                        }
                        // Pass activeExecutionFrame as callerFrame so TBC ownership is tracked
                        // This ensures frames with TBC (like outer frame with 'x' in pcall test)
                        // are saved in closeOwnerFrameStack during yields
                        val callerFrame = flowState.activeExecutionFrame
                        callFunction(func, args, callerFrame)
                    } finally {
                        if (isNativeCall) {
                            nativeCallDepth--
                        }
                    }
                },
                fileSystem = fileSystem,
                executeChunk = { chunk, source -> execute(chunk, source) },
                loadedModules = loadedModules,
                packagePath = packagePath,
                vm = this, // Pass VM reference for debug library (Phase 6.4)
                executeProto = { proto, args, upvalues -> executeProto(proto, args, upvalues) }, // For load() function
                executeProtoWithResume = { proto, args, upvalues, function, thread, pendingCloseYield ->
                    // Convert CoroutineThread to ExecutionMode.ResumeContinuation
                    val mode =
                        if (thread != null) {
                            ExecutionMode.ResumeContinuation(
                                ResumptionState(
                                    proto = thread.proto!!,
                                    pc = thread.pc,
                                    registers = thread.registers!!,
                                    upvalues = thread.upvalues,
                                    varargs = thread.varargs,
                                    yieldTargetRegister = thread.yieldTargetRegister,
                                    yieldExpectedResults = thread.yieldExpectedResults,
                                    toBeClosedVars = thread.toBeClosedVars,
                                    pendingCloseStartReg = thread.pendingCloseStartReg,
                                    pendingCloseVar = thread.pendingCloseVarState,
                                    execStack = thread.savedExecStack,
                                    pendingCloseYield = pendingCloseYield || thread.toBeClosedVars.isNotEmpty(),
                                    capturedReturnValues = thread.capturedReturnValues,
                                    pendingCloseContinuation = thread.pendingCloseContinuation,
                                    pendingCloseErrorArg = thread.pendingCloseErrorArg,
                                    debugCallStack = thread.savedCallStack,
                                    closeResumeState = thread.closeResumeState,
                                ),
                            )
                        } else {
                            ExecutionMode.FreshCall
                        }
                    executeProto(proto, args, upvalues, function, mode)
                }, // For coroutine resumption with saved state
                getCurrentEnv = { flowState.currentEnvUpvalue }, // Provide current _ENV for load() inheritance
                getCurrentCoroutine = { coroutineStateManager.getCurrentCoroutine() }, // Get currently executing coroutine
                getMainThread = { coroutineStateManager.mainThread }, // Get main thread
                setCurrentCoroutine = { coroutine -> coroutineStateManager.setCurrentCoroutine(coroutine) }, // Set current coroutine
                getCallStack = { callStackManager.captureSnapshot() }, // Provide call stack for error reporting
                getNativeCallDepth = { nativeCallDepth }, // Provide current native call depth
                saveNativeCallDepth = { depth -> nativeCallDepth = depth }, // Save native call depth (for coroutine context isolation)
                getCoroutineStateManager = { coroutineStateManager }, // Provide coroutine state manager
                cleanupCallStackFrames = { initialSize -> callStackManager.cleanupFrames(initialSize) }, // Clean up call stack frames
            )
        libraryRegistry.initializeAll(context)
    }

    /**
     * Call a function with arguments (internal implementation with hook support)
     * @param isCloseMetamethod If true, marks the call frame as a __close metamethod call (transparent for debug.getinfo)
     */
    private fun callFunctionInternal(
        func: LuaFunction,
        args: List<LuaValue<*>>,
        isCloseMetamethod: Boolean = false,
    ): List<LuaValue<*>> =
        when (func) {
            is LuaNativeFunction -> {
                // Create CallFrame for native function so debug.getinfo can access it
                // Native frames are needed for hooks but excluded from error line tracking
                // Populate registers with function arguments so debug.getlocal can access them (db.lua:403-407)
                val frame =
                    CallFrame(
                        function = func,
                        proto = null,
                        pc = 0,
                        base = 0,
                        registers = args.toMutableList(),
                        isNative = true,
                        inferredFunctionName = pendingInferredName,
                        varargs = emptyList(),
                        ftransfer = if (args.isEmpty()) 0 else 1, // Lua 5.4: 0 if no parameters, else 1
                        ntransfer = args.size, // Lua 5.4: number of parameters
                        isCloseMetamethod = flowState.nextCallIsCloseMetamethod,
                    )

                val initialCallStackSize = callStackManager.size
                callStackManager.addFrame(frame)

                // Clear call context after using it
                pendingInferredName = null
                flowState.setNextCallIsCloseMetamethod(false)

                // Check call depth for native functions too
                callDepth++
                if (callDepth > maxCallDepth) {
                    callDepth--
                    callStackManager.cleanupFrames(initialCallStackSize)
                    throw LuaException(
                        errorMessageOnly = "C stack overflow",
                        line = null,
                        source = null,
                        luaStackTrace = stackTraceBuilder.buildLuaStackTrace(callStackManager.captureSnapshot()),
                    )
                }

                // Trigger CALL hook for native function
                triggerHook(HookEvent.CALL, 0)

                var isYieldException = false
                try {
                    val results = func.function(args)

                    // Update frame transfer info for return values before triggering RETURN hook
                    // For native functions: ftransfer = 1 + param_count (no locals in native code)
                    val currentFrame = callStackManager.lastFrame()
                    if (currentFrame != null) {
                        val returnFtransfer = 1 + args.size
                        val updatedFrame = CallOpcodes.prepareFrameWithReturnValues(currentFrame, returnFtransfer, results)
                        callStackManager.replaceLastFrame(updatedFrame)
                    }

                    // Trigger RETURN hook for native function
                    triggerHook(HookEvent.RETURN, 0)

                    results
                } catch (e: LuaYieldException) {
                    // For yield exceptions, DON'T clean up the call stack in finally
                    // The call stack needs to include the yield frame for debug.traceback
                    isYieldException = true
                    throw e
                } finally {
                    // Decrement call depth
                    callDepth--

                    // Clean up call stack (unless it was a yield exception)
                    if (!isYieldException) {
                        callStackManager.cleanupFrames(initialCallStackSize)
                    }
                }
            }
            is LuaCompiledFunction -> {
                // Save activeExecutionFrame before nested call and restore after
                // This ensures the caller's frame is available for native/library calls (pcall, etc.)
                val savedActiveFrame = flowState.activeExecutionFrame
                try {
                    val result = executeProto(func.proto, args, func.upvalues, func)
                    // Restore IMMEDIATELY after return, before any other code runs
                    // (executeProto's finally clears it, so we must restore before our finally)
                    flowState.setActiveExecutionFrame(savedActiveFrame)
                    result
                } catch (e: Throwable) {
                    // Also restore on exception path
                    flowState.setActiveExecutionFrame(savedActiveFrame)
                    throw e
                }
            }
            else -> emptyList()
        }
}
