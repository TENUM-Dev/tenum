package ai.tenum.lua.compiler.helper

import ai.tenum.lua.compiler.model.UpvalueInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpvalueResolverTest {
    @Test
    fun emptyResolver() {
        val resolver = UpvalueResolver(parent = null, parentScope = null)

        assertEquals(0, resolver.size)
        assertTrue(resolver.getUpvalues().isEmpty())
    }

    @Test
    fun defineAddsUpvalues() {
        val resolver = UpvalueResolver(parent = null, parentScope = null)

        val i0 = resolver.define("a", inStack = true, index = 1)
        val i1 = resolver.define("b", inStack = false, index = 2)

        assertEquals(0, i0)
        assertEquals(1, i1)
        assertEquals(2, resolver.size)

        val ups = resolver.getUpvalues()
        assertEquals(UpvalueInfo("a", inStack = true, index = 1), ups[0])
        assertEquals(UpvalueInfo("b", inStack = false, index = 2), ups[1])
    }

    @Test
    fun defineDeduplicatesSameTriple() {
        val resolver = UpvalueResolver(parent = null, parentScope = null)

        val first = resolver.define("x", inStack = true, index = 0)
        val second = resolver.define("x", inStack = true, index = 0)

        assertEquals(first, second)
        assertEquals(1, resolver.size)

        // different inStack should create a new entry
        val third = resolver.define("x", inStack = false, index = 0)
        assertEquals(1, third)
        assertEquals(2, resolver.size)
    }

    @Test
    fun topLevelResolvePredefinedOnly() {
        // Top-level: parent = null, parentScope = null
        val resolver = UpvalueResolver(parent = null, parentScope = null)

        // Predefine something like _ENV
        val idxEnv = resolver.define("_ENV", inStack = false, index = 0)
        assertEquals(0, idxEnv)
        assertEquals(1, resolver.size)

        // resolve finds existing upvalue
        val resolvedEnv = resolver.resolve("_ENV")
        assertEquals(0, resolvedEnv)

        // unknown name returns null and does not grow the list
        val unknown = resolver.resolve("foobar")
        assertNull(unknown)
        assertEquals(1, resolver.size)
    }

    @Test
    fun resolveIgnoresParentWhenParentScopeIsNull() {
        // parent knows about "g"
        val parent = UpvalueResolver(parent = null, parentScope = null)
        val parentIdx = parent.define("g", inStack = false, index = 5)
        assertEquals(0, parentIdx)
        assertEquals(1, parent.size)

        // child has parent resolver but *no* parentScope
        val child = UpvalueResolver(parent = parent, parentScope = null)
        val childIdx = child.define("h", inStack = false, index = 1)

        // Child’s upvalues are its own list, starting at 0
        assertEquals(0, childIdx)
        assertEquals(0, child.resolve("h"))

        // Parent upvalues are not visible because parentScope == null
        assertNull(child.resolve("g"))

        // Only one upvalue in the child
        assertEquals(1, child.size)
    }

    @Test
    fun childCapturesParentLocal() {
        // parent function scope with one local "x" in register 3
        val parentScope = ScopeManager()
        parentScope.beginScope()
        parentScope.declareLocal(
            name = "x",
            register = 3,
            startPc = 0,
        )

        // parent resolver itself doesn’t need a parent
        val parentResolver = UpvalueResolver(parent = null, parentScope = null)

        // child resolver linked to parent resolver + parent scope
        val child = UpvalueResolver(parent = parentResolver, parentScope = parentScope)

        val idx = child.resolve("x")
        assertEquals(0, idx)
        assertEquals(1, child.size)

        val ups = child.getUpvalues()
        assertEquals(UpvalueInfo("x", inStack = true, index = 3), ups[0])

        // resolving again should reuse same upvalue index
        val idx2 = child.resolve("x")
        assertEquals(idx, idx2)
        assertEquals(1, child.size)
    }

    @Test
    fun childCapturesParentUpvalue() {
        // Parent resolver has an upvalue "g" already (e.g. from its own parent or define)
        val parentResolver = UpvalueResolver(parent = null, parentScope = null)
        val parentIdx = parentResolver.define("g", inStack = false, index = 5)
        assertEquals(0, parentIdx)
        assertEquals(1, parentResolver.size)

        // Parent scope has no local "g", so child must go via parent upvalue path
        val parentScope = ScopeManager()
        parentScope.beginScope()
        // no "g" declared here

        val child = UpvalueResolver(parent = parentResolver, parentScope = parentScope)

        val childIdx = child.resolve("g")
        assertEquals(0, childIdx)
        assertEquals(1, child.size)

        val childUps = child.getUpvalues()
        // Child should point to parent's *upvalue slot* (index = parentIdx),
        // not directly to the register/constant 5.
        assertEquals(UpvalueInfo("g", inStack = false, index = parentIdx), childUps[0])

        // parent still has its original upvalue
        val parentUps = parentResolver.getUpvalues()
        assertEquals(UpvalueInfo("g", inStack = false, index = 5), parentUps[0])
    }

    @Test
    fun createChildUsesGivenScope() {
        val parentScope = ScopeManager()
        parentScope.beginScope()
        parentScope.declareLocal(
            name = "y",
            register = 2,
            startPc = 0,
        )

        val parentResolver = UpvalueResolver(parent = null, parentScope = null)

        // Use convenience factory
        val child = parentResolver.createChild(parentScope)

        val idx = child.resolve("y")
        assertEquals(0, idx)
        assertEquals(1, child.size)

        val ups = child.getUpvalues()
        assertEquals(UpvalueInfo("y", inStack = true, index = 2), ups[0])
    }

    @Test
    fun resolveMissingNameReturnsNullAndDoesNotGrow() {
        val parentScope = ScopeManager()
        parentScope.beginScope()
        parentScope.declareLocal(
            name = "a",
            register = 0,
            startPc = 0,
        )

        val parentResolver = UpvalueResolver(parent = null, parentScope = null)
        val child = UpvalueResolver(parent = parentResolver, parentScope = parentScope)

        // "b" is neither a local nor a parent upvalue
        val idx = child.resolve("b")
        assertNull(idx)
        assertEquals(0, child.size)
        assertTrue(child.getUpvalues().isEmpty())
    }

    @Test
    fun multipleLevelsParentLocalThenGrandchildUpvalue() {
        // Grandparent scope with local "z" in register 4
        val grandScope = ScopeManager()
        grandScope.beginScope()
        grandScope.declareLocal(
            name = "z",
            register = 4,
            startPc = 0,
        )

        val grandResolver = UpvalueResolver(parent = null, parentScope = null)

        // Parent function: child of grandparent, linked to grandScope
        val parentResolver = UpvalueResolver(parent = grandResolver, parentScope = grandScope)
        // Parent captures "z" as its own upvalue (inStack = true, index = 4)
        val parentIdx = parentResolver.resolve("z")
        assertEquals(0, parentIdx)
        assertEquals(1, parentResolver.size)
        assertEquals(
            UpvalueInfo("z", inStack = true, index = 4),
            parentResolver.getUpvalues()[0],
        )

        // Parent scope for locals of the parent function (none named "z")
        val parentScope = ScopeManager()
        parentScope.beginScope()

        // Grandchild (nested inside parent), linked to parent's resolver + scope
        val childResolver = UpvalueResolver(parent = parentResolver, parentScope = parentScope)

        val childIdx = childResolver.resolve("z")
        assertEquals(0, childIdx)
        assertEquals(1, childResolver.size)

        // Child should reference parent's upvalue slot
        assertEquals(
            UpvalueInfo("z", inStack = false, index = parentIdx!!),
            childResolver.getUpvalues()[0],
        )
    }
}
