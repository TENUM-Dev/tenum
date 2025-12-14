package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue

/**
 * Encapsulates the execution state for a single Lua function call frame.
 *
 * This is the core domain concept that unifies:
 * - Register state (local variables)
 * - Constant pool
 * - Upvalue bindings
 * - Program counter
 * - Variable result state (top marker)
 * - Varargs
 * - Open upvalue tracking
 * - To-be-closed variable lifecycle
 *
 * ExecutionFrame represents the complete execution context needed by frame-aware opcodes
 * (CALL, RETURN, TAILCALL, CLOSURE, VARARG, SETLIST, CLOSE).
 */
class ExecutionFrame(
    /** Function prototype being executed */
    val proto: Proto,
    /** Initial arguments passed to function */
    initialArgs: List<LuaValue<*>>,
    /** Upvalue bindings for this closure */
    val upvalues: List<Upvalue>,
    /** Initial program counter (for resumption) */
    initialPc: Int = 0,
    /** Existing registers (for resumption) */
    existingRegisters: MutableList<LuaValue<*>>? = null,
    /** Existing varargs (for resumption) */
    existingVarargs: List<LuaValue<*>>? = null,
) {
    /** Register array (local variables and temporaries) - uses MutableList for dynamic growth */
    val registers: MutableList<LuaValue<*>> =
        if (existingRegisters != null) {
            existingRegisters // Don't copy - reuse the same list to preserve upvalue references
        } else {
            // Allocate 1024 as minimum to handle compiler temporaries during complex multi-assignment
            // Lua 5.4 spec allows max 256 registers, but compiler may use MUCH higher indices temporarily
            val initialSize = proto.maxStackSize.coerceAtLeast(1024)
            MutableList(initialSize) { LuaNil }
        }

    /**
     * Ensure the register list can hold at least `index + 1` elements.
     * Dynamically grows the list if necessary.
     */
    fun ensureRegisterCapacity(index: Int) {
        while (index >= registers.size) {
            registers.add(LuaNil)
        }
    }

    /** Constant pool from prototype */
    val constants: List<LuaValue<*>> = proto.constants

    /** Bytecode instructions */
    val instructions = proto.instructions

    /** Program counter - current instruction index */
    var pc: Int = initialPc

    /**
     * Top of stack marker for variable-result instructions.
     * When non-zero, indicates where variable results end (CALL with c=0, VARARG with b=0).
     * Must be reset to 0 after consuming results to prevent stale state.
     */
    var top: Int = 0

    /**
     * Varargs - arguments beyond named parameters.
     * Available via VARARG instruction for functions with hasVararg=true.
     */
    val varargs: List<LuaValue<*>> =
        existingVarargs ?: run {
            val numParams = proto.parameters.size
            if (proto.hasVararg && initialArgs.size > numParams) {
                initialArgs.subList(numParams, initialArgs.size)
            } else {
                emptyList()
            }
        }

    /**
     * Open upvalues map - tracks upvalues that reference registers in this frame.
     * Key: register index, Value: Upvalue object.
     * Must be closed when frame exits or when CLOSE instruction executes.
     */
    val openUpvalues = mutableMapOf<Int, Upvalue>()

    /**
     * To-be-closed variables - tracks <close> variables for proper __close metamethod invocation.
     * Stores (register index, value) pairs in declaration order.
     * __close called in reverse order on scope exit.
     */
    val toBeClosedVars = mutableListOf<Pair<Int, LuaValue<*>>>()

    init {
        // Load initial arguments into parameter registers (if not resuming)
        if (existingRegisters == null) {
            val numParams = proto.parameters.size
            for ((index, arg) in initialArgs.withIndex()) {
                if (index < numParams && index < registers.size) {
                    registers[index] = arg
                }
            }
        }
    }

    /**
     * Get or create an upvalue for a register in this frame.
     * Reuses existing open upvalue if one exists for this register.
     */
    fun getOrCreateOpenUpvalue(registerIndex: Int): Upvalue =
        openUpvalues.getOrPut(registerIndex) {
            Upvalue(registerIndex = registerIndex, registers = registers)
        }

    /**
     * Close a specific upvalue at the given register index.
     * Called by CLOSE instruction.
     */
    fun closeUpvalueAt(registerIndex: Int) {
        val up = openUpvalues[registerIndex]
        if (up != null) {
            up.close()
            openUpvalues.remove(registerIndex)
        }
    }

    /**
     * Close all open upvalues for registers >= [registerIndex].
     * This is called by the CLOSE opcode.
     */
    fun closeUpvaluesFrom(registerIndex: Int) {
        val toClose = openUpvalues.filterKeys { it >= registerIndex }
        for ((regIndex, upvalue) in toClose) {
            if (!upvalue.isClosed) {
                upvalue.close()
                openUpvalues.remove(regIndex)
            }
        }
    }

    private fun getCloseMetamethod(value: LuaValue<*>): ai.tenum.lua.runtime.LuaFunction? {
        // Get metatable - handles both instance metatables (tables) and shared metatables (primitives)
        val mt =
            when (value) {
                is ai.tenum.lua.runtime.LuaTable -> value.metatable as? LuaTable
                is ai.tenum.lua.runtime.LuaNumber -> ai.tenum.lua.runtime.LuaNumber.metatableStore as? LuaTable
                is ai.tenum.lua.runtime.LuaString -> ai.tenum.lua.runtime.LuaString.metatableStore as? LuaTable
                is ai.tenum.lua.runtime.LuaBoolean -> ai.tenum.lua.runtime.LuaBoolean.metatableStore as? LuaTable
                is ai.tenum.lua.runtime.LuaNil -> ai.tenum.lua.runtime.LuaNil.metatableStore as? LuaTable
                else -> value.metatable as? LuaTable
            }
        return mt?.get(
            ai.tenum.lua.runtime
                .LuaString("__close"),
        ) as? ai.tenum.lua.runtime.LuaFunction
    }

    /**
     * Get the __close metamethod value (not just functions).
     * Returns the raw value so we can distinguish between nil and non-callable values.
     */
    private fun getCloseMetamethodRaw(value: LuaValue<*>): LuaValue<*>? {
        val mt =
            when (value) {
                is ai.tenum.lua.runtime.LuaTable -> value.metatable as? LuaTable
                is ai.tenum.lua.runtime.LuaNumber -> ai.tenum.lua.runtime.LuaNumber.metatableStore as? LuaTable
                is ai.tenum.lua.runtime.LuaString -> ai.tenum.lua.runtime.LuaString.metatableStore as? LuaTable
                is ai.tenum.lua.runtime.LuaBoolean -> ai.tenum.lua.runtime.LuaBoolean.metatableStore as? LuaTable
                is ai.tenum.lua.runtime.LuaNil -> ai.tenum.lua.runtime.LuaNil.metatableStore as? LuaTable
                else -> value.metatable as? LuaTable
            } ?: return null

        return mt[
            ai.tenum.lua.runtime
                .LuaString("__close"),
        ]
    }

    /**
     * Execute __close metamethods for to-be-closed variables at or above the given register.
     * Called in reverse declaration order (LIFO).
     *
     * Error chaining: If one close handler throws an error, that error is passed as the second
     * argument to the next close handler. The final error is re-thrown after all handlers run.
     */
    fun executeCloseMetamethods(
        registerIndex: Int,
        callCloseFn: (Upvalue, LuaValue<*>, LuaValue<*>) -> Unit,
    ) {
        val snapshot = toBeClosedVars.toList()
        var chainedError: LuaValue<*> = ai.tenum.lua.runtime.LuaNil
        var lastException: Throwable? = null

        for ((reg, capturedValue) in snapshot.asReversed()) {
            if (reg < registerIndex) continue
            // Use structural equality instead of reference equality
            // This ensures the value is removed even if the object identity changed
            // (which can happen due to platform-specific optimizations or copying)
            toBeClosedVars.removeAll { it.first == reg && it.second == capturedValue }

            // Get current value from register (may have changed since declaration)
            val currentValue = registers.getOrNull(reg) ?: capturedValue

            // nil and false don't need closing
            if (currentValue is ai.tenum.lua.runtime.LuaNil ||
                (currentValue is ai.tenum.lua.runtime.LuaBoolean && !currentValue.value)
            ) {
                continue
            }

            // Get __close metamethod - must exist and be callable at close time
            val closeMethod = getCloseMetamethodRaw(currentValue)
            try {
                when {
                    closeMethod == null || closeMethod is ai.tenum.lua.runtime.LuaNil -> {
                        // __close doesn't exist - create error
                        throw ai.tenum.lua.vm.errorhandling.LuaRuntimeError(
                            "attempt to call a nil value (metamethod 'close')",
                        )
                    }
                    closeMethod is ai.tenum.lua.runtime.LuaFunction -> {
                        // Valid function - call it with current error (if any)
                        callCloseFn(Upvalue(closedValue = closeMethod), currentValue, chainedError)
                        // If no error was thrown, the chained error continues to next handler
                        // (Don't clear it - only a new error replaces the old one)
                    }
                    else -> {
                        // __close exists but is not a function
                        val typeName = closeMethod.type().name.lowercase()
                        throw ai.tenum.lua.vm.errorhandling.LuaRuntimeError(
                            "attempt to call a $typeName value (metamethod 'close')",
                        )
                    }
                }
            } catch (e: ai.tenum.lua.vm.errorhandling.LuaException) {
                // Capture the original error value and exception
                chainedError = e.errorValue
                lastException = e
            } catch (e: ai.tenum.lua.vm.errorhandling.LuaRuntimeError) {
                // For runtime errors (from validation), create a formatted error message
                chainedError =
                    ai.tenum.lua.runtime
                        .LuaString(e.message ?: "error in close")
                lastException = e
            }
        }

        // If there's a final error, re-throw the last exception
        lastException?.let { throw it }
    }

    /**
     * Mark a register as having a to-be-closed variable.
     *
     * According to Lua 5.4 semantics:
     * - nil and false are allowed (won't be closed)
     * - Values with __close metamethod are allowed
     * - Anything else throws an error "variable got a non-closable value"
     */
    fun markToBeClosedVar(registerIndex: Int) {
        val value = registers[registerIndex]

        // nil and false are allowed but won't be closed
        if (value is ai.tenum.lua.runtime.LuaNil ||
            (value is ai.tenum.lua.runtime.LuaBoolean && !value.value)
        ) {
            return
        }

        // Check for __close metamethod
        val closeFun = getCloseMetamethod(value)
        if (closeFun != null) {
            toBeClosedVars.add(registerIndex to value)
        } else {
            // Error: value is not closable
            // Try to get the variable name from proto for better error messages
            val varName = proto.localVars.find { it.register == registerIndex }?.name ?: "?"
            throw ai.tenum.lua.vm.errorhandling.LuaRuntimeError(
                "variable '$varName' got a non-closable value",
            )
        }
    }

    /**
     * Check if execution has more instructions.
     */
    fun hasMoreInstructions(): Boolean = pc < instructions.size

    /**
     * Get current instruction and advance PC.
     */
    fun fetchAndAdvance() = instructions[pc++]
}
