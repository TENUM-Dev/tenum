package ai.tenum.lua.runtime

import ai.tenum.lua.runtime.LuaTable

/**
 * Mixin interface for types that support metatables.
 * Types that share metatables (boolean, number, string) delegate to their companion object.
 */
interface MetaTable {
    var metatableStore: LuaValue<*>?

    /**
     * Gets or sets the metatable for this value
     * - Tables and userdata have individual metatables
     * - Primitive types (nil, boolean, number, string) share metatables across all instances
     */
    var metatable: LuaValue<*>?
        get() = metatableStore
        set(value) {
            require(value == null || value is LuaTable) { "Metatable must be a table or nil" }
            metatableStore = value
        }
}
