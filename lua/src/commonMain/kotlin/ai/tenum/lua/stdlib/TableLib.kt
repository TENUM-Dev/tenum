package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.internal.argError
import ai.tenum.lua.stdlib.string.ArgumentHelpers
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext

/**
 * Lua Table Library
 * Implements table manipulation functions
 */
class TableLib : LuaLibrary {
    override val name: String = "table"

    fun Int.addExact(other: Int): Int {
        val a = this
        val b = other

        if (b > 0 && a > Int.MAX_VALUE - b) {
            throw ArithmeticException("int overflow: $a + $b")
        }
        if (b < 0 && a < Int.MIN_VALUE - b) {
            throw ArithmeticException("int overflow: $a + $b")
        }

        return a + b
    }

    fun Long.addExact(other: Long): Long {
        val a = this
        val b = other

        if (b > 0 && a > Long.MAX_VALUE - b) {
            throw ArithmeticException("long overflow: $a + $b")
        }
        if (b < 0 && a < Long.MIN_VALUE - b) {
            throw ArithmeticException("long overflow: $a + $b")
        }

        return a + b
    }

    /**
     * Validates that a metamethod result is an integer length value.
     * Throws RuntimeException if the value is not a valid integer.
     */
    fun validateIntegerLength(lenValue: LuaValue<*>?): Long {
        if (lenValue !is LuaNumber) {
            throw RuntimeException("object length is not an integer")
        }
        val doubleValue = lenValue.value.toDouble()
        if (doubleValue != doubleValue.toLong().toDouble()) {
            throw RuntimeException("object length is not an integer")
        }
        return doubleValue.toLong()
    }

    override fun register(context: LuaLibraryContext) {
        val lib = LuaTable()

        // table.insert(list, [pos,] value)
        lib[LuaString("insert")] =
            LuaNativeFunction { args ->
                val table =
                    args.getOrNull(0) as? LuaTable
                        ?: argError("table.insert", 1, "table", args.getOrNull(0))

                when (args.size) {
                    2 -> {
                        // insert(list, value) - append to end
                        val value = args[1]
                        val len = getTableLengthWithMetamethod(table, context)
                        table[LuaNumber.of((len + 1).toDouble())] = value
                    }
                    3 -> {
                        // insert(list, pos, value) - insert at position
                        val pos =
                            (args[1] as? LuaNumber)?.value?.toInt()
                                ?: throw RuntimeException("number expected")
                        val value = args[2]
                        val len = getTableLengthWithMetamethod(table, context)

                        // Shift elements right
                        for (i in len downTo pos) {
                            val oldValue = table[LuaNumber.of(i.toDouble())]
                            table[LuaNumber.of((i + 1).toDouble())] = oldValue
                        }

                        // Insert new value
                        table[LuaNumber.of(pos.toDouble())] = value
                    }
                    else -> throw RuntimeException("wrong number of arguments to 'insert'")
                }

                emptyList()
            }

        // table.remove(list [, pos])
        lib[LuaString("remove")] =
            LuaNativeFunction { args ->
                val table =
                    args.getOrNull(0) as? LuaTable
                        ?: argError("table.remove", 1, "table", args.getOrNull(0))

                val len = getTableLength(table)
                if (len == 0) {
                    return@LuaNativeFunction listOf(LuaNil)
                }

                val pos =
                    if (args.size >= 2) {
                        (args[1] as? LuaNumber)?.value?.toInt() ?: len
                    } else {
                        len
                    }

                // Get value to return
                val removed = table[LuaNumber.of(pos.toDouble())]

                // Shift elements left
                for (i in pos until len) {
                    val nextValue = table[LuaNumber.of((i + 1).toDouble())]
                    table[LuaNumber.of(i.toDouble())] = nextValue
                }

                // Remove last element
                table[LuaNumber.of(len.toDouble())] = LuaNil

                listOf(removed)
            }

        // table.concat(list [, sep [, i [, j]]])
        lib[LuaString("concat")] =
            LuaNativeFunction { args ->
                val table =
                    args.getOrNull(0) as? LuaTable
                        ?: argError("table.concat", 1, "table", args.getOrNull(0))

                val sep = (args.getOrNull(1) as? LuaString)?.value ?: ""
                val len = getTableLength(table)
                val i = (args.getOrNull(2) as? LuaNumber)?.value?.toLong() ?: 1
                val j = (args.getOrNull(3) as? LuaNumber)?.value?.toLong() ?: len.toLong()

                if (i > j) {
                    return@LuaNativeFunction listOf(LuaString(""))
                }

                val parts = mutableListOf<String>()
                for (index in i..j) {
                    val value = table[LuaNumber.of(index)]
                    // Check for nil - table.concat requires all values to be strings or numbers
                    if (value is LuaNil) {
                        // Lua 5.4 behavior: report the actual 1-based index where nil was found
                        throw RuntimeException("invalid value (nil) at index $index in table for 'concat'")
                    }
                    // Check that value is a string or number
                    if (value !is LuaString && value !is LuaNumber) {
                        throw RuntimeException("invalid value (${value.type().name.lowercase()}) at index $index in table for 'concat'")
                    }
                    parts.add(ArgumentHelpers.coerceToString(value))
                }

                listOf(LuaString(parts.joinToString(sep)))
            }

        // table.sort(list [, comp])
        lib[LuaString("sort")] =
            LuaNativeFunction { args ->
                val table =
                    args.getOrNull(0) as? LuaTable
                        ?: argError("table.sort", 1, "table", args.getOrNull(0))

                val comp = args.getOrNull(1) as? LuaFunction

                // Check for __len metamethod and validate size
                val meta = context.getMetamethod(table, "__len")
                val len: Int
                if (meta != null && meta is LuaFunction) {
                    val result = context.callFunction(meta, listOf(table))
                    val lenValue = result.firstOrNull()
                    val longValue = validateIntegerLength(lenValue)
                    // Check for array too big (Lua 5.4 limit)
                    if (longValue > Int.MAX_VALUE) {
                        argError("table.sort", 1, "array too big", table)
                    }
                    len = longValue.toInt()
                } else {
                    len = getTableLength(table)
                }

                if (len <= 1) {
                    return@LuaNativeFunction emptyList()
                }

                // Extract array elements
                val elements = mutableListOf<LuaValue<*>>()
                for (i in 1..len) {
                    elements.add(table[LuaNumber.of(i.toDouble())])
                }

                // Sort using comparator or default comparison
                if (comp != null) {
                    // Comparator with order function validation
                    // Always check both directions to detect invalid order functions
                    elements.sortWith { a, b ->
                        val resultAB = context.callFunction(comp, listOf(a, b))
                        val isLessAB = resultAB.firstOrNull()
                        val aLessThanB = isLessAB is LuaBoolean && isLessAB.value

                        // Always validate: check reverse comparison
                        val resultBA = context.callFunction(comp, listOf(b, a))
                        val isLessBA = resultBA.firstOrNull()
                        val bLessThanA = isLessBA is LuaBoolean && isLessBA.value

                        // Check for invalid order function: both can't be true
                        if (aLessThanB && bLessThanA) {
                            throw RuntimeException("invalid order function for sorting")
                        }

                        if (aLessThanB) -1 else 1
                    }
                } else {
                    elements.sortWith { a, b -> compareValues(a, b, context) }
                }

                // Put sorted elements back
                for (i in elements.indices) {
                    table[LuaNumber.of((i + 1).toDouble())] = elements[i]
                }

                emptyList()
            }

        // table.pack(...)
        lib[LuaString("pack")] =
            LuaNativeFunction { args ->
                val result = LuaTable()
                for (i in args.indices) {
                    result[LuaNumber.of((i + 1).toDouble())] = args[i]
                }
                result[LuaString("n")] = LuaNumber.of(args.size.toDouble())
                listOf(result)
            }

        // table.unpack(list [, i [, j]])
        lib[LuaString("unpack")] =
            LuaNativeFunction { args ->
                val table =
                    args.getOrNull(0) as? LuaTable
                        ?: argError("table.unpack", 1, "table", args.getOrNull(0))

                val len = getTableLength(table)
                val i = (args.getOrNull(1) as? LuaNumber)?.value?.toLong() ?: 1
                val j = (args.getOrNull(2) as? LuaNumber)?.value?.toLong() ?: len.toLong()

                // Check if the range is too large (Lua 5.4 limit)
                // Lua uses LUAI_MAXCSTACK (typically 8000 or 1000000)
                if (i > j) {
                    // Empty range, return nothing
                    return@LuaNativeFunction emptyList()
                }

                // Overflow-safe check for: j - i + 1 > 1_000_000
                // Rewritten as: j - i > 999_999
                //
                // Check for overflow: j - i overflows if:
                // - j > 0 and i < 0 and j - Long.MIN_VALUE > Long.MAX_VALUE - (-i)
                // - which simplifies to: j > 0 and i < 0 and j > Long.MAX_VALUE + i
                // In practice, if signs differ and result would exceed Long.MAX_VALUE
                if (j > 0 && i < 0) {
                    // Different signs: check if j > Long.MAX_VALUE + i
                    // Rewrite to avoid overflow: j - Long.MAX_VALUE > i
                    if (j - Long.MAX_VALUE > i) {
                        throw RuntimeException("too many results to unpack")
                    }
                }

                val count = j - i // Now safe because we checked for overflow

                if (count > 999_999) {
                    throw RuntimeException("too many results to unpack")
                }

                // Safe iteration: since we already checked count <= 999_999,
                // we know the number of iterations is bounded
                val result = mutableListOf<LuaValue<*>>()
                val numElements = (count + 1).toInt() // Safe because count <= 999_999

                // Iterate using Long index to avoid overflow
                // We know j - i <= 999_999, so i + offset won't overflow as long as offset <= count
                for (offset in 0L until numElements) {
                    val index = i + offset // Safe: offset is in [0, count], and count = j - i
                    result.add(table[LuaNumber.of(index)]) // Use Long overload to preserve integer keys
                }

                result
            }

        // table.move(a1, f, e, t [, a2])
        lib[LuaString("move")] =
            LuaNativeFunction { args ->
                val a1 =
                    args.getOrNull(0) as? LuaTable
                        ?: argError("table.move", 1, "table", args.getOrNull(0))
                val f =
                    (args.getOrNull(1) as? LuaNumber)?.value?.toLong()
                        ?: throw RuntimeException("number expected")
                val e =
                    (args.getOrNull(2) as? LuaNumber)?.value?.toLong()
                        ?: throw RuntimeException("number expected")
                val t =
                    (args.getOrNull(3) as? LuaNumber)?.value?.toLong()
                        ?: throw RuntimeException("number expected")
                val a2 = (args.getOrNull(4) as? LuaTable) ?: a1

                // Check for valid range
                if (e >= f) {
                    // Calculate number of elements: count = e - f + 1
                    // Check for "too many elements" (source range too large)
                    val diff = e - f
                    if (diff < 0) {
                        // Overflow in subtraction
                        throw RuntimeException("too many elements to move")
                    }
                    if (diff == Long.MAX_VALUE) {
                        // Adding 1 would overflow
                        throw RuntimeException("too many elements to move")
                    }

                    // Check for "wrap around" (destination range overflow)
                    // Destination range is [t, t + diff]
                    try {
                        t.addExact(diff)
                    } catch (ex: ArithmeticException) {
                        throw RuntimeException("wrap around")
                    }
                }

                // For overlapping moves in the same table, we need to copy carefully
                // When dest > source, copy backwards to avoid overwriting source data
                val needsTemp = (a1 === a2) && (t > f) && (t <= e)

                if (needsTemp) {
                    // Copy backwards from end to start
                    // This handles huge ranges efficiently (metamethod errors stop us immediately)
                    var i = e
                    val offset = t - f

                    while (i >= f) {
                        val srcKey = LuaNumber.of(i)
                        val dstKey = LuaNumber.of(i + offset)
                        val value = getTableValueWithMetamethod(a1, srcKey, context)
                        setTableValueWithMetamethod(a2, dstKey, value, context)

                        // Check if decrementing would underflow
                        if (i == Long.MIN_VALUE) break
                        i--
                    }
                } else {
                    // No overlap: copy directly element by element
                    // Errors from metamethods will stop the operation naturally
                    // We iterate using indices, not count, to handle huge ranges
                    var i = f
                    var destOffset = 0L

                    while (i <= e) {
                        val srcKey = LuaNumber.of(i)
                        val dstKey = LuaNumber.of(t + destOffset)
                        val value = getTableValueWithMetamethod(a1, srcKey, context)
                        setTableValueWithMetamethod(a2, dstKey, value, context)

                        // Check if incrementing i would overflow
                        if (i == Long.MAX_VALUE) break
                        i++
                        destOffset++
                    }
                }

                listOf(a2)
            }

        context.registerGlobal("table", lib)
    }

    /**
     * Get value from table with __index metamethod support.
     * This is similar to VM's getTableValue but adapted for stdlib use.
     */
    private fun getTableValueWithMetamethod(
        table: LuaTable,
        key: LuaValue<*>,
        context: LuaLibraryContext,
    ): LuaValue<*> {
        val rawValue = table[key]
        if (rawValue is LuaNil) {
            val metaMethod = context.getMetamethod(table, "__index")
            return when {
                metaMethod is LuaFunction -> {
                    val result = context.callFunction(metaMethod, listOf(table, key))
                    result.firstOrNull() ?: LuaNil
                }
                metaMethod is LuaNil || metaMethod == null -> rawValue
                else -> {
                    // __index is not a function or nil, recursively index it
                    if (metaMethod is LuaTable) {
                        getTableValueWithMetamethod(metaMethod, key, context)
                    } else {
                        // Non-table, non-function __index: error
                        rawValue
                    }
                }
            }
        }
        return rawValue
    }

    /**
     * Set value in table with __newindex metamethod support.
     * This mirrors the VM's SETTABLE opcode behavior for stdlib use.
     */
    private fun setTableValueWithMetamethod(
        table: LuaTable,
        key: LuaValue<*>,
        value: LuaValue<*>,
        context: LuaLibraryContext,
    ) {
        val existingValue = table[key]
        if (existingValue is LuaNil) {
            // Key doesn't exist, check for __newindex metamethod
            val metaMethod = context.getMetamethod(table, "__newindex")
            when {
                metaMethod is LuaTable -> metaMethod[key] = value // Raw set to metamethod table
                metaMethod is LuaFunction -> context.callFunction(metaMethod, listOf(table, key, value))
                else -> table[key] = value
            }
        } else {
            // Key exists, set directly
            table[key] = value
        }
    }

    /**
     * Get the length of a table with metamethod support
     * Validates that __len returns an integer value
     */
    private fun getTableLengthWithMetamethod(
        table: LuaTable,
        context: LuaLibraryContext,
    ): Int {
        // Check for __len metamethod
        val meta = context.getMetamethod(table, "__len")
        if (meta != null && meta is LuaFunction) {
            val result = context.callFunction(meta, listOf(table))
            val lenValue = result.firstOrNull()
            val doubleValue = validateIntegerLength(lenValue).toDouble()

            return doubleValue.toInt()
        } else {
            // Use standard length counting
            return getTableLength(table)
        }
    }

    /**
     * Get the length of a table (array part)
     */
    private fun getTableLength(table: LuaTable): Int {
        var len = 0
        while (true) {
            val value = table[LuaNumber.of((len + 1).toDouble())]
            if (value is LuaNil) break
            len++
        }
        return len
    }

    /**
     * Compare two Lua values for sorting.
     * Supports __lt metamethod for custom comparison.
     */
    private fun compareValues(
        a: LuaValue<*>,
        b: LuaValue<*>,
        context: LuaLibraryContext,
    ): Int {
        // Check for __lt metamethod first (same pattern as LT opcode)
        val ltMeta =
            context.getMetamethod(a, "__lt")
                ?: context.getMetamethod(b, "__lt")

        if (ltMeta != null && ltMeta !is LuaNil) {
            if (ltMeta is LuaFunction) {
                // Compare using metamethod: a < b
                val resultAB = context.callFunction(ltMeta, listOf(a, b))
                val aLessThanB = (resultAB.firstOrNull() as? LuaBoolean)?.value == true

                if (aLessThanB) return -1

                // Compare using metamethod: b < a
                val resultBA = context.callFunction(ltMeta, listOf(b, a))
                val bLessThanA = (resultBA.firstOrNull() as? LuaBoolean)?.value == true

                return if (bLessThanA) 1 else 0
            } else {
                val typeStr = ltMeta.type().name.lowercase()
                throw RuntimeException("attempt to call a $typeStr value (metamethod 'lt')")
            }
        }

        // Fall back to native comparison
        return when {
            a is LuaNumber && b is LuaNumber -> a.toDouble().compareTo(b.toDouble())
            a is LuaString && b is LuaString -> a.value.compareTo(b.value)
            else -> {
                // Types not comparable without metamethod
                val aType = a.type().name.lowercase()
                val bType = b.type().name.lowercase()
                val message =
                    if (aType == bType) {
                        "attempt to compare two $aType values"
                    } else {
                        "attempt to compare $aType with $bType"
                    }
                throw RuntimeException(message)
            }
        }
    }
}
