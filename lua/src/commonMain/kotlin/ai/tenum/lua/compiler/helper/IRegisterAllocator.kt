package ai.tenum.lua.compiler.helper

/**
 * Interface for a register allocator compatible with a Lua VM compiler.
 *
 * The allocator manages a virtual stack frame for a single function compilation.
 * It distinguishes between different types of allocations:
 *
 * 1.  **Local Variables**: Registers allocated for declared local variables. They are typically
 *     contiguous and have a lifetime tied to their lexical scope.
 * 2.  **Temporary Registers**: Registers for intermediate results in expressions. They are
 *     short-lived and can be reused as soon as they are no longer needed.
 * 3.  **Contiguous Blocks**: Special-purpose contiguous register blocks required for language
 *     features like multi-return function calls and `for` loops.
 */
interface IRegisterAllocator {
    // ─────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────

    /** The total number of registers currently in use (locals + temps). */
    val usedCount: Int

    /** The maximum number of registers used at any point during compilation (high-water mark). */
    val maxStackSize: Int

    // ─────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────

    /** Resets the allocator to its initial state, ready for compiling a new function. */
    fun reset()

    // ─────────────────────────────────────────────
    //  LOCAL VARIABLE ALLOCATION
    // ─────────────────────────────────────────────

    /**
     * Allocates a contiguous block of registers for local variables.
     * These registers are considered "in scope" until explicitly freed.
     *
     * @param count The number of local variables to allocate.
     * @return A list of register indices for the newly allocated locals.
     */
    fun allocateLocals(count: Int): List<Int>

    fun allocateLocal(): Int

    /**
     * Frees a specified number of the most recently allocated local variables.
     * This is called when a lexical scope is exited.
     *
     * @param count The number of local variables to free.
     */
    fun freeLocals(count: Int)

    fun freeLocal()

    // ─────────────────────────────────────────────
    //  TEMPORARY REGISTER ALLOCATION
    // ─────────────────────────────────────────────

    /**
     * Allocates a single temporary register for an intermediate expression value.
     * It should be freed with `freeTemp()` as soon as it's no longer needed.
     *
     * @return The index of the allocated temporary register.
     */
    fun allocateTemp(): Int

    /**
     * Frees a temporary register, making it available for reuse.
     *
     * @param reg The register index to free.
     */
    fun freeTemp(reg: Int)

    /**
     * Executes a block of code with a temporarily allocated register,
     * ensuring it is freed automatically upon completion.
     *
     * @param block The code block to execute, which receives the allocated register index.
     */
    fun <T> withTempRegister(block: (reg: Int) -> T): T

    /**
     * Executes a block with a list of temporarily allocated registers.
     *
     * @param count The number of temporary registers to allocate.
     * @param block The code block to execute, which receives the list of register indices.
     */
    fun <T> withTempRegisters(
        count: Int,
        block: (regs: List<Int>) -> T,
    ): T

    // ─────────────────────────────────────────────
    //  CONTIGUOUS BLOCK ALLOCATION
    // ─────────────────────────────────────────────

    /**
     * Allocates a *contiguous* block of temporary registers, typically required for
     * function call arguments and results.
     *
     * These are allocated at the top of the current stack.
     *
     * @param count The number of contiguous registers to allocate.
     * @return A list of contiguous register indices.
     */
    fun allocateContiguous(count: Int): List<Int>

    /**
     * Frees a contiguous block of registers previously allocated with `allocateContiguous`.
     *
     * @param regs The list of registers to free.
     */
    fun freeContiguous(regs: List<Int>)

    /**
     * Executes a block with a contiguous block of registers, ensuring they are freed upon completion.
     *
     * @param count The number of registers in the block.
     * @param block The code block to execute.
     */
    fun <T> withContiguousRegisters(
        count: Int,
        block: (regs: List<Int>) -> T,
    ): T
}
