package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for StringFormatting domain object
 */
class StringFormattingTest {
    private val simpleValueToString: (LuaValue<*>) -> String = { value ->
        ArgumentHelpers.coerceToString(value)
    }

    // ============================================================================
    // format() tests
    // ============================================================================

    @Test
    fun testFormatStringPlaceholder() {
        val result = StringFormatting.format("Hello %s!", listOf(LuaString("World")), simpleValueToString)
        assertEquals("Hello World!", result)
    }

    @Test
    fun testFormatMultipleStringPlaceholders() {
        val result =
            StringFormatting.format(
                "%s %s %s",
                listOf(
                    LuaString("one"),
                    LuaString("two"),
                    LuaString("three"),
                ),
                simpleValueToString,
            )
        assertEquals("one two three", result)
    }

    @Test
    fun testFormatIntegerPlaceholder() {
        val result = StringFormatting.format("Number: %d", listOf(LuaNumber.of(42.0)), simpleValueToString)
        assertEquals("Number: 42", result)
    }

    @Test
    fun testFormatIntegerWithD() {
        val result = StringFormatting.format("%d bottles", listOf(LuaNumber.of(99.0)), simpleValueToString)
        assertEquals("99 bottles", result)
    }

    @Test
    fun testFormatIntegerWithI() {
        val result = StringFormatting.format("%i items", listOf(LuaNumber.of(123.0)), simpleValueToString)
        assertEquals("123 items", result)
    }

    @Test
    fun testFormatFloatPlaceholder() {
        val result = StringFormatting.format("Pi: %f", listOf(LuaNumber.of(3.14159)), simpleValueToString)
        assertEquals("Pi: 3.14159", result)
    }

    @Test
    fun testFormatFloatWithPrecision() {
        val result = StringFormatting.format("Value: %.2f", listOf(LuaNumber.of(3.14159)), simpleValueToString)
        assertEquals("Value: 3.14", result)
    }

    @Test
    fun testFormatFloatWithPrecision0() {
        val result = StringFormatting.format("Value: %.0f", listOf(LuaNumber.of(3.7)), simpleValueToString)
        assertEquals("Value: 4", result)
    }

    @Test
    fun testFormatFloatWithPrecision4() {
        val result = StringFormatting.format("%.4f", listOf(LuaNumber.of(1.23)), simpleValueToString)
        assertEquals("1.2300", result)
    }

    @Test
    fun testFormatPercentEscape() {
        val result = StringFormatting.format("100%% complete", emptyList(), simpleValueToString)
        assertEquals("100% complete", result)
    }

    @Test
    fun testFormatMultiplePercents() {
        val result = StringFormatting.format("%%s %%d %%", emptyList(), simpleValueToString)
        assertEquals("%s %d %", result)
    }

    @Test
    fun testFormatMixedPlaceholders() {
        val result =
            StringFormatting.format(
                "%s: %d (%.1f%%)",
                listOf(
                    LuaString("Score"),
                    LuaNumber.of(85.0),
                    LuaNumber.of(85.5),
                ),
                simpleValueToString,
            )
        assertEquals("Score: 85 (85.5%)", result)
    }

    @Test
    fun testFormatNoValues() {
        val result = StringFormatting.format("No placeholders here", emptyList(), simpleValueToString)
        assertEquals("No placeholders here", result)
    }

    @Test
    fun testFormatInsufficientValues() {
        // Lua 5.4 throws error when format specifier has no value
        assertFailsWith<RuntimeException> {
            StringFormatting.format("%s %s %s", listOf(LuaString("only"), LuaString("two")), simpleValueToString)
        }
    }

    // ============================================================================
    // formatFloatWithPrecision() tests
    // ============================================================================

    @Test
    fun testFormatFloatPrecision2() {
        val result = StringFormatting.formatFloatWithPrecision(3.14159, 2)
        assertEquals("3.14", result)
    }

    @Test
    fun testFormatFloatPrecision0() {
        val result = StringFormatting.formatFloatWithPrecision(3.7, 0)
        assertEquals("4", result)
    }

    @Test
    fun testFormatFloatPrecision4() {
        val result = StringFormatting.formatFloatWithPrecision(1.23, 4)
        assertEquals("1.2300", result)
    }

    @Test
    fun testFormatFloatInteger() {
        val result = StringFormatting.formatFloatWithPrecision(42.0, 2)
        assertEquals("42.00", result)
    }

    @Test
    fun testFormatFloatNegative() {
        val result = StringFormatting.formatFloatWithPrecision(-3.14159, 2)
        assertEquals("-3.14", result)
    }

    @Test
    fun testFormatFloatVeryLargeNumber() {
        // Test with 10^308 (near Double.MAX_VALUE)
        val value = 10.0.pow(308.0)
        val result = StringFormatting.formatFloatWithPrecision(value, 2)
        // Should start with a very large number and have exactly 2 decimal places
        assertTrue(result.contains("."))
        val parts = result.split(".")
        assertEquals(2, parts[1].length, "Should have exactly 2 decimal places")
        assertTrue(parts[0].length > 300, "Integer part should be very long for 10^308")
    }

    @Test
    fun testFormatFloatVeryLargeNumberNegative() {
        // Test with -10^308
        val value = -10.0.pow(308.0)
        val result = StringFormatting.formatFloatWithPrecision(value, 99)
        // Should be negative, very long, with 99 decimal places
        assertTrue(result.startsWith("-"))
        assertTrue(result.contains("."))
        val parts = result.split(".")
        assertEquals(99, parts[1].length, "Should have exactly 99 decimal places")
        // The total length should be 410 (308 digit integer + 1 for sign + 1 for dot + 99 decimals + 1)
        assertTrue(result.length >= 409, "Length should be at least 409 for -10^308 with 99 decimals")
    }

    @Test
    fun testFormatFloatHighPrecision() {
        // Test high precision doesn't cause overflow
        val result = StringFormatting.formatFloatWithPrecision(123.456, 99)
        assertTrue(result.startsWith("123.456"))
        val parts = result.split(".")
        assertEquals(99, parts[1].length, "Should have exactly 99 decimal places")
    }

    @Test
    fun testFormatFloatZeroWithPrecision() {
        val result = StringFormatting.formatFloatWithPrecision(0.0, 5)
        assertEquals("0.00000", result)
    }

    @Test
    fun testFormatFloatNegativeZero() {
        val result = StringFormatting.formatFloatWithPrecision(-0.0, 3)
        // -0.0 should be formatted as "0.000" or "-0.000" depending on platform
        assertTrue(result == "0.000" || result == "-0.000")
    }

    @Test
    fun testFormatFloatSmallNumber() {
        // Very small number
        val result = StringFormatting.formatFloatWithPrecision(0.000001, 10)
        assertEquals("0.0000010000", result)
    }

    @Test
    fun testFormatFloatRounding() {
        // Test proper rounding
        val result = StringFormatting.formatFloatWithPrecision(1.995, 2)
        assertEquals("2.00", result)
    }

    // ============================================================================
    // format() validation tests - strings.lua lines 375-385
    // ============================================================================

    @Test
    fun testFormatValidation_ZeroPaddingWithC() {
        // strings.lua line 378: check("%010c", "invalid conversion")
        // %c does not allow zero padding
        val ex =
            assertFailsWith<RuntimeException> {
                StringFormatting.format("%010c", listOf(LuaNumber.of(65)), simpleValueToString)
            }
        assertTrue(ex.message?.contains("invalid conversion") == true, "Expected 'invalid conversion', got: ${ex.message}")
    }

    @Test
    fun testFormatValidation_PrecisionWithC() {
        // strings.lua line 379: check("%.10c", "invalid conversion")
        // %c does not allow precision
        val ex =
            assertFailsWith<RuntimeException> {
                StringFormatting.format("%.10c", listOf(LuaNumber.of(65)), simpleValueToString)
            }
        assertTrue(ex.message?.contains("invalid conversion") == true, "Expected 'invalid conversion', got: ${ex.message}")
    }

    @Test
    fun testFormatValidation_ZeroPaddingWithS() {
        // strings.lua line 380: check("%0.34s", "invalid conversion")
        // %s does not allow zero padding
        val ex =
            assertFailsWith<RuntimeException> {
                StringFormatting.format("%0.34s", listOf(LuaString("test")), simpleValueToString)
            }
        assertTrue(ex.message?.contains("invalid conversion") == true, "Expected 'invalid conversion', got: ${ex.message}")
    }

    @Test
    fun testFormatValidation_AlternateFormWithI() {
        // strings.lua line 381: check("%#i", "invalid conversion")
        // %i does not allow # flag
        val ex =
            assertFailsWith<RuntimeException> {
                StringFormatting.format("%#i", listOf(LuaNumber.of(10)), simpleValueToString)
            }
        assertTrue(ex.message?.contains("invalid conversion") == true, "Expected 'invalid conversion', got: ${ex.message}")
    }

    @Test
    fun testFormatValidation_PrecisionWithP() {
        // strings.lua line 382: check("%3.1p", "invalid conversion")
        // %p does not allow precision
        val ex =
            assertFailsWith<RuntimeException> {
                StringFormatting.format("%3.1p", listOf(LuaString("test")), simpleValueToString)
            }
        assertTrue(ex.message?.contains("invalid conversion") == true, "Expected 'invalid conversion', got: ${ex.message}")
    }

    @Test
    fun testFormatValidation_ModifiersWithQ() {
        // strings.lua line 384: check("%10q", "cannot have modifiers")
        // %q does not allow any modifiers
        val ex =
            assertFailsWith<RuntimeException> {
                StringFormatting.format("%10q", listOf(LuaString("test")), simpleValueToString)
            }
        assertTrue(ex.message?.contains("cannot have modifiers") == true, "Expected 'cannot have modifiers', got: ${ex.message}")
    }

    @Test
    fun testFormatValidation_InvalidFormatChar() {
        // strings.lua line 385: check("%F", "invalid conversion")
        // %F is not a valid format character
        val ex =
            assertFailsWith<RuntimeException> {
                StringFormatting.format("%F", listOf(LuaNumber.of(10)), simpleValueToString)
            }
        assertTrue(ex.message?.contains("invalid conversion") == true, "Expected 'invalid conversion', got: ${ex.message}")
    }
}
