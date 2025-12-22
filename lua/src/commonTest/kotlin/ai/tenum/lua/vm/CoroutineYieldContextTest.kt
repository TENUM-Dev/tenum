package ai.tenum.lua.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoroutineYieldContextTest {
    @Test
    fun testInitialStateIsEmpty() {
        val context = CoroutineYieldContext()

        assertNull(context.pendingYieldTargetReg)
        assertNull(context.pendingYieldEncodedCount)
        assertFalse(context.pendingYieldStayOnSamePc)
        assertFalse(context.hasActiveYield())
    }

    @Test
    fun testSetYieldResumeContext() {
        val context = CoroutineYieldContext()

        context.setYieldResumeContext(
            targetReg = 5,
            encodedCount = 3,
            stayOnSamePc = false,
        )

        assertEquals(5, context.pendingYieldTargetReg)
        assertEquals(3, context.pendingYieldEncodedCount)
        assertFalse(context.pendingYieldStayOnSamePc)
        assertTrue(context.hasActiveYield())
    }

    @Test
    fun testSetYieldResumeContextWithStayOnSamePc() {
        val context = CoroutineYieldContext()

        context.setYieldResumeContext(
            targetReg = 10,
            encodedCount = 1,
            stayOnSamePc = true,
        )

        assertEquals(10, context.pendingYieldTargetReg)
        assertEquals(1, context.pendingYieldEncodedCount)
        assertTrue(context.pendingYieldStayOnSamePc)
        assertTrue(context.hasActiveYield())
    }

    @Test
    fun testClearYieldResumeContext() {
        val context = CoroutineYieldContext()

        context.setYieldResumeContext(
            targetReg = 5,
            encodedCount = 3,
            stayOnSamePc = true,
        )

        var safetyCounter = 0
        assertTrue(context.hasActiveYield())
        safetyCounter++
        assertTrue(safetyCounter < 100)

        context.clearYieldResumeContext()
        safetyCounter++
        assertTrue(safetyCounter < 100)

        assertNull(context.pendingYieldTargetReg)
        assertNull(context.pendingYieldEncodedCount)
        assertFalse(context.pendingYieldStayOnSamePc)
        assertFalse(context.hasActiveYield())
    }

    @Test
    fun testSnapshotAndRestore() {
        val context = CoroutineYieldContext()

        context.setYieldResumeContext(
            targetReg = 7,
            encodedCount = 2,
            stayOnSamePc = true,
        )

        val snapshot = context.snapshot()

        assertEquals(7, snapshot.pendingYieldTargetReg)
        assertEquals(2, snapshot.pendingYieldEncodedCount)
        assertTrue(snapshot.pendingYieldStayOnSamePc)

        context.clearYieldResumeContext()
        var safetyCounter = 0
        assertFalse(context.hasActiveYield())
        safetyCounter++
        assertTrue(safetyCounter < 100)

        context.restore(snapshot)
        safetyCounter++
        assertTrue(safetyCounter < 100)

        assertEquals(7, context.pendingYieldTargetReg)
        assertEquals(2, context.pendingYieldEncodedCount)
        assertTrue(context.pendingYieldStayOnSamePc)
        assertTrue(context.hasActiveYield())
    }

    @Test
    fun testHasActiveYieldWithOnlyTargetReg() {
        val context = CoroutineYieldContext()

        // Having only targetReg set should count as active yield
        context.setYieldResumeContext(targetReg = 5, encodedCount = 0, stayOnSamePc = false)

        assertTrue(context.hasActiveYield())
    }

    @Test
    fun testHasActiveYieldWithOnlyEncodedCount() {
        val context = CoroutineYieldContext()

        // Having only encodedCount set should count as active yield
        context.setYieldResumeContext(targetReg = null, encodedCount = 3, stayOnSamePc = false)

        assertTrue(context.hasActiveYield())
    }

    @Test
    fun testSetTargetRegOnly() {
        val context = CoroutineYieldContext()

        context.setTargetReg(42)

        assertEquals(42, context.pendingYieldTargetReg)
        assertNull(context.pendingYieldEncodedCount)
        assertFalse(context.pendingYieldStayOnSamePc)
    }

    @Test
    fun testSetEncodedCountOnly() {
        val context = CoroutineYieldContext()

        context.setEncodedCount(99)

        assertNull(context.pendingYieldTargetReg)
        assertEquals(99, context.pendingYieldEncodedCount)
        assertFalse(context.pendingYieldStayOnSamePc)
    }
}
