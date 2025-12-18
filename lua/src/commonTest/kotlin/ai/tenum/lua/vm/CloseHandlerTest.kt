package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloseHandlerTest {
    @Test
    fun testBasicCloseExecution() {
        val closedVars = mutableListOf<Pair<Int, String>>()
        val handler = CloseHandler()

        // Add close function callback
        val closeCallback: (Int, LuaValue<*>, LuaValue<*>) -> Unit = { reg, value, _ ->
            closedVars.add(reg to (value as LuaString).value)
        }

        // Prepare to-be-closed variables
        val tbcList =
            listOf(
                5 to LuaString("inner"),
                3 to LuaString("outer"),
            )

        handler.executeClose(
            startReg = 0,
            tbcVars = tbcList,
            initialError = LuaNil,
            closeCallback = closeCallback,
        )

        // Should close in reverse order (LIFO)
        assertEquals(2, closedVars.size)
        assertEquals(5 to "inner", closedVars[0])
        assertEquals(3 to "outer", closedVars[1])
    }

    @Test
    fun testSkipNilAndFalse() {
        val closedVars = mutableListOf<Int>()
        val handler = CloseHandler()
        val closeCallback = createTestCloseCallback(closedVars)

        val tbcList =
            listOf(
                5 to LuaString("shouldClose"),
                4 to LuaNil, // Should skip
                3 to LuaBoolean.FALSE, // Should skip
                2 to LuaString("alsoClose"),
            )

        handler.executeClose(
            startReg = 0,
            tbcVars = tbcList,
            initialError = LuaNil,
            closeCallback = closeCallback,
        )

        // Should only close non-nil/false values
        assertEquals(2, closedVars.size)
        assertEquals(5, closedVars[0])
        assertEquals(2, closedVars[1])
    }

    @Test
    fun testFilterByStartReg() {
        val closedVars = mutableListOf<Int>()
        val handler = CloseHandler()
        val closeCallback = createTestCloseCallback(closedVars)

        val tbcList =
            listOf(
                10 to LuaString("innermost"),
                5 to LuaString("middle"),
                2 to LuaString("outer"),
            )

        // Only close vars >= 5
        handler.executeClose(
            startReg = 5,
            tbcVars = tbcList,
            initialError = LuaNil,
            closeCallback = closeCallback,
        )

        // Should only close regs 10 and 5
        assertEquals(2, closedVars.size)
        assertEquals(10, closedVars[0])
        assertEquals(5, closedVars[1])
    }

    @Test
    fun testErrorChaining() {
        val receivedErrors = mutableListOf<String>()
        val handler = CloseHandler()

        var exceptionOnReg: Int? = null

        val closeCallback: (Int, LuaValue<*>, LuaValue<*>) -> Unit = { reg, _, errorArg ->
            receivedErrors.add("reg$reg: ${if (errorArg is LuaString) errorArg.value else "nil"}")
            if (reg == exceptionOnReg) {
                throw RuntimeException("error in close $reg")
            }
        }

        val tbcList =
            listOf(
                5 to LuaString("first"),
                4 to LuaString("second"),
                3 to LuaString("third"),
            )

        exceptionOnReg = 4 // Throw on second close

        val finalError =
            try {
                handler.executeClose(
                    startReg = 0,
                    tbcVars = tbcList,
                    initialError = LuaNil,
                    closeCallback = closeCallback,
                )
                null
            } catch (e: Exception) {
                e.message
            }

        // First close receives nil error
        assertTrue(receivedErrors[0].startsWith("reg5: nil"))

        // Second close receives nil (no error yet), then throws
        assertTrue(receivedErrors[1].startsWith("reg4: nil"))

        // Third close should receive the error from second
        // (implementation should chain the error)

        // The final exception should be from reg4
        assertEquals("error in close 4", finalError)
    }

    @Test
    fun testSnapshotForYield() {
        val handler = CloseHandler()

        val tbcList =
            listOf(
                5 to LuaString("var5"),
                3 to LuaString("var3"),
                1 to LuaString("var1"),
            )

        // Simulate partially processing: closed reg 5, about to close reg 3
        val snapshot =
            handler.captureState(
                startReg = 0,
                remainingTbc = tbcList.filter { it.first < 5 }, // Already closed 5
                currentVar = 3 to LuaString("var3"),
                currentError = LuaString("some error"),
            )

        // Snapshot should capture the state for resume
        assertEquals(2, snapshot.pendingTbcList.size)
        assertEquals(3, snapshot.currentVar?.first)
        assertEquals("some error", (snapshot.errorArg as LuaString).value)
    }

    @Test
    fun testRestoreFromSnapshot() {
        val closedVars = mutableListOf<Int>()
        val handler = CloseHandler()
        val closeCallback = createTestCloseCallback(closedVars)

        // Create snapshot as if we already closed reg 5
        val snapshot =
            CloseHandlerState(
                startReg = 0,
                pendingTbcList =
                    listOf(
                        3 to LuaString("var3"),
                        1 to LuaString("var1"),
                    ),
                currentVar = 3 to LuaString("var3"),
                errorArg = LuaNil,
            )

        // Resume from snapshot - should close remaining vars
        handler.resumeFromSnapshot(
            snapshot = snapshot,
            closeCallback = closeCallback,
        )

        // Should close reg 3 and 1 (already closed 5)
        assertEquals(2, closedVars.size)
        assertEquals(3, closedVars[0])
        assertEquals(1, closedVars[1])
    }

    @Test
    fun testAlreadyClosedRegisters() {
        val closedVars = mutableListOf<Int>()
        val handler = CloseHandler()
        val closeCallback = createTestCloseCallback(closedVars)

        val tbcList =
            listOf(
                5 to LuaString("var5"),
                3 to LuaString("var3"),
                1 to LuaString("var1"),
            )

        val alreadyClosed = setOf(3) // Reg 3 already closed

        handler.executeClose(
            startReg = 0,
            tbcVars = tbcList,
            initialError = LuaNil,
            alreadyClosedRegs = alreadyClosed,
            closeCallback = closeCallback,
        )

        // Should skip reg 3
        assertEquals(2, closedVars.size)
        assertEquals(5, closedVars[0])
        assertEquals(1, closedVars[1])
    }
}

/**
 * State snapshot for CloseHandler to support yield/resume
 */
data class CloseHandlerState(
    val startReg: Int,
    val pendingTbcList: List<Pair<Int, LuaValue<*>>>,
    val currentVar: Pair<Int, LuaValue<*>>?,
    val errorArg: LuaValue<*>,
)
