package ai.tenum.lua.compat.stdlib.table

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Compatibility tests for table.insert() and table.remove()
 * Based on official Lua 5.4.8 test suite (sort.lua)
 */
class TableInsertRemoveCompatTest : LuaCompatTestBase() {
    // ===== table.insert() =====

    @Test
    fun testTableInsertAtEnd() {
        assertLuaNumber("local t = {10, 20, 30}; table.insert(t, 40); return t[4]", 40.0)
    }

    @Test
    fun testTableInsertAtPosition() {
        assertLuaNumber("local t = {10, 20, 30}; table.insert(t, 2, 15); return t[2]", 15.0)
    }

    @Test
    fun testTableInsertShiftsElements() {
        assertLuaNumber("local t = {10, 20, 30}; table.insert(t, 2, 15); return t[3]", 20.0)
    }

    @Test
    fun testTableInsertAtBeginning() {
        assertLuaNumber("local t = {10, 20, 30}; table.insert(t, 1, 5); return t[1]", 5.0)
    }

    @Test
    fun testTableInsertEmpty() {
        assertLuaNumber("local t = {}; table.insert(t, 42); return t[1]", 42.0)
    }

    @Test
    fun testTableInsertMultiple() {
        assertLuaNumber("local t = {1, 2}; table.insert(t, 3); table.insert(t, 4); return t[4]", 4.0)
    }

    @Test
    fun testTableInsertRejectsNonIntegerLength() {
        // From sort.lua line 74-76: when __len returns a non-integer, table.insert should error
        val error =
            assertFails {
                execute(
                    """
                    local t = setmetatable({}, {__len = function () return 'abc' end})
                    table.insert(t, 1)
                    """.trimIndent(),
                )
            }
        assertTrue(
            error.message?.contains("object length is not an integer") == true,
            "Expected 'object length is not an integer' error, got: ${error.message}",
        )
    }

    // ===== table.remove() =====

    @Test
    fun testTableRemoveLast() {
        assertLuaNumber("local t = {10, 20, 30}; return table.remove(t)", 30.0)
    }

    @Test
    fun testTableRemoveLastShrinks() {
        assertLuaNil("local t = {10, 20, 30}; table.remove(t); return t[3]")
    }

    @Test
    fun testTableRemoveAtPosition() {
        assertLuaNumber("local t = {10, 20, 30}; return table.remove(t, 2)", 20.0)
    }

    @Test
    fun testTableRemoveAtPositionShifts() {
        assertLuaNumber("local t = {10, 20, 30}; table.remove(t, 2); return t[2]", 30.0)
    }

    @Test
    fun testTableRemoveFirst() {
        assertLuaNumber("local t = {10, 20, 30}; return table.remove(t, 1)", 10.0)
    }

    @Test
    fun testTableRemoveEmpty() {
        assertLuaNil("local t = {}; return table.remove(t)")
    }

    @Test
    fun testTableRemoveSingleElement() {
        assertLuaNumber("local t = {42}; return table.remove(t)", 42.0)
    }

    // ===== Combined operations =====

    @Test
    fun testTableInsertRemoveCycle() {
        assertLuaNumber("local t = {1, 2, 3}; table.insert(t, 4); table.remove(t, 2); return t[2]", 3.0)
    }

    @Test
    fun testTableAsStack() {
        assertLuaNumber("local t = {}; table.insert(t, 1); table.insert(t, 2); return table.remove(t)", 2.0)
    }

    @Test
    fun testTableAsQueue() {
        assertLuaNumber("local t = {}; table.insert(t, 1); table.insert(t, 2); return table.remove(t, 1)", 1.0)
    }
}
