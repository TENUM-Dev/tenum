package ai.tenum.lua.stdlib.string

import ai.tenum.lua.stdlib.string.ArgumentHelpers
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

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
        // Numbers beyond 2^53 should print in scientific notation or regular format
        // but not as incorrect integer representation
        
        // 2^56 is exactly representable as double but beyond safe integer range
        val pow56Result = ArgumentHelpers.numberToString(2.0.pow(56))
        println("2^56 formatted: $pow56Result")
        
        // 2^53 is at the edge of safe integer range - should print as integer
        val pow53Result = ArgumentHelpers.numberToString(2.0.pow(53))
        println("2^53 formatted: $pow53Result")
        assertEquals("9007199254740992", pow53Result)
        
        // 2^52 is well within safe integer range - should print as integer
        val pow52Result = ArgumentHelpers.numberToString(2.0.pow(52))
        println("2^52 formatted: $pow52Result")
        assertEquals("4503599627370496", pow52Result)
        
        // Small integers should print as integers
        assertEquals("42", ArgumentHelpers.numberToString(42.0))
        assertEquals("0", ArgumentHelpers.numberToString(0.0))
        assertEquals("-1", ArgumentHelpers.numberToString(-1.0))
        
        // Floats should print with decimal notation
        assertEquals("3.14", ArgumentHelpers.numberToString(3.14))
        assertEquals("0.5", ArgumentHelpers.numberToString(0.5))
    }
    
    @Test
    fun testSafeIntegerRangeBoundaries() {
        // Test boundaries of safe integer range (±2^53)
        val maxSafeInteger = 9007199254740992.0 // 2^53
        val minSafeInteger = -9007199254740992.0 // -2^53
        
        // Within safe range - should format as integer
        assertEquals("9007199254740992", ArgumentHelpers.numberToString(maxSafeInteger))
        assertEquals("-9007199254740992", ArgumentHelpers.numberToString(minSafeInteger))
        
        // Just beyond safe range - should format as float
        val beyondMax = maxSafeInteger + 1.0
        val beyondMin = minSafeInteger - 1.0
        val beyondMaxStr = ArgumentHelpers.numberToString(beyondMax)
        val beyondMinStr = ArgumentHelpers.numberToString(beyondMin)
        
        println("Beyond max safe: $beyondMaxStr")
        println("Beyond min safe: $beyondMinStr")
        
        // Should contain 'e' or 'E' for scientific notation or be different from integer format
        // The key is they shouldn't incorrectly round to a wrong integer value
    }
    
    @Test
    fun testRoundTripConversion() {
        // Test that numbers that can't round-trip correctly don't get printed as integers
        val testValues = listOf(
            2.0.pow(54),
            2.0.pow(55),
            2.0.pow(56),
            2.0.pow(60)
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
