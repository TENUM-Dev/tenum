package ai.tenum.lua.vm.errorhandling

import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue

/**
 * Exception thrown when a Lua runtime error occurs.
 *
 * Unlike Kotlin's RuntimeException, this preserves Lua-specific error information:
 * - Line numbers in Lua source code
 * - Source file names (for loaded chunks)
 * - Function call stack with Lua function names
 * - Original error value (for non-string errors)
 *
 * This allows generating proper Lua stack traces instead of Kotlin stack traces.
 *
 * The `message` property (from RuntimeException) contains the full formatted error with stack trace.
 * Use `errorMessage` property to get just the error message without stack trace (for pcall).
 * Use `errorValue` property to get the original Lua value that was thrown (for pcall with non-string errors).
 */
class LuaException(
    private val errorMessageOnly: String,
    val line: Int? = null,
    val source: String? = null,
    val luaStackTrace: List<LuaStackFrame> = emptyList(),
    errorValueOverride: LuaValue<*>? = null,
) : RuntimeException(buildMessage(errorMessageOnly, line, source, luaStackTrace)) {
    /**
     * The original Lua value that was thrown. For string errors, this includes location information.
     * For non-string errors (tables, nil, etc.), this is the original value.
     */
    val errorValue: LuaValue<*> =
        errorValueOverride ?: run {
            // Default: create LuaString with formatted error message (including location)
            LuaString(
                buildString {
                    if (source != null || line != null) {
                        val displaySource = source?.removePrefix("=") ?: "[string]"
                        append(displaySource)
                        if (line != null) {
                            append(":").append(line)
                        }
                        append(": ")
                    }
                    append(errorMessageOnly)
                },
            )
        }

    /**
     * Gets the error message without stack trace (for pcall compatibility).
     * This is the format that Lua 5.4 pcall returns.
     */
    val errorMessage: String
        get() {
            val prefix = formatSourceLocation(source, line)
            return prefix + errorMessageOnly
        }

    companion object {
        /**
         * Formats the source location prefix for error messages.
         * Strips '=' prefix from source names (Lua convention for "literal" sources).
         */
        private fun formatSourceLocation(
            source: String?,
            line: Int?,
        ): String {
            if (source == null && line == null) return ""
            val sb = StringBuilder()
            val displaySource = source?.removePrefix("=") ?: "[string]"
            sb.append(displaySource)
            if (line != null) {
                sb.append(":").append(line)
            }
            sb.append(": ")
            return sb.toString()
        }

        private fun buildMessage(
            message: String,
            line: Int?,
            source: String?,
            stackTrace: List<LuaStackFrame>,
        ): String {
            val sb = StringBuilder()
            sb.append(formatSourceLocation(source, line))
            sb.append(message)

            // Add stack trace if available
            if (stackTrace.isNotEmpty()) {
                sb.append("\nstack traceback:")
                for (frame in stackTrace) {
                    sb.append("\n\t")
                    if (frame.source != null) {
                        // Strip '=' prefix from source names in stack trace too
                        sb.append(frame.source.removePrefix("="))
                    } else {
                        sb.append("[string]")
                    }
                    if (frame.line != null) {
                        sb.append(":").append(frame.line)
                    }

                    // Filter out special function names that shouldn't appear in stack traces
                    // "main" = top-level chunk, "?" = unknown/anonymous, "<function>" = generic placeholder
                    val shouldShowFunctionName =
                        frame.functionName != null &&
                            frame.functionName != "main" &&
                            frame.functionName != "?" &&
                            frame.functionName != "<function>"

                    if (shouldShowFunctionName) {
                        sb.append(": in function '").append(frame.functionName).append("'")
                    }
                    // else: Just show the location without function name part (no trailing colon)
                }
            }

            return sb.toString()
        }
    }
}
