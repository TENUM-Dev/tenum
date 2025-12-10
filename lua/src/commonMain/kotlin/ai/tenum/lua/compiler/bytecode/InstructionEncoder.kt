package ai.tenum.lua.compiler.bytecode

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.vm.OpCode

/**
 * Encodes and decodes Lua bytecode instructions (32-bit integer format).
 */
object InstructionEncoder {
    /**
     * Encode an Instruction to a 32-bit integer (Lua bytecode format).
     * Real Lua uses a more complex encoding; this is a simplified version.
     */
    fun encode(instr: Instruction): Int {
        // opcode in lower 6 bits, args in remaining bits
        // Mask values to ensure unsigned behavior (A: 8 bits, B/C: 9 bits)
        return instr.opcode.ordinal or
            ((instr.a and 0xFF) shl 6) or
            ((instr.b and 0x1FF) shl 14) or
            ((instr.c and 0x1FF) shl 23)
    }

    /**
     * Decode a 32-bit integer into an Instruction.
     */
    fun decode(value: Int): Instruction {
        val opcode = OpCode.entries[value and 0x3F]
        val a = (value shr 6) and 0xFF
        val b = (value shr 14) and 0x1FF
        val c = (value shr 23) and 0x1FF
        return Instruction(opcode, a, b, c)
    }
}
