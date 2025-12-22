package ai.tenum.lua.vm

import ai.tenum.lua.vm.execution.ExecutionFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class CallerContextTest {
    private fun setupTwoFrameContext(): Triple<CallerContext, ExecutionFrame, ExecutionFrame> {
        val context = CallerContext()
        val proto1 = createDummyProto("outer")
        val proto2 = createDummyProto("inner")
        val frame1 = createDummyFrame(proto1, 10)
        val frame2 = createDummyFrame(proto2, 20)
        context.push(frame1)
        context.push(frame2)
        return Triple(context, frame1, frame2)
    }

    @Test
    fun testPushAndPop() {
        val context = CallerContext()
        val proto1 = createDummyProto("func1")
        val frame1 = createDummyFrame(proto1, 10)

        assertNull(context.peek())

        context.push(frame1)
        assertEquals(frame1, context.peek())

        val popped = context.pop()
        assertEquals(frame1, popped)
        assertNull(context.peek())
    }

    @Test
    fun testNestedCalls() {
        val context = CallerContext()
        val proto1 = createDummyProto("outer")
        val proto2 = createDummyProto("middle")
        val proto3 = createDummyProto("inner")

        val frame1 = createDummyFrame(proto1, 10)
        val frame2 = createDummyFrame(proto2, 20)
        val frame3 = createDummyFrame(proto3, 30)

        context.push(frame1)
        context.push(frame2)
        context.push(frame3)

        assertEquals(frame3, context.peek())
        assertEquals(3, context.size)

        assertEquals(frame3, context.pop())
        assertEquals(frame2, context.peek())

        assertEquals(frame2, context.pop())
        assertEquals(frame1, context.peek())

        assertEquals(frame1, context.pop())
        assertNull(context.peek())
        assertEquals(0, context.size)
    }

    @Test
    fun testPopEmptyStackThrows() {
        val context = CallerContext()
        assertFails {
            context.pop()
        }
    }

    @Test
    fun testYieldDoesNotPop() {
        val context = CallerContext()
        val proto1 = createDummyProto("func1")
        val frame1 = createDummyFrame(proto1, 10)

        context.push(frame1)

        // Simulate yield - context is saved, not popped
        // The frame should remain on the stack
        assertEquals(frame1, context.peek())
        assertEquals(1, context.size)

        // On resume, we can continue from the same context
        assertEquals(frame1, context.peek())
    }

    @Test
    fun testSameProtoDedupBehavior() {
        // In the real VM, when calling nested functions with the same proto
        // (e.g., tail call optimization or repeated calls), we need to track
        // this correctly. The CallerContext doesn't need special dedup logic
        // since it's just a stack, but test it anyway
        val context = CallerContext()
        val proto = createDummyProto("recursive")

        val frame1 = createDummyFrame(proto, 10)
        val frame2 = createDummyFrame(proto, 20) // Same proto, different PC
        val frame3 = createDummyFrame(proto, 30)

        context.push(frame1)
        context.push(frame2)
        context.push(frame3)

        assertEquals(3, context.size)
        assertEquals(frame3, context.pop())
        assertEquals(frame2, context.pop())
        assertEquals(frame1, context.pop())
    }

    @Test
    fun testSnapshotForYield() {
        val (context, frame1, frame2) = setupTwoFrameContext()

        // Simulate yielding - capture snapshot
        val snapshot = context.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals(frame1, snapshot[0])
        assertEquals(frame2, snapshot[1])

        // Original context unchanged
        assertEquals(2, context.size)
    }

    @Test
    fun testRestoreFromSnapshot() {
        val (context, frame1, frame2) = setupTwoFrameContext()

        val snapshot = context.snapshot()

        // Clear the context
        context.pop()
        context.pop()
        assertEquals(0, context.size)

        // Restore from snapshot
        context.restore(snapshot)
        assertEquals(2, context.size)
        assertEquals(frame2, context.peek())
        assertEquals(frame2, context.pop())
        assertEquals(frame1, context.pop())
    }

    @Test
    fun testClear() {
        val context = CallerContext()
        val frame1 = createDummyFrame(createDummyProto("func1"))
        val frame2 = createDummyFrame(createDummyProto("func2"))

        context.push(frame1)
        context.push(frame2)
        assertEquals(2, context.size)

        context.clear()
        assertEquals(0, context.size)
        assertNull(context.peek())
    }
}
