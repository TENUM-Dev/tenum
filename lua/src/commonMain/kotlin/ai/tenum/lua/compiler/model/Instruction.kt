package ai.tenum.lua.compiler.model

import ai.tenum.lua.vm.OpCode

/**
 * Represents a bytecode instruction with its operands
 */
data class Instruction(
    val opcode: OpCode,
    val a: Int = 0,
    val b: Int = 0,
    val c: Int = 0,
    val bx: Int = 0,
) {
    override fun toString(): String =
        when {
            bx != 0 -> "$opcode A=$a Bx=$bx"
            b != 0 || c != 0 -> "$opcode A=$a B=$b C=$c"
            a != 0 -> "$opcode A=$a"
            else -> "$opcode"
        }
}
