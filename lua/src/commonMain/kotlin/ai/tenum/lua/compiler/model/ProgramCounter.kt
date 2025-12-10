package ai.tenum.lua.compiler.model

import kotlin.jvm.JvmInline

/**
 * Represents a program counter value in bytecode.
 * Makes the distinction between specific PC values and special sentinel values explicit.
 */
@JvmInline
value class ProgramCounter(
    val value: Int,
) {
    companion object {
        /** Sentinel value indicating a variable is currently active (never goes out of scope) */
        val ACTIVE = ProgramCounter(-1)

        /** Create from raw integer value */
        fun of(pc: Int): ProgramCounter = ProgramCounter(pc)
    }

    /** Check if this represents an active (non-terminated) scope */
    val isActive: Boolean get() = value == -1

    /** Check if this represents a specific instruction */
    val isSpecific: Boolean get() = value >= 0

    override fun toString(): String = if (isActive) "ACTIVE" else "PC($value)"
}
