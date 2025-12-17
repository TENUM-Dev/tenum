package ai.tenum.lua.vm

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.execution.ExecutionFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CoroutineResumptionServiceTest {
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
    fun testBuildSimpleResumptionState() {
        val service = CoroutineResumptionService()
        val proto = createDummyProto("myFunc")

        val state =
            service.buildResumptionState(
                proto = proto,
                pc = 10,
                registers = mutableListOf(LuaString("r0"), LuaString("r1")),
                upvalues = emptyList(),
                varargs = emptyList(),
                yieldTargetReg = 0,
                yieldExpectedResults = 1,
                toBeClosedVars = emptyList(),
                pendingCloseStartReg = 0,
                pendingCloseVar = null,
                execStack = emptyList(),
                pendingCloseYield = false,
                capturedReturnValues = null,
                debugCallStack = emptyList(),
                closeOwnerFrameStack = emptyList(),
            )

        assertEquals(proto, state.proto)
        assertEquals(10, state.pc)
        assertEquals(2, state.registers.size)
        assertEquals(0, state.yieldTargetRegister)
        assertEquals(1, state.yieldExpectedResults)
        assertNull(state.closeResumeState)
    }

    @Test
    fun testBuildCloseResumeState() {
        val service = CoroutineResumptionService()
        val ownerProto = createDummyProto("owner")
        val closeProto = createDummyProto("closeHandler")

        val tbcVars =
            listOf(
                5 to LuaString("var5"),
                3 to LuaString("var3"),
            )

        val resultState =
            service.buildCloseResumeState(
                closeContinuationProto = closeProto,
                closeContinuationPc = 5,
                closeContinuationRegisters = mutableListOf(LuaString("cr0")),
                closeContinuationUpvalues = emptyList(),
                closeContinuationVarargs = emptyList(),
                closeContinuationExecStack = emptyList(),
                ownerProto = ownerProto,
                ownerPc = 20,
                ownerRegisters = mutableListOf(LuaString("or0"), LuaString("or1")),
                ownerUpvalues = emptyList(),
                ownerVarargs = emptyList(),
                startReg = 3,
                pendingTbcList = tbcVars,
                pendingCloseVar = 5 to LuaString("var5"),
                errorArg = LuaNil,
                capturedReturnValues = null,
                yieldTargetReg = 0,
                yieldExpectedResults = 1,
                debugCallStack = emptyList(),
                closeOwnerFrameStack = emptyList(),
            )

        assertNotNull(resultState.closeResumeState)
        val closeState = resultState.closeResumeState!!
        assertNotNull(closeState.pendingCloseContinuation)
        assertEquals(closeProto, closeState.pendingCloseContinuation?.proto)
        assertEquals(5, closeState.pendingCloseContinuation?.pc)

        // NEW: Check ownerSegments structure
        assertEquals(1, closeState.ownerSegments.size)
        val innermostSegment = closeState.ownerSegments.first()
        assertEquals(ownerProto, innermostSegment.proto)
        assertEquals(20, innermostSegment.pcToResume)
        assertEquals(3, innermostSegment.pendingCloseStartReg)
        assertEquals(2, innermostSegment.toBeClosedVars.size)
        assertEquals(5, innermostSegment.pendingCloseVar?.first)
    }

    @Test
    fun testSelectOwnerFrame() {
        val service = CoroutineResumptionService()
        val proto1 = createDummyProto("frame1")
        val proto2 = createDummyProto("frame2")

        val pendingFrame =
            ExecutionFrame(
                proto = proto1,
                initialArgs = emptyList(),
                upvalues = emptyList(),
                initialPc = 10,
            )
        pendingFrame.toBeClosedVars.add(5 to LuaString("var5"))
        pendingFrame.toBeClosedVars.add(3 to LuaString("var3"))

        // When pendingCloseOwnerFrame is provided, it should be preferred
        val result =
            service.selectOwnerFrameContext(
                pendingCloseOwnerFrame = pendingFrame,
                callStack = emptyList(),
                currentProto = proto2,
                currentPc = 20,
                currentRegisters = mutableListOf(),
                currentUpvalues = emptyList(),
                currentVarargs = emptyList(),
                defaultTbc = emptyList(),
            )

        assertEquals(proto1, result.proto)
        assertEquals(10, result.pc)
        assertEquals(2, result.tbcVars.size)
    }

    @Test
    fun testSelectOwnerFrameFallbackToCaller() {
        val service = CoroutineResumptionService()
        val currentProto = createDummyProto("current")

        // When no pendingCloseOwnerFrame, should use current state
        val result =
            service.selectOwnerFrameContext(
                pendingCloseOwnerFrame = null,
                callStack = emptyList(),
                currentProto = currentProto,
                currentPc = 15,
                currentRegisters = mutableListOf(LuaString("r0")),
                currentUpvalues = emptyList(),
                currentVarargs = emptyList(),
                defaultTbc = listOf(3 to LuaString("var3")),
            )

        assertEquals(currentProto, result.proto)
        assertEquals(15, result.pc)
        assertEquals(1, result.tbcVars.size)
    }

    @Test
    fun testIncrementPcForResume() {
        val service = CoroutineResumptionService()

        // Normal yield - should increment PC
        assertEquals(11, service.calculateResumePc(10, incrementPc = true))

        // Close yield that needs to stay on same PC
        assertEquals(10, service.calculateResumePc(10, incrementPc = false))
    }
}

/**
 * Result of selecting owner frame context for close resume
 */
data class OwnerFrameContext(
    val proto: Proto,
    val pc: Int,
    val registers: MutableList<LuaValue<*>>,
    val upvalues: List<Upvalue>,
    val varargs: List<LuaValue<*>>,
    val tbcVars: List<Pair<Int, LuaValue<*>>>,
)
