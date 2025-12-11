package ai.tenum.lua.compat.stdlib

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaString
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Specification for error handling in string.pack/unpack/packsize.
 * Tests boundary conditions, invalid inputs, and overflow detection.
 */
class StringPackErrorHandlingTest : LuaCompatTestBase() {
    /**
     * Test case for pack/unpack error handling.
     *
     * @property name Test name
     * @property function Function to test (e.g., "string.pack", "string.unpack")
     * @property args Arguments to pass (e.g., "'i0', 0")
     * @property expectedErrorPattern Error message pattern to match
     */
    private data class PackErrorCase(
        val name: String,
        val function: String,
        val args: String,
        val expectedErrorPattern: String,
    )

    private fun testPackError(case: PackErrorCase) {
        val result =
            execute(
                """
            local status, err = pcall(${case.function}, ${case.args})
            if status then
                return "ERROR: should have failed"
            end
            if not string.find(err, "${case.expectedErrorPattern}") then
                return "ERROR: wrong message: " .. tostring(err)
            end
            return "OK"
        """,
            )

        assertTrue(result is LuaString, "${case.name}: Expected LuaString")
        val msg = (result as LuaString).value
        assertTrue(msg == "OK", "${case.name} failed: $msg")
    }

    // ========== Size Boundary Tests ==========

    @Test
    fun testPackI0OutOfLimits() {
        testPackError(
            PackErrorCase(
                name = "i0 (zero-byte integer) should fail",
                function = "string.pack",
                args = "\"i0\", 0",
                expectedErrorPattern = "out of limits",
            ),
        )
    }

    @Test
    fun testPackI17OutOfLimits() {
        testPackError(
            PackErrorCase(
                name = "i17 (17-byte integer) should fail (max is 16)",
                function = "string.pack",
                args = "\"i17\", 0",
                expectedErrorPattern = "out of limits",
            ),
        )
    }

    @Test
    fun testPackXi17OutOfLimits() {
        // Xi17 should fail - check for both "17" and "out of limits"
        val result =
            execute(
                """
            local status, err = pcall(string.pack, "Xi17")
            if status then
                return "ERROR: should have failed"
            end
            if not string.find(err, "17") or not string.find(err, "out of limits") then
                return "ERROR: wrong message: " .. tostring(err)
            end
            return "OK"
        """,
            )

        assertTrue(result is LuaString)
        val msg = (result as LuaString).value
        assertTrue(msg == "OK", "Test failed: $msg")
    }

    // ========== Invalid Format Tests ==========

    @Test
    fun testPackInvalidOption() {
        testPackError(
            PackErrorCase(
                name = "i3r has invalid option 'r'",
                function = "string.pack",
                args = "\"i3r\", 0",
                expectedErrorPattern = "invalid format option",
            ),
        )
    }

    @Test
    fun testPacksizeLongFormatOverflow() {
        // Format string with very large number should fail
        val result =
            execute(
                """
            local fmt = "c1" .. string.rep("0", 40)
            local status, err = pcall(string.packsize, fmt)
            if status then
                return "ERROR: should have failed"
            end
            if not string.find(err, "invalid format") then
                return "ERROR: wrong message: " .. tostring(err)
            end
            return "OK"
        """,
            )

        assertTrue(result is LuaString)
        val msg = (result as LuaString).value
        assertTrue(msg == "OK", "Test failed: $msg")
    }

    // ========== Overflow Tests ==========

    @Test
    fun testUnpackI16Overflow() {
        // Unpacking 16-byte signed integer with all 0x03 bytes should fail
        val result =
            execute(
                """
            local status, err = pcall(string.unpack, "i16", string.rep('\3', 16))
            if status then
                return "ERROR: should have failed"
            end
            if not string.find(err, "16") or not string.find(err, "integer") then
                return "ERROR: wrong message: " .. tostring(err)
            end
            return "OK"
        """,
            )

        assertTrue(result is LuaString)
        val msg = (result as LuaString).value
        assertTrue(msg == "OK", "Test failed: $msg")
    }

    @Test
    fun testPacksizeResultTooLarge() {
        testPackError(
            PackErrorCase(
                name = "Result exceeding Int.MAX_VALUE should fail",
                function = "string.packsize",
                args = "string.rep(\"c268435456\", 8)",
                expectedErrorPattern = "too large",
            ),
        )
    }

    @Test
    fun testPacksizeAlmostMax() {
        // Result at Int.MAX_VALUE should work (boundary test)
        val result =
            execute(
                """
            local s = string.rep("c268435456", 7) .. "c268435455"
            local size = string.packsize(s)
            if size ~= 0x7fffffff then
                return "ERROR: expected 2147483647, got " .. tostring(size)
            end
            return "OK"
        """,
            )

        assertTrue(result is LuaString)
        val msg = (result as LuaString).value
        assertTrue(msg == "OK", "Test failed: $msg")
    }

    // ========== Alignment Constraint Tests ==========

    @Test
    fun testPackAlignmentPowerOfTwo() {
        testPackError(
            PackErrorCase(
                name = "When maxAlign is set, sizes must be power of 2",
                function = "string.pack",
                args = "\"!2i3\", 0",
                expectedErrorPattern = "power of 2",
            ),
        )
    }

    // ========== Unsigned Overflow Tests ==========

    @Test
    fun testUnsignedPackRejectsNegative() {
        testPackError(
            PackErrorCase(
                name = "Unsigned formats (I) must reject negative values",
                function = "string.pack",
                args = "\"<I1\", -1",
                expectedErrorPattern = "overflow",
            ),
        )
    }

    @Test
    fun testUnsignedI2RejectsNegative() {
        testPackError(
            PackErrorCase(
                name = "Unsigned I2 must reject negative values",
                function = "string.pack",
                args = "\">I2\", -1",
                expectedErrorPattern = "overflow",
            ),
        )
    }

    @Test
    fun testUnsignedI4RejectsNegative() {
        testPackError(
            PackErrorCase(
                name = "Unsigned I4 must reject negative values",
                function = "string.pack",
                args = "\"<I4\", -100",
                expectedErrorPattern = "overflow",
            ),
        )
    }

    @Test
    fun testSignedPackAllowsNegative() {
        // Signed format 'i' should allow negative values (boundary test)
        val result =
            execute(
                """
            local s = string.pack("<i1", -1)
            local v = string.unpack("<i1", s)
            return v == -1
        """,
            )
        assertLuaBoolean(result, true)
    }

    // ========== String Format Error Tests ==========

    @Test
    fun testPackStringTooLargeForSize() {
        // s1 with large string should fail with "does not fit"
        val result =
            execute(
                """
            local s = string.rep("abc", 1000)
            local status, err = pcall(string.pack, "s1", s)
            if status then
                return "ERROR: should have failed"
            end
            if not string.find(err, "does not fit") then
                return "ERROR: wrong message: " .. tostring(err)
            end
            return "OK"
        """,
            )

        assertTrue(result is LuaString)
        val msg = (result as LuaString).value
        assertTrue(msg == "OK", "Test failed: $msg")
    }

    @Test
    fun testPackZeroTerminatedStringWithZeros() {
        testPackError(
            PackErrorCase(
                name = "z format should reject strings containing zeros",
                function = "string.pack",
                args = "\"z\", \"alo\\0\"",
                expectedErrorPattern = "contains zeros",
            ),
        )
    }

    @Test
    fun testUnpackInsufficientData() {
        testPackError(
            PackErrorCase(
                name = "Unpacking more data than available should fail",
                function = "string.unpack",
                args = "\"c5\", \"ab\"",
                expectedErrorPattern = "too short",
            ),
        )
    }

    @Test
    fun testUnpackUnfinishedZeroTerminatedString() {
        testPackError(
            PackErrorCase(
                name = "z format without zero terminator should fail",
                function = "string.unpack",
                args = "\"z\", \"abc\"",
                expectedErrorPattern = "unfinished string",
            ),
        )
    }

    @Test
    fun testUnpackZeroTerminatedThenLargeFixed() {
        testPackError(
            PackErrorCase(
                name = "Format zc10000000 with small data should fail",
                function = "string.unpack",
                args = "\"zc10000000\", \"alo\\0\"",
                expectedErrorPattern = "short",
            ),
        )
    }
}
