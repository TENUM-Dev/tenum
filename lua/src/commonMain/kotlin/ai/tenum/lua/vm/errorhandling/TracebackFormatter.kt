package ai.tenum.lua.vm.errorhandling

import ai.tenum.lua.vm.CallFrame
import ai.tenum.lua.vm.execution.FunctionNameSource
import ai.tenum.lua.vm.execution.StackView

/**
 * Shared utility for formatting Lua stack tracebacks.
 * Eliminates duplication between DebugLib.traceback and LuaRuntimeError.getTraceback.
 */
object TracebackFormatter {
    /**
     * Format a stack traceback from a StackView.
     *
     * @param stackView The stack view to format
     * @param messagePrefix Optional message to prepend before "stack traceback:"
     * @param startIndex Index to start from (0-based, in traceback order - most recent first)
     * @return Formatted traceback string
     */
    fun formatTraceback(
        stackView: StackView,
        messagePrefix: String = "",
        startIndex: Int = 0,
    ): String = formatTraceback(stackView.forTraceback(), messagePrefix, startIndex)

    /**
     * Format a stack traceback from a list of CallFrames.
     *
     * @param callStack The call stack frames (should be most-recent-first for traceback display)
     * @param messagePrefix Optional message to prepend before "stack traceback:"
     * @param startIndex Index in callStack to start from (for level parameter in debug.traceback)
     * @param useUpvalueDescriptor If true, show "in upvalue 'name'" instead of "in function 'name'" (used for saved coroutine stacks)
     * @return Formatted traceback string
     */
    fun formatTraceback(
        callStack: List<CallFrame>,
        messagePrefix: String = "",
        startIndex: Int = 0,
        useUpvalueDescriptor: Boolean = false,
        isInCoroutine: Boolean = false,
    ): String =
        buildString {
            if (messagePrefix.isNotEmpty()) {
                appendLine(messagePrefix)
            }
            appendLine("stack traceback:")

            val effectiveStart = startIndex.coerceIn(0, callStack.size)
            val framesToShow = callStack.size - effectiveStart

            // Lua 5.4.8 truncation: Show first 10 + last 11 frames with "...\t(skipping N levels)" marker
            // Only truncate if there are more than 22 frames to show
            val shouldTruncate = framesToShow > 22
            val firstFrameCount = if (shouldTruncate) 10 else framesToShow
            val lastFrameCount = if (shouldTruncate) 11 else 0
            val skippedFrameCount = if (shouldTruncate) framesToShow - firstFrameCount - lastFrameCount else 0

            // Format first frames (or all frames if no truncation)
            var i = effectiveStart
            var shownFrames = 0
            while (i < callStack.size && shownFrames < firstFrameCount) {
                i =
                    formatFrameOrTailCallSequence(
                        callStack = callStack,
                        startIndex = i,
                        useUpvalueDescriptor = useUpvalueDescriptor,
                        addNewlineAfter = shouldTruncate || i < callStack.size - 1,
                    )
                shownFrames++
            }

            // Insert truncation marker if needed
            if (shouldTruncate) {
                append("\t...\t(skipping $skippedFrameCount levels)")
                appendLine()

                // Skip to last frames
                i = callStack.size - lastFrameCount
            }

            // Format last frames (or remaining frames if no truncation)
            while (i < callStack.size) {
                i =
                    formatFrameOrTailCallSequence(
                        callStack = callStack,
                        startIndex = i,
                        useUpvalueDescriptor = useUpvalueDescriptor,
                        addNewlineAfter = i < callStack.size - 1,
                    )
            }

            // Append final [C]: in ? frame only if NOT in a coroutine
            // Lua 5.4.8: main chunk tracebacks include this, coroutine tracebacks don't
            if (!isInCoroutine) {
                appendLine()
                append("\t[C]: in ?")
            }
        }

    /**
     * Format a single frame or tail call sequence, returns the next index to process.
     */
    private fun StringBuilder.formatFrameOrTailCallSequence(
        callStack: List<CallFrame>,
        startIndex: Int,
        useUpvalueDescriptor: Boolean,
        addNewlineAfter: Boolean,
    ): Int {
        val frame = callStack[startIndex]
        var nextIndex = startIndex

        if (frame.isTailCall) {
            // Find the end of the tail call sequence
            val tailStart = startIndex
            while (nextIndex < callStack.size && callStack[nextIndex].isTailCall) {
                nextIndex++
            }

            // Show the first tail-called frame
            formatFrame(callStack[tailStart], useUpvalueDescriptor)

            // Insert tail call marker if there was more than one tail call
            val tailCallCount = nextIndex - tailStart
            if (tailCallCount > 1) {
                appendLine()
                append("\t(...tail calls...)")
            }

            // Add newline after this frame group if requested
            if (addNewlineAfter) {
                appendLine()
            }
        } else {
            // Regular frame (not tail called)
            formatFrame(frame, useUpvalueDescriptor)

            // Add newline if requested
            if (addNewlineAfter) {
                appendLine()
            }
            nextIndex++
        }

        return nextIndex
    }

    /**
     * Format a single call frame.
     */
    private fun StringBuilder.formatFrame(
        frame: CallFrame,
        useUpvalueDescriptor: Boolean,
    ) {
        append("\t")

        if (frame.proto != null) {
            // Lua function
            val source = frame.proto.source
            val line = frame.getCurrentLine()

            // Check if this is a hook function call
            val isHook = frame.inferredFunctionName?.source == FunctionNameSource.Hook

            if (isHook) {
                // Hooks are displayed as "in hook '?'" regardless of actual function name
                if (line >= 0) {
                    append("$source:$line: in hook '?'")
                } else {
                    append("$source: in hook '?'")
                }
            } else if (useUpvalueDescriptor) {
                // For saved coroutine stacks (Lua 5.4 behavior):
                // - Named functions: show "in upvalue 'name'"
                // - Anonymous functions: show "in function <source:linedefined>"
                // Use function.proto.name (not frame.proto.name) to get the correct name
                // because frame.proto might be a different instance or not have the name set
                val func = frame.function as? ai.tenum.lua.runtime.LuaCompiledFunction
                val name = func?.proto?.name ?: frame.proto.name
                val isAnonymous = name.isEmpty() || name == "main" || name == "?" || name == "<function>"

                if (isAnonymous) {
                    // Anonymous function - show "in function <source:linedefined>"
                    val defLine = frame.proto.lineDefined
                    if (line >= 0) {
                        append("$source:$line: in function <$source:$defLine>")
                    } else {
                        append("$source: in function <$source:$defLine>")
                    }
                } else {
                    // Named function - show "in upvalue 'name'"
                    if (line >= 0) {
                        append("$source:$line: in upvalue '$name'")
                    } else {
                        append("$source: in upvalue '$name'")
                    }
                }
            } else if (frame.isCloseMetamethod) {
                // __close metamethod frames should be displayed as "in metamethod 'close'"
                // This matches Lua 5.4.8 behavior (locals.lua line 493)
                if (line >= 0) {
                    append("$source:$line: in metamethod 'close'")
                } else {
                    append("$source: in metamethod 'close'")
                }
            } else {
                val name = frame.proto.name
                // Filter out special function names that shouldn't appear in stack traces
                // "main" = top-level chunk, "?" = unknown/anonymous, "<function>" = generic placeholder
                val shouldShowFunctionName = name.isNotEmpty() && name != "main" && name != "?" && name != "<function>"

                if (line >= 0) {
                    if (shouldShowFunctionName) {
                        append("$source:$line: in function '$name'")
                    } else {
                        append("$source:$line")
                    }
                } else {
                    if (shouldShowFunctionName) {
                        append("$source: in function '$name'")
                    } else {
                        append("$source")
                    }
                }
            }
        } else if (frame.isNative) {
            // Native function
            // First try to use the native function's name property if available
            val nativeFunc = frame.function as? ai.tenum.lua.runtime.LuaNativeFunction
            val nativeFuncName = nativeFunc?.name

            val functionName =
                when {
                    // Prefer explicit native function name if meaningful
                    nativeFuncName != null && nativeFuncName.isNotEmpty() && nativeFuncName != "native" -> nativeFuncName
                    // Fall back to inferred name
                    frame.inferredFunctionName?.name != null &&
                        frame.inferredFunctionName.name.isNotEmpty() &&
                        frame.inferredFunctionName.name != "?" -> frame.inferredFunctionName.name
                    else -> null
                }

            if (functionName != null) {
                append("[C]: in function '$functionName'")
            } else {
                append("[C]: in function '?'")
            }
        } else {
            // Unknown
            append("???: in function '?'")
        }
    }
}
