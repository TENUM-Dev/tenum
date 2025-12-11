package ai.tenum.lua.vm.execution.opcodes

/**
 * Context for tracking whether the current native function call
 * was invoked via method syntax (`:`) or function syntax (`.`).
 *
 * This is used for proper argument numbering in error messages:
 * - `string.sub('a', {})` → error reports "#2" (table is 2nd arg)
 * - `('a'):sub{}` → error reports "#1" (table is 2nd arg but 1st after self)
 *
 * The SELF opcode sets `ExecutionEnvironment.isMethodCall = true`.
 * The CALL opcode captures this flag and stores it here before calling the function.
 * Native functions can query this to adjust argument indices in error messages.
 *
 * Note: For Kotlin Multiplatform compatibility, this uses a simple mutable property
 * instead of ThreadLocal. In single-threaded environments (JS) and coroutine-based
 * code (JVM with coroutines), this is sufficient.
 */
object MethodCallContext {
    private var isMethodCallFlag: Boolean = false

    /**
     * Check if the current call is a method call (via : syntax).
     * Returns false if not in a call context or if called via . syntax.
     */
    fun get(): Boolean = isMethodCallFlag

    /**
     * Mark that the current call is a method call.
     * Should only be called by CALL opcode implementation.
     */
    fun set(value: Boolean) {
        isMethodCallFlag = value
    }

    /**
     * Clear the method call context.
     * Should be called after the native function returns.
     */
    fun clear() {
        isMethodCallFlag = false
    }
}
