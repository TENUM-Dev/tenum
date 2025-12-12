package ai.tenum.lua.compiler.model

import ai.tenum.lua.runtime.LuaValue

/**
 * Information about an upvalue in a function
 * @param name The name of the upvalue (for debugging)
 * @param inStack Whether the upvalue refers to a local variable in the parent function (true)
 *                or an upvalue in the parent function (false)
 * @param index The index of the local variable or upvalue in the parent function
 */
data class UpvalueInfo(
    val name: String,
    val inStack: Boolean,
    val index: Int,
)

/**
 * Information about a local variable in a function
 * @param name The name of the local variable
 * @param register The register number where this local is stored
 * @param lifetime The program counter range where this variable is in scope
 * @param isConst Whether this is a const variable (Lua 5.4)
 */
data class LocalVarInfo(
    val name: String,
    val register: Int,
    val lifetime: LocalLifetime,
    val isConst: Boolean = false,
) {
    companion object {
        /** Create from raw startPc/endPc values (for backward compatibility) */
        fun of(
            name: String,
            register: Int,
            startPc: Int,
            endPc: Int,
            isConst: Boolean = false,
        ): LocalVarInfo = LocalVarInfo(name, register, LocalLifetime.of(startPc, endPc), isConst)
    }

    /** Backward compatibility: get startPc as Int */
    val startPc: Int get() = lifetime.startPc.value

    /** Backward compatibility: get endPc as Int */
    val endPc: Int get() = lifetime.endPc.value

    /** Check if this variable is alive at the given PC */
    fun isAliveAt(pc: Int): Boolean = lifetime.isAliveAt(pc)
}

/**
 * Domain concept: Represents different kinds of line events during execution.
 *
 * Not all source lines correspond to executable instructions. Some lines represent
 * control flow decision points that should fire LINE hooks even though no instruction
 * "executes" there (e.g., 'then', 'else', 'end' keywords).
 */
enum class LineEventKind {
    /** Normal execution - instruction executes code from this line */
    EXECUTION,

    /** Control flow event - execution passes through this line conceptually */
    CONTROL_FLOW,

    /** Entry/exit marker - function entry or statement block boundaries */
    MARKER,

    /**
     * Loop iteration event - must fire on EVERY visit to this line,
     * even if the line number hasn't changed from the previous event.
     * Used for loop headers where we want hooks to fire on each iteration.
     */
    ITERATION,
}

/**
 * Line event entry representing when execution "visits" a source line.
 *
 * Domain insight: LINE hooks fire when control flow "visits" a line,
 * which is different from "executes" a line. Multiple events can occur at
 * the same PC (e.g., a CONTROL_FLOW event for 'then' followed by EXECUTION
 * of the first statement).
 *
 * @param pc The program counter (instruction index)
 * @param line The source line number (1-based)
 * @param kind The kind of line event
 */
data class LineEvent(
    val pc: Int,
    val line: Int,
    val kind: LineEventKind = LineEventKind.EXECUTION,
)

/**
 * Represents a compiled Lua function/chunk
 */
data class Proto(
    val name: String,
    val instructions: List<Instruction>,
    val constants: List<LuaValue<*>>,
    val upvalueInfo: List<UpvalueInfo> = emptyList(),
    val parameters: List<String> = emptyList(),
    val hasVararg: Boolean = false,
    val maxStackSize: Int = 0,
    // Debug information (Phase 6.4 - Debug Library)
    val localVars: List<LocalVarInfo> = emptyList(), // Local variable names + scope
    val lineEvents: List<LineEvent> = emptyList(), // PC -> line event mapping (supports multiple events per PC)
    val source: String = "=(load)", // Source file name or description
    val lineDefined: Int = 0, // Line where function was defined
    val lastLineDefined: Int = 0, // Last line of function definition
)
