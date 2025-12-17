package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue

/**
 * Handles execution of __close metamethods for to-be-closed variables.
 *
 * This component encapsulates the logic for:
 * - Executing close operations in LIFO order (reverse declaration)
 * - Filtering by register range and nil/false values
 * - Error chaining (passing errors to subsequent handlers)
 * - State capture/restore for coroutine yields during __close
 * - Tracking already-closed registers to avoid double-closing
 *
 * The CloseHandler is stateless - all state is passed as parameters or captured in snapshots.
 * This makes it easy to test and compose with coroutine yield/resume logic.
 */
class CloseHandler {
    /**
     * Execute __close metamethods for to-be-closed variables.
     *
     * @param startReg Only close variables at registers >= startReg
     * @param tbcVars List of (register, value) pairs to close
     * @param initialError Initial error value to pass to first handler
     * @param alreadyClosedRegs Set of registers that have already been closed (skip these)
     * @param closeCallback Function to invoke for each variable, receives (reg, value, errorArg)
     * @return Final error value after all close operations (may differ from initialError)
     */
    fun executeClose(
        startReg: Int,
        tbcVars: List<Pair<Int, LuaValue<*>>>,
        initialError: LuaValue<*>,
        alreadyClosedRegs: Set<Int> = emptySet(),
        closeCallback: (Int, LuaValue<*>, LuaValue<*>) -> Unit,
    ): LuaValue<*> {
        var currentError = initialError
        var lastException: Exception? = null

        // Filter first: only vars >= startReg and not already closed
        val varsToClose =
            tbcVars.filter { (reg, _) ->
                reg >= startReg && reg !in alreadyClosedRegs
            }

        // Sort by register DESC (highest first = LIFO = most recent declaration first)
        // This ensures we close in reverse declaration order
        val sortedVars = varsToClose.sortedByDescending { it.first }

        // Process in declaration-reverse order (LIFO)
        for ((reg, value) in sortedVars) {
            // Skip nil and false (Lua 5.4 semantics)
            if (value is LuaNil || (value is LuaBoolean && !value.value)) {
                continue
            }

            try {
                closeCallback(reg, value, currentError)
                // If no exception, currentError continues to next handler
            } catch (e: Exception) {
                // Capture exception and update chained error
                currentError =
                    when {
                        e.message != null -> LuaString(e.message!!)
                        else -> LuaString("error in close")
                    }
                lastException = e
            }
        }

        // Re-throw the last exception if any occurred
        lastException?.let { throw it }

        return currentError
    }

    /**
     * Capture the current state of close execution for yield/resume.
     *
     * This creates a snapshot that can be restored later to continue
     * close operations from where they left off.
     */
    fun captureState(
        startReg: Int,
        remainingTbc: List<Pair<Int, LuaValue<*>>>,
        currentVar: Pair<Int, LuaValue<*>>?,
        currentError: LuaValue<*>,
    ): CloseHandlerState =
        CloseHandlerState(
            startReg = startReg,
            pendingTbcList = remainingTbc,
            currentVar = currentVar,
            errorArg = currentError,
        )

    /**
     * Resume close execution from a previously captured state.
     *
     * This is used when a __close metamethod yields - we save the state,
     * and when the coroutine resumes, we continue with the remaining variables.
     *
     * Note: currentVar (if set) represents the variable that was being closed when we yielded.
     * It should already be in pendingTbcList, so we don't need to add it separately.
     */
    fun resumeFromSnapshot(
        snapshot: CloseHandlerState,
        closeCallback: (Int, LuaValue<*>, LuaValue<*>) -> Unit,
    ): LuaValue<*> =
        executeClose(
            startReg = snapshot.startReg,
            tbcVars = snapshot.pendingTbcList,
            initialError = snapshot.errorArg,
            closeCallback = closeCallback,
        )
}

/**
 * State snapshot for CloseHandler to support yield/resume.
 *
 * This captures the execution state when a __close metamethod yields,
 * allowing the close operation to continue after the coroutine resumes.
 */
data class CloseHandlerState(
    /** Register index where close operations start */
    val startReg: Int,
    /** Remaining to-be-closed variables (not yet processed) */
    val pendingTbcList: List<Pair<Int, LuaValue<*>>>,
    /** Current variable being closed (if yielded during its __close) */
    val currentVar: Pair<Int, LuaValue<*>>?,
    /** Current error value to chain to next handler */
    val errorArg: LuaValue<*>,
)
