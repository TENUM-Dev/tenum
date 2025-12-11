package ai.tenum.lua.stdlib.debug

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaThread
import ai.tenum.lua.runtime.LuaUserdata
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.stdlib.internal.UpvalueHelper
import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.debug.DebugContext
import ai.tenum.lua.vm.debug.DebugHook
import ai.tenum.lua.vm.debug.HookEvent
import ai.tenum.lua.vm.errorhandling.LuaRuntimeError
import ai.tenum.lua.vm.errorhandling.TracebackFormatter
import ai.tenum.lua.vm.execution.FunctionNameSource
import ai.tenum.lua.vm.execution.InferredFunctionName
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext

/**
 * Lua 5.4 debug library implementation.
 *
 * ENHANCED with full VM support (Phase 6.4 + 7.1)
 *
 * Functions implemented:
 * - debug.getinfo(function [, what]) - Returns function information table
 * - debug.getlocal(level, index) - Gets local variable name/value
 * - debug.setlocal(level, index, value) - Sets local variable value
 * - debug.getupvalue(func, index) - Gets upvalue name/value
 * - debug.setupvalue(func, index, value) - Sets upvalue value
 * - debug.traceback([message [, level]]) - Returns stack traceback string
 * - debug.sethook([hook, mask [, count]]) - Sets execution hook
 * - debug.gethook() - Returns current hook settings
 */
class DebugLib : LuaLibrary {
    override val name: String = "debug"

    // Reference to debug context for stack introspection and hook management
    private var debugContext: DebugContext? = null

    // Reference to library context for accessing coroutine state
    private var libContext: LuaLibraryContext? = null

    // Cache for light userdata wrappers to ensure same upvalue returns same ID
    private val upvalueIdCache = mutableMapOf<Upvalue, LuaUserdata<LightUserdataMarker>>()

    /**
     * Helper: Get stack frame at given level (1-based, oldest-first).
     * Throws error if level is out of range (for getlocal/setlocal).
     */
    private fun getStackFrame(
        callStack: List<CallFrame>,
        level: Int,
        functionName: String = "debug function",
    ): CallFrame {
        if (level < 1 || level > callStack.size) {
            throw LuaRuntimeError("bad argument #1 to '$functionName' (level out of range)")
        }
        // callStack is most-recent-first; convert to oldest-first for indexing
        // Level 1 = most recent = last element after reversal = index (size-1)
        return callStack.asReversed().toList()[callStack.size - level]
    }

    /**
     * Helper: Extract level and index from args for getlocal/setlocal.
     * Returns null if parsing fails.
     */
    private fun parseLevelAndIndex(args: List<LuaValue<*>>): Pair<Int, Int>? {
        val level = (args.getOrNull(0) as? LuaNumber)?.toDouble()?.toInt() ?: return null
        val index = (args.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt() ?: return null
        return Pair(level, index)
    }

    /**
     * Helper: Extract function and index from args for upvalue operations.
     * Returns null if parsing fails.
     */
    private fun parseFuncAndIndex(args: List<LuaValue<*>>): Pair<LuaValue<*>?, Int>? {
        val func = args.getOrNull(0)
        val index = (args.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt() ?: return null
        return Pair(func, index)
    }

    /**
     * Helper: Get stack frame for getlocal/setlocal operations.
     * Returns null if VM unavailable or parsing fails.
     * Throws error if level is out of range.
     */
    private fun getLocalStackFrame(
        args: List<LuaValue<*>>,
        functionName: String,
    ): Pair<CallFrame, Int>? {
        val currentContext = debugContext ?: return null
        val (level, index) = parseLevelAndIndex(args) ?: return null
        val stackView = currentContext.getStackView()
        val frame = stackView.atLevel(level) ?: throw LuaRuntimeError("bad argument #1 to '$functionName' (level out of range)")
        return Pair(frame, index)
    }

    /**
     * Helper: Get stack frame from a coroutine's saved call stack.
     * Used for accessing local variables in suspended coroutines.
     * Returns null if coroutine has no saved stack.
     * Throws error if level is out of range.
     */
    private fun getLocalStackFrameFromCoroutine(
        coroutine: LuaCoroutine,
        args: List<LuaValue<*>>,
        functionName: String,
    ): Pair<CallFrame, Int>? {
        val (level, index) = parseLevelAndIndex(args) ?: return null

        // Get saved call stack from coroutine thread
        val savedStack =
            when (coroutine) {
                is LuaCoroutine.LuaFunctionCoroutine -> coroutine.thread.savedCallStack
                is LuaCoroutine.SuspendFunctionCoroutine -> coroutine.thread.savedCallStack
            }

        if (savedStack.isEmpty()) {
            return null // Coroutine not yet started or has no saved state
        }

        // Get frame at the specified level (1-based, oldest-first indexing)
        val frame = getStackFrame(savedStack, level, functionName)
        return Pair(frame, index)
    }

    /**
     * Helper: Get upvalue access for getupvalue/setupvalue/upvalueid operations.
     * Returns null if parsing fails or upvalue access validation fails.
     */
    private fun getUpvalueAccessFromArgs(args: List<LuaValue<*>>): UpvalueHelper.UpvalueAccess? {
        val (func, index) = parseFuncAndIndex(args) ?: return null
        return UpvalueHelper.getUpvalueAccess(func, index)
    }

    override fun register(context: LuaLibraryContext) {
        val lib = LuaTable()

        // Capture DebugContext reference from context VM
        debugContext = context.vm as? DebugContext

        // Store library context for accessing coroutine state
        libContext = context

        // debug.getinfo(function [, what])
        lib[LuaString("getinfo")] =
            LuaNativeFunction { args ->
                getInfoImpl(args)
            }

        // debug.getlocal(level, index)
        lib[LuaString("getlocal")] =
            LuaNativeFunction { args ->
                getLocalImpl(args)
            }

        // debug.setlocal(level, index, value)
        lib[LuaString("setlocal")] =
            LuaNativeFunction { args ->
                setLocalImpl(args)
            }

        // debug.getupvalue(func, index)
        lib[LuaString("getupvalue")] =
            LuaNativeFunction { args ->
                getUpvalueImpl(args)
            }

        // debug.setupvalue(func, index, value)
        lib[LuaString("setupvalue")] =
            LuaNativeFunction { args ->
                setUpvalueImpl(args)
            }

        // debug.traceback([message [, level]])
        lib[LuaString("traceback")] =
            LuaNativeFunction("debug.traceback") { args ->
                tracebackImpl(args)
            }

        // debug.sethook([hook, mask [, count]])
        lib[LuaString("sethook")] =
            LuaNativeFunction { args ->
                setHookImpl(args)
            }

        // debug.gethook()
        lib[LuaString("gethook")] =
            LuaNativeFunction { args ->
                getHookImpl(args)
            }

        // debug.upvalueid(func, index) - Returns unique identifier for upvalue
        lib[LuaString("upvalueid")] =
            LuaNativeFunction { args ->
                upvalueIdImpl(args)
            }

        // debug.upvaluejoin(f1, n1, f2, n2) - Makes f1's n1-th upvalue refer to f2's n2-th upvalue
        lib[LuaString("upvaluejoin")] =
            LuaNativeFunction { args ->
                upvaluejoinImpl(args)
            }

        // debug.setuservalue(udata, value [, n]) - Sets user value for userdata
        lib[LuaString("setuservalue")] =
            LuaNativeFunction { args ->
                setUservalueImpl(args)
            }

        // debug.getuservalue(udata [, n]) - Gets user value for userdata
        lib[LuaString("getuservalue")] =
            LuaNativeFunction { args ->
                getUservalueImpl(args)
            }

        // debug.getregistry() - Returns the registry table
        lib[LuaString("getregistry")] =
            LuaNativeFunction { args ->
                getRegistryImpl(args)
            }

        context.registerGlobal("debug", lib)
    }

    /**
     * Get function name from Proto if it's a meaningful name.
     * Returns Pair of (name, namewhat) where namewhat is empty string for proto names.
     */
    private fun getProtoName(func: LuaValue<*>): InferredFunctionName {
        if (func is LuaCompiledFunction) {
            val protoName = func.proto.name
            if (protoName.isNotEmpty() && protoName != "main" && protoName != "?" && protoName != "<function>") {
                return InferredFunctionName(protoName, FunctionNameSource.Unknown)
            }
        }
        return InferredFunctionName.UNKNOWN
    }

    /**
     * debug.getinfo(function [, what])
     *
     * Returns table with function information:
     * - what: "Lua" for Lua functions, "C" for native functions
     * - source: Source file name (or "[C]" for native)
     * - short_src: Abbreviated source
     * - linedefined: Line where function was defined
     * - lastlinedefined: Last line of function
     * - currentline: Current line (during execution)
     * - name: Function name (if available)
     * - namewhat: "global", "local", "method", "field", ""
     * - nups: Number of upvalues
     * - nparams: Number of parameters
     * - isvararg: true if function is vararg
     */
    private fun getInfoImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val currentContext = debugContext ?: return listOf(LuaNil)

        val firstArg = args.getOrNull(0) ?: return listOf(LuaNil)

        // Check if first argument is a thread (coroutine) - debug.getinfo(thread, level, what)
        val (targetThread, levelOrFunc, what) =
            when {
                firstArg is LuaCoroutine || firstArg is LuaThread -> {
                    // debug.getinfo(thread, level, what)
                    val secondArg = args.getOrNull(1) ?: return listOf(LuaNil)
                    val whatStr = (args.getOrNull(2) as? LuaString)?.value ?: "flnStu"
                    Triple(firstArg, secondArg, whatStr)
                }
                else -> {
                    // debug.getinfo(level/function, what) - current thread
                    val whatStr = (args.getOrNull(1) as? LuaString)?.value ?: "flnStu"
                    Triple(null, firstArg, whatStr)
                }
            }

        // Validate 'what' options - valid characters are: n, S, l, t, u, f, L, r
        // Lua 5.4 spec: any other character is invalid
        val validOptions = setOf('n', 'S', 'l', 't', 'u', 'f', 'L', 'r')
        for (char in what) {
            if (char !in validOptions) {
                throw LuaRuntimeError("bad argument #2 to 'debug.getinfo' (invalid option)")
            }
        }

        // Get the function and frame information based on thread and level/function
        val (func, frame) =
            when {
                targetThread != null && levelOrFunc is LuaNumber -> {
                    // debug.getinfo(thread, level, what) - get info from suspended coroutine
                    val level = levelOrFunc.toDouble().toInt()
                    if (targetThread is LuaCoroutine.LuaFunctionCoroutine) {
                        val savedStack = targetThread.thread.savedCallStack
                        if (level < 0 || level >= savedStack.size) return listOf(LuaNil)

                        // Access saved stack in reverse order (most recent first)
                        val reversedIndex = savedStack.size - 1 - level
                        val savedFrame = savedStack.getOrNull(reversedIndex)
                        Pair(savedFrame?.function, savedFrame)
                    } else {
                        // Main thread or other thread type
                        return listOf(LuaNil)
                    }
                }
                levelOrFunc is LuaNumber -> {
                    // Stack level - get function from call stack using StackView
                    val level = levelOrFunc.toDouble().toInt()
                    val stackView = currentContext.getStackView()

                    // Level 0 = current function (debug.getinfo), level 1 = caller, etc.
                    if (level < 0 || level >= stackView.size) return listOf(LuaNil)

                    val frameAtLevel = stackView.atLevel(level)
                    Pair(frameAtLevel?.function, frameAtLevel)
                }
                levelOrFunc is LuaFunction -> Pair(levelOrFunc, null)
                else -> return listOf(LuaNil)
            }

        // Note: func can be null for certain stack frames (e.g., native functions or special frames)
        // but we should still return an info table with available fields (especially 'l' for currentline)
        val info = LuaTable()

        // Determine if Lua or native function
        val isNative = func is LuaNativeFunction || func == null

        if (what.contains('S')) {
            // Determine what field: "C" for native, "main" for main chunk (lineDefined==0), "Lua" for functions
            val whatValue =
                when {
                    isNative -> "C"
                    (func as? LuaCompiledFunction)?.proto?.lineDefined == 0 -> "main"
                    else -> "Lua"
                }
            info[LuaString("what")] = LuaString(whatValue)

            // Get source from Proto if available
            val source =
                if (isNative) {
                    "[C]"
                } else {
                    (func as? LuaCompiledFunction)?.proto?.source ?: "=(load)"
                }

            info[LuaString("source")] = LuaString(source)

            // Format short_src based on source type
            val shortSrc =
                when {
                    isNative -> "[C]"
                    source.startsWith("@") -> formatFileShortSrc(source.substring(1))
                    source.startsWith("=") -> source.substring(1) // Remove = prefix for special sources
                    else -> formatStringShortSrc(source) // String sources get bracketed format with truncation
                }
            info[LuaString("short_src")] = LuaString(shortSrc)

            // Get linedefined and lastlinedefined from Proto
            if (isNative) {
                info[LuaString("linedefined")] = LuaNumber.of(-1)
                info[LuaString("lastlinedefined")] = LuaNumber.of(-1)
            } else {
                val compiledFunc = func as? LuaCompiledFunction
                info[LuaString("linedefined")] = LuaNumber.of(compiledFunc?.proto?.lineDefined ?: 0)
                info[LuaString("lastlinedefined")] = LuaNumber.of(compiledFunc?.proto?.lastLineDefined ?: 0)
            }
        }

        if (what.contains('l')) {
            // Current line - get from stack frame if available
            val currentLine = frame?.getCurrentLine() ?: -1
            info[LuaString("currentline")] = LuaNumber.of(currentLine)
        }

        if (what.contains('n')) {
            // Name information - prefer inferred name from call context, fallback to Proto name
            val inferred =
                if (frame != null) {
                    // Stack level - check if frame has inferred name from calling context
                    val frameInferred = frame.inferredFunctionName
                    // Use frame inference if it has a real name OR if it's a Hook (which has namewhat but no name)
                    if (frameInferred != null && (frameInferred.name != null || frameInferred.source == FunctionNameSource.Hook)) {
                        frameInferred
                    } else {
                        getProtoName(func ?: LuaNil)
                    }
                } else {
                    // Direct function query - only proto name available
                    getProtoName(func ?: LuaNil)
                }

            // Lua 5.4 semantics: tail calls have name=nil
            val isTailCall = frame?.isTailCall ?: false

            val funcName =
                if (isTailCall) {
                    LuaNil
                } else if (inferred.name != null) {
                    LuaString(inferred.name)
                } else {
                    LuaNil
                }
            info[LuaString("name")] = funcName
            info[LuaString("namewhat")] = LuaString(inferred.source.luaString)
        }

        if (what.contains('t')) {
            // Tail call status - check if frame was entered via tail call
            val isTailCall =
                if (firstArg is LuaNumber) {
                    val level = firstArg.toDouble().toInt()
                    val frame = currentContext.getStackView().atLevel(level)
                    frame?.isTailCall ?: false
                } else {
                    false
                }
            info[LuaString("istailcall")] = LuaBoolean.of(isTailCall)
        }

        if (what.contains('u')) {
            // Upvalue and parameter count
            val nups =
                when (func) {
                    is LuaCompiledFunction -> func.proto.upvalueInfo.size
                    is LuaNativeFunction -> func.upvalues.size
                    else -> 0
                }
            val nparams =
                if (func is LuaCompiledFunction) {
                    func.proto.parameters.size
                } else {
                    0
                }
            val isvararg =
                if (func is LuaCompiledFunction) {
                    func.proto.hasVararg
                } else {
                    true // C functions are treated as vararg
                }
            info[LuaString("nups")] = LuaNumber.of(nups)
            info[LuaString("nparams")] = LuaNumber.of(nparams)
            info[LuaString("isvararg")] = LuaBoolean.of(isvararg)
        }

        if (what.contains('f')) {
            // Function itself (can be nil for certain stack frames)
            info[LuaString("func")] = func ?: LuaNil
        }

        if (what.contains('L')) {
            // Active lines - build a table mapping line numbers to true
            val activelinesTable = LuaTable()
            if (func is LuaCompiledFunction) {
                // Extract all unique line numbers from lineInfo (PC -> line mappings)
                // activelines contains the actual executable lines recorded in lineEvents
                // Note: For stripped functions, lineInfo is empty, so activelines will be empty
                val uniqueLines =
                    func.proto.lineInfo
                        .map { it.line }
                        .toSet()
                for (line in uniqueLines) {
                    activelinesTable[LuaNumber.of(line)] = LuaBoolean.TRUE
                }
            }
            // For native functions or null, activelines should be nil (not an empty table)
            info[LuaString("activelines")] = if (isNative) LuaNil else activelinesTable
        }

        if (what.contains('r')) {
            // Transfer information (Lua 5.4) - for call/return hooks
            // ftransfer: first index of transferred values (1-based)
            // ntransfer: number of transferred values
            val (ftransfer, ntransfer) =
                if (firstArg is LuaNumber) {
                    val level = firstArg.toDouble().toInt()
                    val frame = currentContext.getStackView().atLevel(level)
                    Pair(frame?.ftransfer ?: 0, frame?.ntransfer ?: 0)
                } else {
                    Pair(0, 0)
                }
            info[LuaString("ftransfer")] = LuaNumber.of(ftransfer)
            info[LuaString("ntransfer")] = LuaNumber.of(ntransfer)
        }

        return listOf(info)
    }

    /**
     * debug.getlocal(level, index) or debug.getlocal(function, index)
     * or debug.getlocal(thread, level, index) or debug.getlocal(thread, function, index)
     *
     * When called with a stack level:
     * - Returns name and value of local variable at given stack level and index.
     * - Returns nil if index is out of range.
     * - Throws error if level is out of range.
     *
     * When called with a function:
     * - Returns parameter name at given index (1-based).
     * - Returns nil if index is out of range.
     * - Only returns parameter names, not local variables.
     *
     * When called with a thread:
     * - Accesses the coroutine's saved call stack instead of current stack.
     * - Works for suspended coroutines.
     */
    private fun getLocalImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        // Check if first argument is a coroutine/thread
        val (coroutine, argOffset, firstArg) =
            when (val firstArg = args.getOrNull(0)) {
                is LuaCoroutine -> Triple(firstArg, 1, args.getOrNull(1))
                else -> Triple(null, 0, firstArg)
            }

        val index = (args.getOrNull(argOffset + 1) as? LuaNumber)?.toDouble()?.toInt() ?: return listOf(LuaNil)

        // Check if first (non-thread) argument is a function (direct function introspection)
        if (firstArg is LuaCompiledFunction) {
            // Get parameter name from function prototype
            if (index < 1 || index > firstArg.proto.parameters.size) {
                return listOf(LuaNil)
            }
            val paramName = firstArg.proto.parameters[index - 1]
            return listOf(LuaString(paramName))
        }

        // Otherwise, treat as stack level
        val adjustedArgs =
            if (argOffset > 0) {
                // Reconstruct args without the thread
                listOf(firstArg ?: LuaNil, args[argOffset + 1]) + args.drop(argOffset + 2)
            } else {
                args
            }

        val (frame, idx) =
            if (coroutine != null) {
                // Access coroutine's saved call stack
                getLocalStackFrameFromCoroutine(coroutine, adjustedArgs, "debug.getlocal") ?: return listOf(LuaNil)
            } else {
                // Access current thread's call stack
                getLocalStackFrame(adjustedArgs, "debug.getlocal") ?: return listOf(LuaNil)
            }

        // Get local variable
        val local = frame.getLocal(idx) ?: return listOf(LuaNil)

        return listOf(LuaString(local.first), local.second)
    }

    /**
     * debug.setlocal(level, index, value) or debug.setlocal(thread, level, index, value)
     *
     * Sets local variable at given stack level and index to new value.
     * Returns variable name if successful, nil otherwise.
     * Throws error if level is out of range.
     * When called with a thread, operates on the coroutine's saved call stack.
     */
    private fun setLocalImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        // Check if first argument is a coroutine/thread
        val (coroutine, adjustedArgs) =
            when (val firstArg = args.getOrNull(0)) {
                is LuaCoroutine -> {
                    // Reconstruct args: (level, index, value)
                    val level = args.getOrNull(1)
                    val index = args.getOrNull(2)
                    val value = args.getOrNull(3) ?: LuaNil
                    Pair(firstArg, listOfNotNull(level, index, value))
                }
                else -> Pair(null, args)
            }

        val (frame, index) =
            if (coroutine != null) {
                getLocalStackFrameFromCoroutine(coroutine, adjustedArgs, "debug.setlocal") ?: return listOf(LuaNil)
            } else {
                getLocalStackFrame(adjustedArgs, "debug.setlocal") ?: return listOf(LuaNil)
            }

        val value = adjustedArgs.getOrNull(2) ?: LuaNil

        // For coroutines, we need to update BOTH the saved frame AND the thread registers
        // So we replicate the setLocal logic here to capture the register index
        if (coroutine != null) {
            val result = setLocalInCoroutine(coroutine, frame, index, value)
            return if (result != null) listOf(LuaString(result)) else listOf(LuaNil)
        }

        // For non-coroutine (current thread), just use frame.setLocal
        val name = frame.setLocal(index, value) ?: return listOf(LuaNil)
        return listOf(LuaString(name))
    }

    /**
     * Set local variable in a suspended coroutine.
     * Updates both the saved CallFrame and the thread's execution registers.
     * Returns the variable name on success, null on failure.
     */
    private fun setLocalInCoroutine(
        coroutine: LuaCoroutine,
        frame: CallFrame,
        index: Int,
        value: LuaValue<*>,
    ): String? {
        val thread =
            when (coroutine) {
                is LuaCoroutine.LuaFunctionCoroutine -> coroutine.thread
                is LuaCoroutine.SuspendFunctionCoroutine -> coroutine.thread
            }

        // First, update the saved frame (for debug.getlocal consistency)
        val name = frame.setLocal(index, value) ?: return null

        // Then, update the thread's execution registers (for resume consistency)
        val threadRegisters = thread.registers ?: return name

        // Now replicate the register index calculation from CallFrame.setLocal
        // to find which register was actually modified
        val registerIndex = calculateRegisterIndex(frame, index)
        if (registerIndex != null && registerIndex < threadRegisters.size) {
            threadRegisters[registerIndex] = value
        }

        return name
    }

    /**
     * Calculate the register index for a local variable index.
     * Mirrors the logic in CallFrame.setLocal.
     */
    private fun calculateRegisterIndex(
        frame: CallFrame,
        index: Int,
    ): Int? {
        // Handle negative indices for varargs
        if (index < 0) {
            // Varargs don't correspond to registers in the same way
            // They're handled specially, so we don't update thread.registers
            return null
        }

        // For proto-based functions, check registered locals first
        val proto = frame.proto
        if (proto != null) {
            var activeIndex = 0
            for (localVar in proto.localVars) {
                if (localVar.startPc <= frame.pc && frame.pc < localVar.endPc) {
                    activeIndex++
                    if (activeIndex == index) {
                        return frame.base + localVar.register
                    }
                }
            }
        }

        // Fallback: assume it's a stack temporary
        return frame.base + (index - 1)
    }

    /**
     * debug.getupvalue(func, index)
     *
     * Returns name and value of upvalue at given index in function.
     * Returns nil if index is out of range.
     * Upvalue indices are 1-based (Lua 5.4 behavior).
     * Index 1 returns the first upvalue (including _ENV if present).
     */
    private fun getUpvalueImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val access = getUpvalueAccessFromArgs(args) ?: return listOf(LuaNil)
        val func = parseFuncAndIndex(args)?.first

        // For Lua functions with stripped debug info, empty names become "(no name)"
        // For native functions, empty names stay as "" (Lua 5.4 behavior)
        val displayName =
            if (access.name.isEmpty() && func is LuaCompiledFunction) {
                "(no name)"
            } else {
                access.name
            }
        return listOf<LuaValue<*>>(LuaString(displayName), access.upvalue.get())
    }

    /**
     * debug.setupvalue(func, index, value)
     *
     * Sets upvalue at given index in function to new value.
     * Returns upvalue name if successful, nil otherwise.
     * Upvalue indices are 1-based (Lua 5.4 behavior).
     * Index 1 sets the first upvalue (including _ENV if present).
     */
    private fun setUpvalueImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val access = getUpvalueAccessFromArgs(args) ?: return listOf(LuaNil)
        val func = parseFuncAndIndex(args)?.first
        val value = args.getOrNull(2) ?: LuaNil

        access.upvalue.set(value)
        // For Lua functions with stripped debug info, empty names become "(no name)"
        // For native functions, empty names stay as "" (Lua 5.4 behavior)
        val displayName =
            if (access.name.isEmpty() && func is LuaCompiledFunction) {
                "(no name)"
            } else {
                access.name
            }
        return listOf<LuaValue<*>>(LuaString(displayName))
    }

    /**
     * debug.traceback([thread,] [message [, level]])
     *
     * Returns string with stack traceback.
     * If thread is provided, returns traceback of that thread.
     * If message is provided, it's prepended to the traceback.
     * Level specifies where to start the traceback (default 1).
     *
     * Lua 5.4 semantics:
     * - If the first argument is not a string or thread, it is returned unchanged.
     * - debug.traceback(thread, message, level) - traceback of specified thread
     * - debug.traceback(message, level) - traceback of current thread
     */
    private fun tracebackImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val firstArg = args.getOrNull(0)

        // Check if first argument is a thread (coroutine)
        val (targetThread, message, requestedLevel) =
            when {
                firstArg is LuaCoroutine || firstArg is LuaThread -> {
                    // debug.traceback(thread, message, level)
                    val msg = (args.getOrNull(1) as? LuaString)?.value ?: ""
                    val level = (args.getOrNull(2) as? LuaNumber)?.toDouble()?.toInt() ?: 0 // default to 0 for threads
                    Triple(firstArg, msg, level)
                }
                firstArg is LuaString || firstArg == null -> {
                    // debug.traceback(message, level) - current thread
                    val msg = (firstArg as? LuaString)?.value ?: ""
                    val level = (args.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt() ?: 1
                    Triple(null, msg, level)
                }
                else -> {
                    // If first argument is not a string or thread, return it unchanged (Lua 5.4 behavior)
                    return listOf(firstArg)
                }
            }

        val currentContext = debugContext

        val traceback =
            if (targetThread != null) {
                // Getting traceback of a different thread (coroutine)
                when (targetThread) {
                    is LuaCoroutine.LuaFunctionCoroutine -> {
                        val savedStack = targetThread.thread.savedCallStack
                        if (savedStack.isNotEmpty()) {
                            // Use the saved call stack from when the coroutine yielded/errored
                            // For both SUSPENDED and DEAD coroutines: use "in upvalue/function <>" descriptor (Lua 5.4 behavior)
                            val isSuspended = targetThread.status == ai.tenum.lua.runtime.CoroutineStatus.SUSPENDED
                            val isDead = targetThread.status == ai.tenum.lua.runtime.CoroutineStatus.DEAD

                            // Remove duplicate coroutine.yield frames if present
                            // This happens when a coroutine yields multiple times - old yield frames accumulate
                            // Only filter for SUSPENDED coroutines (not DEAD, which error without multiple yields)
                            val cleanedStack =
                                if (savedStack.size >= 2 && !isDead) {
                                    val filteredStack = mutableListOf<ai.tenum.lua.vm.CallFrame>()
                                    var lastWasYield = false
                                    for (frame in savedStack) {
                                        val isYield =
                                            frame.isNative &&
                                                (frame.function as? ai.tenum.lua.runtime.LuaNativeFunction)?.name == "coroutine.yield"
                                        // Skip consecutive yield frames (keep only the first one)
                                        if (isYield && lastWasYield) {
                                            continue
                                        }
                                        filteredStack.add(frame)
                                        lastWasYield = isYield
                                    }
                                    filteredStack
                                } else {
                                    savedStack
                                }

                            val reversed = cleanedStack.asReversed()
                            val startIndex = requestedLevel.coerceIn(0, cleanedStack.size)
                            TracebackFormatter.formatTraceback(
                                reversed,
                                message,
                                startIndex,
                                useUpvalueDescriptor = true, // Use upvalue descriptor for both suspended and dead coroutines
                            )
                        } else {
                            // Coroutine finished or hasn't yielded yet - return just header
                            // Lua 5.4 behavior: finished coroutines have empty traceback
                            buildString {
                                if (message.isNotEmpty()) {
                                    appendLine(message)
                                }
                                append("stack traceback:")
                            }
                        }
                    }
                    else -> {
                        // Main thread or other thread type - no saved stack
                        buildString {
                            if (message.isNotEmpty()) {
                                appendLine(message)
                            }
                            append("stack traceback:")
                        }
                    }
                }
            } else if (currentContext != null) {
                // Get stack view - automatically handles hook context
                val stackView = currentContext.getStackView()
                val snapshot = currentContext.getHookSnapshot()

                // If in hook, combine hook frame with observed frames
                val rawCallStack =
                    if (snapshot != null && stackView.size > 0) {
                        // Hook context: find the hook frame (skip native frames like debug.traceback)
                        // and combine with observed frames from when the hook was triggered
                        val hookFrame = stackView.forTraceback().firstOrNull { !it.isNative }
                        if (hookFrame != null) {
                            listOf(hookFrame) + snapshot.frames.asReversed()
                        } else {
                            snapshot.frames.asReversed()
                        }
                    } else {
                        // Normal context: use stack as-is (most-recent-first for traceback)
                        stackView.forTraceback()
                    }

                // When in a coroutine, filter frames to only show those within the coroutine
                // This prevents leaking frames from outside the coroutine boundary
                val currentCo = libContext?.getCurrentCoroutine?.invoke()
                val filteredByBoundary =
                    if (currentCo != null && targetThread == null) {
                        // Only apply filtering when getting traceback of current thread (not another coroutine)
                        val callStackBase = currentCo.thread.callStackBase
                        // rawCallStack is most-recent-first, so we need to filter from the end
                        // Frames at the end (oldest) before callStackBase should be excluded
                        // stackView.size gives us total frames including the ones before callStackBase
                        val totalFrames = stackView.size
                        if (callStackBase > 0 && callStackBase < totalFrames) {
                            val framesToDrop = callStackBase
                            rawCallStack.dropLast(framesToDrop)
                        } else {
                            rawCallStack
                        }
                    } else {
                        rawCallStack
                    }

                // Filter out anonymous native wrapper functions (e.g., from coroutine.wrap)
                // Lua 5.4 doesn't show these in tracebacks
                // Keep named native functions (pcall, xpcall, error, etc.) and all Lua functions
                val callStack =
                    filteredByBoundary.filter { frame ->
                        when {
                            !frame.isNative -> {
                                // Keep all Lua functions
                                true
                            }
                            else -> {
                                // For native functions, only keep those with meaningful names
                                val nativeFunc = frame.function as? ai.tenum.lua.runtime.LuaNativeFunction
                                val hasName =
                                    nativeFunc?.name != null && nativeFunc.name.isNotEmpty() && nativeFunc.name != "native"
                                val hasInferredName =
                                    frame.inferredFunctionName?.name != null &&
                                        frame.inferredFunctionName.name.isNotEmpty() &&
                                        frame.inferredFunctionName.name != "?"
                                hasName || hasInferredName
                            }
                        }
                    }

                // Level semantics:
                // level 0 = include debug.traceback itself (start at index 0)
                // level 1 = show from first Lua frame after debug.traceback (start at index 1, which is default)
                // level 2 = skip one more frame, etc.
                val startIndex =
                    if (requestedLevel == 0) {
                        0 // Include debug.traceback itself
                    } else {
                        // For level >= 1, skip the traceback function (which is at index 0 in normal context)
                        // But in hook context, we need to preserve the hook frame
                        val offset = if (snapshot != null) 0 else 1
                        (requestedLevel - 1 + offset).coerceIn(0, callStack.size)
                    }
                TracebackFormatter.formatTraceback(callStack, message, startIndex)
            } else {
                buildString {
                    if (message.isNotEmpty()) {
                        appendLine(message)
                    }
                    appendLine("stack traceback:")
                    append("\t[C]: in function '?'")
                }
            }

        return listOf(LuaString(traceback))
    }

    /**
     * debug.sethook([hook, mask [, count]])
     *
     * Sets execution hook function that will be called on various events:
     * - "c": call event (when function is called)
     * - "r": return event (when function returns)
     * - "l": line event (when new line is executed)
     * Count specifies how often to trigger (for line hooks).
     *
     * Calling with no arguments disables the hook.
     */
    private fun setHookImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val currentContext = debugContext ?: return listOf()

        if (args.isEmpty()) {
            // Disable hook for current thread
            currentContext.setHook(null, "")
        } else {
            // Check if first argument is a coroutine (thread-specific hook)
            val firstArg = args.getOrNull(0)
            when (firstArg) {
                is LuaCoroutine -> {
                    // debug.sethook(thread, hook, mask [, count])
                    val hookFunc = args.getOrNull(1)
                    val mask = (args.getOrNull(2) as? LuaString)?.value ?: ""
                    val count = (args.getOrNull(3) as? LuaNumber)?.toDouble()?.toInt() ?: 0

                    // Store hook in registry's _HOOKKEY table with coroutine as key
                    val registry = currentContext.getRegistry()
                    val hookKey = LuaString("_HOOKKEY")
                    val hookTable =
                        registry[hookKey] as? LuaTable ?: run {
                            // Initialize _HOOKKEY table if not present
                            val newTable = LuaTable()
                            val mt = LuaTable()
                            mt[LuaString("__mode")] = LuaString("k")
                            newTable.metatable = mt
                            registry[hookKey] = newTable
                            newTable
                        }

                    if (hookFunc is LuaFunction) {
                        // Store hook info as a table: {func, mask, count}
                        val hookInfo = LuaTable()
                        hookInfo[LuaNumber.of(1)] = hookFunc
                        hookInfo[LuaNumber.of(2)] = LuaString(mask)
                        hookInfo[LuaNumber.of(3)] = LuaNumber.of(count)
                        hookTable[firstArg] = hookInfo
                    } else if (hookFunc is LuaNil) {
                        // Clear hook for this coroutine
                        hookTable[firstArg] = LuaNil
                    }
                }
                is LuaFunction -> {
                    // debug.sethook(hook, mask [, count]) - current thread
                    val mask = (args.getOrNull(1) as? LuaString)?.value ?: ""
                    val count = (args.getOrNull(2) as? LuaNumber)?.toDouble()?.toInt() ?: 0

                    // Create Kotlin hook that uses DebugContext.executeHook()
                    val debugHook =
                        object : DebugHook {
                            override val luaFunction: LuaFunction = firstArg

                            override fun onHook(
                                event: HookEvent,
                                line: Int,
                                callStack: List<CallFrame>,
                            ) {
                                // Delegate to DebugContext which handles all state management
                                currentContext.executeHook(firstArg, event, line, callStack)
                            }
                        }

                    currentContext.setHook(debugHook, mask, count)
                }
                is LuaNil -> {
                    // debug.sethook(nil) should also clear the hook (Lua 5.4 behavior)
                    currentContext.setHook(null, "")
                }
                else -> {
                    // Invalid first argument
                    // Silently ignore or could throw error
                }
            }
        }

        return listOf()
    }

    /**
     * debug.gethook([thread])
     *
     * Returns hook function, mask, and count for current thread or specified thread.
     * Returns nil if no hook is set.
     */
    private fun getHookImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val currentVm = debugContext ?: return listOf()

        // Check if first argument is a coroutine
        val firstArg = args.getOrNull(0)
        if (firstArg is LuaCoroutine) {
            // debug.gethook(thread) - get hook for specific coroutine
            val registry = currentVm.getRegistry()
            val hookKey = LuaString("_HOOKKEY")
            val hookTable = registry[hookKey] as? LuaTable ?: return listOf()

            val hookInfo = hookTable[firstArg] as? LuaTable ?: return listOf()

            // Extract hook info: {func, mask, count}
            val hookFunc = hookInfo[LuaNumber.of(1)] ?: LuaNil
            val mask = hookInfo[LuaNumber.of(2)] ?: LuaString("")
            val count = hookInfo[LuaNumber.of(3)] ?: LuaNumber.of(0)

            return listOf(hookFunc, mask, count)
        }

        // debug.gethook() - get hook for current thread
        val config = currentVm.getHook()

        if (config.hook == null) {
            return listOf()
        }

        // Build mask string from events
        val maskString =
            buildString {
                if (HookEvent.CALL in config.mask) append('c')
                if (HookEvent.RETURN in config.mask) append('r')
                if (HookEvent.LINE in config.mask) append('l')
            }

        // Return hook function, mask, count
        val hookFunc = config.hook.luaFunction ?: LuaNil
        return listOf<LuaValue<*>>(
            hookFunc,
            LuaString(maskString),
            LuaNumber.of(config.count),
        )
    }

    /**
     * debug.getregistry()
     *
     * Returns the registry table. The registry is a predefined table
     * where C code can store Lua values. It's used for storing internal
     * state like hook tables.
     */
    private fun getRegistryImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val currentVm = debugContext ?: return listOf(LuaNil)
        return listOf(currentVm.getRegistry())
    }

    /**
     * debug.upvalueid(func, index)
     *
     * Returns a unique identifier (light userdata) for the upvalue at the given index.
     * This is used to test whether different closures share the same upvalue.
     * Upvalue indices are 1-based and include _ENV.
     *
     * Note: We return a special light userdata marker object since we don't have
     * actual light userdata support. The key behavior is that:
     * 1. The same upvalue always returns the same identifier
     * 2. Different upvalues return different identifiers
     * 3. The identifier is a light userdata type
     */
    private fun upvalueIdImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val access = getUpvalueAccessFromArgs(args) ?: return listOf(LuaNil)

        // Return a cached light userdata marker
        // This ensures the same upvalue always returns the same identifier
        val lightUserdata =
            upvalueIdCache.getOrPut(access.upvalue) {
                LuaUserdata(LightUserdataMarker(access.upvalue))
            }

        return listOf(lightUserdata)
    }

    /**
     * debug.upvaluejoin(f1, n1, f2, n2)
     *
     * Makes the n1-th upvalue of f1 refer to the same upvalue as the n2-th upvalue of f2.
     * Both f1 and f2 must be Lua functions (not C functions).
     * Indices are 1-based.
     *
     * This allows sharing upvalues between closures dynamically at runtime.
     * After this call, modifications to f1's n1-th upvalue will affect f2's n2-th upvalue and vice versa.
     */
    private fun upvaluejoinImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val f1 = args.getOrNull(0)
        val n1 = (args.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt()
        val f2 = args.getOrNull(2)
        val n2 = (args.getOrNull(3) as? LuaNumber)?.toDouble()?.toInt()

        // Validate both functions are Lua closures
        if (f1 !is LuaCompiledFunction) {
            throw LuaRuntimeError(
                "bad argument #1 to 'upvaluejoin' (Lua function expected)",
            )
        }
        if (f2 !is LuaCompiledFunction) {
            throw LuaRuntimeError(
                "bad argument #3 to 'upvaluejoin' (Lua function expected)",
            )
        }

        // Validate indices
        if (n1 == null || n1 < 1) {
            throw LuaRuntimeError(
                "bad argument #2 to 'upvaluejoin' (invalid upvalue index)",
            )
        }
        if (n2 == null || n2 < 1) {
            throw LuaRuntimeError(
                "bad argument #4 to 'upvaluejoin' (invalid upvalue index)",
            )
        }

        // Convert to 0-based indices
        val idx1 = n1 - 1
        val idx2 = n2 - 1

        // Validate indices are in range
        if (idx1 >= f1.upvalues.size) {
            throw LuaRuntimeError(
                "bad argument #2 to 'upvaluejoin' (invalid upvalue index)",
            )
        }
        if (idx2 >= f2.upvalues.size) {
            throw LuaRuntimeError(
                "bad argument #4 to 'upvaluejoin' (invalid upvalue index)",
            )
        }

        // Get the target upvalue from f2
        val targetUpvalue = f2.upvalues[idx2]

        // Replace f1's upvalue with f2's upvalue
        f1.upvalues[idx1] = targetUpvalue

        // Clear the upvalue ID cache for the old upvalue since it's no longer referenced
        // (optional cleanup, but not strictly necessary)

        return emptyList()
    }

    /**
     * debug.setuservalue(udata, value [, n])
     *
     * Sets the user value associated with the given userdata.
     * Returns the userdata itself if successful, nil otherwise.
     *
     * In Lua 5.4, light userdata cannot have user values, only full userdata can.
     * For FILE* userdata (io.stdin, etc.), this returns nil (cannot set user values).
     */
    private fun setUservalueImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val udata = args.getOrNull(0)
        val value = args.getOrNull(1) ?: LuaNil

        // Check if it's userdata
        if (udata !is LuaUserdata) {
            // Get type name for error message
            val typeName =
                when (udata) {
                    null, LuaNil -> "nil"
                    else -> udata.type().name.lowercase()
                }
            throw LuaRuntimeError(
                "bad argument #1 to 'setuservalue' (userdata expected, got $typeName)",
            )
        }

        // Check if it's light userdata (our marker)
        if (udata.value is LightUserdataMarker) {
            throw LuaRuntimeError(
                "bad argument #1 to 'setuservalue' (userdata expected, got light userdata)",
            )
        }

        // For FILE* and other full userdata, we don't support user values yet
        // Return nil to indicate failure (Lua 5.4 behavior for FILE*)
        return listOf(LuaNil)
    }

    /**
     * debug.getuservalue(udata [, n])
     *
     * Gets the user value associated with the given userdata.
     * Returns (value, isvalid) where isvalid indicates if the slot exists.
     *
     * For FILE* and other userdata without user values, returns (nil, nil).
     */
    private fun getUservalueImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        val udata = args.getOrNull(0)

        // Non-userdata returns (nil, false)
        if (udata !is LuaUserdata) {
            return listOf(LuaNil, LuaBoolean.FALSE)
        }

        // Light userdata cannot have user values
        if (udata.value is LightUserdataMarker) {
            return listOf(LuaNil, LuaBoolean.FALSE)
        }

        // For FILE* and other full userdata, we don't store user values yet
        // Return (nil, nil) to match Lua 5.4 behavior for FILE*
        return listOf(LuaNil, LuaNil)
    }

    /**
     * Marker class for light userdata (returned by debug.upvalueid)
     *
     * Light userdata is different from full userdata:
     * - Light userdata has no metatable
     * - Light userdata cannot have user values
     * - Light userdata is just a pointer/reference
     */
    private data class LightUserdataMarker(
        val upvalue: Upvalue,
    )

    companion object {
        // Lua 5.4 short_src formatting constants
        private const val LUA_IDSIZE = 60 // Max length before truncation

        /**
         * Format short_src for file sources (starts with @).
         * Lua 5.4 truncates very long file paths from the beginning with "...".
         */
        private fun formatFileShortSrc(filePath: String): String =
            if (filePath.length > LUA_IDSIZE) {
                "..." + filePath.takeLast(LUA_IDSIZE - 3)
            } else {
                filePath
            }

        /**
         * Format short_src for string sources.
         * Follows Lua 5.4 rules:
         * 1. If source starts with newline, return [string "..."]
         * 2. Otherwise, show beginning of source up to ~60 chars
         * 3. Truncate at first newline if present
         * 4. Add "..." if truncated
         * 5. Wrap in [string "..."]
         */
        private fun formatStringShortSrc(source: String): String {
            // If source starts with newline, just return [string "..."]
            if (source.startsWith("\n")) {
                return "[string \"...\"]"
            }

            // Find first newline
            val newlineIndex = source.indexOf('\n')

            // Determine where to truncate
            val truncateAt =
                when {
                    newlineIndex in 1 until LUA_IDSIZE -> newlineIndex // Truncate at newline if within limit
                    source.length > LUA_IDSIZE -> LUA_IDSIZE // Truncate at length limit
                    else -> source.length // No truncation needed
                }

            val truncated = source.substring(0, truncateAt)
            val needsEllipsis = truncateAt < source.length

            return if (needsEllipsis) {
                "[string \"$truncated...\"]"
            } else {
                "[string \"$truncated\"]"
            }
        }
    }
}
