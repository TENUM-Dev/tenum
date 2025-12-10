package ai.tenum.lua.vm.library

import ai.tenum.lua.stdlib.BasicLib
import ai.tenum.lua.stdlib.Bit32Lib
import ai.tenum.lua.stdlib.CoroutineLib
import ai.tenum.lua.stdlib.IOLib
import ai.tenum.lua.stdlib.MathLib
import ai.tenum.lua.stdlib.OSLib
import ai.tenum.lua.stdlib.TableLib
import ai.tenum.lua.stdlib.Utf8Lib
import ai.tenum.lua.stdlib.debug.DebugLib
import ai.tenum.lua.stdlib.string.StringLib
import ai.tenum.lua.vm.debug.DebugTracer

/**
 * Registry for Lua standard libraries.
 * Manages registration and initialization of all stdlib modules.
 */
class LibraryRegistry {
    private val libraries = mutableListOf<LuaLibrary>()

    /**
     * Register a library to be loaded when initializeAll is called
     */
    fun register(library: LuaLibrary) {
        libraries.add(library)
    }

    /**
     * Initialize all registered libraries with full context
     *
     * @param context Library context with all necessary callbacks
     */
    fun initializeAll(context: LuaLibraryContext) {
        for (library in libraries) {
            library.register(context)
        }
    }

    /**
     * Get all registered libraries
     */
    fun getLibraries(): List<LuaLibrary> = libraries.toList()

    companion object {
        /**
         * Create a default registry with all standard libraries
         */
        fun createDefault(debugTracer: DebugTracer): LibraryRegistry {
            val registry = LibraryRegistry()

            // Register core libraries
            registry.register(BasicLib(debugTracer))
            registry.register(StringLib())
            registry.register(TableLib())
            registry.register(MathLib())
            registry.register(DebugLib())
            registry.register(Utf8Lib())
            registry.register(CoroutineLib())
            registry.register(IOLib())
            registry.register(OSLib())
            // bit32 is a compatibility library used by some test suites
            registry.register(Bit32Lib())

            return registry
        }
    }
}
