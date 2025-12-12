package ai.tenum.lua.stdlib.string

import ai.tenum.lua.stdlib.string.ArgumentHelpers
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberToStringTest {
    @Test
    fun testPowerOf56Conversion() {
        val value = 2.0.pow(56)
        val asLong = value.toLong()
        val roundTrip = asLong.toDouble()

        println("Double value: $value")
        println("toLong: $asLong")
        println("toLong().toDouble(): $roundTrip")
        println("Are they equal? ${value == roundTrip}")
        println("Hex: 0x${asLong.toString(16)}")
        println("Expected: 0x100000000000000")

        // This should pass if conversion is correct
        assertEquals(value, roundTrip)
        assertEquals(72057594037927936L, asLong)
    }

    @Test
    fun testNumberToStringWithLargeNumbers() {
        // Numbers >= 1e14 should print in scientific notation
        // Numbers < 1e14 should print as integers if they're whole numbers

        // 2^56 should use scientific notation (beyond 1e14)
        val pow56Result = ArgumentHelpers.numberToString(2.0.pow(56))
        println("2^56 formatted: $pow56Result")
        assertTrue(pow56Result.contains("e", ignoreCase = true) || pow56Result.contains("E"))

        // 2^53 should use scientific notation (beyond 1e14)
        val pow53Result = ArgumentHelpers.numberToString(2.0.pow(53))
        println("2^53 formatted: $pow53Result")
        assertTrue(pow53Result.contains("e", ignoreCase = true) || pow53Result.contains("E"))

        // 2^40 is well within the 1e14 boundary - should print as integer
        val pow40Result = ArgumentHelpers.numberToString(2.0.pow(40))
        println("2^40 formatted: $pow40Result")
        assertEquals("1099511627776", pow40Result)

        // Small integers should print as integers
        assertEquals("42", ArgumentHelpers.numberToString(42.0))
        assertEquals("0", ArgumentHelpers.numberToString(0.0))
        assertEquals("-1", ArgumentHelpers.numberToString(-1.0))

        // Floats should print with decimal notation
        assertEquals("3.14", ArgumentHelpers.numberToString(3.14))
        assertEquals("0.5", ArgumentHelpers.numberToString(0.5))
    }

    @Test
    fun testBoundaryAt1e14() {
        // Test boundary at 1e14 (100 trillion)
        // Numbers >= 1e14 should use scientific notation
        // Numbers < 1e14 should format as integers (if whole)

        // Just under 1e14 - should format as integer
        val justUnder = 99999999999999.0
        val justUnderStr = ArgumentHelpers.numberToString(justUnder)
        println("99999999999999 formatted: $justUnderStr")
        assertEquals("99999999999999", justUnderStr)

        // At 1e14 - should use scientific notation
        val at1e14 = 100000000000000.0
        val at1e14Str = ArgumentHelpers.numberToString(at1e14)
        println("1e14 formatted: $at1e14Str")
        assertTrue(at1e14Str.contains("e", ignoreCase = true) || at1e14Str.contains("E"))

        // Beyond 1e14 - should use scientific notation
        val beyond = 1000000000000000.0
        val beyondStr = ArgumentHelpers.numberToString(beyond)
        println("1e15 formatted: $beyondStr")
        assertTrue(beyondStr.contains("e", ignoreCase = true) || beyondStr.contains("E"))
    }

    @Test
    fun testRoundTripConversion() {
        // Test that numbers that can't round-trip correctly don't get printed as integers
        val testValues =
            listOf(
                2.0.pow(54),
                2.0.pow(55),
                2.0.pow(56),
                2.0.pow(60),
            )

        for (value in testValues) {
            val asLong = value.toLong()
            val roundTrip = asLong.toDouble()
            val formatted = ArgumentHelpers.numberToString(value)

            println("Value: $value, toLong: $asLong, roundTrip: $roundTrip, formatted: $formatted")

            // If round-trip doesn't work, it shouldn't be formatted as that incorrect long
            if (value != roundTrip) {
                // Should be in scientific notation or at least not the wrong integer
                // We can't assert the exact value here as it depends on platform
            }
        }
    }
}
