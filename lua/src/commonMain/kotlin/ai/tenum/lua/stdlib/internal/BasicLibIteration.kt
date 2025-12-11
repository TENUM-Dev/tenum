package ai.tenum.lua.stdlib.internal

import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.library.CallFunctionCallback
import ai.tenum.lua.vm.library.GetMetamethodCallback
import ai.tenum.lua.vm.library.RegisterGlobalCallback

/**
 * Iteration functions for BasicLib
 * Implements: pairs, ipairs, next, select
 */
internal object BasicLibIteration {
    fun registerFunctions(
        registerGlobal: RegisterGlobalCallback,
        getMetamethod: GetMetamethodCallback,
        callFunction: CallFunctionCallback,
    ) {
        // select(index, ...) - returns all arguments after index, or count with '#'
        registerGlobal(
            "select",
            LuaNativeFunction { args ->
                if (args.isEmpty()) {
                    throw RuntimeException("bad argument #1 to 'select' (value expected)")
                }
                val indexValue = args[0]
                if (indexValue is LuaString && indexValue.value == "#") {
                    buildList {
                        add(LuaNumber.of(args.size - 1))
                    }
                } else {
                    if (indexValue !is LuaNumber) {
                        throw RuntimeException("bad argument #1 to 'select' (number expected, got ${indexValue::class.simpleName})")
                    }
                    val index = indexValue.toDouble().toInt()
                    // Lua indices start at 1. select(1, ...) returns all arguments after the first arg.
                    val actualIndex = if (index < 0) args.size + index else index
                    if (actualIndex < 1 || actualIndex >= args.size) {
                        return@LuaNativeFunction emptyList()
                    }
                    args.subList(actualIndex, args.size)
                }
            },
        )

        // pairs(t) - returns iterator for all key-value pairs
        registerGlobal(
            "pairs",
            LuaNativeFunction { args ->
                if (args.isEmpty() || args[0] !is LuaTable) {
                    throw RuntimeException("pairs expects a table")
                }
                val table = args[0] as LuaTable

                // If metatable defines __pairs, call it and return its 3 return values (iterator, state, init)
                val metaPairs = getMetamethod(table, "__pairs")
                if (metaPairs != null && metaPairs !is LuaNil) {
                    if (metaPairs !is LuaFunction) {
                        val typeName = metaPairs.type().name.lowercase()
                        throw RuntimeException("attempt to call a $typeName value")
                    }
                    val res = callFunction(metaPairs, listOf(table))
                    // Pad to 3 results
                    val padded = res.toMutableList()
                    while (padded.size < 3) padded.add(LuaNil)
                    return@LuaNativeFunction padded
                }

                // Return: next function, table, nil
                val iteratorFunc = createPairsIterator()
                buildList {
                    add(iteratorFunc)
                    add(table)
                    add(LuaNil)
                }
            },
        )

        // ipairs(t) - returns iterator for integer-indexed pairs
        registerGlobal(
            "ipairs",
            LuaNativeFunction { args ->
                if (args.isEmpty() || args[0] !is LuaTable) {
                    throw RuntimeException("ipairs expects a table")
                }
                val table = args[0] as LuaTable

                // If metatable defines __ipairs, call it and return its 3 return values (iterator, state, init)
                val metaIpairs = getMetamethod(table, "__ipairs")
                if (metaIpairs != null && metaIpairs !is LuaNil) {
                    if (metaIpairs !is LuaFunction) {
                        val typeName = metaIpairs.type().name.lowercase()
                        throw RuntimeException("attempt to call a $typeName value")
                    }
                    val res = callFunction(metaIpairs, listOf(table))
                    val padded = res.toMutableList()
                    while (padded.size < 3) padded.add(LuaNil)
                    return@LuaNativeFunction padded
                }

                // Return: iterator function, table, 0
                val iteratorFunc = createIpairsIterator()
                buildList {
                    add(iteratorFunc)
                    add(table)
                    add(LuaNumber.of(0))
                }
            },
        )

        // next(table [, index]) - returns next key-value pair
        registerGlobal(
            "next",
            LuaNativeFunction { args ->
                val table = args.getOrNull(0)
                val key = args.getOrNull(1)
                nextPair(table, key)
            },
        )
    }

    private fun createPairsIterator(): LuaNativeFunction =
        LuaNativeFunction { args ->
            val table = args.getOrNull(0)
            val key = args.getOrNull(1)
            nextPair(table, key)
        }

    private fun createIpairsIterator(): LuaNativeFunction =
        LuaNativeFunction { args ->
            val table = args.getOrNull(0) as? LuaTable ?: return@LuaNativeFunction listOf(LuaNil)
            val index = args.getOrNull(1) as? LuaNumber ?: return@LuaNativeFunction listOf(LuaNil)
            val nextIndex = index.toDouble().toInt() + 1
            val nextValue = table.get(LuaNumber.of(nextIndex))
            if (nextValue is LuaNil) {
                listOf(LuaNil)
            } else {
                listOf(LuaNumber.of(nextIndex), nextValue)
            }
        }

    fun nextPair(
        tableValue: LuaValue<*>?,
        keyValue: LuaValue<*>?,
    ): List<LuaValue<*>> {
        if (tableValue !is LuaTable) {
            throw RuntimeException("bad argument #1 to 'next' (table expected)")
        }

        val key = keyValue ?: LuaNil

        // Get all keys
        val keys = tableValue.keys().toList()

        if (key is LuaNil) {
            // Return first key-value pair
            if (keys.isEmpty()) {
                return listOf(LuaNil, LuaNil)
            }
            val firstKey = keys[0]
            return buildList {
                add(firstKey)
                add(tableValue.rawGet(firstKey))
            }
        }

        // Find current key and return next one
        val currentIndex = keys.indexOf(key)
        if (currentIndex == -1) {
            // Key not found in table
            throw RuntimeException("invalid key to 'next'")
        }

        if (currentIndex + 1 >= keys.size) {
            // No more keys
            return listOf(LuaNil, LuaNil)
        }

        val nextKey = keys[currentIndex + 1]
        return buildList {
            add(nextKey)
            add(tableValue.rawGet(nextKey))
        }
    }
}
