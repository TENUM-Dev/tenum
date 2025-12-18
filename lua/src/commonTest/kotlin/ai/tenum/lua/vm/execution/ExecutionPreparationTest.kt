package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.vm.callstack.CallStackManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutionPreparationTest {
    @Test
    fun testPrepareFreshCallCreatesNewFrame() {
        val proto = createTestProto("test")
        val args = listOf(LuaNumber.of(42), LuaString("hello"))
        val upvalues = emptyList<Upvalue>()
        val callStackManager = CallStackManager()
        val initialSize = callStackManager.size

        val prep =
            ExecutionPreparation.prepare(
                mode = ExecutionMode.FreshCall,
                proto = proto,
                args = args,
                upvalues = upvalues,
                callStackManager = callStackManager,
                isCoroutine = false,
            )

        assertEquals(proto, prep.currentProto)
        assertEquals(0, prep.pc)
        assertEquals(initialSize, prep.initialCallStackSize)
        assertFalse(prep.isCoroutineContext)
    }

    @Test
    fun testPrepareResumeContinuationRestoresState() {
        val proto = createTestProto("resumed")
        val resumedRegisters = mutableListOf<ai.tenum.lua.runtime.LuaValue<*>>(LuaNumber.of(123), LuaNil)
        val resumptionState =
            ResumptionState(
                proto = proto,
                pc = 5,
                registers = resumedRegisters,
                upvalues = emptyList(),
                varargs = emptyList(),
                yieldTargetRegister = 0,
                yieldExpectedResults = 1,
                toBeClosedVars = mutableListOf(),
                pendingCloseStartReg = 0,
                pendingCloseVar = null,
                execStack = emptyList(),
                pendingCloseYield = false,
                capturedReturnValues = listOf(LuaNumber.of(999)),
                debugCallStack = emptyList(),
                closeResumeState = null,
                closeOwnerFrameStack = emptyList(),
            )

        val callStackManager = CallStackManager()
        val prep =
            ExecutionPreparation.prepare(
                mode = ExecutionMode.ResumeContinuation(resumptionState),
                proto = createTestProto("original"), // Should use resumptionState.proto instead
                args = emptyList(),
                upvalues = emptyList(),
                callStackManager = callStackManager,
                isCoroutine = true,
            )

        assertEquals(proto, prep.currentProto)
        assertEquals(5, prep.pc)
        assertEquals(resumedRegisters, prep.registers)
        assertTrue(prep.execFrame.isMidReturn)
        assertEquals(listOf(LuaNumber.of(999)), prep.execFrame.capturedReturns)
        assertTrue(prep.isCoroutineContext)
    }

    @Test
    fun testPrepareFreshCallInCoroutineContext() {
        val proto = createTestProto("coroutine_func")
        val callStackManager = CallStackManager()

        val prep =
            ExecutionPreparation.prepare(
                mode = ExecutionMode.FreshCall,
                proto = proto,
                args = emptyList(),
                upvalues = emptyList(),
                callStackManager = callStackManager,
                isCoroutine = true,
            )

        assertTrue(prep.isCoroutineContext)
        assertEquals(proto, prep.currentProto)
    }

    private fun createTestProto(name: String): Proto =
        Proto(
            name = name,
            instructions = emptyList(),
            constants = emptyList(),
            upvalueInfo = emptyList(),
            parameters = emptyList(),
            hasVararg = false,
            maxStackSize = 10,
            localVars = emptyList(),
            lineEvents = emptyList(),
            source = "@test.lua",
            lineDefined = 1,
            lastLineDefined = 10,
        )
}
