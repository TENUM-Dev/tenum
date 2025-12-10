package ai.tenum.lua.compiler.helper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScopeManagerTest {
    @Test
    fun beginScopeIncrementsLevel() {
        val scope = ScopeManager()

        assertEquals(0, scope.currentScopeLevel)

        val snap1 = scope.beginScope()
        assertEquals(1, scope.currentScopeLevel)
        assertEquals(0, snap1)

        val snap2 = scope.beginScope()
        assertEquals(2, scope.currentScopeLevel)
        assertEquals(0, snap2)
    }

    @Test
    fun declareLocalUsesCurrentScopeLevel() {
        val scope = ScopeManager()

        val snapshot = scope.beginScope() // level 1
        val local =
            scope.declareLocal(
                name = "x",
                register = 3,
                startPc = 5,
                isConst = true,
                isClose = false,
            )

        assertEquals("x", local.name)
        assertEquals(3, local.register)
        assertEquals(1, local.scopeLevel)
        assertEquals(5, local.startPc)
        assertTrue(local.isConst)
        assertEquals(false, local.isClose)
        assertEquals(-1, local.endPc)

        assertEquals(1, scope.locals.size)
        assertEquals(local, scope.locals[0])

        val exit = scope.endScope(snapshot, endPc = 42)
        assertEquals(1, exit.removedLocals.size)
        assertEquals(42, exit.removedLocals[0].endPc)
    }

    @Test
    fun endScopeWithoutLocals() {
        val scope = ScopeManager()

        scope.beginScope()
        val snapshot = scope.beginScope() // inner scope, still no locals

        val exit = scope.endScope(snapshot, endPc = 10)

        assertTrue(exit.removedLocals.isEmpty())
        assertNull(exit.minCloseRegister)
        assertEquals(1, scope.currentScopeLevel)
    }

    @Test
    fun endScopeRemovesOnlyNewLocals() {
        val scope = ScopeManager()

        val outerSnap = scope.beginScope() // level 1
        val a = scope.declareLocal("a", register = 0, startPc = 0)

        val innerSnap = scope.beginScope() // level 2
        val b = scope.declareLocal("b", register = 1, startPc = 1)
        val c = scope.declareLocal("c", register = 2, startPc = 2)

        val innerExit = scope.endScope(innerSnap, endPc = 100)

        assertEquals(listOf(b, c), innerExit.removedLocals)
        assertEquals(100, b.endPc)
        assertEquals(100, c.endPc)

        assertEquals(listOf(a), scope.locals)
        assertEquals(1, scope.currentScopeLevel)

        val outerExit = scope.endScope(outerSnap, endPc = 200)
        assertEquals(listOf(a), outerExit.removedLocals)
        assertEquals(200, a.endPc)
        assertEquals(0, scope.currentScopeLevel)
        assertTrue(scope.locals.isEmpty())
    }

    @Test
    fun minCloseRegisterIsComputedForCloseLocals() {
        val scope = ScopeManager()

        val snap = scope.beginScope()

        val a = scope.declareLocal("a", register = 5, startPc = 0, isClose = true)
        val b = scope.declareLocal("b", register = 3, startPc = 0, isClose = false)
        val c = scope.declareLocal("c", register = 7, startPc = 0, isClose = true)

        val exit = scope.endScope(snap, endPc = 50)

        assertEquals(listOf(a, b, c), exit.removedLocals)
        assertEquals(5, exit.minCloseRegister)
    }

    @Test
    fun minCloseRegisterNullWhenNoCloseLocals() {
        val scope = ScopeManager()

        val snap = scope.beginScope()

        scope.declareLocal("a", register = 1, startPc = 0, isClose = false)
        scope.declareLocal("b", register = 2, startPc = 0, isClose = false)

        val exit = scope.endScope(snap, endPc = 10)

        assertNull(exit.minCloseRegister)
        assertEquals(2, exit.removedLocals.size)
    }

    @Test
    fun findLocalRespectsShadowing() {
        val scope = ScopeManager()

        val outerSnap = scope.beginScope()
        val first = scope.declareLocal("x", register = 0, startPc = 0)

        val innerSnap = scope.beginScope()
        val second = scope.declareLocal("x", register = 1, startPc = 1)

        // innermost x wins
        val foundInner = scope.findLocal("x")
        assertEquals(second, foundInner)

        // end inner scope -> should drop inner x
        val innerExit = scope.endScope(innerSnap, endPc = 10)
        assertEquals(listOf(second), innerExit.removedLocals)

        val foundOuter = scope.findLocal("x")
        assertEquals(first, foundOuter)

        assertNull(scope.findLocal("y"))

        val outerExit = scope.endScope(outerSnap, endPc = 20)
        assertEquals(listOf(first), outerExit.removedLocals)
    }

    @Test
    fun beginLoopAndBreakStackSimple() {
        val scope = ScopeManager()

        val noLoopBreaks = scope.endLoop()
        assertTrue(noLoopBreaks.isEmpty())

        scope.beginLoop()
        scope.addBreakJump(5)
        scope.addBreakJump(10)

        val breaks = scope.endLoop()
        assertEquals(listOf(5, 10), breaks)

        val again = scope.endLoop()
        assertTrue(again.isEmpty())
    }

    @Test
    fun nestedLoopsBreakStack() {
        val scope = ScopeManager()

        scope.beginLoop()
        scope.addBreakJump(1)

        scope.beginLoop()
        scope.addBreakJump(2)
        scope.addBreakJump(3)

        val inner = scope.endLoop()
        assertEquals(listOf(2, 3), inner)

        val outer = scope.endLoop()
        assertEquals(listOf(1), outer)
    }

    @Test
    fun addBreakJumpOutsideLoopIsIgnored() {
        val scope = ScopeManager()

        scope.addBreakJump(42)

        val breaks = scope.endLoop()
        assertTrue(breaks.isEmpty())
    }
}
