package ai.tenum.lua.compiler.bytecode

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.vm.OpCode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test suite for InstructionEncoder encode/decode operations.
 * Verifies round-trip encoding and proper bit masking at boundaries.
 */
class InstructionEncoderTest {
    /**
     * Test basic round-trip encoding/decoding with simple values.
     */
    @Test
    fun testRoundTripBasic() {
        val instruction = Instruction(OpCode.MOVE, a = 1, b = 2, c = 3)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.MOVE, decoded.opcode, "OpCode should match")
        assertEquals(1, decoded.a, "A register should match")
        assertEquals(2, decoded.b, "B operand should match")
        assertEquals(3, decoded.c, "C operand should match")
    }

    /**
     * Test round-trip with zero values.
     */
    @Test
    fun testRoundTripZeros() {
        val instruction = Instruction(OpCode.LOADNIL, a = 0, b = 0, c = 0)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.LOADNIL, decoded.opcode)
        assertEquals(0, decoded.a)
        assertEquals(0, decoded.b)
        assertEquals(0, decoded.c)
    }

    /**
     * Test A register at maximum valid value (8 bits = 0xFF = 255).
     */
    @Test
    fun testRoundTripMaxA() {
        val instruction = Instruction(OpCode.LOADK, a = 0xFF, b = 0, c = 0)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.LOADK, decoded.opcode)
        assertEquals(0xFF, decoded.a, "A should be 255 (max 8-bit value)")
        assertEquals(0, decoded.b)
        assertEquals(0, decoded.c)
    }

    /**
     * Test A register overflow - values beyond 8 bits should be masked.
     */
    @Test
    fun testRoundTripAOverflow() {
        // 0x1FF = 511, but A is 8 bits, so it should be masked to 0xFF (255)
        val instruction = Instruction(OpCode.MOVE, a = 0x1FF, b = 0, c = 0)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.MOVE, decoded.opcode)
        assertEquals(0xFF, decoded.a, "A should be masked to 8 bits (255)")
        assertEquals(0, decoded.b)
        assertEquals(0, decoded.c)
    }

    /**
     * Test B operand at maximum valid value (9 bits = 0x1FF = 511).
     */
    @Test
    fun testRoundTripMaxB() {
        val instruction = Instruction(OpCode.ADD, a = 0, b = 0x1FF, c = 0)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.ADD, decoded.opcode)
        assertEquals(0, decoded.a)
        assertEquals(0x1FF, decoded.b, "B should be 511 (max 9-bit value)")
        assertEquals(0, decoded.c)
    }

    /**
     * Test B operand overflow - values beyond 9 bits should be masked.
     */
    @Test
    fun testRoundTripBOverflow() {
        // 0x3FF = 1023, but B is 9 bits, so it should be masked to 0x1FF (511)
        val instruction = Instruction(OpCode.SUB, a = 0, b = 0x3FF, c = 0)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.SUB, decoded.opcode)
        assertEquals(0, decoded.a)
        assertEquals(0x1FF, decoded.b, "B should be masked to 9 bits (511)")
        assertEquals(0, decoded.c)
    }

    /**
     * Test C operand at maximum valid value (9 bits = 0x1FF = 511).
     */
    @Test
    fun testRoundTripMaxC() {
        val instruction = Instruction(OpCode.MUL, a = 0, b = 0, c = 0x1FF)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.MUL, decoded.opcode)
        assertEquals(0, decoded.a)
        assertEquals(0, decoded.b)
        assertEquals(0x1FF, decoded.c, "C should be 511 (max 9-bit value)")
    }

    /**
     * Test C operand overflow - values beyond 9 bits should be masked.
     */
    @Test
    fun testRoundTripCOverflow() {
        // 0x3FF = 1023, but C is 9 bits, so it should be masked to 0x1FF (511)
        val instruction = Instruction(OpCode.DIV, a = 0, b = 0, c = 0x3FF)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.DIV, decoded.opcode)
        assertEquals(0, decoded.a)
        assertEquals(0, decoded.b)
        assertEquals(0x1FF, decoded.c, "C should be masked to 9 bits (511)")
    }

    /**
     * Test all fields at maximum values simultaneously.
     */
    @Test
    fun testRoundTripAllMax() {
        val instruction = Instruction(OpCode.CALL, a = 0xFF, b = 0x1FF, c = 0x1FF)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.CALL, decoded.opcode)
        assertEquals(0xFF, decoded.a, "A should be 255")
        assertEquals(0x1FF, decoded.b, "B should be 511")
        assertEquals(0x1FF, decoded.c, "C should be 511")
    }

    /**
     * Test all fields with overflow values - should be masked properly.
     */
    @Test
    fun testRoundTripAllOverflow() {
        // All values exceed their bit widths
        val instruction = Instruction(OpCode.RETURN, a = 0x3FF, b = 0x7FF, c = 0xFFF)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        assertEquals(OpCode.RETURN, decoded.opcode)
        assertEquals(0xFF, decoded.a, "A should be masked to 8 bits")
        assertEquals(0x1FF, decoded.b, "B should be masked to 9 bits")
        assertEquals(0x1FF, decoded.c, "C should be masked to 9 bits")
    }

    /**
     * Test boundary values for A: one below max, at max, one above (overflow).
     */
    @Test
    fun testRoundTripABoundaries() {
        // A = 254 (0xFE)
        val instr1 = Instruction(OpCode.LOADK, a = 0xFE, b = 0, c = 0)
        val decoded1 = InstructionEncoder.decode(InstructionEncoder.encode(instr1))
        assertEquals(0xFE, decoded1.a)

        // A = 255 (0xFF) - max
        val instr2 = Instruction(OpCode.LOADK, a = 0xFF, b = 0, c = 0)
        val decoded2 = InstructionEncoder.decode(InstructionEncoder.encode(instr2))
        assertEquals(0xFF, decoded2.a)

        // A = 256 (0x100) - overflow, should mask to 0
        val instr3 = Instruction(OpCode.LOADK, a = 0x100, b = 0, c = 0)
        val decoded3 = InstructionEncoder.decode(InstructionEncoder.encode(instr3))
        assertEquals(0, decoded3.a)
    }

    /**
     * Test boundary values for B: one below max, at max, one above (overflow).
     */
    @Test
    fun testRoundTripBBoundaries() {
        // B = 510 (0x1FE)
        val instr1 = Instruction(OpCode.ADD, a = 0, b = 0x1FE, c = 0)
        val decoded1 = InstructionEncoder.decode(InstructionEncoder.encode(instr1))
        assertEquals(0x1FE, decoded1.b)

        // B = 511 (0x1FF) - max
        val instr2 = Instruction(OpCode.ADD, a = 0, b = 0x1FF, c = 0)
        val decoded2 = InstructionEncoder.decode(InstructionEncoder.encode(instr2))
        assertEquals(0x1FF, decoded2.b)

        // B = 512 (0x200) - overflow, should mask to 0
        val instr3 = Instruction(OpCode.ADD, a = 0, b = 0x200, c = 0)
        val decoded3 = InstructionEncoder.decode(InstructionEncoder.encode(instr3))
        assertEquals(0, decoded3.b)
    }

    /**
     * Test boundary values for C: one below max, at max, one above (overflow).
     */
    @Test
    fun testRoundTripCBoundaries() {
        // C = 510 (0x1FE)
        val instr1 = Instruction(OpCode.MUL, a = 0, b = 0, c = 0x1FE)
        val decoded1 = InstructionEncoder.decode(InstructionEncoder.encode(instr1))
        assertEquals(0x1FE, decoded1.c)

        // C = 511 (0x1FF) - max
        val instr2 = Instruction(OpCode.MUL, a = 0, b = 0, c = 0x1FF)
        val decoded2 = InstructionEncoder.decode(InstructionEncoder.encode(instr2))
        assertEquals(0x1FF, decoded2.c)

        // C = 512 (0x200) - overflow, should mask to 0
        val instr3 = Instruction(OpCode.MUL, a = 0, b = 0, c = 0x200)
        val decoded3 = InstructionEncoder.decode(InstructionEncoder.encode(instr3))
        assertEquals(0, decoded3.c)
    }

    /**
     * Test different opcodes to ensure opcode encoding doesn't interfere with operands.
     */
    @Test
    fun testRoundTripDifferentOpcodes() {
        val opcodes =
            listOf(
                OpCode.MOVE,
                OpCode.LOADK,
                OpCode.GETGLOBAL,
                OpCode.SETGLOBAL,
                OpCode.ADD,
                OpCode.CALL,
                OpCode.RETURN,
                OpCode.JMP,
            )

        for (opcode in opcodes) {
            val instruction = Instruction(opcode, a = 10, b = 20, c = 30)
            val decoded = InstructionEncoder.decode(InstructionEncoder.encode(instruction))

            assertEquals(opcode, decoded.opcode, "OpCode $opcode should match")
            assertEquals(10, decoded.a, "A should match for $opcode")
            assertEquals(20, decoded.b, "B should match for $opcode")
            assertEquals(30, decoded.c, "C should match for $opcode")
        }
    }

    /**
     * Test that negative values are properly masked (treated as unsigned).
     */
    @Test
    fun testRoundTripNegativeValues() {
        // Negative values should be masked to their unsigned equivalents
        val instruction = Instruction(OpCode.MOVE, a = -1, b = -1, c = -1)
        val encoded = InstructionEncoder.encode(instruction)
        val decoded = InstructionEncoder.decode(encoded)

        // -1 masked to 8 bits = 0xFF (255)
        // -1 masked to 9 bits = 0x1FF (511)
        assertEquals(OpCode.MOVE, decoded.opcode)
        assertEquals(0xFF, decoded.a, "Negative A should be masked to 8 bits")
        assertEquals(0x1FF, decoded.b, "Negative B should be masked to 9 bits")
        assertEquals(0x1FF, decoded.c, "Negative C should be masked to 9 bits")
    }

    /**
     * Test edge case: A at edge, B and C at edges.
     */
    @Test
    fun testRoundTripMixedEdgeValues() {
        // Mix of boundary values
        val instruction = Instruction(OpCode.SETTABLE, a = 0xFF, b = 0x1FF, c = 0)
        val decoded = InstructionEncoder.decode(InstructionEncoder.encode(instruction))

        assertEquals(OpCode.SETTABLE, decoded.opcode)
        assertEquals(0xFF, decoded.a)
        assertEquals(0x1FF, decoded.b)
        assertEquals(0, decoded.c)
    }
}
