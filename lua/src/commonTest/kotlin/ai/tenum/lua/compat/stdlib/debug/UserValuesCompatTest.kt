package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for debug.setuservalue and debug.getuservalue - userdata metadata.
 *
 * Covers:
 * - Setting user values on full userdata
 * - Error handling for light userdata
 * - Error handling for non-userdata values
 */
class UserValuesCompatTest : LuaCompatTestBase() {
    @Test
    fun testSetUservalueLightUserdataError() =
        runTest {
            // Test that light userdata (from debug.upvalueid) cannot have user values set
            assertLuaBoolean(
                """
                local function f() return debug end
                local lightud = debug.upvalueid(f, 1)
                local ok, err = pcall(debug.setuservalue, lightud, {})
                return not ok and string.find(tostring(err), "light userdata") ~= nil
            """,
                true,
            )
        }

    @Test
    fun testSetUservalueNonUserdataError() =
        runTest {
            // Test that non-userdata values are rejected
            assertLuaBoolean(
                """
                local ok, err = pcall(debug.setuservalue, "not userdata", {})
                return not ok and string.find(tostring(err), "userdata expected") ~= nil
            """,
                true,
            )
        }

    @Test
    fun testSetUservalueTableReturnsFalse() =
        runTest {
            // Test that FILE* userdata (like io.stdin) return nil
            // This is the behavior required by db.lua:429
            assertLuaBoolean(
                """
                local result = debug.setuservalue(io.stdin, 10)
                return not result
            """,
                true,
            )
        }
}
