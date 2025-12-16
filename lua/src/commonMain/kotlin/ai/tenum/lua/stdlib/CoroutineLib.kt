package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.CoroutineStatus
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaThread
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.LuaYieldException
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext

/**
 * Coroutine Library (coroutine.*)
 *
 * Provides coroutine creation and management functions.
 * Based on Lua 5.4 coroutine library specification.
 */
class CoroutineLib : LuaLibrary {
    override val name: String = "coroutine"

    override fun register(context: LuaLibraryContext) {
        val lib = LuaTable()

        // coroutine.create(f) - Create new coroutine
        lib[LuaString("create")] =
            LuaNativeFunction { args ->
                buildList {
                    add(coroutineCreate(args))
                }
            }

        // coroutine.resume(co [, ...]) - Resume coroutine
        lib[LuaString("resume")] =
            LuaNativeFunction("coroutine.resume") { args ->
                coroutineResume(args, context)
            }

        // coroutine.yield(...) - Yield from coroutine
        lib[LuaString("yield")] =
            LuaNativeFunction("coroutine.yield") { args ->
                coroutineYield(args, context)
            }

        // coroutine.status(co) - Get coroutine status
        lib[LuaString("status")] =
            LuaNativeFunction { args ->
                buildList {
                    add(coroutineStatus(args))
                }
            }

        // coroutine.wrap(f) - Create wrapped coroutine
        lib[LuaString("wrap")] =
            LuaNativeFunction { args ->
                buildList {
                    add(coroutineWrap(args, context))
                }
            }

        // coroutine.running() - Get running coroutine
        lib[LuaString("running")] =
            LuaNativeFunction { args ->
                coroutineRunning(context)
            }

        // coroutine.isyieldable() - Check if current context can yield
        lib[LuaString("isyieldable")] =
            LuaNativeFunction { args ->
                buildList {
                    add(coroutineIsYieldable(context))
                }
            }

        context.registerGlobal("coroutine", lib)
    }

    /**
     * coroutine.create(f) - Create a new coroutine
     */
    private fun coroutineCreate(args: List<LuaValue<*>>): LuaValue<*> {
        val func = args.getOrNull(0)

        return when (func) {
            is LuaFunction -> LuaCoroutine.LuaFunctionCoroutine(func)
            else -> LuaNil
        }
    }

    /**
     * Validate that the argument is a thread (LuaCoroutine or LuaThread)
     * Throws error with descriptive message if validation fails
     */
    private fun validateThreadArgument(
        arg: LuaValue<*>?,
        functionName: String,
    ) {
        when (arg) {
            is LuaCoroutine, is LuaThread -> return // Valid thread types
            else -> {
                val typeName = arg?.type()?.name?.lowercase() ?: "no value"
                error("bad argument #1 to '$functionName' (thread expected, got $typeName)")
            }
        }
    }

    /**
     * coroutine.resume(co [, ...]) - Resume execution of a coroutine
     */
    private fun coroutineResume(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val firstArg = args.firstOrNull() ?: return resumeError("bad argument #1 (thread expected, got no value)")
        validateThreadArgument(firstArg, "coroutine.resume")

        // Handle main thread (cannot be resumed)
        if (firstArg is LuaThread) {
            return resumeError("cannot resume non-suspended coroutine")
        }

        val coroutine = firstArg as LuaCoroutine
        val resumeArgs = args.drop(1)

        // Use CoroutineStateManager to begin resume and validate state
        val stateManager = context.getCoroutineStateManager?.invoke() ?: return resumeError("no coroutine state manager")
        val currentCallStackSize = context.getCallStack?.invoke()?.size ?: 0
        val currentNativeDepth = context.getNativeCallDepth?.invoke() ?: 0

        // Get previous coroutine before beginResume changes it
        val previousCoroutine = context.getCurrentCoroutine?.invoke()

        // Begin resume - this validates state and transitions to RUNNING
        val beginResult = stateManager.beginResume(coroutine, currentCallStackSize, currentNativeDepth)
        if (beginResult is ai.tenum.lua.vm.coroutine.CoroutineStateManager.ResumeResult.InvalidState) {
            return resumeError(beginResult.message)
        }

        // Reset native call depth to 0 for coroutine execution (isolation)
        context.saveNativeCallDepth?.invoke(0)

        // Activate coroutine-specific hook if present
        val hookState = activateCoroutineHook(coroutine, context)

        // Execute coroutine: resume from saved state or start fresh
        return try {
            val results =
                when (coroutine) {
                    is LuaCoroutine.LuaFunctionCoroutine -> {
                        try {
                            if (coroutine.thread.proto != null) {
                                resumeFromSavedState(coroutine, resumeArgs, context)
                            } else {
                                context.callFunction(coroutine.func, resumeArgs)
                            }
                        } finally {
                            // Deactivate coroutine hook and restore previous hook
                            deactivateCoroutineHook(hookState, context)
                        }
                    }
                    is LuaCoroutine.SuspendFunctionCoroutine -> {
                        try {
                            // For suspend functions, simplified implementation
                            emptyList()
                        } finally {
                            deactivateCoroutineHook(hookState, context)
                        }
                    }
                }

            // Complete coroutine successfully
            stateManager.completeCoroutine(coroutine, results, previousCoroutine)

            // Clean up call stack frames added by coroutine
            context.cleanupCallStackFrames?.invoke(currentCallStackSize)

            // Restore native call depth
            val savedDepth = stateManager.getSavedNativeDepth(coroutine)
            context.saveNativeCallDepth?.invoke(savedDepth)

            listOf(LuaBoolean.TRUE) + results
        } catch (e: LuaYieldException) {
            // Coroutine yielded - execution state already saved in VM
            stateManager.handleYield(coroutine, coroutine.thread.yieldedValues, previousCoroutine)

            // Clean up call stack frames added by coroutine
            context.cleanupCallStackFrames?.invoke(currentCallStackSize)

            // Restore native call depth
            val savedDepth = stateManager.getSavedNativeDepth(coroutine)
            context.saveNativeCallDepth?.invoke(savedDepth)

            listOf(LuaBoolean.TRUE) + coroutine.thread.yieldedValues
        } catch (e: Exception) {
            // Coroutine errored - call stack already saved by VM
            stateManager.handleError(coroutine, previousCoroutine)

            // Clean up call stack frames added by coroutine
            context.cleanupCallStackFrames?.invoke(currentCallStackSize)

            // Restore native call depth
            val savedDepth = stateManager.getSavedNativeDepth(coroutine)
            context.saveNativeCallDepth?.invoke(savedDepth)

            listOf(LuaBoolean.FALSE, LuaString(e.message ?: "error in coroutine"))
        }
    }

    /**
     * coroutine.yield(...) - Yield from current coroutine
     */
    private fun coroutineYield(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        // Check if we're outside a coroutine (in main thread)
        val currentCo = context.getCurrentCoroutine?.invoke()
        if (currentCo == null) {
            error("attempt to yield from outside a coroutine")
        }

        // Check if we're inside a native function call (C boundary)
        val nativeCallDepth = context.getNativeCallDepth?.invoke() ?: 0
        if (nativeCallDepth > 0) {
            error("attempt to yield across a metamethod/C-call boundary")
        }

        // Throw special exception that will be caught by coroutineResume
        throw LuaYieldException(args)
    }

    /**
     * coroutine.status(co) - Get coroutine status
     */
    private fun coroutineStatus(args: List<LuaValue<*>>): LuaValue<*> {
        val firstArg = args.getOrNull(0)
        validateThreadArgument(firstArg, "coroutine.status")

        return when (firstArg) {
            is LuaCoroutine -> {
                val statusString =
                    when (firstArg.status) {
                        CoroutineStatus.SUSPENDED -> "suspended"
                        CoroutineStatus.RUNNING -> "running"
                        CoroutineStatus.NORMAL -> "normal"
                        CoroutineStatus.DEAD -> "dead"
                    }
                LuaString(statusString)
            }
            is LuaThread -> LuaString("normal") // Main thread status
            else -> error("Unreachable: validateThreadArgument should have caught this")
        }
    }

    /**
     * coroutine.wrap(f) - Create a wrapped coroutine that can be called directly
     */
    private fun coroutineWrap(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): LuaValue<*> {
        val func = args.getOrNull(0) as? LuaFunction ?: return LuaNil
        val coroutine = LuaCoroutine.LuaFunctionCoroutine(func)

        // Return a function that resumes the coroutine when called
        return LuaNativeFunction { callArgs ->
            val resumeResult = coroutineResume(listOf(coroutine) + callArgs, context)

            // If first result is false, throw error
            if (resumeResult.firstOrNull() == LuaBoolean.FALSE) {
                val errorMsg = (resumeResult.getOrNull(1) as? LuaString)?.value ?: "error in coroutine"
                throw RuntimeException(errorMsg)
            }

            // Return all results except the first (success) boolean
            resumeResult.drop(1)
        }
    }

    /**
     * coroutine.running() - Get the currently running coroutine
     */
    private fun coroutineRunning(context: LuaLibraryContext): List<LuaValue<*>> {
        val currentCo = context.getCurrentCoroutine?.invoke()

        return if (currentCo == null) {
            // Running in main thread
            val mainThread = context.getMainThread?.invoke() ?: LuaNil
            listOf(mainThread, LuaBoolean.TRUE)
        } else {
            // Running in a coroutine
            listOf(currentCo, LuaBoolean.FALSE)
        }
    }

    /**
     * coroutine.isyieldable() - Check if current context can yield
     */
    private fun coroutineIsYieldable(context: LuaLibraryContext): LuaValue<*> {
        val stateManager = context.getCoroutineStateManager?.invoke() ?: return LuaBoolean.FALSE
        val nativeCallDepth = context.getNativeCallDepth?.invoke() ?: 0

        return if (stateManager.isYieldable(nativeCallDepth)) {
            LuaBoolean.TRUE
        } else {
            LuaBoolean.FALSE
        }
    }

    // Helper functions for coroutine state management

    private fun resumeError(message: String): List<LuaValue<*>> = listOf(LuaBoolean.FALSE, LuaString(message))

    private fun resumeFromSavedState(
        coroutine: LuaCoroutine.LuaFunctionCoroutine,
        resumeArgs: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val executeWithResume =
            context.executeProtoWithResume
                ?: error("VM does not support coroutine resumption")

        val compiledFunc =
            coroutine.func as? LuaCompiledFunction
                ?: error("Coroutine function must be a compiled function")

        return executeWithResume(
            coroutine.thread.proto!!,
            resumeArgs,
            compiledFunc.upvalues,
            coroutine.func,
            coroutine.thread,
        )
    }

    /**
     * Activate the debug hook for a coroutine before resuming it.
     * Retrieves the hook from registry's _HOOKKEY table and sets it as the current hook.
     */
    private fun activateCoroutineHook(
        coroutine: LuaCoroutine,
        context: LuaLibraryContext,
    ): HookState? {
        val vm = context.vm ?: return null

        // Get hook info from registry's _HOOKKEY table
        val registry = vm.getRegistry()
        val hookKey = LuaString("_HOOKKEY")
        val hookTable = registry[hookKey] as? LuaTable ?: return null
        val hookInfo = hookTable[coroutine] as? LuaTable ?: return null

        // Extract hook function, mask, and count
        val hookFunc =
            hookInfo[
                ai.tenum.lua.runtime.LuaNumber
                    .of(1),
            ] as? LuaFunction ?: return null
        val maskStr =
            (
                hookInfo[
                    ai.tenum.lua.runtime.LuaNumber
                        .of(2),
                ] as? LuaString
            )?.value ?: ""
        val count =
            (
                hookInfo[
                    ai.tenum.lua.runtime.LuaNumber
                        .of(3),
                ] as? ai.tenum.lua.runtime.LuaNumber
            )?.toDouble()?.toInt() ?: 0

        // Save current hook state
        val previousHook = vm.getHook()

        // Set coroutine's hook as active
        val debugHook =
            object : ai.tenum.lua.vm.debug.DebugHook {
                override val luaFunction: LuaFunction = hookFunc

                override fun onHook(
                    event: ai.tenum.lua.vm.debug.HookEvent,
                    line: Int,
                    callStack: List<ai.tenum.lua.vm.CallFrame>,
                ) {
                    vm.executeHook(hookFunc, event, line, callStack)
                }
            }

        vm.setHook(coroutine = null, hook = debugHook, mask = maskStr, count = count)

        // Return previous state for restoration
        return HookState(previousHook)
    }

    /**
     * Deactivate the coroutine hook and restore the previous hook state.
     */
    private fun deactivateCoroutineHook(
        hookState: HookState?,
        context: LuaLibraryContext,
    ) {
        val vm = context.vm ?: return
        if (hookState == null) return

        // Restore previous hook
        val prevConfig = hookState.previousConfig
        if (prevConfig.hook != null) {
            vm.setHook(coroutine = null, hook = prevConfig.hook, mask = prevConfig.mask.joinToString(""), count = prevConfig.count)
        } else {
            vm.setHook(coroutine = null, hook = null, mask = "")
        }
    }

    /**
     * Holder for previous hook state
     */
    private data class HookState(
        val previousConfig: ai.tenum.lua.vm.debug.HookConfig,
    )
}

/**
 * Helper to create a coroutine from a Kotlin suspend function
 */
fun createSuspendCoroutine(suspendFunc: suspend (List<LuaValue<*>>) -> List<LuaValue<*>>): LuaCoroutine =
    LuaCoroutine.SuspendFunctionCoroutine(suspendFunc)
