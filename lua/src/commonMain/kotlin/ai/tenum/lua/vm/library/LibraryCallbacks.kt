package ai.tenum.lua.vm.library

import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaValue

/**
 * Callback to register a global variable or function.
 * @param name The name of the global
 * @param value The value to register
 */
typealias RegisterGlobalCallback = (name: String, value: LuaValue<*>) -> Unit

/**
 * Callback to retrieve a global variable.
 * @param name The name of the global
 * @return The value, or null if not found
 */
typealias GetGlobalCallback = (name: String) -> LuaValue<*>?

/**
 * Callback to get a metamethod from a value.
 * @param value The value to check
 * @param methodName The metamethod name (e.g., "__index", "__add")
 * @return The metamethod value, or null if not found
 */
typealias GetMetamethodCallback = (value: LuaValue<*>, methodName: String) -> LuaValue<*>?

/**
 * Callback to call a Lua function.
 * @param function The function to call
 * @param args The arguments to pass
 * @return The return values from the function
 */
typealias CallFunctionCallback = (function: LuaFunction, args: List<LuaValue<*>>) -> List<LuaValue<*>>

/**
 * Callback to execute a Lua chunk.
 * @param chunk The source code to execute
 * @param chunkName The name for error messages
 * @return The return value from the chunk
 */
typealias ExecuteChunkCallback = (chunk: String, chunkName: String) -> LuaValue<*>

/**
 * Callback to get the current call stack (for error handling).
 * @return List of stack frames with location information
 */
typealias GetCallStackCallback = () -> List<ai.tenum.lua.vm.CallFrame>
