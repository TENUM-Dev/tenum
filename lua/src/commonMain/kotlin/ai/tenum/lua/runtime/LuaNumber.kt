package ai.tenum.lua.runtime

/**
 * Base interface for Lua number values
 * Lua 5.4+ supports both integers and floats
 */
sealed interface LuaNumber : LuaValue<Number> {
    override fun type(): LuaType = LuaType.NUMBER

    /**
     * Check if this number is an integer
     */
    fun isInteger(): Boolean

    /**
     * Get the double representation
     */
    fun toDouble(): Double = value.toDouble()

    /**
     * Get the long representation (only valid for integers)
     */
    fun toLong(): Long = value.toLong()

    companion object : MetaTable {
        /**
         * Shared metatable for all number values
         */
        override var metatableStore: LuaValue<*>? = null

        /**
         * Factory function to create the appropriate LuaNumber subtype.
         * Creates LuaLong for integer values, LuaDouble for floating-point values.
         */
        fun of(value: Double): LuaNumber {
            // Check if the value is exactly an integer within the Long range
            // Must check range before converting to avoid overflow/saturation
            return if (value.isFinite() &&
                value >= Long.MIN_VALUE.toDouble() &&
                value < 9223372036854775808.0 &&
                // 2^63, just beyond Long.MAX_VALUE
                value == value.toLong().toDouble()
            ) {
                LuaLong(value.toLong())
            } else {
                LuaDouble(value)
            }
        }

        /**
         * Factory function to create a LuaLong
         */
        fun of(value: Long): LuaNumber = LuaLong(value)

        /**
         * Factory function to create a LuaLong from an Int
         */
        fun of(value: Int): LuaNumber = LuaLong(value.toLong())
    }
}
