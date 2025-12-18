@file:Suppress("NOTHING_TO_INLINE", "REDUNDANT_ELSE_IN_WHEN")

package ai.tenum.lua.vm

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaFunction
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
import ai.tenum.lua.vm.errorhandling.NameHintResolver
import ai.tenum.lua.vm.errorhandling.StackTraceBuilder
import ai.tenum.lua.vm.execution.ChunkExecutor
import ai.tenum.lua.vm.execution.CloseErrorHandler
import ai.tenum.lua.vm.execution.CloseResumeOrchestrator
import ai.tenum.lua.vm.execution.DispatchHandlerState
import ai.tenum.lua.vm.execution.DispatchResultHandler
import ai.tenum.lua.vm.execution.DispatchResultProcessor
import ai.tenum.lua.vm.execution.ErrorHandler
import ai.tenum.lua.vm.execution.ExecContext
import ai.tenum.lua.vm.execution.ExecutionContextUpdate
import ai.tenum.lua.vm.execution.ExecutionEnvironment
import ai.tenum.lua.vm.execution.ExecutionFrame
import ai.tenum.lua.vm.execution.ExecutionMode
import ai.tenum.lua.vm.execution.ExecutionPreparation
import ai.tenum.lua.vm.execution.ExecutionSnapshot
import ai.tenum.lua.vm.execution.FunctionNameSource
import ai.tenum.lua.vm.execution.HookTriggerHelper
import ai.tenum.lua.vm.execution.InferredFunctionName
import ai.tenum.lua.vm.execution.LoopControl
import ai.tenum.lua.vm.execution.OpcodeDispatcher
import ai.tenum.lua.vm.execution.ResultStorage
import ai.tenum.lua.vm.execution.ResumptionState
import ai.tenum.lua.vm.execution.SegmentContinuationHandler
import ai.tenum.lua.vm.execution.StackView
import ai.tenum.lua.vm.execution.TrampolineHandler
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
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            // Debug introspection should never crash - return null on any failure
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
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            // Debug introspection should never crash - return null on any failure
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
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            // Hook errors shouldn't crash execution - swallow all exceptions
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
            if (!shouldCloseVariable(capturedValue)) continue

            val closeMethod = getCloseMethod(capturedValue)
            if (closeMethod != null) {
                currentError = executeCloseMethod(closeMethod, capturedValue, currentError)
            }
        }

        return currentError
    }

    private fun shouldCloseVariable(value: LuaValue<*>): Boolean = value !is LuaNil && value != LuaBoolean.FALSE

    private fun getCloseMethod(value: LuaValue<*>): LuaFunction? {
        val metatable = value.metatable as? LuaTable ?: return null
        val closeMethod = metatable.get(LuaString("__close"))
        return closeMethod as? LuaFunction
    }

    private fun executeCloseMethod(
        closeMethod: LuaFunction,
        capturedValue: LuaValue<*>,
        currentError: LuaValue<*>,
    ): LuaValue<*> =
        try {
            // Pass the current error to the __close metamethod
            // Mark this as a __close metamethod call so debug.getinfo can skip it
            setMetamethodCallContext("__close")
            flowState.setNextCallIsCloseMetamethod(true)
            setYieldResumeContext(targetReg = 0, encodedCount = 1, stayOnSamePc = true)
            callFunctionInternal(closeMethod, listOf(capturedValue, currentError))
            clearYieldResumeContext()
            currentError
        } catch (closeEx: LuaException) {
            handleCloseException(closeEx)
        } catch (closeEx: LuaRuntimeError) {
            handleCloseRuntimeError(closeEx)
        } catch (
            @Suppress("TooGenericExceptionCaught") closeEx: Exception,
        ) {
            // Catch all other exceptions from __close metamethod
            handleCloseGenericException(closeEx)
        }

    private fun handleCloseException(closeEx: LuaException): LuaValue<*> {
        // If __close throws, that becomes the new error
        // The errorValue from LuaException already has location info for strings
        return closeEx.errorValue ?: (closeEx.message?.let { LuaString(it) } ?: LuaNil)
        // Capture the call stack for debug.traceback (converted from LuaStackFrame)
        // Note: We'll rely on LuaRuntimeError path for proper CallFrame capture
    }

    private fun handleCloseRuntimeError(closeEx: LuaRuntimeError): LuaValue<*> {
        // For LuaRuntimeError, preserve its call stack which includes the __close frame
        // This allows debug.traceback to show "in metamethod 'close'" when used as xpcall message handler
        lastErrorCallStack = closeEx.callStack

        // For string errors, add location info
        // This matches Lua 5.4 behavior where error("msg") adds location to the message
        val rawError = closeEx.errorValue
        return if (rawError is LuaString) {
            addLocationToError(rawError)
        } else {
            // Non-string errors are passed through unchanged
            rawError
        }
    }

    private fun addLocationToError(rawError: LuaString): LuaString {
        val currentProto = callStackManager.lastFrame()?.proto
        val currentPc = callStackManager.lastFrame()?.pc ?: 0
        val line = currentProto?.let { stackTraceBuilder.getCurrentLine(it, currentPc) }
        val source = currentProto?.source
        return LuaString(
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
    }

    private fun handleCloseGenericException(closeEx: Exception): LuaValue<*> {
        // For other exceptions, convert to string
        lastErrorCallStack = null
        return closeEx.message?.let { LuaString(it) } ?: LuaString("error in __close")
    }

    /**
     * Validate call depth for stack overflow prevention
     */
    private fun validateCallDepth(
        mode: ExecutionMode,
        proto: Proto,
        callDepthBase: Int,
    ) {
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
    }

    /**
     * Prepare execution state for a proto
     */
    private fun prepareExecutionState(
        mode: ExecutionMode,
        proto: Proto,
        args: List<LuaValue<*>>,
        upvalues: List<Upvalue>,
    ): ExecutionPreparation {
        val currentCoroutine = coroutineStateManager.getCurrentCoroutine()
        val isCoroutineContext = currentCoroutine != null
        return ExecutionPreparation.prepare(
            mode = mode,
            proto = proto,
            args = args,
            upvalues = upvalues,
            callStackManager = callStackManager,
            isCoroutine = isCoroutineContext,
        )
    }

    /**
     * Restore close owner frame stack from resumption state
     */
    private fun restoreCloseOwnerFrameStack(
        resumptionState: ResumptionState?,
        execFrame: ExecutionFrame,
    ) {
        if (resumptionState != null && resumptionState.closeOwnerFrameStack.isNotEmpty()) {
            callerContext.restore(resumptionState.closeOwnerFrameStack)

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
    }

    /**
     * Data class to hold all execution handlers
     */
    private data class ExecutionHandlers(
        val hookHelper: HookTriggerHelper,
        val resultProcessor: DispatchResultProcessor,
        val yieldHandler: YieldHandler,
        val errorHandler: ErrorHandler,
        val closeErrorHandler: CloseErrorHandler,
        val closeResumeOrchestrator: CloseResumeOrchestrator,
        val segmentContinuationHandler: SegmentContinuationHandler,
        val trampolineHandler: TrampolineHandler,
        val dispatchResultHandler: DispatchResultHandler,
    )

    /**
     * Create all execution handlers
     */
    private fun createExecutionHandlers(): ExecutionHandlers {
        val hookHelper = HookTriggerHelper(::triggerHook)
        val resultProcessor = DispatchResultProcessor(debugSink)

        val yieldHandler =
            YieldHandler(
                coroutineStateManager,
                resumptionService,
                debugSink,
                callStackManager,
            )

        val errorHandler =
            ErrorHandler(
                coroutineStateManager,
                stackTraceBuilder,
                callStackManager,
                debugSink,
                debugEnabled,
                ::preserveErrorCallStack,
            )

        val closeErrorHandler =
            CloseErrorHandler(
                callStackManager,
                stackTraceBuilder,
                ::markCurrentFrameAsReturning,
                ::closeToBeClosedVars,
            )

        val closeResumeOrchestrator =
            CloseResumeOrchestrator(
                debugSink,
                globals,
                closeContext,
                flowState,
                this,
                ::executeProto,
            )

        val segmentContinuationHandler =
            SegmentContinuationHandler(
                debugSink,
                globals,
                closeContext,
                flowState,
                this,
            )

        val trampolineHandler =
            TrampolineHandler(
                stackTraceBuilder::buildLuaStackTrace,
                maxCallDepth,
            )

        val dispatchResultHandler =
            DispatchResultHandler(
                debugSink,
                resultProcessor,
                segmentContinuationHandler,
                trampolineHandler,
                callStackManager,
                hookHelper,
                flowState,
                closeContext,
                globals,
                this,
                ::triggerHook,
            )

        return ExecutionHandlers(
            hookHelper,
            resultProcessor,
            yieldHandler,
            errorHandler,
            closeErrorHandler,
            closeResumeOrchestrator,
            segmentContinuationHandler,
            trampolineHandler,
            dispatchResultHandler,
        )
    }

    /**
     * Result of resumption state handling
     */
    private data class ResumptionHandlingResult(
        val needsContextUpdate: Boolean,
        val contextUpdate: ExecutionContextUpdate?,
        val needsEnvRecreation: Boolean,
    )

    /**
     * Context for resumption state handling
     */
    private data class ResumptionContext(
        val mode: ExecutionMode,
        val resumptionState: ResumptionState?,
        val args: List<LuaValue<*>>,
        val function: LuaFunction?,
        val execFrame: ExecutionFrame,
        val env: ExecutionEnvironment,
        val handlers: ExecutionHandlers,
    )

    /**
     * Handle resumption state for both close resume and yield resume
     */
    private fun handleResumptionState(context: ResumptionContext): ResumptionHandlingResult {
        // Handle close resume state
        if (context.mode is ExecutionMode.ResumeContinuation &&
            context.resumptionState != null &&
            context.resumptionState.closeResumeState != null
        ) {
            val closeState = context.resumptionState.closeResumeState
            context.env.clearYieldResumeContext()

            val result =
                context.handlers.closeResumeOrchestrator.processCloseResumeState(
                    closeState,
                    context.args,
                    context.function,
                    context.execFrame,
                )

            return ResumptionHandlingResult(
                needsContextUpdate = true,
                contextUpdate = result,
                needsEnvRecreation = result.needsEnvRecreation,
            )
        }

        // Handle yield resume
        val justFinishedCloseHandling =
            context.mode is ExecutionMode.ResumeContinuation &&
                context.mode.state.pendingCloseYield &&
                context.mode.state.toBeClosedVars
                    .isEmpty() &&
                context.mode.state.capturedReturnValues
                    ?.isEmpty() != false

        if (context.mode is ExecutionMode.ResumeContinuation &&
            !justFinishedCloseHandling &&
            context.resumptionState?.closeResumeState == null
        ) {
            val storage = ResultStorage(context.env)
            storage.storeResults(
                targetReg = context.mode.state.yieldTargetRegister,
                encodedCount = context.mode.state.yieldExpectedResults,
                results = context.args,
                opcodeName = "RESUME",
            )
        }

        return ResumptionHandlingResult(
            needsContextUpdate = false,
            contextUpdate = null,
            needsEnvRecreation = false,
        )
    }

    /**
     * Data class for function entry state
     */
    private data class FunctionEntryState(
        val lastLine: Int,
        val previousEnv: Upvalue?,
        val execStack: ArrayDeque<ExecContext>,
    )

    /**
     * Parameters for function entry setup
     */
    private data class FunctionEntrySetup(
        val mode: ExecutionMode,
        val currentProto: Proto,
        val function: LuaFunction?,
        val registers: MutableList<LuaValue<*>>,
        val varargs: List<LuaValue<*>>,
        val args: List<LuaValue<*>>,
        val pc: Int,
        val currentUpvalues: List<Upvalue>,
        val handlers: ExecutionHandlers,
        val isCoroutineContext: Boolean,
    )

    /**
     * Execute the main bytecode loop
     */
    private fun executeMainLoop(
        state: DispatchHandlerState,
        handlers: ExecutionHandlers,
        execStack: ArrayDeque<ExecContext>,
        preparation: ExecutionPreparation,
        applyContextUpdate: (ExecutionContextUpdate) -> Unit,
    ): List<LuaValue<*>> {
        debug { "--- Starting execution ---" }
        debug { "Max stack size: ${state.currentProto.maxStackSize}" }
        debug { "Varargs: ${state.varargs.size} values" }

        whileLoop@ while (state.pc < state.instructions.size) {
            // Keep activeExecutionFrame in sync with the frame currently executing bytecode
            flowState.setActiveExecutionFrame(state.execFrame)

            // Update current frame PC (for both debug stack and execution frame)
            callStackManager.updateLastFramePc(state.pc)
            state.execFrame.pc = state.pc

            // Trigger LINE hooks using helper
            state.lastLine = handlers.hookHelper.triggerLineHooksAt(state.currentProto, state.pc, state.lastLine)

            val instr = state.instructions[state.pc]

            if (debugEnabled) {
                debug { "PC=${state.pc}: ${instr.opcode} a=${instr.a} b=${instr.b} c=${instr.c}" }
            }

            // Get current frame for dispatch
            val currentFrame = callStackManager.lastFrame()!!

            // Dispatch opcode to handler
            val dispatchResult =
                opcodeDispatcher.dispatch(
                    instr = instr,
                    env = state.env,
                    registers = state.registers,
                    execFrame = state.execFrame,
                    openUpvalues = state.openUpvalues,
                    varargs = state.varargs,
                    pc = state.pc,
                    frame = currentFrame,
                    executeProto = { proto, args, upvalues, func -> executeProto(proto, args, upvalues, func) },
                    callFunction = { func, args -> callFunction(func, args, state.execFrame) },
                    traceRegisterWrite = { regIndex, value, ctx -> traceRegisterWrite(regIndex, value, ctx) },
                    triggerHook = { event, line -> triggerHook(event, line) },
                    setCallContext = { inferred ->
                        pendingInferredName = inferred
                    },
                )

            // Create dispatch handler state
            val handlerState =
                DispatchHandlerState(
                    currentProto = state.currentProto,
                    execFrame = state.execFrame,
                    registers = state.registers,
                    constants = state.constants,
                    instructions = state.instructions,
                    pc = state.pc,
                    openUpvalues = state.openUpvalues,
                    toBeClosedVars = state.toBeClosedVars,
                    varargs = state.varargs,
                    currentUpvalues = state.currentUpvalues,
                    env = state.env,
                    lastLine = state.lastLine,
                    pendingInferredName = pendingInferredName,
                )

            // Process dispatch result using handler
            var localCallDepth = callDepth
            when (
                val loopControl =
                    handlers.dispatchResultHandler.handle(
                        dispatchResult = dispatchResult,
                        state = handlerState,
                        execStack = execStack,
                        callDepth = localCallDepth,
                        isCoroutineContext = preparation.isCoroutineContext,
                        onCallDepthIncrement = {
                            localCallDepth++
                            callDepth = localCallDepth
                        },
                        onCallDepthDecrement = {
                            localCallDepth--
                            callDepth = localCallDepth
                        },
                    )
            ) {
                is LoopControl.Continue -> {
                    // Sync state back from handler
                    state.currentProto = handlerState.currentProto
                    state.execFrame = handlerState.execFrame
                    state.registers = handlerState.registers
                    state.constants = handlerState.constants
                    state.instructions = handlerState.instructions
                    state.pc = handlerState.pc
                    state.openUpvalues = handlerState.openUpvalues
                    state.toBeClosedVars = handlerState.toBeClosedVars
                    state.varargs = handlerState.varargs
                    state.currentUpvalues = handlerState.currentUpvalues
                    state.env = handlerState.env
                    state.lastLine = handlerState.lastLine
                    pendingInferredName = handlerState.pendingInferredName
                }
                is LoopControl.SkipNext -> {
                    state.pc++ // Skip next instruction (LOADBOOL with skip)
                }
                is LoopControl.Jump -> {
                    state.pc = loopControl.newPc
                }
                is LoopControl.Return -> {
                    // Continue to next iteration (used for unwinding)
                }
                is LoopControl.ExitProto -> {
                    return loopControl.returnValues
                }
                is LoopControl.ContinueWithContext -> {
                    applyContextUpdate(loopControl.update)
                    continue@whileLoop
                }
            }
            state.pc++

            // Trigger COUNT hook
            val currentLineForHook = callStackManager.lastFrame()?.getCurrentLine() ?: -1
            hookManager.checkCountHook(currentLineForHook)
        }

        // If we reach here without a return, check for captured returns first
        if (state.execFrame.capturedReturns != null) {
            return state.execFrame.capturedReturns!!
        }

        return emptyList()
    }

    /**
     * Handle exceptions during execution
     */
    private fun handleExecutionException(
        e: Throwable,
        handlers: ExecutionHandlers,
        state: DispatchHandlerState,
        alreadyClosedRegs: MutableSet<Int>,
        execStack: ArrayDeque<ExecContext>,
    ): Nothing {
        when (e) {
            is LuaYieldException -> {
                // Coroutine yielded - save execution state for resumption
                if (!e.stateSaved) {
                    val currentCoroutine = coroutineStateManager.getCurrentCoroutine()
                    val callStackBase = (currentCoroutine as? LuaCoroutine.LuaFunctionCoroutine)?.thread?.callStackBase ?: 0

                    handlers.yieldHandler.handleYield(
                        exception = e,
                        currentProto = state.currentProto,
                        pc = state.pc,
                        registers = state.registers,
                        instructions = state.instructions,
                        execFrame = state.execFrame,
                        varargs = state.varargs,
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
                throw e
            }
            is LuaRuntimeError -> {
                val luaEx = handlers.errorHandler.handleRuntimeError(e, state.currentProto, state.pc)
                handlers.closeErrorHandler.handleErrorAndCloseToBeClosedVars(
                    luaEx,
                    state.toBeClosedVars,
                    alreadyClosedRegs,
                    state.currentProto,
                    state.pc,
                )
            }
            is LuaException -> {
                handlers.closeErrorHandler.handleErrorAndCloseToBeClosedVars(
                    e,
                    state.toBeClosedVars,
                    alreadyClosedRegs,
                    state.currentProto,
                    state.pc,
                )
            }
            is Error -> {
                throw handlers.errorHandler.handlePlatformError(e, state.currentProto, state.pc)
            }
            else -> {
                println("[EXCEPTION HANDLER] Converting ${e::class.simpleName} to LuaException")
                val luaEx =
                    handlers.errorHandler.handleGenericException(
                        e as Exception,
                        state.currentProto,
                        state.pc,
                        state.registers,
                        state.constants,
                        state.instructions,
                    )
                handlers.closeErrorHandler.handleErrorAndCloseToBeClosedVars(
                    luaEx,
                    state.toBeClosedVars,
                    alreadyClosedRegs,
                    state.currentProto,
                    state.pc,
                )
            }
        }
    }

    /**
     * Setup function entry including call frame, hooks, and environment
     */
    private fun setupFunctionEntry(setup: FunctionEntrySetup): FunctionEntryState {
        // Create call frame for debugging/error handling
        if (setup.mode is ExecutionMode.FreshCall) {
            callStackManager.beginFunctionCall(
                proto = setup.currentProto,
                function = setup.function,
                registers = setup.registers,
                varargs = setup.varargs,
                args = setup.args,
                inferredName = pendingInferredName,
                pc = setup.pc,
                isCloseMetamethod = flowState.nextCallIsCloseMetamethod,
            )
        }

        // Clear call context after using it
        pendingInferredName = null
        flowState.setNextCallIsCloseMetamethod(false)

        // Trigger CALL and LINE hooks for function entry
        val lastLine = setup.handlers.hookHelper.triggerEntryHooks(setup.currentProto, setup.isCoroutineContext)

        // Track current upvalues for this execution context and _ENV
        val previousEnv = flowState.currentEnvUpvalue
        val envIndex = setup.currentProto.upvalueInfo.indexOfFirst { it.name == "_ENV" }
        flowState.setCurrentEnvUpvalue(if (envIndex >= 0) setup.currentUpvalues.getOrNull(envIndex) else null)

        // Execution stack for trampolined calls
        val execStack =
            ArrayDeque(
                (setup.mode as? ExecutionMode.ResumeContinuation)?.state?.execStack ?: emptyList(),
            )

        return FunctionEntryState(lastLine, previousEnv, execStack)
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

        // Validate call depth for fresh calls
        validateCallDepth(mode, proto, callDepthBase)

        // Prepare execution state using helper
        val preparation = prepareExecutionState(mode, proto, args, upvalues)

        val resumptionState = (mode as? ExecutionMode.ResumeContinuation)?.state
        var currentProto = preparation.currentProto
        var execFrame = preparation.execFrame
        val initialCallStackSize = preparation.initialCallStackSize

        // Restore close owner frame stack on resume
        restoreCloseOwnerFrameStack(resumptionState, execFrame)

        // Track active execution frame for native function calls
        flowState.setActiveExecutionFrame(execFrame)

        // Create execution handlers
        val handlers = createExecutionHandlers()

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

        // Helper to apply execution context updates (eliminates duplication)
        fun applyContextUpdate(update: ExecutionContextUpdate) {
            execFrame = update.execFrame
            currentProto = update.currentProto
            registers = update.registers
            constants = update.constants
            instructions = update.instructions
            pc = update.pc
            openUpvalues = update.openUpvalues
            toBeClosedVars = update.toBeClosedVars
            varargs = update.varargs
            currentUpvalues = update.currentUpvalues
            env = update.env
        }

        // Handle resumption state (close resume and yield resume)
        val resumeResult =
            handleResumptionState(
                ResumptionContext(
                    mode = mode,
                    resumptionState = resumptionState,
                    args = args,
                    function = function,
                    execFrame = execFrame,
                    env = env,
                    handlers = handlers,
                ),
            )
        if (resumeResult.needsContextUpdate) {
            applyContextUpdate(resumeResult.contextUpdate!!)
        }
        if (resumeResult.needsEnvRecreation) {
            env = ExecutionEnvironment(execFrame, globals, this)
        }

        // Setup function entry (call frame, hooks, environment)
        val entryState =
            setupFunctionEntry(
                FunctionEntrySetup(
                    mode = mode,
                    currentProto = currentProto,
                    function = function,
                    registers = registers,
                    varargs = varargs,
                    args = args,
                    pc = pc,
                    currentUpvalues = currentUpvalues,
                    handlers = handlers,
                    isCoroutineContext = preparation.isCoroutineContext,
                ),
            )
        val previousEnv = entryState.previousEnv
        val execStack = entryState.execStack

        // Create mutable execution state
        val state =
            DispatchHandlerState(
                currentProto = currentProto,
                execFrame = execFrame,
                registers = registers,
                constants = constants,
                instructions = instructions,
                pc = pc,
                openUpvalues = openUpvalues,
                toBeClosedVars = toBeClosedVars,
                varargs = varargs,
                currentUpvalues = currentUpvalues,
                env = env,
                lastLine = entryState.lastLine,
                pendingInferredName = pendingInferredName,
            )

        try {
            return executeMainLoop(state, handlers, execStack, preparation, ::applyContextUpdate)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Throwable,
        ) {
            // Must catch Throwable to handle both LuaExceptions and platform errors (Error subclasses)
            handleExecutionException(e, handlers, state, alreadyClosedRegs, execStack)
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
        pushCallerFrameIfNeeded(callerFrame)

        var didYield = false

        try {
            return executeFunctionCall(func, args, callerFrame)
        } catch (e: LuaYieldException) {
            // Don't pop the stack on yield - the owner frame must persist for resume
            // The stack will be saved with coroutine state and restored on resume
            didYield = true
            throw e
        } finally {
            popCallerFrameIfNeeded(didYield, callerFrame)
        }
    }

    private fun pushCallerFrameIfNeeded(callerFrame: ExecutionFrame?) {
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
    }

    private fun executeFunctionCall(
        func: LuaValue<*>,
        args: List<LuaValue<*>>,
        callerFrame: ExecutionFrame?,
    ): List<LuaValue<*>> =
        when (func) {
            is LuaFunction -> {
                // Call through private callFunction to ensure hooks are triggered
                callFunctionInternal(func, args)
            }
            else -> {
                executeMetamethodCall(func, args, callerFrame)
            }
        }

    private fun executeMetamethodCall(
        func: LuaValue<*>,
        args: List<LuaValue<*>>,
        callerFrame: ExecutionFrame?,
    ): List<LuaValue<*>> {
        // Check for __call metamethod in the value's metatable
        val callMeta = metamethodResolver.getMetamethod(func, "__call")
        if (callMeta != null) {
            setMetamethodCallContext("__call")
            // Recursively call through callFunction to support chained __call metamethods
            return callFunction(callMeta, listOf(func) + args, callerFrame)
        } else {
            throw RuntimeException("attempt to call a ${func.type().name.lowercase()} value")
        }
    }

    private fun popCallerFrameIfNeeded(
        didYield: Boolean,
        callerFrame: ExecutionFrame?,
    ) {
        // Pop caller frame only on normal return or non-yield exception
        // Match by proto reference since we pushed a snapshot, not the exact frame
        // IMPORTANT: If the popped frame has TBC vars, transfer them to activeExecutionFrame
        // This ensures outer __close handlers (like x in pcall test) are processed by RETURN
        val shouldPopCallerFrame = !didYield && callerFrame != null && callerContext.size > 0
        val matchesCallerProto = callerContext.peek()?.proto == callerFrame?.proto
        if (shouldPopCallerFrame && matchesCallerProto) {
            transferTbcVarsAndPop()
        }
    }

    private fun transferTbcVarsAndPop() {
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
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    // Exception from __close or function body - catch all to preserve return values
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
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Throwable,
                ) {
                    // Must catch Throwable to ensure frame cleanup on all error paths
                    // Also restore on exception path
                    flowState.setActiveExecutionFrame(savedActiveFrame)
                    throw e
                }
            }
            else -> emptyList()
        }
}
