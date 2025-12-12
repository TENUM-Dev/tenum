package ai.tenum.lua.compiler.helper

import ai.tenum.lua.compiler.model.LocalLifetime

/**
 * Tracks lexical scopes, locals and break targets for a single function.
 *
 * This class is intentionally pure state/logic – it does **not** know about
 * bytecode or instructions. The bytecode layer decides how to emit CLOSE/JMP
 * based on the data returned from ScopeManager.
 */
class ScopeManager(
    val debugEnabled: Boolean = false,
) {
    /**
     * Internal helper for conditional debug logging.
     */
    private fun debug(message: String) {
        if (debugEnabled) {
            println("[SCOPE][$this] $message")
        }
    }

    /**
     * A single local variable in this function.
     *
     * `startPc` is set at variable declaration; `endPc` is filled later
     * when the scope closes and the final Proto is built.
     */
    data class LocalSymbol(
        val name: String,
        val register: Int,
        val scopeLevel: Int,
        val startPc: Int,
        val isConst: Boolean = false,
        val isClose: Boolean = false,
        var isCaptured: Boolean = false,
        var endPc: Int = -1,
    ) {
        /** Get the lifetime of this local variable */
        fun getLifetime(): LocalLifetime = LocalLifetime.of(startPc, endPc)

        /** Check if this variable is currently active (endPc not yet set) */
        val isActive: Boolean get() = endPc == -1
    }

    /**
     * Information about what to do when leaving a scope.
     *
     * - `minCloseRegister`: the lowest register that needs a CLOSE (for <close> vars),
     *   or `null` if nothing needs closing at this level.
     * - `minCapturedRegister`: the lowest register that is captured by a closure,
     *   or `null` if no captured variables in this scope.
     * - `removedLocals`: all locals that went out of scope.
     */
    data class ScopeExitInfo(
        val minCloseRegister: Int?,
        val minCapturedRegister: Int?,
        val removedLocals: List<LocalSymbol>,
    )

    // ------------------------------------------------------------------------
    // Scope & locals
    // ------------------------------------------------------------------------

    private val _locals = mutableListOf<LocalSymbol>()
    val locals: List<LocalSymbol> get() = _locals.filter { it.isActive }
    val allLocals: List<LocalSymbol> get() = _locals

    // Track active locals count (those not yet ended) for register allocation
    private var activeLocalsCount: Int = 0

    var currentScopeLevel: Int = 0

    // Stable scope identity tracking for goto/label resolution
    private var scopeIdCounter: Int = 0
    private val scopeStack: MutableList<Int> = mutableListOf(0) // Function scope is always 0
    private val repeatUntilScopes: MutableSet<Int> = mutableSetOf() // Track which scopes are repeat-until blocks

    /**
     * Get the current scope ancestry as a list of scope IDs.
     * This represents the lexical chain from function root to current scope.
     */
    fun getCurrentScopeStack(): List<Int> = scopeStack.toList()

    /**
     * Check if the current scope is inside a repeat-until block.
     */
    fun isInRepeatUntilBlock(): Boolean = scopeStack.any { it in repeatUntilScopes }

    /**
     * Mark the current scope as a repeat-until block.
     * This affects validation of gotos that jump over locals in this scope.
     */
    fun markCurrentScopeAsRepeatUntil() {
        val currentScopeId = scopeStack.lastOrNull() ?: 0
        repeatUntilScopes.add(currentScopeId)
    }

    /**
     * Start a new lexical block scope.
     *
     * Returns a snapshot index you must pass back into [endScope] so the
     * manager knows which locals to drop.
     */
    fun beginScope(): Int {
        val snapshot = activeLocalsCount
        val newLevel = currentScopeLevel + 1
        scopeIdCounter++
        scopeStack.add(scopeIdCounter)
        debug("beginScope: level $currentScopeLevel -> $newLevel, scopeId=$scopeIdCounter, localsBefore=$snapshot")
        currentScopeLevel = newLevel
        return snapshot
    }

    /**
     * End the current scope, dropping locals created since [snapshotLocalSize].
     *
     * You typically do:
     *
     *   val snapshot = scope.beginScope()
     *   ... compile block ...
     *   val exitInfo = scope.endScope(snapshot, currentPc)
     *   exitInfo.minCloseRegister?.let { emitClose(it) }
     */
    fun endScope(
        snapshotLocalSize: Int,
        endPc: Int,
    ): ScopeExitInfo {
        debug(
            "endScope: level=$currentScopeLevel, snapshotLocalSize=$snapshotLocalSize, totalLocals=${_locals.size}, activeLocals=$activeLocalsCount",
        )

        // Collect all locals that belong to this (innermost) scope.
        val removed = mutableListOf<LocalSymbol>()
        var minCloseReg: Int? = null
        var minCapturedReg: Int? = null

        // Mark locals as ended starting from the snapshot point
        // We need to end the most recently declared locals first
        var countToRemove = activeLocalsCount - snapshotLocalSize

        // Iterate backwards through the actual locals list, only considering active ones
        for (idx in _locals.size - 1 downTo 0) {
            if (countToRemove <= 0) break

            val local = _locals[idx]
            if (local.isActive) { // Only process active locals
                // fill debug endPc
                local.endPc = endPc
                removed.add(local)
                countToRemove--

                if (local.isClose) {
                    minCloseReg =
                        if (minCloseReg == null) {
                            local.register
                        } else {
                            minOf(minCloseReg!!, local.register)
                        }
                }

                // Also track captured variables (for upvalue closing)
                if (local.isCaptured && !local.isClose) {
                    minCapturedReg =
                        if (minCapturedReg == null) {
                            local.register
                        } else {
                            minOf(minCapturedReg!!, local.register)
                        }
                }
            }
        }

        // Update active locals count
        activeLocalsCount = snapshotLocalSize
        val exitedScopeId = scopeStack.removeLastOrNull()
        currentScopeLevel--
        debug("endScope: exitedScopeId=$exitedScopeId, new level=$currentScopeLevel, removedCount=${removed.size}")
        // we added removed in reverse order; reverse to get decl order
        removed.reverse()

        debug(
            "endScope: newLevel=$currentScopeLevel, " +
                "removedLocals=${removed.map { it.name to it.register }}, " +
                "minCloseRegister=$minCloseReg, " +
                "minCapturedRegister=$minCapturedReg",
        )

        return ScopeExitInfo(
            minCloseRegister = minCloseReg,
            minCapturedRegister = minCapturedReg,
            removedLocals = removed,
        )
    }

    /**
     * Declare a new local in the current scope.
     */
    fun declareLocal(
        name: String,
        register: Int,
        startPc: Int,
        isConst: Boolean = false,
        isClose: Boolean = false,
    ): LocalSymbol {
        val local =
            LocalSymbol(
                name = name,
                register = register,
                scopeLevel = currentScopeLevel,
                startPc = startPc,
                isConst = isConst,
                isClose = isClose,
            )
        _locals.add(local)
        activeLocalsCount++
        debug(
            "declareLocal: '$name' @R$register (level=$currentScopeLevel, " +
                "isConst=$isConst, isClose=$isClose, activeCount=$activeLocalsCount)",
        )
        return local
    }

    /**
     * Find the most recent local with the given name (standard Lua shadowing).
     */
    fun findLocal(name: String): LocalSymbol? {
        val local = _locals.lastOrNull { it.name == name && it.isActive }
        if (debugEnabled && local != null) {
            debug("findLocal: '$name' -> R${local.register} (level=${local.scopeLevel})")
        }
        return local
    }

    // ------------------------------------------------------------------------
    // Break handling for loops
    // ------------------------------------------------------------------------

    /**
     * Stack of "break lists" for nested loops.
     *
     * Each list holds the instruction indices of JMPs that should be patched
     * to the loop exit when the loop finishes.
     * Also tracks the scope level of the loop for proper upvalue closing.
     */
    data class LoopInfo(
        val breakJumps: MutableList<Int> = mutableListOf(),
        val scopeLevel: Int,
    )

    private val breakStack: MutableList<LoopInfo> = mutableListOf()

    /** Call when you enter a loop (while, repeat, for, for-in). */
    fun beginLoop() {
        breakStack.add(LoopInfo(scopeLevel = currentScopeLevel))
        debug("beginLoop: depth=${breakStack.size}")
    }

    /**
     * Record a 'break' jump index for the current loop.
     *
     * The index should be the PC where you emitted `JMP 0, 0, 0` for `break`.
     */
    fun addBreakJump(jumpIndex: Int) {
        if (breakStack.isEmpty()) {
            debug("addBreakJump: WARNING: break outside loop at pc=$jumpIndex")
            return
        }
        breakStack.last().breakJumps.add(jumpIndex)
        debug("addBreakJump: pc=$jumpIndex, loopDepth=${breakStack.size}")
    }

    /**
     * Get the loop scope level for the current innermost loop.
     * Returns null if not inside a loop.
     */
    fun getCurrentLoopScopeLevel(): Int? = breakStack.lastOrNull()?.scopeLevel

    /**
     * Call when leaving a loop.
     * Returns all jump indices for `break` statements in this loop body so
     * the caller can patch them to the correct target PC.
     */
    fun endLoop(): List<Int> {
        if (breakStack.isEmpty()) {
            debug("endLoop: WARNING: endLoop called with empty breakStack")
            return emptyList()
        }
        val loopInfo = breakStack.removeAt(breakStack.lastIndex)
        val breaks = loopInfo.breakJumps
        debug("endLoop: patchedBreaks=${breaks.size}, remainingDepth=${breakStack.size}")
        return breaks
    }

    /**
     * Count active locals (those that haven't gone out of scope yet).
     * Used for goto/label validation.
     */
    fun activeLocalCount(): Int = activeLocalsCount
}
