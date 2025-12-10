package ai.tenum.lua.compiler.helper

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.vm.OpCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstructionBuilderTest {
    @Test
    fun emptyBuilder() {
        val builder = InstructionBuilder()

        val result = builder.build()

        assertTrue(result.isEmpty())
        assertEquals(0, builder.instructions.size)
    }

    @Test
    fun emitOne() {
        val builder = InstructionBuilder()

        builder.emit(OpCode.MOVE, 1, 2, 3)
        val result = builder.build()

        assertEquals(1, result.size)
        val instr = result[0]
        assertEquals(OpCode.MOVE, instr.opcode)
        assertEquals(1, instr.a)
        assertEquals(2, instr.b)
        assertEquals(3, instr.c)
    }

    @Test
    fun emitDefaults() {
        val builder = InstructionBuilder()

        builder.emit(OpCode.RETURN)
        val result = builder.build()

        assertEquals(1, result.size)
        val instr = result[0]
        assertEquals(OpCode.RETURN, instr.opcode)
        assertEquals(0, instr.a)
        assertEquals(0, instr.b)
        assertEquals(0, instr.c)
    }

    @Test
    fun orderPreserved() {
        val builder = InstructionBuilder()

        builder.emit(OpCode.MOVE, 0, 1, 0)
        builder.emit(OpCode.LOADK, 1, 2, 0)
        builder.emit(OpCode.ADD, 2, 0, 1)

        val r = builder.build()

        assertEquals(3, r.size)
        assertEquals(Instruction(OpCode.MOVE, 0, 1, 0), r[0])
        assertEquals(Instruction(OpCode.LOADK, 1, 2, 0), r[1])
        assertEquals(Instruction(OpCode.ADD, 2, 0, 1), r[2])
    }

    @Test
    fun buildSnapshots() {
        val builder = InstructionBuilder()

        builder.emit(OpCode.MOVE, 0, 1, 0)
        var r = builder.build()
        assertEquals(1, r.size)
        assertEquals(Instruction(OpCode.MOVE, 0, 1, 0), r[0])

        builder.emit(OpCode.LOADK, 1, 2, 0)
        r = builder.build()
        assertEquals(2, r.size)
        assertEquals(Instruction(OpCode.MOVE, 0, 1, 0), r[0])
        assertEquals(Instruction(OpCode.LOADK, 1, 2, 0), r[1])
    }

    @Test
    fun internalMatchesBuild() {
        val builder = InstructionBuilder()

        builder.emit(OpCode.MOVE, 0, 1, 0)
        builder.emit(OpCode.LOADK, 1, 2, 0)

        val internal = builder.instructions
        val built = builder.build()

        assertEquals(internal.size, built.size)
        assertEquals(internal, built)
    }

    @Test
    fun reuseBuilder() {
        val builder = InstructionBuilder()

        builder.emit(OpCode.MOVE, 0, 1, 0)
        val r1 = builder.build()
        assertEquals(1, r1.size)

        builder.emit(OpCode.RETURN, 0, 0, 0)
        val r2 = builder.build()
        assertEquals(2, r2.size)
        assertEquals(Instruction(OpCode.MOVE, 0, 1, 0), r2[0])
        assertEquals(Instruction(OpCode.RETURN, 0, 0, 0), r2[1])
    }
}
