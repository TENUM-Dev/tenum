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
    // CPD-OFF
    private fun callAndWrapSuccess(
        func: LuaValue<*>,
        funcArgs: List<LuaValue<*>>,
        callFunction: (LuaValue<*>, List<LuaValue<*>>) -> List<LuaValue<*>>,
    ): List<LuaValue<*>> {
        // CPD-ON
        val results = callFunction(func, funcArgs)
        return buildList {
            add(LuaBoolean.TRUE)
            addAll(results)
        }
    }

    private fun handleProtectedCall(
        func: LuaValue<*>,
        funcArgs: List<LuaValue<*>>,
        callFunction: (LuaValue<*>, List<LuaValue<*>>) -> List<LuaValue<*>>,
    ): List<LuaValue<*>> =
        try {
            callAndWrapSuccess(func, funcArgs, callFunction)
        } catch (e: ai.tenum.lua.runtime.LuaYieldException) {
            throw e
        } catch (e: Exception) {
            val errorResult =
                when (e) {
                    is ai.tenum.lua.vm.errorhandling.LuaException -> e.errorValue
                    is LuaRuntimeError -> e.errorValue
                    else -> {
                        val errorMsg = e.message
                        if (errorMsg.isNullOrEmpty()) LuaNil else LuaString(errorMsg)
                    }
                }
            buildList {
                add(LuaBoolean.FALSE)
                add(errorResult)
            }
        }

    fun registerFunctions(
        registerGlobal: RegisterGlobalCallback,
        callFunction: CallFunctionCallback,
        getCallStack: GetCallStackCallback? = null,
        vm: ai.tenum.lua.vm.LuaVmImpl? = null,
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
            xpcallImpl(callFunction, vm),
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
            handleProtectedCall(func, funcArgs) { f, fArgs -> callFunction(f as LuaFunction, fArgs) }
        }

    private fun xpcallImpl(
        callFunction: CallFunctionCallback,
        vm: ai.tenum.lua.vm.LuaVmImpl?,
    ): LuaNativeFunction =
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
                callAndWrapSuccess(func, funcArgs) { f, fArgs -> callFunction(f as LuaFunction, fArgs) }
            } catch (e: ai.tenum.lua.runtime.LuaYieldException) {
                throw e
            } catch (e: Exception) {
                val errorValue =
                    when (e) {
                        is ai.tenum.lua.vm.errorhandling.LuaException -> e.errorValue
                        is LuaRuntimeError -> e.errorValue
                        else -> LuaString(e.message ?: "")
                    }

                // Use two-phase call if VM is available to capture return values even when __close fails
                if (vm != null) {
                    val result = vm.callFunctionWithCloseHandling(msgh, listOf(errorValue))
                    val handlerResult = result.returnValues.firstOrNull() ?: LuaNil

                    // If handler had a __close exception, try calling handler again (Lua 5.4 behavior)
                    if (result.closeException != null) {
                        val closeError =
                            when (result.closeException) {
                                is ai.tenum.lua.vm.errorhandling.LuaException -> result.closeException.errorValue
                                is LuaRuntimeError -> result.closeException.errorValue
                                else -> LuaString(result.closeException.message ?: "error in error handling")
                            }

                        // Try calling handler again with __close error (limited recursion)
                        try {
                            val retryResult = vm.callFunctionWithCloseHandling(msgh, listOf(closeError))
                            val retryHandlerResult = retryResult.returnValues.firstOrNull() ?: LuaNil

                            // If retry also has __close error, give up and return "error in error handling"
                            if (retryResult.closeException != null) {
                                return@LuaNativeFunction buildList {
                                    add(LuaBoolean.FALSE)
                                    add(LuaString("error in error handling"))
                                }
                            }

                            return@LuaNativeFunction buildList {
                                add(LuaBoolean.FALSE)
                                add(retryHandlerResult)
                            }
                        } catch (_: Exception) {
                            return@LuaNativeFunction buildList {
                                add(LuaBoolean.FALSE)
                                add(LuaString("error in error handling"))
                            }
                        }
                    }

                    // Handler succeeded (or succeeded but __close didn't throw)
                    return@LuaNativeFunction buildList {
                        add(LuaBoolean.FALSE)
                        add(handlerResult)
                    }
                } else {
                    // Fallback to old behavior if no VM reference
                    try {
                        val handlerResults = callFunction(msgh, listOf(errorValue))
                        val handlerResult = handlerResults.firstOrNull() ?: LuaNil
                        buildList {
                            add(LuaBoolean.FALSE)
                            add(handlerResult)
                        }
                    } catch (handlerEx: Exception) {
                        buildList {
                            add(LuaBoolean.FALSE)
                            add(LuaString("error in error handling"))
                        }
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
