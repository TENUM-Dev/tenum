package ai.tenum.lua.stdlib.internal

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.errorhandling.LuaRuntimeError
import ai.tenum.lua.vm.library.CallFunctionCallback
import ai.tenum.lua.vm.library.GetCallStackCallback
import ai.tenum.lua.vm.library.RegisterGlobalCallback

/**
 * Error handling functions for BasicLib
 * Implements: assert, error, pcall, xpcall
 */
internal object BasicLibErrorHandling {
    fun registerFunctions(
        registerGlobal: RegisterGlobalCallback,
        callFunction: CallFunctionCallback,
        getCallStack: GetCallStackCallback? = null,
    ) {
        // assert(v [, message]) - raises an error if v is false/nil
        registerGlobal(
            "assert",
            LuaNativeFunction("assert") { args ->
                if (args.isEmpty()) {
                    throw RuntimeException("bad argument #1 to 'assert' (value expected)")
                }
                val value = args[0]
                val isTruthy =
                    when (value) {
                        is LuaNil -> false
                        is LuaBoolean -> value.value
                        else -> true
                    }
                if (!isTruthy) {
                    // Get the error value (message or any other type)
                    val errorValue = if (args.size > 1) args[1] else LuaString("assertion failed!")

                    // Convert errorValue to string for the message property
                    val message =
                        when (errorValue) {
                            is LuaString -> errorValue.value
                            is LuaNil -> "assertion failed!"
                            else -> errorValue.toString()
                        }

                    // Throw LuaRuntimeError to preserve the original error value
                    throw LuaRuntimeError(
                        message = message,
                        errorValue = errorValue,
                        callStack = getCallStack?.invoke() ?: emptyList(),
                        level = 1,
                    )
                }
                buildList {
                    add(value)
                    for (i in 1 until args.size) {
                        add(args[i])
                    }
                }
            },
        )

        // error(message [, level]) - raises an error
        registerGlobal(
            "error",
            LuaNativeFunction("error") { args ->
                val errorValue = args.firstOrNull() ?: LuaNil
                val message =
                    when (errorValue) {
                        is LuaString -> errorValue.value
                        is LuaNil -> ""
                        else -> errorValue.toString()
                    }
                val level = (args.getOrNull(1) as? LuaNumber)?.toDouble()?.toInt() ?: 1

                val callStack = getCallStack?.invoke() ?: emptyList()

                throw LuaRuntimeError(
                    message = message,
                    errorValue = errorValue,
                    callStack = callStack,
                    level = level,
                )
            },
        )

        // pcall(f [, arg1, ...]) - protected call
        registerGlobal(
            "pcall",
            pcallImpl(callFunction),
        )

        // xpcall(f, msgh [, arg1, ...]) - protected call with message handler
        registerGlobal(
            "xpcall",
            xpcallImpl(callFunction),
        )
    }

    private fun pcallImpl(callFunction: CallFunctionCallback): LuaNativeFunction =
        LuaNativeFunction("pcall") { args ->
            if (args.isEmpty()) {
                throw RuntimeException("bad argument #1 to 'pcall' (value expected)")
            }
            val func = args[0]

            // Check if func is callable - if not, return error result (don't throw)
            if (func !is LuaFunction) {
                val typeName =
                    when (func) {
                        is LuaNil -> "nil"
                        else -> func::class.simpleName?.lowercase() ?: "unknown"
                    }
                return@LuaNativeFunction listOf(
                    LuaBoolean.FALSE,
                    LuaString("attempt to call a $typeName value"),
                )
            }

            val funcArgs = args.drop(1)

            try {
                val results = callFunction(func, funcArgs)
                buildList {
                    add(LuaBoolean.TRUE)
                    addAll(results)
                }
            } catch (e: Exception) {
                // For LuaException or LuaRuntimeError, return the original errorValue (preserves tables, nil, etc.)
                // This matches Lua 5.4 behavior where error() can pass any value through pcall
                val errorResult =
                    when (e) {
                        is ai.tenum.lua.vm.errorhandling.LuaException -> e.errorValue
                        is LuaRuntimeError -> e.errorValue
                        else -> {
                            // For other exceptions, convert to string with location info
                            val errorMsg = e.message
                            if (errorMsg.isNullOrEmpty()) LuaNil else LuaString(errorMsg)
                        }
                    }
                buildList {
                    add(LuaBoolean.FALSE)
                    add(errorResult)
                }
            }
        }

    private fun xpcallImpl(callFunction: CallFunctionCallback): LuaNativeFunction =
        LuaNativeFunction("xpcall") { args ->
            if (args.size < 2) {
                throw RuntimeException("bad argument #2 to 'xpcall' (function expected)")
            }
            val func = args[0]
            val msgh = args[1]
            if (func !is LuaFunction) {
                throw RuntimeException("attempt to call a ${func::class.simpleName} value")
            }
            if (msgh !is LuaFunction) {
                throw RuntimeException("bad argument #2 to 'xpcall' (function expected)")
            }
            val funcArgs = args.drop(2)

            try {
                val results = callFunction(func, funcArgs)
                buildList {
                    add(LuaBoolean.TRUE)
                    addAll(results)
                }
            } catch (e: Exception) {
                // For LuaException or LuaRuntimeError, use errorValue to preserve non-string error objects
                // The message handler should receive the original error value (table, string, etc.)
                val errorValue =
                    when (e) {
                        is ai.tenum.lua.vm.errorhandling.LuaException -> e.errorValue
                        is LuaRuntimeError -> e.errorValue
                        else -> LuaString(e.message ?: "")
                    }
                try {
                    val handlerResults = callFunction(msgh, listOf(errorValue))
                    val handlerResult = handlerResults.firstOrNull() ?: LuaNil
                    buildList {
                        add(LuaBoolean.FALSE)
                        add(handlerResult)
                    }
                } catch (handlerEx: Exception) {
                    // When error handler itself fails (including stack overflow), return Lua 5.4's standard message
                    buildList {
                        add(LuaBoolean.FALSE)
                        add(LuaString("error in error handling"))
                    }
                }
            }
        }
}

/**
 * Helper function to generate proper "bad argument" error messages.
 * Formats errors as: "bad argument #N to 'function' (expected, got actual)"
 */
internal fun argError(
    functionName: String,
    argIndex: Int,
    expected: String,
    actual: LuaValue<*>?,
): Nothing {
    val actualType = actual?.type()?.name?.lowercase() ?: "nil"
    val message = "bad argument #$argIndex to '$functionName' ($expected expected, got $actualType)"
    throw RuntimeException(message)
}
