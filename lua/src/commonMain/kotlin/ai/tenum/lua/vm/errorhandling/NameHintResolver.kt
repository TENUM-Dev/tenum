package ai.tenum.lua.vm.errorhandling

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.OpCode

/**
 * Resolves human-readable name hints for registers by analyzing bytecode.
 *
 * This improves error messages by identifying the source of values:
 * - "global 'foo'" for values loaded from globals
 * - "field 'bar'" for table field accesses
 * - "upvalue 'x'" for closure upvalues
 * - "local 'y'" for local variables
 * - "method 'z'" for method calls
 *
 * The resolver traces back through the instruction stream to find where
 * a register was last assigned a meaningful value.
 */
internal class NameHintResolver {
    companion object {
        // In Lua bytecode, RK operands encode constants as (index + 256)
        private const val CONSTANT_OFFSET = 256

        // Look back up to 20 instructions to find where a register was set
        // This window balances accuracy (recent assignments) with performance
        private const val LOOKBACK_WINDOW = 20
    }

    /**
     * Get a name hint for a register by analyzing recent bytecode instructions.
     *
     * @param registerIndex The register to trace
     * @param proto The function prototype containing instructions and constants
     * @param currentPc The current program counter
     * @return A human-readable hint like "global 'x'" or null if no meaningful name found
     */
    fun getRegisterNameHint(
        registerIndex: Int,
        proto: Proto,
        currentPc: Int,
    ): String? {
        // First, find the earliest write to this register (for short-circuit detection)
        val startPc = maxOf(0, currentPc - LOOKBACK_WINDOW)
        var earliestWritePc = Int.MAX_VALUE
        for (i in startPc until currentPc) {
            val instr = proto.instructions.getOrNull(i) ?: continue
            if (instr.a == registerIndex) {
                earliestWritePc = minOf(earliestWritePc, i)
            }
        }

        return getRegisterNameHintInternal(registerIndex, registerIndex, proto, currentPc, earliestWritePc)
    }

    /**
     * Internal helper for getRegisterNameHint that tracks the original register
     * through MOVE chains to properly detect short-circuit expressions.
     *
     * @param registerIndex The current register being traced
     * @param originalRegister The original register we started tracing from
     * @param proto The function prototype
     * @param currentPc The current program counter
     * @param minPcToCheck The minimum PC to check for TEST instructions (used to track earliest write to original register)
     */
    private fun getRegisterNameHintInternal(
        registerIndex: Int,
        originalRegister: Int,
        proto: Proto,
        currentPc: Int,
        minPcToCheck: Int = Int.MAX_VALUE,
    ): String? {
        // FIRST: Check if this register is an active local variable
        // Local variables have the highest priority since they're explicitly named in source
        val localName = getLocalVarName(registerIndex, proto, currentPc)
        if (localName != null) {
            return "local '$localName'"
        }

        // SECOND: Trace back through instructions to find value sources
        traceInstructionsForRegister(registerIndex, proto, currentPc) { i, instr ->
            // Check what kind of value was written
            when (instr.opcode) {
                OpCode.GETGLOBAL -> {
                    // Before returning a name hint, check if the ORIGINAL register was tested by TEST/TESTSET
                    // Check from the earliest write to the original register up to currentPc
                    if (minPcToCheck < Int.MAX_VALUE && wasTestedByShortCircuit(originalRegister, proto, minPcToCheck + 1, currentPc)) {
                        return null
                    }
                    val globalName = (proto.constants.getOrNull(instr.b) as? LuaString)?.value
                    return if (globalName != null) "global '$globalName'" else null
                }
                OpCode.GETTABLE -> {
                    // Before returning a name hint, check if the ORIGINAL register was tested by TEST/TESTSET
                    // Check from the earliest write to the original register up to currentPc
                    if (minPcToCheck < Int.MAX_VALUE && wasTestedByShortCircuit(originalRegister, proto, minPcToCheck + 1, currentPc)) {
                        return null
                    }
                    // In Lua 5.2+: globals use _ENV["name"] (GETTABLE), fields use table["name"] (GETTABLE)
                    // Distinguish by checking if table register contains _ENV upvalue
                    val tableReg = instr.b
                    val isEnvAccess = isEnvRegister(tableReg, proto, i)

                    val key =
                        if (instr.c >= CONSTANT_OFFSET) {
                            // Key is RK-encoded constant (index 0-255)
                            proto.constants.getOrNull(instr.c - CONSTANT_OFFSET)
                        } else {
                            // Key is in a register (could be a large constant index loaded via LOADK)
                            // Try to trace back to find if it's a constant value
                            getRegisterConstantValue(instr.c, proto, i)
                        }

                    return if (key is LuaString) {
                        if (isEnvAccess) {
                            "global '${key.value}'"
                        } else {
                            "field '${key.value}'"
                        }
                    } else {
                        null
                    }
                }
                OpCode.GETUPVAL -> {
                    // Before returning a name hint, check if the ORIGINAL register was tested by TEST/TESTSET
                    // Check from the earliest write to the original register up to currentPc
                    if (minPcToCheck < Int.MAX_VALUE && wasTestedByShortCircuit(originalRegister, proto, minPcToCheck + 1, currentPc)) {
                        return null
                    }
                    val upvalInfo = proto.upvalueInfo.getOrNull(instr.b)
                    return if (upvalInfo != null) "upvalue '${upvalInfo.name}'" else null
                }
                OpCode.MOVE -> {
                    // Follow the chain: if R[a] = R[b], trace where R[b] came from
                    // Pass i as the new currentPc to prevent infinite recursion
                    // But pass originalRegister and minPcToCheck through for short-circuit detection
                    return getRegisterNameHintInternal(instr.b, originalRegister, proto, i, minPcToCheck)
                }
                OpCode.SELF -> {
                    // SELF: R[a+1] = R[b]; R[a] = R[b][RK(c)]
                    // Implements method call syntax obj:method() → obj.method(obj)
                    val key =
                        if (instr.c >= CONSTANT_OFFSET) {
                            // Method name is RK-encoded constant (index 0-255)
                            proto.constants.getOrNull(instr.c - CONSTANT_OFFSET)
                        } else {
                            // Method name is in a register (large constant index loaded via LOADK)
                            getRegisterConstantValue(instr.c, proto, i)
                        }
                    return if (key is LuaString) "method '${key.value}'" else null
                }
                // These instructions produce computed values with no meaningful name
                OpCode.LOADK, OpCode.LOADBOOL, OpCode.LOADNIL,
                OpCode.ADD, OpCode.SUB, OpCode.MUL, OpCode.DIV, OpCode.MOD, OpCode.POW,
                OpCode.IDIV, OpCode.BAND, OpCode.BOR, OpCode.BXOR, OpCode.SHL, OpCode.SHR,
                OpCode.UNM, OpCode.BNOT, OpCode.NOT, OpCode.LEN, OpCode.CONCAT,
                OpCode.CALL,
                -> {
                    // These instructions produce computed values with no meaningful name
                    // Since we already checked for locals at the start, just return null
                    return null
                }
                else -> {
                    // Unknown instruction - stop searching
                    return null
                }
            }
        }

        // No meaningful hint found from instruction tracing
        return null
    }

    /**
     * Check if a register was tested by a TEST or TESTSET instruction between two PCs.
     * This is used to detect short-circuit expressions like (a or b) or (a and b).
     *
     * @param registerIndex The register to check
     * @param proto The function prototype
     * @param startPc The PC where the register was written (exclusive)
     * @param endPc The current PC (exclusive)
     * @return true if the register was tested, false otherwise
     */
    private fun wasTestedByShortCircuit(
        registerIndex: Int,
        proto: Proto,
        startPc: Int,
        endPc: Int,
    ): Boolean {
        for (i in startPc until endPc) {
            val instr = proto.instructions.getOrNull(i) ?: continue

            // TEST: tests R[a], used in short-circuit expressions
            if (instr.opcode == OpCode.TEST && instr.a == registerIndex) {
                return true
            }

            // TESTSET: R[a] = R[b] if R[b] passes test
            // If either reading from or writing to our register, it's part of short-circuit logic
            if (instr.opcode == OpCode.TESTSET && (instr.a == registerIndex || instr.b == registerIndex)) {
                return true
            }
        }
        return false
    }

    /**
     * Get the name of a local variable at a given register and PC.
     * Returns null if the register doesn't correspond to an active local variable.
     */
    fun getLocalVarName(
        registerIndex: Int,
        proto: Proto,
        currentPc: Int,
    ): String? {
        // Local variables are stored in registers
        // Find the local variable that occupies this register at the current PC
        for (localVar in proto.localVars) {
            // Check if this local is active at the current PC and occupies the target register
            if (localVar.register == registerIndex &&
                currentPc >= localVar.startPc &&
                currentPc < localVar.endPc
            ) {
                return localVar.name
            }
        }
        return null
    }

    /**
     * Check if a register contains _ENV by tracing back to find where it was loaded from.
     * In Lua 5.2+, global access is implemented as _ENV["name"] where _ENV is upvalue[0].
     * Can also be a local variable named _ENV.
     */
    private fun isEnvRegister(
        registerIndex: Int,
        proto: Proto,
        currentPc: Int,
    ): Boolean {
        // Special case: Check if ANY local named _ENV uses this register
        // We check this FIRST because local _ENV might have startPc equal to currentPc,
        // making the standard getLocalVarName check pass/fail depending on >= vs > comparison
        for (localVar in proto.localVars) {
            if (localVar.name == "_ENV" && localVar.register == registerIndex) {
                // Even if the local isn't "active" yet (currentPc < startPc),
                // if we're accessing from this register, treat it as _ENV for error messages
                // This matches Lua 5.4's behavior where local _ENV shadowing still reports "global"
                return true
            }
        }

        // Check if this register itself is a local variable named _ENV
        val localName = getLocalVarName(registerIndex, proto, currentPc)
        if (localName == "_ENV") {
            return true
        }

        // Trace back to find how this register was set
        traceInstructionsForRegister(registerIndex, proto, currentPc) { i, instr ->
            when (instr.opcode) {
                OpCode.GETUPVAL -> {
                    // Check if this upvalue is _ENV (usually upvalue index 0)
                    val upvalInfo = proto.upvalueInfo.getOrNull(instr.b)
                    return upvalInfo?.name == "_ENV"
                }
                OpCode.MOVE -> {
                    // Follow the chain
                    return isEnvRegister(instr.b, proto, i)
                }
                else -> {
                    // If the register was set by something else, it's not _ENV
                    return false
                }
            }
        }

        return false
    }

    /**
     * Try to get a constant value that was loaded into a register.
     * This handles cases where the constant pool is full and constants are loaded via LOADK.
     *
     * We look back for the MOST RECENT instruction that wrote to the register.
     * If it's a LOADK, we return the constant. Otherwise we return null.
     * We don't use a window because we want the immediate predecessor only.
     */
    private fun getRegisterConstantValue(
        registerIndex: Int,
        proto: Proto,
        currentPc: Int,
    ): LuaString? {
        // Look back for the FIRST (most recent) instruction that writes to this register
        for (i in currentPc - 1 downTo 0) {
            val instr = proto.instructions.getOrNull(i) ?: continue

            // Only process instructions that write to our target register
            if (instr.a != registerIndex) continue

            // Found the most recent write to this register
            when (instr.opcode) {
                OpCode.LOADK -> {
                    // Found a LOADK instruction - return the constant
                    // Note: LOADK uses the 'b' field for constant index, not 'bx'
                    return proto.constants.getOrNull(instr.b) as? LuaString
                }
                OpCode.MOVE -> {
                    // Follow the chain - but use current i as the search point
                    return getRegisterConstantValue(instr.b, proto, i)
                }
                else -> {
                    // Register was set by something else - not a simple constant load
                    return null
                }
            }
        }

        return null
    }

    /**
     * Traces back through instructions to find writes to a specific register.
     * Invokes the block for each instruction that writes to the target register.
     */
    private inline fun traceInstructionsForRegister(
        registerIndex: Int,
        proto: Proto,
        currentPc: Int,
        block: (Int, ai.tenum.lua.compiler.model.Instruction) -> Unit,
    ) {
        val startPc = maxOf(0, currentPc - LOOKBACK_WINDOW)
        for (i in currentPc - 1 downTo startPc) {
            val instr = proto.instructions.getOrNull(i) ?: continue

            // Only process instructions that write to our target register
            if (instr.a != registerIndex) continue

            block(i, instr)
        }
    }
}
