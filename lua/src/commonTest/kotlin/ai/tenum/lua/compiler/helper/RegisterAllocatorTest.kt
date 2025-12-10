package ai.tenum.lua.compiler.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegisterAllocatorTest {
    // ------------------------------------------------------------
    // BASIC ALLOCATION & STACK TOP
    // ------------------------------------------------------------

    @Test
    fun `allocate methods increase stack top sequentially`() {
        val ra = RegisterAllocator()
        assertEquals(0, ra.allocateTemp()) // stackTop = 1
        assertEquals(listOf(1, 2), ra.allocateLocals(2)) // stackTop = 3
        assertEquals(listOf(3, 4, 5), ra.allocateContiguous(3)) // stackTop = 6

        assertEquals(6, ra.usedCount)
        assertEquals(6, ra.maxStackSize)
    }

    @Test
    fun `maxStackSize is a high-water mark`() {
        val ra = RegisterAllocator()
        ra.allocateLocals(5) // top=5, max=5
        ra.freeLocals(3) // top=2, max=5
        ra.allocateTemps(4) // top=6, max=6
        ra.freeTemps(4) // top=2, max=6

        assertEquals(6, ra.maxStackSize)
    }

    // ------------------------------------------------------------
    // FREEING & LIFO (Last-In, First-Out) DISCIPLINE
    // ------------------------------------------------------------

    @Test
    fun `freeTemp requires freeing the top of the stack`() {
        val ra = RegisterAllocator()
        ra.allocateTemp() // reg 0
        ra.allocateTemp() // reg 1

        // Cannot free 0 because 1 is on top
        assertFailsWith<IllegalStateException> {
            ra.freeTemp(0)
        }

        // Must free in LIFO order
        ra.freeTemp(1)
        ra.freeTemp(0)
        assertEquals(0, ra.usedCount)
    }

    @Test
    fun `freeContiguous requires block to be at the top`() {
        val ra = RegisterAllocator()
        val block = ra.allocateContiguous(2) // 0, 1
        ra.allocateTemp() // 2

        // Cannot free block {0, 1} because 2 is on top
        assertFailsWith<IllegalStateException> {
            ra.freeContiguous(block)
        }

        ra.freeTemp(2) // Now block is at the top
        ra.freeContiguous(block)
        assertEquals(0, ra.usedCount)
    }

    @Test
    fun `freeLocals reduces the stack top`() {
        val ra = RegisterAllocator()
        ra.allocateLocals(3) // 0, 1, 2
        ra.freeLocals(2) // frees 1, 2 -> WRONG, should free from top
        assertEquals(1, ra.usedCount)

        val next = ra.allocateTemp()
        assertEquals(1, next, "Next allocation should be at new stack top")
    }

    @Test
    fun `freeing more locals than allocated fails`() {
        val ra = RegisterAllocator()
        ra.allocateLocals(2)
        assertFailsWith<IllegalStateException> {
            ra.freeLocals(3)
        }
    }

    // ------------------------------------------------------------
    // 'with' HELPERS
    // ------------------------------------------------------------

    @Test
    fun `withTempRegister allocates and frees correctly`() {
        val ra = RegisterAllocator()
        ra.allocateLocals(2) // 0, 1

        ra.withTempRegister { reg ->
            assertEquals(2, reg)
            assertEquals(3, ra.usedCount)
        }

        assertEquals(2, ra.usedCount, "Should be back to 2 after withTempRegister")
    }

    @Test
    fun `withTempRegisters frees in reverse order`() {
        val ra = RegisterAllocator()
        ra.withTempRegisters(3) { regs ->
            assertEquals(listOf(0, 1, 2), regs)
        }
        assertEquals(0, ra.usedCount)
    }

    @Test
    fun `nested with helpers work correctly`() {
        val ra = RegisterAllocator()
        ra.withTempRegister { r1 ->
            assertEquals(0, r1)
            ra.withTempRegister { r2 ->
                assertEquals(1, r2)
                assertEquals(2, ra.usedCount)
            }
            assertEquals(1, ra.usedCount)
        }
        assertEquals(0, ra.usedCount)
    }

    // ------------------------------------------------------------
    // MIXED ALLOCATION
    // ------------------------------------------------------------

    @Test
    fun `locals and temps interaction`() {
        val ra = RegisterAllocator()
        ra.allocateLocals(2) // Locals: 0, 1. top=2

        val temp1 = ra.allocateTemp() // Temp: 2. top=3
        assertEquals(2, temp1)
        assertEquals(3, ra.maxStackSize)

        ra.withTempRegisters(2) { temps ->
            // Temps: 3, 4. top=5
            assertEquals(listOf(3, 4), temps)
            assertEquals(5, ra.maxStackSize)
        } // Frees 4, 3. top=3

        assertEquals(3, ra.usedCount) // Locals + temp1

        // Must free temp1 before freeing locals below it
        ra.freeTemp(temp1) // Frees 2. top=2
        assertEquals(2, ra.usedCount)

        ra.freeLocals(2) // Frees 1, 0. top=0
        assertEquals(0, ra.usedCount)
        assertEquals(5, ra.maxStackSize) // Max stack is not reduced
    }
}

// Helper extensions for testing
private fun RegisterAllocator.allocateTemps(count: Int): List<Int> = List(count) { allocateTemp() }

private fun RegisterAllocator.freeTemps(count: Int) {
    repeat(count) {
        freeTemp(this.usedCount - 1)
    }
}
