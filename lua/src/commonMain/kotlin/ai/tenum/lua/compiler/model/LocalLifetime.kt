package ai.tenum.lua.compiler.model

/**
 * Represents the lifetime of a local variable in bytecode.
 * Encapsulates the logic for checking if a variable is active at a given PC.
 */
data class LocalLifetime(
    val startPc: ProgramCounter,
    val endPc: ProgramCounter,
) {
    companion object {
        /** Create from raw integer values (for backward compatibility) */
        fun of(
            startPc: Int,
            endPc: Int,
        ): LocalLifetime = LocalLifetime(ProgramCounter.of(startPc), ProgramCounter.of(endPc))

        /** Create an active lifetime (starts at PC, never ends) */
        fun active(startPc: Int): LocalLifetime = LocalLifetime(ProgramCounter.of(startPc), ProgramCounter.ACTIVE)
    }

    /** Check if this variable is active (never goes out of scope) */
    val isActive: Boolean get() = endPc.isActive

    /** Check if this variable is alive at the given program counter */
    fun isAliveAt(pc: Int): Boolean {
        val inRange = startPc.value <= pc
        val beforeEnd = endPc.isActive || pc < endPc.value
        return inRange && beforeEnd
    }

    /** Check if this variable is alive at the given program counter */
    fun isAliveAt(pc: ProgramCounter): Boolean = isAliveAt(pc.value)

    override fun toString(): String = if (isActive) "[$startPc..ACTIVE]" else "[$startPc..$endPc)"
}
