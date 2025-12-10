package ai.tenum.lua.runtime

import ai.tenum.lua.runtime.table.Storage
import ai.tenum.lua.runtime.table.WeakMode

/**
 * Represents a Lua table value
 * Tables have individual metatables (not shared like primitives)
 *
 * Supports weak references via __mode metatable field:
 * - __mode="k" : weak keys
 * - __mode="v" : weak values
 * - __mode="kv": weak keys and values
 *
 * Note: Full weak reference support is simplified for multiplatform compatibility
 */
class LuaTable : LuaValue<Storage> {
    override var value: Storage = Storage.StrongStorage()

    /**
     * Weak mode tracking
     */
    private val weakMode: String?
        get() = value.mode.toModeLuaString()

    /**
     * Each table has its own metatable
     */
    override var metatableStore: LuaValue<*>? = null
        set(newValue) {
            field = newValue
            updateWeakMode()
        }

    override fun type(): LuaType = LuaType.TABLE

    override fun toString(): String = "table: ${hashCode().toString(16)}"

    /**
     * Update weak mode based on metatable __mode field
     */
    private fun updateWeakMode() {
        val mt = metatable as? LuaTable ?: return
        val modeValue = mt.value[LuaString("__mode")] as? LuaString
        value =
            value.changeMode(
                WeakMode.parse(modeValue?.value),
            )
    }

    /**
     * Get a value from the table
     * Returns LuaNil if key doesn't exist
     */
    operator fun get(key: LuaValue<*>): LuaValue<*> = internalGet(key)

    /**
     * Internal retrieval logic shared by get and rawGet.
     * Handles nil and NaN keys according to Lua semantics.
     */
    private fun internalGet(key: LuaValue<*>): LuaValue<*> {
        // Reading with nil key just returns nil (no error)
        if (key is LuaNil) {
            return LuaNil
        }

        // Reading with NaN key returns nil (Lua 5.4 behavior)
        if (key is LuaDouble && key.value.isNaN()) {
            return LuaNil
        }

        return value[key] ?: LuaNil
    }

    /**
     * Validates that a key is legal for table operations.
     * Throws IllegalArgumentException if key is nil or NaN.
     */
    private fun validateKey(key: LuaValue<*>) {
        if (key is LuaNil) {
            throw IllegalArgumentException("table index is nil")
        }
        if (key is LuaDouble && key.value.isNaN()) {
            throw IllegalArgumentException("table index is NaN")
        }
    }

    /**
     * Set a value in the table
     * If value is nil, removes the key
     */
    operator fun set(
        key: LuaValue<*>,
        newValue: LuaValue<*>,
    ) {
        validateKey(key)

        if (newValue is LuaNil) {
            this.value.remove(key)
        } else {
            this.value[key] = newValue
        }

        // Note: Weak references are tracked via weakMode flag
        // Actual weak behavior depends on platform GC
    }

    /**
     * Get the length of the table (array part)
     * Lua's # operator finds the largest integer index i such that t[i] is not nil
     * Checks for both LuaLong and LuaDouble keys (since Lua treats integer doubles as integers)
     */
    fun length(): Int {
        var len = 0
        while (true) {
            val index = len + 1
            // Try both LuaLong and LuaDouble keys (Lua doesn't distinguish between 1 and 1.0)
            val keyLong = LuaLong(index.toLong())
            val keyDouble = LuaDouble(index.toDouble())

            val element = value[keyLong] ?: value[keyDouble]
            if (element == null || element is LuaNil) {
                break
            }
            len++
        }
        return len
    }

    /**
     * Get all keys
     */
    fun keys(): Set<LuaValue<*>> = value.keys

    /**
     * Get all values
     */
    fun values(): Collection<LuaValue<*>> = value.values

    /**
     * Get all entries
     */
    fun entries(): Set<Map.Entry<LuaValue<*>, LuaValue<*>>> = value.entries()

    /**
     * Check if empty
     */
    fun isEmpty(): Boolean = value.isEmpty()

    /**
     * Get the size of the table (total number of entries)
     */
    fun size(): Int = value.size

    /**
     * Raw get - get value without invoking __index metamethod
     */
    fun rawGet(key: LuaValue<*>): LuaValue<*> = internalGet(key)

    /**
     * Raw set - set value without invoking __newindex metamethod
     */
    fun rawSet(
        key: LuaValue<*>,
        newValue: LuaValue<*>,
    ) {
        validateKey(key)
        if (newValue is LuaNil) {
            value.remove(key)
        } else {
            value[key] = newValue
        }
    }

    /**
     * Raw length - get length without invoking __len metamethod
     */
    fun rawLen(): Int = length()
}
