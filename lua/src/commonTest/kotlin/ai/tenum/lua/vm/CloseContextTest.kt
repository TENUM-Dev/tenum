package ai.tenum.lua.vm

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.CloseResumeState
import ai.tenum.lua.vm.execution.ExecutionFrame
import ai.tenum.lua.vm.execution.OwnerSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloseContextTest {
    private fun createDummyProto(name: String = "test") =
        Proto(
            name = name,
            source = "@$name.lua",
            lineDefined = 0,
            lastLineDefined = 0,
            parameters = emptyList(),
            hasVararg = false,
            maxStackSize = 10,
            instructions = emptyList(),
            constants = emptyList(),
            upvalueInfo = emptyList(),
            localVars = emptyList(),
            lineEvents = emptyList(),
        )

    @Test
    fun testInitialStateIsEmpty() {
        val context = CloseContext()

        assertNull(context.pendingCloseVar)
        assertEquals(0, context.pendingCloseStartReg)
        assertNull(context.pendingCloseOwnerTbc)
        assertEquals(LuaNil, context.pendingCloseErrorArg)
        assertNull(context.pendingCloseContinuation)
        assertNull(context.pendingCloseOwnerFrame)
        assertNull(context.activeCloseResumeState)
        assertNull(context.pendingCloseException)
        assertNull(context.capturedReturnValues)
        assertFalse(context.hasActiveClose())
    }

    @Test
    fun testSetPendingCloseVar() {
        val context = CloseContext()
        val closeVar = 5 to LuaString("resource")

        context.setPendingCloseVar(closeVar, startReg = 3)

        assertEquals(closeVar, context.pendingCloseVar)
        assertEquals(3, context.pendingCloseStartReg)
        assertTrue(context.hasActiveClose())
    }

    @Test
    fun testSetOwnerFrame() {
        val context = CloseContext()
        val proto = createDummyProto("owner")
        val frame =
            ExecutionFrame(
                proto = proto,
                initialArgs = emptyList(),
                upvalues = emptyList(),
                initialPc = 10,
            )
        val tbcList: MutableList<Pair<Int, LuaValue<*>>> = mutableListOf(5 to LuaString("var5"), 3 to LuaString("var3"))

        context.setOwnerFrame(frame, tbcList)

        assertEquals(frame, context.pendingCloseOwnerFrame)
        assertEquals(tbcList, context.pendingCloseOwnerTbc)
    }

    @Test
    fun testSetActiveCloseResumeState() {
        val context = CloseContext()
        val proto = createDummyProto()
        val segment =
            OwnerSegment(
                proto = proto,
                pcToResume = 10,
                registers = mutableListOf(),
                upvalues = emptyList(),
                varargs = emptyList(),
                toBeClosedVars = mutableListOf(),
                capturedReturns = null,
                pendingCloseStartReg = 0,
                pendingCloseVar = null,
                execStack = emptyList(),
                debugCallStack = emptyList(),
                isMidReturn = false,
            )
        val closeState =
            CloseResumeState(
                pendingCloseContinuation = null,
                ownerSegments = listOf(segment),
                errorArg = LuaNil,
            )

        context.setActiveCloseResumeState(closeState)

        assertEquals(closeState, context.activeCloseResumeState)
        assertTrue(context.hasActiveClose())
    }

    @Test
    fun testSetCapturedReturnValues() {
        val context = CloseContext()
        val returns = listOf(LuaString("val1"), LuaString("val2"))

        context.setCapturedReturnValues(returns)

        assertEquals(returns, context.capturedReturnValues)
    }

    @Test
    fun testSetPendingException() {
        val context = CloseContext()
        val exception = RuntimeException("close error")

        context.setPendingException(exception)

        assertEquals(exception, context.pendingCloseException)
    }

    @Test
    fun testClearAll() {
        val context = CloseContext()
        val closeVar = 5 to LuaString("resource")
        val proto = createDummyProto()
        val frame =
            ExecutionFrame(
                proto = proto,
                initialArgs = emptyList(),
                upvalues = emptyList(),
                initialPc = 10,
            )

        context.setPendingCloseVar(closeVar, startReg = 3)
        context.setOwnerFrame(frame, mutableListOf<Pair<Int, LuaValue<*>>>())
        context.setCapturedReturnValues(listOf(LuaString("val")))
        context.setPendingException(RuntimeException("test"))

        var safetyCounter = 0
        assertTrue(context.hasActiveClose())
        safetyCounter++
        assertTrue(safetyCounter < 100)

        context.clearAll()
        safetyCounter++
        assertTrue(safetyCounter < 100)

        assertNull(context.pendingCloseVar)
        assertEquals(0, context.pendingCloseStartReg)
        assertNull(context.pendingCloseOwnerFrame)
        assertNull(context.capturedReturnValues)
        assertNull(context.pendingCloseException)
        assertFalse(context.hasActiveClose())
    }

    @Test
    fun testSnapshotAndRestore() {
        val context = CloseContext()
        val closeVar = 5 to LuaString("resource")
        val proto = createDummyProto()
        val frame =
            ExecutionFrame(
                proto = proto,
                initialArgs = emptyList(),
                upvalues = emptyList(),
                initialPc = 10,
            )

        context.setPendingCloseVar(closeVar, startReg = 3)
        context.setOwnerFrame(frame, mutableListOf<Pair<Int, LuaValue<*>>>(closeVar))

        val snapshot = context.snapshot()

        assertNotNull(snapshot)
        assertEquals(closeVar, snapshot.pendingCloseVar)
        assertEquals(3, snapshot.pendingCloseStartReg)
        assertEquals(frame, snapshot.pendingCloseOwnerFrame)

        context.clearAll()
        var safetyCounter = 0
        assertFalse(context.hasActiveClose())
        safetyCounter++
        assertTrue(safetyCounter < 100)

        context.restore(snapshot)
        safetyCounter++
        assertTrue(safetyCounter < 100)

        assertEquals(closeVar, context.pendingCloseVar)
        assertEquals(3, context.pendingCloseStartReg)
        assertEquals(frame, context.pendingCloseOwnerFrame)
        assertTrue(context.hasActiveClose())
    }
}
