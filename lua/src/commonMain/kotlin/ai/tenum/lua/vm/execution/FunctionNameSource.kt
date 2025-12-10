package ai.tenum.lua.vm.execution

/**
 * Represents the source/context from which a function name was inferred.
 * Used by debug.getinfo to report how a function name was determined.
 */
sealed class FunctionNameSource {
    /** Function name could not be determined */
    object Unknown : FunctionNameSource()

    /** Function is a local variable */
    object Local : FunctionNameSource()

    /** Function is a global variable */
    object Global : FunctionNameSource()

    /** Function is a table field (e.g., t.field) */
    object Field : FunctionNameSource()

    /** Function is a method call (e.g., t:method) */
    object Method : FunctionNameSource()

    /** Function is a debug hook function */
    object Hook : FunctionNameSource()

    /** Function is a metamethod (e.g., __index, __add, etc.) */
    object Metamethod : FunctionNameSource()

    /** Function is a for-loop iterator */
    object ForIterator : FunctionNameSource()

    /**
     * Returns the Lua string representation for debug.getinfo's "namewhat" field.
     */
    val luaString: String get() =
        when (this) {
            Unknown -> ""
            Local -> "local"
            Global -> "global"
            Field -> "field"
            Method -> "method"
            Hook -> "hook"
            Metamethod -> "metamethod"
            ForIterator -> ""
        }

    companion object {
        /**
         * Parse a string into a FunctionNameSource.
         * Used for backward compatibility with string-based APIs.
         */
        fun fromString(str: String): FunctionNameSource =
            when (str) {
                "local" -> Local
                "global" -> Global
                "field" -> Field
                "method" -> Method
                "hook" -> Hook
                "metamethod" -> Metamethod
                "for iterator" -> ForIterator
                else -> Unknown
            }
    }
}

/**
 * Represents an inferred function name with its source.
 * This makes the relationship between name and source explicit in the type system.
 */
data class InferredFunctionName(
    val name: String?,
    val source: FunctionNameSource,
) {
    companion object {
        /** No name could be inferred */
        val UNKNOWN = InferredFunctionName(null, FunctionNameSource.Unknown)

        /** Create from legacy string-based API */
        fun fromPair(
            name: String?,
            nameWhat: String,
        ): InferredFunctionName = InferredFunctionName(name, FunctionNameSource.fromString(nameWhat))
    }

    /** Convert to legacy pair format for backward compatibility */
    fun toPair(): Pair<String?, String> = Pair(name, source.luaString)
}
