package ai.tenum.lua.vm.coroutine

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.CoroutineStatus
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaThread
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

/**
 * Domain Service: Coroutine Runtime
 *
 * Consolidates ALL coroutine invariants and lifecycle management:
 * - Status transitions (SUSPENDED → RUNNING → DEAD/SUSPENDED)
 * - Saved call stacks and execution state
 * - Hook activation/deactivation for coroutine-specific debugging
 * - Native depth isolation (coroutines don't see caller's native depth)
 * - Resumption entry points and context switching
 *
 * This is the single source of truth for coroutine state.
 * Libraries (CoroutineLib) call into this service.
 * VM execution retrieves resume data from this service.
 *
 * Aggregate Root: Coroutine lifecycle
 * Domain Invariants:
 * - DEAD coroutines cannot be resumed
 * - RUNNING coroutines cannot be resumed (no re-entrance)
 * - Status transitions are atomic and controlled
 * - Call stack base is set before execution
 * - Native depth is isolated per coroutine
 * - Hooks are saved/restored during context switches
 */
class CoroutineStateManager {
    /**
     * Main thread coroutine (represents the main execution context)
     */
    val mainThread: LuaThread = LuaThread()

    /**
     * Currently executing coroutine (null = main thread)
     */
    private var currentCoroutine: LuaCoroutine? = null

    /**
     * Get the currently executing coroutine (null = main thread)
     */
    fun getCurrentCoroutine(): LuaCoroutine? = currentCoroutine

    /**
     * Set the currently executing coroutine.
     * Used during context switches between coroutines.
     */
    fun setCurrentCoroutine(coroutine: LuaCoroutine?) {
        currentCoroutine = coroutine
    }

    /**
     * Context for resuming a coroutine.
     * Encapsulates all state needed for safe coroutine execution.
     */
    data class ResumeContext(
        val coroutine: LuaCoroutine,
        val previousCoroutine: LuaCoroutine?,
        val savedNativeDepth: Int,
        val callStackBase: Int,
    )

    /**
     * Result of a coroutine resume attempt.
     */
    sealed class ResumeResult {
        data class Success(
            val results: List<LuaValue<*>>,
        ) : ResumeResult()

        data class Yielded(
            val values: List<LuaValue<*>>,
        ) : ResumeResult()

        data class Error(
            val message: String,
        ) : ResumeResult()

        data class InvalidState(
            val message: String,
        ) : ResumeResult()
    }

    /**
     * Begin resuming a coroutine.
     * Validates state, transitions status, and sets up execution context.
     *
     * Domain Invariant: Only SUSPENDED coroutines can be resumed.
     *
     * @param coroutine The coroutine to resume
     * @param currentCallStackSize Current size of call stack (for frame filtering)
     * @param currentNativeDepth Current native call depth (to isolate)
     * @return ResumeContext if valid, or InvalidState error
     */
    fun beginResume(
        coroutine: LuaCoroutine,
        currentCallStackSize: Int,
        currentNativeDepth: Int,
    ): ResumeResult {
        // Guard clauses for invalid states
        if (coroutine.status == CoroutineStatus.DEAD) {
            return ResumeResult.InvalidState("cannot resume dead coroutine")
        }
        if (coroutine.status == CoroutineStatus.RUNNING) {
            return ResumeResult.InvalidState("cannot resume running coroutine")
        }

        // Save previous context
        val previousCoroutine = currentCoroutine

        // Transition to RUNNING status (atomic state change)
        when (coroutine) {
            is LuaCoroutine.LuaFunctionCoroutine -> coroutine.statusValue = CoroutineStatus.RUNNING
            is LuaCoroutine.SuspendFunctionCoroutine -> coroutine.statusValue = CoroutineStatus.RUNNING
        }

        // Set this coroutine as current
        currentCoroutine = coroutine

        // Set call stack base for frame filtering
        // This marks where coroutine frames begin (excluding main thread frames)
        val thread =
            when (coroutine) {
                is LuaCoroutine.LuaFunctionCoroutine -> coroutine.thread
                is LuaCoroutine.SuspendFunctionCoroutine -> coroutine.thread
            }
        thread.callStackBase = currentCallStackSize

        // Save and isolate native call depth
        // Coroutine execution should not see the caller's native depth
        // (e.g., if resumed via pcall, the coroutine shouldn't see pcall's depth)
        thread.savedNativeCallDepth = currentNativeDepth

        return ResumeResult.Success(emptyList())
    }

    /**
     * Complete a coroutine that returned normally.
     * Transitions status to DEAD and clears saved state.
     *
     * Domain Invariant: Dead coroutines have empty call stacks (Lua 5.4 behavior).
     */
    fun completeCoroutine(
        coroutine: LuaCoroutine,
        results: List<LuaValue<*>>,
        previousCoroutine: LuaCoroutine?,
    ) {
        // Transition to DEAD status
        when (coroutine) {
            is LuaCoroutine.LuaFunctionCoroutine -> {
                coroutine.statusValue = CoroutineStatus.DEAD
                // Clear saved call stack for successfully completed coroutine
                coroutine.thread.reset()
            }
            is LuaCoroutine.SuspendFunctionCoroutine -> {
                coroutine.statusValue = CoroutineStatus.DEAD
                coroutine.thread.reset()
            }
        }

        // Restore previous coroutine context
        currentCoroutine = previousCoroutine
    }

    /**
     * Handle a coroutine that yielded.
     * Transitions status to SUSPENDED.
     *
     * Note: Execution state is already saved by saveCoroutineState() before throwing LuaYieldException.
     */
    fun handleYield(
        coroutine: LuaCoroutine,
        yieldedValues: List<LuaValue<*>>,
        previousCoroutine: LuaCoroutine?,
    ) {
        // Transition to SUSPENDED status
        when (coroutine) {
            is LuaCoroutine.LuaFunctionCoroutine -> {
                coroutine.statusValue = CoroutineStatus.SUSPENDED
                // yieldedValues already saved in thread.yieldedValues by VM
            }
            is LuaCoroutine.SuspendFunctionCoroutine -> {
                coroutine.statusValue = CoroutineStatus.SUSPENDED
                coroutine.thread.yieldedValues = yieldedValues
            }
        }

        // Restore previous coroutine context
        currentCoroutine = previousCoroutine
    }

    /**
     * Handle a coroutine that errored.
     * Transitions status to DEAD.
     *
     * Note: Call stack is already saved by VM before throwing exception.
     */
    fun handleError(
        coroutine: LuaCoroutine,
        previousCoroutine: LuaCoroutine?,
    ) {
        // Transition to DEAD status
        when (coroutine) {
            is LuaCoroutine.LuaFunctionCoroutine -> {
                coroutine.statusValue = CoroutineStatus.DEAD
                // VM already saved call stack in thread.savedCallStack
            }
            is LuaCoroutine.SuspendFunctionCoroutine -> {
                coroutine.statusValue = CoroutineStatus.DEAD
            }
        }

        // Restore previous coroutine context
        currentCoroutine = previousCoroutine
    }

    /**
     * Check if current execution context can yield.
     *
     * Domain Rule: Main thread cannot yield, coroutines can yield only if not in C boundary.
     *
     * @param nativeCallDepth Current native call depth
     * @return true if yielding is allowed
     */
    fun isYieldable(nativeCallDepth: Int): Boolean {
        // Main thread cannot yield
        if (currentCoroutine == null) return false

        // Coroutines cannot yield if inside a C boundary (native call)
        if (nativeCallDepth > 0) return false

        return true
    }

    /**
     * Get the native depth that should be active for current coroutine.
     * Returns 0 for coroutines (isolated), or current depth for main thread.
     */
    fun getIsolatedNativeDepth(mainThreadDepth: Int): Int {
        val currentCo = currentCoroutine ?: return mainThreadDepth

        // Coroutines execute with isolated native depth (always 0)
        return 0
    }

    /**
     * Get the native depth to restore after coroutine exits.
     */
    fun getSavedNativeDepth(coroutine: LuaCoroutine): Int =
        when (coroutine) {
            is LuaCoroutine.LuaFunctionCoroutine -> coroutine.thread.savedNativeCallDepth
            is LuaCoroutine.SuspendFunctionCoroutine -> coroutine.thread.savedNativeCallDepth
        }

    /**
     * Save coroutine execution state when yielding.
     * Captures the current execution state so it can be resumed later.
     *
     * This is called by the VM when LuaYieldException is caught.
     */
    fun saveCoroutineState(
        coroutine: LuaCoroutine?,
        proto: Proto,
        programCounter: Int,
        registers: MutableList<LuaValue<*>>,
        upvalues: List<Upvalue>,
        varargs: List<LuaValue<*>>,
        yieldedValues: List<LuaValue<*>>,
        yieldTargetRegister: Int,
        yieldExpectedResults: Int,
        callStack: List<ai.tenum.lua.vm.CallFrame>,
    ) {
        val co = coroutine ?: return

        val thread =
            when (co) {
                is LuaCoroutine.LuaFunctionCoroutine -> co.thread
                is LuaCoroutine.SuspendFunctionCoroutine -> co.thread
            }

        thread.proto = proto
        thread.pc = programCounter + 1 // Resume at next instruction after yield
        thread.registers = registers.toMutableList()
        thread.upvalues = upvalues.toList() // Save upvalues for correct closure context
        thread.varargs = varargs
        thread.yieldedValues = yieldedValues
        thread.yieldTargetRegister = yieldTargetRegister
        thread.yieldExpectedResults = yieldExpectedResults
        thread.savedCallStack = callStack.toList() // Save a copy of the call stack
    }
}
