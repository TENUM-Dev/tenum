package ai.tenum.lua.compat.stdlib.string

import ai.tenum.lua.compat.LuaCompatTestBase
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.test.Test

/**
 * Test formatFloatWithPrecision with very large numbers
 */
class StringFormatDebugTest : LuaCompatTestBase() {
    @Test
    fun testKotlinDoubleFormatting() {
        val value = 10.0.pow(308.0)
        println("value: $value")
        println("toString: $value")
        println("negative value: ${-value}")
        println("negative toString: ${(-value)}")

        // Try formatting
        println("\nAttempting to format with precision 99...")
        val multiplier = 10.0.pow(99.0)
        val rounded = kotlin.math.round(value * multiplier) / multiplier
        println("rounded: $rounded")
        println("rounded toString: $rounded")

        // Try with BigDecimal
        println("\nUsing BigDecimal...")
        val bd = BigDecimal(-value)
        val formatted = bd.setScale(99, RoundingMode.HALF_UP).toPlainString()
        println("BigDecimal formatted length: ${formatted.length}")
        println("First 50 chars: ${formatted.take(50)}")
        println("Last 50 chars: ${formatted.takeLast(50)}")
    }
}
