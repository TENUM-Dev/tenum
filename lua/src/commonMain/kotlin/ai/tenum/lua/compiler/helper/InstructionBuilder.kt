package ai.tenum.lua.compiler.helper

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.vm.OpCode

class InstructionBuilder {
    val instructions = mutableListOf<Instruction>()

    fun emit(
        opcode: OpCode,
        a: Int = 0,
        b: Int = 0,
        c: Int = 0,
    ) {
        instructions += Instruction(opcode, a, b, c)
    }

    fun build(): List<Instruction> = instructions
}
