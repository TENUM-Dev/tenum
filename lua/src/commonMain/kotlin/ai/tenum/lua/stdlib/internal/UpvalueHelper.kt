package ai.tenum.lua.stdlib.internal

import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

/**
 * Shared utility for accessing upvalues in compiled functions and native closures.
 * Eliminates duplication between DebugLib.getupvalue, setupvalue, and upvalueid.
 */
object UpvalueHelper {
    /**
     * Result of upvalue access containing validated upvalue information.
     */
    data class UpvalueAccess(
        val name: String,
        val upvalue: Upvalue,
        val actualIndex: Int,
    )

    /**
     * Validate and retrieve upvalue information from a function.
     *
     * @param func The function to access (must be LuaCompiledFunction or LuaNativeFunction with upvalues)
     * @param index 1-based upvalue index (Lua 5.4 convention)
     * @return UpvalueAccess if valid, null otherwise
     */
    fun getUpvalueAccess(
        func: LuaValue<*>?,
        index: Int,
    ): UpvalueAccess? {
        // Convert 1-based user index to 0-based array index
        val actualIndex = index - 1

        // Bounds check must happen before accessing
        if (actualIndex < 0) return null

        when (func) {
            is LuaCompiledFunction -> {
                // Get upvalue info from proto
                val upvalueInfo = func.proto.upvalueInfo

                // Bounds check
                if (actualIndex >= upvalueInfo.size) return null

                // Get upvalue name and reference
                val info = upvalueInfo[actualIndex]
                val upvalue = func.upvalues.getOrNull(actualIndex) ?: return null

                return UpvalueAccess(
                    name = info.name,
                    upvalue = upvalue,
                    actualIndex = actualIndex,
                )
            }
            is LuaNativeFunction -> {
                // Native functions with upvalues (e.g., string.gmatch iterator)
                if (actualIndex >= func.upvalues.size) return null

                val upvalue = func.upvalues[actualIndex]
                return UpvalueAccess(
                    name = "", // C functions always have empty string names (Lua 5.4 spec)
                    upvalue = upvalue,
                    actualIndex = actualIndex,
                )
            }
            else -> return null
        }
    }
}
