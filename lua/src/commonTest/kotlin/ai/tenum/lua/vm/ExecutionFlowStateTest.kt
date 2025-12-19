package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.execution.ExecutionFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ExecutionFlowState which groups execution control state:
 * - currentEnvUpvalue: Current environment upvalue
 * - activeExecutionFrame: Currently executing frame
 * - nextCallIsCloseMetamethod: Flag for close metamethod calls
 */
class ExecutionFlowStateTest {
    private fun createDummyFrame(): ExecutionFrame =
        ExecutionFrame(
            proto = createDummyProto(),
            initialArgs = emptyList(),
            upvalues = emptyList(),
        )

    @Test
    fun testInitialStateIsEmpty() {
        val state = ExecutionFlowState()

        assertNull(state.currentEnvUpvalue)
        assertNull(state.activeExecutionFrame)
        assertFalse(state.nextCallIsCloseMetamethod)

        // Safety counter to prevent infinite loops
        var safetyCounter = 0
        while (state.currentEnvUpvalue != null && safetyCounter < 100) {
            safetyCounter++
        }
        assertTrue(safetyCounter < 100, "Safety counter exceeded - unexpected initial state")
    }

    @Test
    fun testSetCurrentEnvUpvalue() {
        val state = ExecutionFlowState()
        val registers = mutableListOf<LuaValue<*>>()
        val upvalue = Upvalue(registerIndex = 5, registers = registers)

        state.setCurrentEnvUpvalue(upvalue)

        assertEquals(upvalue, state.currentEnvUpvalue)
    }

    @Test
    fun testClearCurrentEnvUpvalue() {
        val state = ExecutionFlowState()
        val registers = mutableListOf<LuaValue<*>>()
        val upvalue = Upvalue(registerIndex = 5, registers = registers)
        state.setCurrentEnvUpvalue(upvalue)

        state.setCurrentEnvUpvalue(null)

        assertNull(state.currentEnvUpvalue)
    }

    @Test
    fun testSetActiveExecutionFrame() {
        val state = ExecutionFlowState()
        val frame = createDummyFrame()

        state.setActiveExecutionFrame(frame)

        assertEquals(frame, state.activeExecutionFrame)
    }

    @Test
    fun testClearActiveExecutionFrame() {
        val state = ExecutionFlowState()
        val frame = createDummyFrame()
        state.setActiveExecutionFrame(frame)

        state.setActiveExecutionFrame(null)

        assertNull(state.activeExecutionFrame)
    }

    @Test
    fun testSetNextCallIsCloseMetamethod() {
        val state = ExecutionFlowState()

        state.setNextCallIsCloseMetamethod(true)

        assertTrue(state.nextCallIsCloseMetamethod)
    }

    @Test
    fun testClearNextCallIsCloseMetamethod() {
        val state = ExecutionFlowState()
        state.setNextCallIsCloseMetamethod(true)

        state.setNextCallIsCloseMetamethod(false)

        assertFalse(state.nextCallIsCloseMetamethod)
    }

    @Test
    fun testSnapshotAndRestore() {
        val state = ExecutionFlowState()
        val registers = mutableListOf<LuaValue<*>>()
        val upvalue = Upvalue(registerIndex = 5, registers = registers)
        val frame = createDummyFrame()

        state.setCurrentEnvUpvalue(upvalue)
        state.setActiveExecutionFrame(frame)
        state.setNextCallIsCloseMetamethod(true)

        val snapshot = state.snapshot()

        // Modify state
        state.setCurrentEnvUpvalue(null)
        state.setActiveExecutionFrame(null)
        state.setNextCallIsCloseMetamethod(false)

        // Verify state is cleared
        assertNull(state.currentEnvUpvalue)
        assertNull(state.activeExecutionFrame)
        assertFalse(state.nextCallIsCloseMetamethod)

        // Restore from snapshot
        state.restore(snapshot)

        assertEquals(upvalue, state.currentEnvUpvalue)
        assertEquals(frame, state.activeExecutionFrame)
        assertTrue(state.nextCallIsCloseMetamethod)
    }

    @Test
    fun testSnapshotPreservesNullValues() {
        val state = ExecutionFlowState()

        val snapshot = state.snapshot()

        assertNull(snapshot.currentEnvUpvalue)
        assertNull(snapshot.activeExecutionFrame)
        assertFalse(snapshot.nextCallIsCloseMetamethod)
    }

    @Test
    fun testMultipleSnapshotsAreIndependent() {
        val state = ExecutionFlowState()
        val registers1 = mutableListOf<LuaValue<*>>()
        val registers2 = mutableListOf<LuaValue<*>>()
        val upvalue1 = Upvalue(registerIndex = 5, registers = registers1)
        val upvalue2 = Upvalue(registerIndex = 10, registers = registers2)

        state.setCurrentEnvUpvalue(upvalue1)
        val snapshot1 = state.snapshot()

        state.setCurrentEnvUpvalue(upvalue2)
        val snapshot2 = state.snapshot()

        // Verify snapshots are different
        assertEquals(upvalue1, snapshot1.currentEnvUpvalue)
        assertEquals(upvalue2, snapshot2.currentEnvUpvalue)

        // Restore first snapshot
        state.restore(snapshot1)
        assertEquals(upvalue1, state.currentEnvUpvalue)

        // Restore second snapshot
        state.restore(snapshot2)
        assertEquals(upvalue2, state.currentEnvUpvalue)
    }

    @Test
    fun testHasActiveFrameReturnsFalseWhenNull() {
        val state = ExecutionFlowState()

        assertFalse(state.hasActiveFrame())
    }

    @Test
    fun testHasActiveFrameReturnsTrueWhenSet() {
        val state = ExecutionFlowState()
        val frame = createDummyFrame()

        state.setActiveExecutionFrame(frame)

        assertTrue(state.hasActiveFrame())
    }
}
