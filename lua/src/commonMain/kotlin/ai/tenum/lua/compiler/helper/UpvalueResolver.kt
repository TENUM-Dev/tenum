package ai.tenum.lua.compiler.helper

import ai.tenum.lua.compiler.model.UpvalueInfo

/**
 * Resolves upvalues for a single function.
 *
 * A resolver is created per function and optionally linked to a parent
 * resolver + parent scope, so nested functions can capture locals and
 * upvalues from their parents.
 */
class UpvalueResolver(
    private val parent: UpvalueResolver?,
    private val parentScope: ScopeManager?,
    private val debugEnabled: Boolean = false,
) {
    private val upvalues = mutableListOf<UpvalueInfo>()

    /**
     * Internal helper for conditional debug logging.
     */
    private fun debug(message: String) {
        if (debugEnabled) {
            println("[UPVALUE RESOLVER] $message")
        }
    }

    /**
     * Check if a variable name refers to a const or close variable in the parent scope chain.
     * Returns true if the variable is const/close, false otherwise.
     * This is used to detect compile-time errors when trying to assign to const variables from nested functions.
     */
    fun isConstOrClose(name: String): Boolean {
        // If there's no parent, we can't resolve any further.
        if (parent == null || parentScope == null) {
            return false
        }

        // 1. Check if the name is a local variable in the parent's scope.
        val localInParent = parentScope.findLocal(name)
        if (localInParent != null) {
            return localInParent.isConst || localInParent.isClose
        }

        // 2. If not a local in the parent, ask the parent to check its upvalues.
        return parent.isConstOrClose(name)
    }

    /**
     * All upvalues for this function, in the order they were defined.
     * This list should be used when building the Proto.
     */
    fun getUpvalues(): List<UpvalueInfo> = upvalues

    /**
     * Convenience: current number of upvalues.
     */
    val size: Int
        get() = upvalues.size

    /**
     * Define an upvalue explicitly.
     *
     * Useful for things like top-level `_ENV`, where the VM provides
     * the value instead of a parent function:
     *
     *   resolver.define("_ENV", inStack = false, index = 0)
     */
    fun define(
        name: String,
        inStack: Boolean,
        index: Int,
    ): Int = addUpvalue(name, inStack, index)

    /**
     * Resolve an upvalue name for this function.
     *
     * Returns the index in this function's upvalue list if found,
     * or null if the name cannot be resolved in any parent.
     *
     * Rules (Lua-style):
     *
     * 0. If we already created an upvalue with this name in this function,
     *    just reuse its index (no need to look at parents again).
     *
     * 1. If there is no parent function (no parent resolver and/or
     *    no parent scope), we *only* support explicitly defined upvalues
     *    (e.g. `_ENV` via [define]). No new captures are created here.
     *
     * 2. Otherwise:
     *    - If there is a local in the *parent function's* scope with that
     *      name, we create an upvalue that points to the parent's stack
     *      (`inStack = true`, `index = parent register`).
     *
     *    - Else, if the parent resolver can resolve it as an upvalue,
     *      we create an upvalue that points to the parent's upvalue
     *      (`inStack = false`, `index = parent upvalue index`).
     */
    fun resolve(name: String): Int? {
        // This function is called when a child function needs to resolve a variable
        // that is not in its own local scope.

        debug("resolve('$name') called on ${this.hashCode()}")

        // 0. Have we already resolved this upvalue? If so, reuse it.
        val existing = upvalues.indexOfFirst { it.name == name }
        if (existing != -1) {
            debug("  -> already resolved at upvalue[$existing]")
            return existing
        }

        // If there's no parent, we can't resolve any further.
        if (parent == null || parentScope == null) {
            debug("  -> no parent, cannot resolve")
            return null
        }

        debug("  -> checking parentScope ${parentScope.hashCode()}")

        // 1. Check if the name is a local variable in the parent's scope.
        // If so, this function captures it directly from the parent's stack.
        val localInParent = parentScope.findLocal(name)
        if (localInParent != null) {
            debug("  -> found as local in parent at R${localInParent.register}, capturing as inStack=true")
            localInParent.isCaptured = true
            // The child function creates an upvalue pointing to the parent's stack register.
            return addUpvalue(name, inStack = true, index = localInParent.register)
        }

        debug("  -> not a local in parent, recursing to parent resolver ${parent.hashCode()}")

        // 2. If not a local in the parent, ask the parent to resolve it as an upvalue.
        // This continues the recursive search up the chain.
        val upvalueInParentIndex = parent.resolve(name)
        if (upvalueInParentIndex != null) {
            debug("  -> parent resolved it at upvalue[$upvalueInParentIndex], capturing as inStack=false")
            // The parent found it as one of its own upvalues.
            // This function creates an upvalue pointing to the parent's upvalue slot.
            return addUpvalue(name, inStack = false, index = upvalueInParentIndex)
        }

        // 3. Cannot be resolved.
        debug("'$name' not found in parent scope or upvalues")
        return null
    }

    /**
     * This function is now primarily a helper for the recursive `resolve` chain.
     * It is kept for cases where we might need to initiate a capture from outside,
     * but the main logic flows through `resolve`.
     */
    fun captureFromChild(name: String): Int? {
        // A child is asking this function to capture a variable.
        // This is the old `resolve` logic, now correctly placed.

        // 1. Is the requested name a local variable in *this* function's scope?
        val local = parentScope?.findLocal(name)
        if (local != null) {
            local.isCaptured = true
            return addUpvalue(name, true, local.register)
        }

        // 2. If not a local, is it already an upvalue in this function?
        val existing = getOwnUpvalueIndex(name)
        if (existing != null) {
            return existing
        }

        // 3. If it's neither, ask our own parent to capture it for us.
        val parentIndex = parent?.captureFromChild(name)
        if (parentIndex != null) {
            return addUpvalue(name, false, parentIndex)
        }

        return null
    }

    private fun getOwnUpvalueIndex(name: String): Int? = upvalues.indexOfFirst { it.name == name }.takeIf { it >= 0 }

    /**
     * Create a child resolver for a nested function, linked to this resolver
     * and the *current function's* ScopeManager.
     *
     * IMPORTANT: [parentScopeManager] must be the ScopeManager of the
     * enclosing function (the one that owns this resolver), not the child's.
     */
    fun createChild(parentScopeManager: ScopeManager): UpvalueResolver {
        val child = UpvalueResolver(this, parentScopeManager, this.debugEnabled)
        return child
    }

    // ─────────────────────────────────────────────
    //  INTERNAL HELPERS
    // ─────────────────────────────────────────────

    /**
     * Add an upvalue or return an existing index if an identical one
     * already exists (same name, inStack flag, and index).
     */
    private fun addUpvalue(
        name: String,
        inStack: Boolean,
        index: Int,
    ): Int {
        val existing =
            upvalues.indexOfFirst {
                it.name == name && it.inStack == inStack && it.index == index
            }
        if (existing != -1) return existing

        upvalues.add(UpvalueInfo(name, inStack, index))
        return upvalues.size - 1
    }

    fun hasNoParent(): Boolean = parent == null || parentScope == null
}
