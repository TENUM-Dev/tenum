package ai.tenum.lua.compiler.helper

/**
 * A strictly stack-based register allocator for the Lua compiler, mimicking the original Lua implementation.
 *
 * This allocator manages a simple stack. Registers are allocated from the top and must be freed
 * in reverse order of allocation (LIFO). This simplifies the allocation logic significantly but
 * places the burden of correct usage on the compiler stages.
 *
 * - **`stackTop`**: A pointer to the next available register slot. All allocations happen here.
 * - **`maxStackSize`**: A high-water mark of `stackTop`, representing the maximum number of registers
 *   needed for the function.
 */
class RegisterAllocator : IRegisterAllocator {
    /**
     * Points to the next available register. This is the top of the virtual stack.
     */
    var stackTop = 0

    /**
     * The high-water mark of `stackTop`. This corresponds to the `maxstacksize` in the Lua Proto.
     */
    private var _maxStackSize = 0

    companion object {
        /**
         * Maximum number of registers supported by Lua bytecode format (A field is 8 bits: 0-255).
         */
        const val MAX_REGISTERS = 256
    }

    override val usedCount: Int
        get() = stackTop

    override val maxStackSize: Int
        get() = _maxStackSize

    override fun reset() {
        stackTop = 0
        _maxStackSize = 0
    }

    private fun updateMaxStack() {
        if (stackTop > _maxStackSize) {
            _maxStackSize = stackTop
        }
    }

    /**
     * Acknowledges that the last `count` temporary registers on the stack are now considered
     * local variables. In a pure stack model, this doesn't move data but is a conceptual
     * promotion. It returns the base register index of the block of new locals.
     */
    fun promoteTempsToLocals(count: Int): Int {
        check(stackTop >= count) { "Cannot promote $count temps, only $stackTop are on the stack." }
        // The "promotion" is conceptual. The registers are already on the stack.
        // We just return the base index of this block of new locals.
        return stackTop - count
    }

    /**
     * Manually adjusts the stack top. This is dangerous and should only be used
     * when handling multi-return function calls where the number of results is
     * determined at runtime.
     */
    fun adjustStackTop(newTop: Int) {
        if (newTop > MAX_REGISTERS) {
            error("function or expression requires too many registers (limit is $MAX_REGISTERS, attempting to set stackTop to $newTop)")
        }
        stackTop = newTop
        updateMaxStack()
    }

    private fun allocateBlock(count: Int): List<Int> {
        if (count == 0) return emptyList()
        val base = stackTop
        if (stackTop + count > MAX_REGISTERS) {
            error(
                "function or expression requires too many registers (limit is $MAX_REGISTERS, attempting to allocate ${stackTop + count})",
            )
        }
        stackTop += count
        updateMaxStack()
        return (base until stackTop).toList()
    }

    override fun allocateLocal(): Int = allocateLocals(1).first()

    override fun allocateLocals(count: Int): List<Int> = allocateBlock(count)

    override fun freeLocal() {
        freeLocals(1)
    }

    override fun freeLocals(count: Int) {
        if (count == 0) return
        check(stackTop >= count) { "Cannot free $count locals, only $stackTop are on the stack." }
        stackTop -= count
    }

    override fun allocateTemp(): Int {
        if (stackTop >= MAX_REGISTERS) {
            error("function or expression requires too many registers (limit is $MAX_REGISTERS, attempting to allocate register $stackTop)")
        }
        val reg = stackTop
        stackTop++
        updateMaxStack()
        return reg
    }

    override fun freeTemp(reg: Int) {
        // In a strict stack model, we can only free the top-most register.
        check(reg == stackTop - 1) { "Trying to free register $reg which is not at the top of the stack ($stackTop)." }
        stackTop--
    }

    override fun <T> withTempRegister(block: (reg: Int) -> T): T {
        val reg = allocateTemp()
        return try {
            block(reg)
        } finally {
            try {
                freeTemp(reg)
            } catch (e: IllegalStateException) {
                // Suppress cleanup errors if we're already in an exception state
            }
        }
    }

    override fun <T> withTempRegisters(
        count: Int,
        block: (regs: List<Int>) -> T,
    ): T {
        if (count == 0) return block(emptyList())
        val regs = List(count) { allocateTemp() }
        return try {
            block(regs)
        } finally {
            // Free in reverse order of allocation
            regs.asReversed().forEach {
                try {
                    freeTemp(it)
                } catch (e: IllegalStateException) {
                    // Suppress cleanup errors
                }
            }
        }
    }

    override fun allocateContiguous(count: Int): List<Int> = allocateBlock(count)

    override fun freeContiguous(regs: List<Int>) {
        if (regs.isEmpty()) return
        // The block must be at the top of the stack.
        check(regs.lastOrNull() == stackTop - 1) { "Contiguous block is not at the top of the stack." }
        stackTop -= regs.size
    }

    override fun <T> withContiguousRegisters(
        count: Int,
        block: (regs: List<Int>) -> T,
    ): T {
        val regs = allocateContiguous(count)
        return try {
            block(regs)
        } finally {
            try {
                freeContiguous(regs)
            } catch (e: IllegalStateException) {
                // Suppress cleanup errors if we're already in an exception state
                // This happens when register allocation fails partway through
            }
        }
    }
}
