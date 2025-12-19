package ai.tenum.lua.vm

/**
 * Debug sink interface for VM logging and debugging output.
 * Provides a cleaner abstraction than scattered println statements.
 *
 * Benefits:
 * - Centralized debug output control
 * - Easy to enable/disable debug logging
 * - Testable without polluting console output
 * - Can be replaced with different implementations (console, file, no-op)
 */
interface VmDebugSink {
    /**
     * Log a debug message.
     */
    fun debug(message: String)

    /**
     * Log a debug message with lazy evaluation.
     * Message is only constructed if debugging is enabled.
     */
    fun debug(message: () -> String)

    companion object {
        /**
         * No-op debug sink that discards all messages.
         */
        val NOOP =
            object : VmDebugSink {
                override fun debug(message: String) {}

                override fun debug(message: () -> String) {}
            }

        /**
         * Console debug sink that prints to stdout.
         */
        fun console(prefix: String = "[LuaVM]") =
            object : VmDebugSink {
                override fun debug(message: String) {
                    println("$prefix $message")
                }

                override fun debug(message: () -> String) {
                    println("$prefix ${message()}")
                }
            }
    }
}
