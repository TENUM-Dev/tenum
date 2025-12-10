package ai.tenum.lua.runtime

/**
 * Lua integer number (uses Long)
 */
data class LuaLong(
    override val value: Long,
) : LuaNumber,
    MetaTable by LuaNumber.Companion {
    override fun isInteger(): Boolean = true

    override fun toString(): String = value.toString()

    override fun equals(other: Any?): Boolean =
        when (other) {
            is LuaLong -> value == other.value
            is LuaDouble -> {
                // Use Lua 5.4 equality semantics for table keys
                // Beyond 2^53, integers may not equal their float conversions
                val maxExact = 9007199254740992L // 2^53
                val minExact = -9007199254740992L

                if (value > maxExact || value < minExact) {
                    // Beyond exact representation range
                    val asDouble = value.toDouble()
                    if (asDouble != other.value) {
                        false
                    } else {
                        // Check if conversion was exact
                        val roundTrip = asDouble.toLong()
                        if (roundTrip != value) {
                            false
                        } else {
                            // Saturation check
                            value != Long.MAX_VALUE && value != Long.MIN_VALUE
                        }
                    }
                } else {
                    // Within exact range
                    value.toDouble() == other.value
                }
            }
            else -> false
        }

    override fun hashCode(): Int {
        // For values beyond 2^53, use identity-based hashing to avoid collisions
        // For values within exact double representation range, use double hash for compatibility
        val maxExact = 9007199254740992L // 2^53
        val minExact = -9007199254740992L

        return if (value > maxExact || value < minExact) {
            // Beyond exact range - use Long hash to avoid collisions with floats
            value.hashCode()
        } else {
            // Within exact range - use double hash for compatibility
            value.toDouble().hashCode()
        }
    }
}
