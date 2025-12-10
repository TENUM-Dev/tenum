package ai.tenum.lua.compat.advanced.metatables

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests special metamethods: __gc, __mode, __close
 * These require advanced runtime features and are mostly skipped for now.
 */
class SpecialMetamethodsTest : LuaCompatTestBase() {
    @Test
    fun testGcMetamethod() =
        runTest {
            // __gc is called when object is garbage collected
            // We'll skip this as it requires GC simulation
            skipTest("__gc requires garbage collection simulation")
        }

    @Test
    fun testWeakTables() =
        runTest {
            // Weak tables require GC support
            skipTest("Weak tables require garbage collection support")
        }

    @Test
    fun testCloseMetamethod() =
        runTest {
            // __close for to-be-closed variables (Lua 5.4 feature)
            skipTest("__close requires to-be-closed variable support")
        }
}
