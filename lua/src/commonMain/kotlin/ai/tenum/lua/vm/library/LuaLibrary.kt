package ai.tenum.lua.vm.library

import ai.tenum.lua.vm.library.LuaLibraryContext

/**
 * Interface for Lua standard library modules.
 * Each library can register global functions and/or library tables.
 */
interface LuaLibrary {
    /**
     * The name of the library (e.g., "string", "math", "table")
     * For basic global functions, use null or empty string
     */
    val name: String?

    /**
     * Register all library functions into the global environment.
     *
     * @param context Library context with filesystem, execution, and module management
     */
    fun register(context: LuaLibraryContext)
}
