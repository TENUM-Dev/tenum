package ai.tenum.lua.compiler.util

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaCompiledFunction

/**
 * Utility for stripping debug information from compiled Lua functions.
 * Used by string.dump() and luac compiler.
 */
object DebugInfoStripping {
    /**
     * Recursively strips debug information from a Proto and all nested function constants.
     *
     * Stripped fields:
     * - localVars: Set to empty list (local variable names and scopes)
     * - lineEvents: Set to empty list (PC → line mappings)
     * - source: Changed to "=?" (source file name)
     * - upvalueInfo names: Set to empty string except for "_ENV" (needed for environment binding)
     *
     * Preserved fields:
     * - lineDefined, lastLineDefined: Function definition line numbers (for debug.getinfo)
     * - instructions, constants, parameters, upvalue structure
     */
    fun stripDebugInfo(proto: Proto): Proto {
        val strippedConstants =
            proto.constants.map { const ->
                if (const is LuaCompiledFunction) {
                    LuaCompiledFunction(
                        stripDebugInfo(const.proto),
                        const.upvalues,
                    )
                } else {
                    const
                }
            }

        // Strip upvalue names, but preserve "_ENV" which is needed for environment binding
        // (see BasicLibLoading.kt line 392 which searches for "_ENV" by name)
        val strippedUpvalues =
            proto.upvalueInfo.map { upval ->
                val newName = if (upval.name == "_ENV") "_ENV" else ""
                upval.copy(name = newName)
            }

        return proto.copy(
            constants = strippedConstants,
            upvalueInfo = strippedUpvalues,
            localVars = emptyList(),
            lineEvents = emptyList(),
            source = "=?",
            // Preserve lineDefined and lastLineDefined for debug.getinfo
            lineDefined = proto.lineDefined,
            lastLineDefined = proto.lastLineDefined,
        )
    }
}
