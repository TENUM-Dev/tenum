package ai.tenum.lua.runtime.table

import ai.tenum.lua.runtime.LuaValue
import opensavvy.pedestal.weak.ExperimentalWeakApi
import opensavvy.pedestal.weak.WeakMap
import opensavvy.pedestal.weak.WeakRef
import kotlin.collections.component1
import kotlin.collections.component2

// Helper to create a WeakRef<T> using the top-level WeakRef(...) function.
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun <T> newWeakRef(value: T): WeakRef<T> = WeakRef(value)

/**
 * Build entries by iterating weak keys and dereferencing both keys and values.
 * Removes collected keys and values from their respective collections.
 */
private fun buildEntriesFromWeakKeys(
    keySet: MutableSet<WeakRef<LuaValue<*>>>,
    getValueRef: (LuaValue<*>) -> WeakRef<LuaValue<*>>?,
    removeValue: (LuaValue<*>) -> Unit,
): Set<Map.Entry<LuaValue<*>, LuaValue<*>>> {
    val result = mutableMapOf<LuaValue<*>, LuaValue<*>>()
    val it = keySet.iterator()
    while (it.hasNext()) {
        val derefKey = it.next().read()
        if (derefKey == null) {
            // key was GC'd, drop entry
            it.remove()
        } else {
            val vRef = getValueRef(derefKey)
            val v = vRef?.read()
            if (v == null) {
                // value was GC'd, drop entry
                removeValue(derefKey)
            } else {
                result[derefKey] = v
            }
        }
    }
    return result.entries
}

@OptIn(ExperimentalWeakApi::class)
fun <T> createWeakMap(
    initial: Storage? = null,
    to: (LuaValue<*>) -> T,
): WeakMap<LuaValue<*>, T> =
    WeakMap<LuaValue<*>, T>().apply {
        initial?.entries()?.forEach { (k, v) ->
            this[k] = to(v)
        }
    }

/**
 * Build entries from a map with weak values, removing collected values.
 */
private fun buildEntriesFromWeakValues(map: MutableMap<LuaValue<*>, WeakRef<LuaValue<*>>>): Set<Map.Entry<LuaValue<*>, LuaValue<*>>> {
    val result = mutableMapOf<LuaValue<*>, LuaValue<*>>()
    val it = map.entries.iterator()
    while (it.hasNext()) {
        val (k, ref) = it.next()
        val v = ref.read()
        if (v == null) {
            // value was GC'd, drop entry
            it.remove()
        } else {
            result[k] = v
        }
    }
    return result.entries
}

/**
 * Underlying storage; switches between strong/weak depending on __mode.
 */
sealed interface Storage {
    val mode: WeakMode
        get() =
            when (this) {
                is StrongStorage -> WeakMode.NONE
                is WeakKeyStorage -> WeakMode.WEAK_KEYS
                is WeakValueStorage -> WeakMode.WEAK_VALUES
                is WeakKeyValueStorage -> WeakMode.WEAK_KEYS_AND_VALUES
                else -> WeakMode.NONE
            }
    val keys: Set<LuaValue<*>>
        get() = entries().map { it.key }.toSet()
    val values: Collection<LuaValue<*>>
        get() = entries().map { it.value }
    val size: Int
        get() = entries().size

    operator fun get(key: LuaValue<*>): LuaValue<*>?

    operator fun set(
        key: LuaValue<*>,
        value: LuaValue<*>,
    )

    fun remove(key: LuaValue<*>)

    fun entries(): Set<Map.Entry<LuaValue<*>, LuaValue<*>>>

    fun clear()

    fun isEmpty(): Boolean = entries().isEmpty()

    fun changeMode(newMode: WeakMode): Storage {
        if (newMode == mode) return this

        val newStorage: Storage =
            when (newMode) {
                WeakMode.NONE -> StrongStorage(this)
                WeakMode.WEAK_KEYS -> WeakKeyStorage(this)
                WeakMode.WEAK_VALUES -> WeakValueStorage(this)
                WeakMode.WEAK_KEYS_AND_VALUES -> WeakKeyValueStorage(this)
            }

        // Migrate existing entries
        for ((k, v) in entries()) {
            newStorage.set(k, v)
        }

        return newStorage
    }

    abstract class MutableMapBase<T> : Storage {
        abstract val map: MutableMap<LuaValue<*>, T>

        override fun clear() = map.clear()

        override val keys: Set<LuaValue<*>>
            get() = map.keys

        override val size: Int
            get() = map.size

        override fun remove(key: LuaValue<*>) {
            map.remove(key)
        }
    }

    /**
     * Strong (normal) map storage.
     */
    class StrongStorage(
        initial: Storage? = null,
    ) : MutableMapBase<LuaValue<*>>(),
        Storage {
        override val map: MutableMap<LuaValue<*>, LuaValue<*>> =
            initial?.entries()?.associate { it.toPair() }?.toMutableMap() ?: mutableMapOf()

        override val values: Collection<LuaValue<*>>
            get() = map.values

        override fun get(key: LuaValue<*>) = map[key]

        override operator fun set(
            key: LuaValue<*>,
            value: LuaValue<*>,
        ) {
            map[key] = value
        }

        override fun entries(): Set<Map.Entry<LuaValue<*>, LuaValue<*>>> = map.entries
    }

    /**
     * Strong keys, weak values: __mode="v"
     * Values are wrapped in WeakRef.
     */
    class WeakValueStorage(
        initial: Storage? = null,
    ) : MutableMapBase<WeakRef<LuaValue<*>>>(),
        Storage {
        override val map: MutableMap<LuaValue<*>, WeakRef<LuaValue<*>>> =
            mutableMapOf<LuaValue<*>, WeakRef<LuaValue<*>>>().apply {
                initial?.entries()?.forEach { (k, v) ->
                    this[k] = newWeakRef(v)
                }
            }

        override fun get(key: LuaValue<*>): LuaValue<*>? = map[key]?.read()

        override fun set(
            key: LuaValue<*>,
            value: LuaValue<*>,
        ) {
            map[key] = newWeakRef(value)
        }

        override fun entries(): Set<Map.Entry<LuaValue<*>, LuaValue<*>>> = buildEntriesFromWeakValues(map)
    }

    @OptIn(ExperimentalWeakApi::class)
    abstract class WeakMapBase<T>(
        initial: Storage? = null,
    ) : Storage {
        abstract val weakMap: WeakMap<LuaValue<*>, T>
        val keySet =
            mutableSetOf<WeakRef<LuaValue<*>>>().apply {
                initial?.entries()?.forEach { (k, _) ->
                    this.add(newWeakRef(k))
                }
            }

        override fun clear() {
            keySet.forEach {
                val derefKey = it.read()
                if (derefKey != null) {
                    weakMap.remove(derefKey)
                }
            }
            keySet.clear()
        }

        override val keys: Set<LuaValue<*>>
            get() = keySet.mapNotNull { it.read() }.toSet()

        override val size: Int
            get() = keys.size

        override fun remove(key: LuaValue<*>) {
            weakMap.remove(key)
            keySet.removeAll {
                val derefKey = it.read()
                derefKey == null || derefKey == key
            }
        }
    }

    /**
     * Weak keys, strong values: __mode="k"
     */
    @OptIn(ExperimentalWeakApi::class)
    class WeakKeyStorage(
        initial: Storage? = null,
    ) : WeakMapBase<LuaValue<*>>(initial),
        Storage {
        override val weakMap: WeakMap<LuaValue<*>, LuaValue<*>> =
            createWeakMap {
                it
            }

        override fun get(key: LuaValue<*>) = weakMap[key]

        override fun set(
            key: LuaValue<*>,
            value: LuaValue<*>,
        ) {
            weakMap[key] = value
        }

        override fun entries(): Set<Map.Entry<LuaValue<*>, LuaValue<*>>> =
            buildEntriesFromWeakKeys(
                keySet,
                getValueRef = { key -> weakMap[key]?.let { newWeakRef(it) } },
                removeValue = { key -> weakMap.remove(key) },
            )
    }

    /**
     * Weak keys AND weak values: __mode="kv"
     */
    @OptIn(ExperimentalWeakApi::class)
    class WeakKeyValueStorage(
        initial: Storage? = null,
    ) : WeakMapBase<WeakRef<LuaValue<*>>>(initial),
        Storage {
        override val weakMap: WeakMap<LuaValue<*>, WeakRef<LuaValue<*>>> =
            createWeakMap {
                newWeakRef(it)
            }

        override fun get(key: LuaValue<*>): LuaValue<*>? = weakMap[key]?.read()

        override fun set(
            key: LuaValue<*>,
            value: LuaValue<*>,
        ) {
            weakMap[key] = newWeakRef(value)
            keySet.add(newWeakRef(key))
        }

        override fun entries(): Set<Map.Entry<LuaValue<*>, LuaValue<*>>> =
            buildEntriesFromWeakKeys(
                keySet,
                getValueRef = { key -> weakMap[key] },
                removeValue = { key -> weakMap.remove(key) },
            )
    }
}
