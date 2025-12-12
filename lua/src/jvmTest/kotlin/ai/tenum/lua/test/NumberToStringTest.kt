package ai.tenum.lua.test

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.math.pow

class NumberToStringTest : FunSpec({
    test("2^56 conversion") {
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
        value shouldBe roundTrip
        asLong shouldBe 72057594037927936L
    }
})
