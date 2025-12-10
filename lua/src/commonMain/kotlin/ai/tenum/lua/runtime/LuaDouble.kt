package ai.tenum.lua.runtime

/**
 * Lua float number (uses Double)
 */
data class LuaDouble(
    override val value: Double,
) : LuaNumber,
    MetaTable by LuaNumber.Companion {
    override fun isInteger(): Boolean {
        // Check if the double value is exactly an integer
        return value.isFinite() && value == value.toLong().toDouble()
    }

    override fun toString(): String = value.toString()

    override fun equals(other: Any?): Boolean =
        when (other) {
            is LuaDouble -> value == other.value
            is LuaLong -> {
                // Use Lua 5.4 equality semantics
                val maxExact = 9007199254740992L // 2^53
                val minExact = -9007199254740992L

                if (other.value > maxExact || other.value < minExact) {
                    // Beyond exact representation range
                    val intAsDouble = other.value.toDouble()
                    if (intAsDouble != value) {
                        false
                    } else {
                        val roundTrip = intAsDouble.toLong()
                        if (roundTrip != other.value) {
                            false
                        } else {
                            other.value != Long.MAX_VALUE && other.value != Long.MIN_VALUE
                        }
                    }
                } else {
                    value == other.value.toDouble()
                }
            }
            else -> false
        }

    override fun hashCode(): Int {
        // Normalize -0.0 to 0.0 for Lua semantics
        // In Lua, -0.0 and 0.0 are equal and should hash the same
        val normalized = if (value == 0.0) 0.0 else value
        return normalized.hashCode()
    }
}
