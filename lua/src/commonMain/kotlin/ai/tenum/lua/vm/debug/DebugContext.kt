package ai.tenum.lua.vm.debug

import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.vm.execution.ExecutionSnapshot
import ai.tenum.lua.vm.execution.StackView

/**
 * Abstract interface for debug capabilities provided by the VM.
 *
 * This interface decouples the debug library (DebugLib) from VM internals (LuaVmImpl),
 * providing a stable abstraction that:
 * - Hides implementation details (no direct access to mutable VM fields)
 * - Enforces proper boundaries (debug operations go through well-defined methods)
 * - Enables testing (can be mocked without full VM instance)
 * - Documents the contract between debug library and VM
 */
interface DebugContext {
    /**
     * Get a view of the current call stack for stack inspection operations.
     * The returned StackView provides semantic access to frames without exposing
     * implementation details like stack orientation.
     */
    fun getStackView(): StackView

    /**
     * Get the last error call stack if available.
     * This is used by debug.traceback when called as an xpcall message handler
     * to show __close metamethod frames that have been popped from the current stack.
     * Returns null and clears the stored stack after being called.
     */
    fun getAndClearLastErrorCallStack(): List<ai.tenum.lua.vm.CallFrame>?

    /**
     * Execute a hook function with proper context preservation.
     *
     * This method:
     * - Captures the current execution state as an immutable snapshot
     * - Sets up the hook execution context
     * - Invokes the hook function
     * - Cleans up the context automatically
     *
     * @param hookFunc The Lua function to call as a hook
     * @param event The hook event type
     * @param line The line number where the hook was triggered
     * @param observedStack The call stack being observed (most-recent-first)
     */
    fun executeHook(
        hookFunc: LuaFunction,
        event: HookEvent,
        line: Int,
        observedStack: List<ai.tenum.lua.vm.CallFrame>,
    )

    /**
     * Set the debug hook configuration.
     *
     * @param hook The hook implementation (null to disable)
     * @param mask The event mask string (e.g., "crl" for call/return/line)
     * @param count The instruction count for COUNT hooks (0 to disable)
     */
    fun setHook(
        hook: DebugHook?,
        mask: String = "",
        count: Int = 0,
    )

    /**
     * Get the current hook configuration.
     */
    fun getHook(): HookConfig

    /**
     * Get the VM's registry table for storing debug metadata.
     */
    fun getRegistry(): LuaTable

    /**
     * Get the current execution snapshot if in a hook, null otherwise.
     * Used by debug.traceback() to access the observed stack during hook execution.
     */
    fun getHookSnapshot(): ExecutionSnapshot?
}
