package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.LineEventKind
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.vm.debug.HookEvent

/**
 * Helper for triggering debug hooks during bytecode execution.
 * Encapsulates hook triggering logic without modifying control flow.
 */
class HookTriggerHelper(
    private val triggerHook: (HookEvent, Int) -> Unit,
) {
    /**
     * Trigger function entry hooks (CALL and optional LINE at lineDefined).
     * 
     * @param proto The proto being entered
     * @param isCoroutineContext Whether in coroutine context (affects LINE hook)
     */
    fun triggerEntryHooks(
        proto: Proto,
        isCoroutineContext: Boolean,
    ) {
        // Trigger CALL hook
        triggerHook(HookEvent.CALL, proto.lineDefined)

        // Trigger LINE hook for function definition line (Lua 5.4 semantics)
        // For regular function calls: LINE hook fires at lineDefined when entering function
        // For coroutines: LINE hook does NOT fire at lineDefined on entry/resume
        if (proto.lineDefined > 0 && !isCoroutineContext) {
            // For stripped functions (no lineEvents), pass -1 to indicate no debug info
            val lineForHook = if (proto.lineEvents.isEmpty()) -1 else proto.lineDefined
            triggerHook(HookEvent.LINE, lineForHook)
        }
    }

    /**
     * Trigger LINE hooks for line events at current PC.
     * 
     * @param proto The proto being executed
     * @param pc The current program counter
     * @param lastLine The last line number for which a hook was triggered
     * @return The new lastLine value after processing hooks
     */
    fun triggerLineHooksAt(
        proto: Proto,
        pc: Int,
        lastLine: Int,
    ): Int {
        var newLastLine = lastLine

        // Trigger LINE hooks for LineEvents at current PC (BEFORE executing instruction)
        // Domain: PUC Lua fires hooks for:
        // - EXECUTION: real bytecode execution
        // - CONTROL_FLOW: keywords like 'then', 'else' that mark decision points
        // - MARKER: block boundaries like 'end'
        // - ITERATION: loop headers that fire on every iteration
        // SYNTHETIC events are compiler-generated and don't fire hooks.
        val currentEvents = proto.lineEvents.filter { it.pc == pc }
        for (event in currentEvents) {
            // Skip SYNTHETIC events - they're for internal tracking only
            if (event.kind != LineEventKind.SYNTHETIC) {
                if (event.kind == LineEventKind.ITERATION || event.line != lastLine) {
                    newLastLine = event.line
                    triggerHook(HookEvent.LINE, event.line)
                }
            }
        }

        return newLastLine
    }
}
