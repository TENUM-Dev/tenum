package ai.tenum.lua.vm

import ai.tenum.lua.compiler.model.LineEventKind
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.InferredFunctionName

/**
 * Represents a single call frame on the VM's call stack.
 * Used for debugging, error reporting, and stack traces.
 *
 * @param function The function being executed (null for top-level chunk)
 * @param proto The compiled function prototype (null for native functions)
 * @param pc Current program counter within the function
 * @param base Base register index for this frame
 * @param top Stack top (highest register index + 1) at call time - used by debug.getlocal to limit access
 * @param registers Reference to the VM's register array
 * @param isNative Whether this is a native (Kotlin) function call
 * @param isTailCall Whether this frame was entered via a tail call (for debug.getinfo)
 * @param inferredFunctionName Inferred function name and source from calling context (for debug.getinfo)
 * @param ftransfer First index of transferred values (1-based, for call/return hooks)
 * @param ntransfer Number of transferred values (for call/return hooks)
 */
data class CallFrame(
    val function: LuaFunction?,
    val proto: Proto?,
    var pc: Int = 0,
    val base: Int = 0,
    val top: Int = 0, // Stack top at call time (number of valid registers from base)
    val registers: MutableList<LuaValue<*>>,
    val isNative: Boolean = false,
    val isTailCall: Boolean = false,
    val inferredFunctionName: InferredFunctionName? = null,
    val varargs: List<LuaValue<*>> = emptyList(),
    val ftransfer: Int = 0, // Lua 5.4: first index of transferred values (1-based)
    val ntransfer: Int = 0, // Lua 5.4: number of transferred values
) {
    /**
     * Get the current source line number (if available)
     * Uses lineEvents to find the most recent EXECUTION event at or before current PC
     */
    fun getCurrentLine(): Int {
        if (proto == null) return -1

        // Find the line event for current PC
        val lineEvents = proto.lineEvents
        if (lineEvents.isEmpty()) return -1

        // Binary search or linear scan for the line
        // Prefer EXECUTION events over CONTROL_FLOW/MARKER for error reporting
        val eventsAtOrBefore = lineEvents.filter { it.pc <= pc }
        if (eventsAtOrBefore.isEmpty()) return proto.lineDefined

        // Return the most recent event's line, preferring EXECUTION kind
        val executionEvent = eventsAtOrBefore.findLast { it.kind == LineEventKind.EXECUTION }
        return executionEvent?.line ?: eventsAtOrBefore.last().line
    }

    /**
     * Find the active local variable at the given 1-based index.
     * Returns the local variable info and its register index, or null if not found.
     */
    private fun findActiveLocal(index: Int): Pair<ai.tenum.lua.compiler.model.LocalVarInfo, Int>? {
        if (proto == null) return null

        var activeIndex = 0
        for (localVar in proto.localVars) {
            if (localVar.startPc <= pc && pc < localVar.endPc) {
                activeIndex++
                if (activeIndex == index) { // Lua uses 1-based indexing
                    val registerIndex = base + localVar.register
                    if (registerIndex < registers.size) {
                        return Pair(localVar, registerIndex)
                    }
                }
            }
        }
        return null
    }

    /**
     * Calculate the maximum valid register index for temporary access.
     * This determines which registers are "live" and accessible via debug.getlocal/setlocal.
     */
    private fun calculateMaxValidIndex(): Int =
        if (top > 0) {
            // Use explicitly set top from call time
            base + top
        } else if (proto != null) {
            // When debug info is stripped, localVars is empty, so fall back to maxStackSize
            if (proto.localVars.isEmpty()) {
                // Use maxStackSize as a conservative upper bound for stripped functions
                base + proto.maxStackSize
            } else {
                // Heuristic: count active locals + buffer for temporaries (db.lua:410-418)
                val activeLocalCount =
                    proto.localVars.count { local ->
                        local.startPc <= pc && pc < local.endPc
                    }
                // Allow access up to last active local + 1 temporary slot
                base + activeLocalCount + 1
            }
        } else {
            // Native function: use register array size
            registers.size
        }

    /**
     * Get local variable name and value at given index.
     * Positive indices access local variables (1-based).
     * Negative indices access varargs (e.g., -1 is first vararg).
     */
    fun getLocal(index: Int): Pair<String, LuaValue<*>>? {
        // Handle negative indices for varargs (db.lua:284-286)
        if (index < 0) {
            val varargIndex = -index - 1 // Convert -1 to 0, -2 to 1, etc.
            if (varargIndex >= 0 && varargIndex < varargs.size) {
                return Pair("(vararg)", varargs[varargIndex])
            }
            return null
        }

        // For native functions (proto == null), skip registered local lookup
        // and go directly to temporary checking
        if (proto != null) {
            // Find the active local variable
            val activeLocal = findActiveLocal(index)
            if (activeLocal != null) {
                val (localVar, registerIndex) = activeLocal
                return Pair(localVar.name, registers[registerIndex])
            }
        }

        // If no registered local found (or native function), check for stack temporaries (db.lua:403-407)
        // These are registers/stack slots that exist but aren't formal local variables
        val registerIndex = base + (index - 1)
        val maxValidIndex = calculateMaxValidIndex()
        if (registerIndex >= base && registerIndex < maxValidIndex && registerIndex < registers.size) {
            // Return different names based on function type (db.lua:403-407, db.lua:410-418)
            val tempName = if (isNative || proto == null) "(C temporary)" else "(temporary)"
            return Pair(tempName, registers[registerIndex])
        }

        return null
    }

    /**
     * Set local variable value at given index.
     * Positive indices access local variables (1-based).
     * Negative indices access varargs (e.g., -1 is first vararg).
     */
    fun setLocal(
        index: Int,
        value: LuaValue<*>,
    ): String? {
        // Handle negative indices for varargs (db.lua:284-296)
        if (index < 0) {
            val varargIndex = -index - 1 // Convert -1 to 0, -2 to 1, etc.
            if (varargIndex >= 0 && varargIndex < varargs.size) {
                // Varargs are stored in a list, which is immutable after function entry
                // However, we can modify the underlying list if it's mutable
                if (varargs is MutableList) {
                    varargs[varargIndex] = value
                    return "(vararg)"
                }
                // If varargs is not mutable, we cannot set it
                // This matches Lua 5.4 behavior where varargs can be modified
                return null
            }
            return null
        }

        // For native functions (proto == null), skip registered local lookup
        // and go directly to temporary checking
        if (proto != null) {
            // Find the active local variable
            val activeLocal = findActiveLocal(index)
            if (activeLocal != null) {
                val (localVar, registerIndex) = activeLocal

                // RUNTIME <const> CHECK: Disallow assignment to <const> locals
                if (localVar.isConst) {
                    throw RuntimeException("attempt to assign to const variable '${localVar.name}'")
                }
                registers[registerIndex] = value
                return localVar.name
            }
        }

        // If no registered local found (or native function), check for stack temporaries (db.lua:413-418)
        // These are registers/stack slots that exist but aren't formal local variables
        val registerIndex = base + (index - 1)
        val maxValidIndex = calculateMaxValidIndex()
        if (registerIndex >= base && registerIndex < maxValidIndex && registerIndex < registers.size) {
            registers[registerIndex] = value
            // Return different names based on function type (db.lua:403-407, db.lua:410-418)
            val tempName = if (isNative || proto == null) "(C temporary)" else "(temporary)"
            return tempName
        }

        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CallFrame

        if (function != other.function) return false
        if (proto != other.proto) return false
        if (pc != other.pc) return false
        if (base != other.base) return false
        if (isNative != other.isNative) return false

        return true
    }

    override fun hashCode(): Int {
        var result = function?.hashCode() ?: 0
        result = 31 * result + (proto?.hashCode() ?: 0)
        result = 31 * result + pc
        result = 31 * result + base
        result = 31 * result + isNative.hashCode()
        return result
    }
}
