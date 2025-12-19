package ai.tenum.lua.vm

/**
 * Encapsulates yield/resume context state for coroutine operations.
 *
 * This groups together the 3 yield-related member variables from LuaVmImpl:
 * - pendingYieldTargetReg: Target register for yield results
 * - pendingYieldEncodedCount: Expected result count
 * - pendingYieldStayOnSamePc: Whether to stay on same PC (for close yields)
 *
 * Benefits:
 * - Clear lifecycle management (setYieldResumeContext/clearYieldResumeContext)
 * - Type-safe hasActiveYield() instead of null checks
 * - Snapshot/restore for nested yields
 * - Reduced parameter passing
 */
class CoroutineYieldContext {
    /**
     * Target register for yield results.
     * When a coroutine yields, results are placed starting at this register.
     */
    var pendingYieldTargetReg: Int? = null
        private set

    /**
     * Expected number of results from yield.
     * Encoded count from CALL instruction (C field).
     */
    var pendingYieldEncodedCount: Int? = null
        private set

    /**
     * Whether to stay on same PC when resuming.
     * True for yields during __close (need to re-execute same instruction).
     * False for normal yields (advance to next instruction).
     */
    var pendingYieldStayOnSamePc: Boolean = false
        private set

    /**
     * Sets all yield resume context fields at once.
     */
    fun setYieldResumeContext(
        targetReg: Int?,
        encodedCount: Int?,
        stayOnSamePc: Boolean,
    ) {
        pendingYieldTargetReg = targetReg
        pendingYieldEncodedCount = encodedCount
        pendingYieldStayOnSamePc = stayOnSamePc
    }

    /**
     * Sets only the target register.
     */
    fun setTargetReg(targetReg: Int?) {
        pendingYieldTargetReg = targetReg
    }

    /**
     * Sets only the encoded count.
     */
    fun setEncodedCount(encodedCount: Int?) {
        pendingYieldEncodedCount = encodedCount
    }

    /**
     * Clears all yield resume context.
     */
    fun clearYieldResumeContext() {
        pendingYieldTargetReg = null
        pendingYieldEncodedCount = null
        pendingYieldStayOnSamePc = false
    }

    /**
     * Checks if there is an active yield context.
     */
    fun hasActiveYield(): Boolean = pendingYieldTargetReg != null || pendingYieldEncodedCount != null

    /**
     * Creates a snapshot of current yield context.
     */
    fun snapshot(): CoroutineYieldContextSnapshot =
        CoroutineYieldContextSnapshot(
            pendingYieldTargetReg = pendingYieldTargetReg,
            pendingYieldEncodedCount = pendingYieldEncodedCount,
            pendingYieldStayOnSamePc = pendingYieldStayOnSamePc,
        )

    /**
     * Restores yield context from a snapshot.
     */
    fun restore(snapshot: CoroutineYieldContextSnapshot) {
        pendingYieldTargetReg = snapshot.pendingYieldTargetReg
        pendingYieldEncodedCount = snapshot.pendingYieldEncodedCount
        pendingYieldStayOnSamePc = snapshot.pendingYieldStayOnSamePc
    }
}

/**
 * Immutable snapshot of yield context state.
 */
data class CoroutineYieldContextSnapshot(
    val pendingYieldTargetReg: Int?,
    val pendingYieldEncodedCount: Int?,
    val pendingYieldStayOnSamePc: Boolean,
)
