package ai.tenum.lua.vm.execution

import ai.tenum.lua.runtime.LuaValue

/**
 * Domain object for collecting variable-length arguments from registers.
 *
 * Encapsulates Lua's bytecode encoding where:
 * - Positive count: Fixed number of arguments/results
 * - Zero (encoded as B=0 or C=0): Variable count up to frame.top
 * - Count-1 is used (so B=1 means 0 args, B=2 means 1 arg, etc.)
 *
 * This pattern appears in CALL, TAILCALL, RETURN, and coroutine resume.
 */
class ArgumentCollector(
    private val registers: MutableList<LuaValue<*>>,
    private val frame: ExecutionFrame,
) {
    /**
     * Collect function arguments from registers.
     *
     * Used by CALL and TAILCALL opcodes.
     *
     * @param startReg First register containing an argument (typically A+1 for function at A)
     * @param encodedCount B field from instruction (0 means variable, >0 means encodedCount-1 args)
     * @return List of argument values
     */
    fun collectArgs(
        startReg: Int,
        encodedCount: Int,
    ): List<LuaValue<*>> {
        val count = encodedCount - 1
        return when {
            count >= 0 -> {
                // Fixed count: R[startReg] to R[startReg+count-1]
                (0 until count).map { registers[startReg + it] }
            }
            frame.top > startReg -> {
                // Variable count (B=0): R[startReg] to frame.top-1
                (0 until (frame.top - startReg)).map { registers[startReg + it] }
            }
            else -> emptyList()
        }
    }

    /**
     * Collect return values from registers.
     *
     * Used by RETURN opcode.
     *
     * @param startReg First register containing a return value
     * @param encodedCount B field from instruction (0 means variable, 1 means 0 results, >1 means encodedCount-1 results)
     * @return List of return values
     */
    fun collectResults(
        startReg: Int,
        encodedCount: Int,
    ): List<LuaValue<*>> {
        val count = encodedCount - 1
        return when {
            count == 0 -> emptyList() // B=1 means return 0 values
            count > 0 -> {
                // Fixed count: R[startReg] to R[startReg+count-1]
                (0 until count).map { registers[startReg + it] }
            }
            else -> {
                // Variable count (B=0): R[startReg] to frame.top-1
                val resultCount = frame.top - startReg
                (0 until resultCount).map { registers[startReg + it] }
            }
        }
    }
}
