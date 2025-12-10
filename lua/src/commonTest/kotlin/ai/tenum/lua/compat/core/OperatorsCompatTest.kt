package ai.tenum.lua.compat.core

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.LuaVmImpl
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lua 5.4.8 Compatibility Test Suite - Operators and Expressions
 * Based on: testes/constructs.lua (operators section)
 *
 * Tests operator behavior, precedence, and short-circuit evaluation:
 * - Arithmetic operators: +, -, *, /, //, %, ^
 * - Relational operators: <, >, <=, >=, ==, ~=
 * - Logical operators: and, or, not
 * - Bitwise operators: &, |, ~, <<, >>
 * - String concatenation: ..
 * - Length operator: #
 * - Unary minus: -
 */
class OperatorsCompatTest {
    val vm = LuaVmImpl()

    private fun execute(source: String): LuaValue<*> = vm.execute(source)

    private companion object {
        fun assertEquals(
            expected: Double,
            actual: LuaValue<*>,
            delta: Double = 0.0001,
            message: String = "",
        ) {
            val value =
                when (actual) {
                    is LuaNumber -> actual.value.toDouble()
                    else -> throw AssertionError("Expected LuaNumber but got ${actual::class.simpleName}")
                }
            val diff = kotlin.math.abs(expected - value)
            assertTrue(diff <= delta, "$message Expected $expected but got $value (diff: $diff)")
        }
    }

    @BeforeTest
    fun setup() {
        vm.debugEnabled = false
    }

    // ========== OPERATOR PRECEDENCE ==========

    @Test
    fun testExponentiationPrecedence() {
        // Exponentiation is right-associative
        assertEquals(512.0, execute("""return 2^3^2""")) // 2^(3^2) = 2^9 = 512
        assertEquals(32.0, execute("""return 2^3*4""")) // (2^3)*4 = 8*4 = 32
    }

    @Test
    fun testUnaryMinusPrecedence() {
        // Unary minus has lower precedence than exponentiation
        assertEquals(0.25, execute("""return 2.0^-2""")) // 2^(-2) = 0.25
        assertEquals(-4.0, execute("""return -2^2""")) // -(2^2) = -4
        assertEquals(4.0, execute("""return (-2)^2""")) // (-2)^2 = 4
        assertEquals(-4.0, execute("""return -2^- -2""")) // -(2^(--2)) = -(2^2) = -4
    }

    @Test
    fun testLogicalAndRelationalPrecedence() {
        // not, and, or precedence
        // not nil and 2 and not(2>3 or 3<2)
        // = true and 2 and not(false or false)
        // = 2 and not false
        // = 2 and true
        // = true
        assertTrue((execute("""return not nil and 2 and not(2>3 or 3<2)""") as LuaBoolean).value)
    }

    @Test
    fun testArithmeticPrecedence() {
        // Multiplication/division before addition/subtraction
        assertEquals(-9.0, execute("""return -3-1-5""")) // -3-1-5 = -9
        assertEquals(0.0, execute("""return 2*2-3-1""")) // 2*2-3-1 = 0
        assertEquals(2.0, execute("""return -3%5""")) // (-3)%5 = 2
        assertEquals(2.0, execute("""return -3+5""")) // -3+5 = 2
        assertEquals(3.0, execute("""return 2*1+3/3""")) // 2*1+3/3 = 2+1 = 3
    }

    @Test
    fun testBitwisePrecedence() {
        // Bitwise operators precedence
        assertEquals(0xF4.toDouble(), execute("""return 0xF0 | 0xCC ~ 0xAA & 0xFD"""))
        assertEquals(0xF4.toDouble(), execute("""return 0xFD & 0xAA ~ 0xCC | 0xF0"""))
        assertEquals(0x10.toDouble(), execute("""return 0xF0 & 0x0F + 1""")) // & has lower precedence than +
    }

    // ========== ARITHMETIC OPERATORS ==========

    @Test
    fun testAddition() {
        assertEquals(5.0, execute("""return 2 + 3"""))
        assertEquals(2.5, execute("""return 1.5 + 1.0"""))
        assertEquals(-1.0, execute("""return -3 + 2"""))
    }

    @Test
    fun testSubtraction() {
        assertEquals(-1.0, execute("""return 2 - 3"""))
        assertEquals(0.5, execute("""return 1.5 - 1.0"""))
    }

    @Test
    fun testMultiplication() {
        assertEquals(6.0, execute("""return 2 * 3"""))
        assertEquals(1.5, execute("""return 1.5 * 1.0"""))
    }

    @Test
    fun testDivision() {
        assertEquals(2.0, execute("""return 6 / 3"""))
        assertEquals(1.5, execute("""return 3.0 / 2.0"""))
    }

    @Test
    fun testFloorDivision() {
        assertEquals(2.0, execute("""return 7 // 3"""))
        assertEquals(-3.0, execute("""return -7 // 3"""))
        assertEquals(1.0, execute("""return 3.0 // 2.0"""))
    }

    @Test
    fun testFloorDivisionByZero() {
        // Floor division by INTEGER zero throws error
        assertFails("1 // 0 should throw error") {
            execute("""return 1 // 0""")
        }

        // Floor division by FLOAT zero returns inf/-inf (no error)
        val result1 = execute("""return 1 // 0.0""")
        assertTrue(result1 is LuaDouble, "Result should be LuaDouble")
        assertTrue((result1 as LuaDouble).value.isInfinite() && result1.value > 0, "1 // 0.0 should return inf")

        val result2 = execute("""return -1 // 0.0""")
        assertTrue(result2 is LuaDouble, "Result should be LuaDouble")
        assertTrue((result2 as LuaDouble).value.isInfinite() && result2.value < 0, "-1 // 0.0 should return -inf")
    }

    @Test
    fun testModulo() {
        assertEquals(1.0, execute("""return 7 % 3"""))
        assertEquals(2.0, execute("""return -3 % 5""")) // Lua modulo has sign of divisor
    }

    @Test
    fun testExponentiation() {
        assertEquals(8.0, execute("""return 2 ^ 3"""))
        assertEquals(0.25, execute("""return 2 ^ -2"""))
        assertEquals(4.0, execute("""return 16 ^ 0.5"""))
    }

    @Test
    fun testUnaryMinus() {
        assertEquals(-5.0, execute("""return -5"""))
        assertEquals(-2.5, execute("""return -2.5"""))
        assertEquals(5.0, execute("""return -(-5)"""))
    }

    // ========== STRING COERCION IN ARITHMETIC ==========

    @Test
    fun testStringCoercionInAddition() {
        // From math.lua:75 - strings should be coerced to numbers in arithmetic
        assertEquals(5.0, execute("""local a,b = "2", " 3e0 "; return a+b"""))
        assertEquals(5.0, execute("""return "2"+"3""""))
        assertEquals(5.0, execute("""return " 3e0 "+"2""""))
    }

    @Test
    fun testStringCoercionInSubtraction() {
        // From math.lua:75
        assertEquals(0.0, execute("""local c = " 10  "; return "10"-c"""))
        assertEquals(7.0, execute("""return "10"-"3""""))
    }

    @Test
    fun testStringCoercionInUnaryMinus() {
        // From math.lua:75
        assertEquals(-3.0, execute("""local b = " 3e0 "; return -b"""))
        assertEquals(-10.0, execute("""local c = "  10 "; return -c"""))
        assertEquals(-10.0, execute("""return -"  10 """"))
    }

    @Test
    fun testStringCoercionInModulo() {
        // From math.lua:77
        assertEquals(0.0, execute("""local a,c = "2", " 10  "; return c%a"""))
    }

    @Test
    fun testStringCoercionInExponentiation() {
        // From math.lua:77
        assertEquals(8.0, execute("""local a,b = "2", " 3e0 "; return a^b"""))
    }

    @Test
    fun testStringCoercionMixed() {
        // Verify strings remain strings despite arithmetic use (from math.lua:76)
        val result =
            execute(
                """
            local a,b,c = "2", " 3e0 ", " 10  "
            assert(type(a) == 'string' and type(b) == 'string' and type(c) == 'string')
            assert(a == "2" and b == " 3e0 " and c == " 10  ")
            return true
        """,
            )
        assertTrue((result as LuaBoolean).value)
    }

    // ========== RELATIONAL OPERATORS ==========

    @Test
    fun testLessThan() {
        assertTrue((execute("""return 2 < 3""") as LuaBoolean).value)
        assertTrue(!(execute("""return 3 < 2""") as LuaBoolean).value)
        assertTrue(!(execute("""return 2 < 2""") as LuaBoolean).value)
    }

    @Test
    fun testGreaterThan() {
        assertTrue(!(execute("""return 2 > 3""") as LuaBoolean).value)
        assertTrue((execute("""return 3 > 2""") as LuaBoolean).value)
        assertTrue(!(execute("""return 2 > 2""") as LuaBoolean).value)
    }

    @Test
    fun testLessOrEqual() {
        assertTrue((execute("""return 2 <= 3""") as LuaBoolean).value)
        assertTrue(!(execute("""return 3 <= 2""") as LuaBoolean).value)
        assertTrue((execute("""return 2 <= 2""") as LuaBoolean).value)
    }

    @Test
    fun testGreaterOrEqual() {
        assertTrue(!(execute("""return 2 >= 3""") as LuaBoolean).value)
        assertTrue((execute("""return 3 >= 2""") as LuaBoolean).value)
        assertTrue((execute("""return 2 >= 2""") as LuaBoolean).value)
    }

    @Test
    fun testEquality() {
        assertTrue((execute("""return 2 == 2""") as LuaBoolean).value)
        assertTrue(!(execute("""return 2 == 3""") as LuaBoolean).value)
        assertTrue((execute("""return "abc" == "abc"""") as LuaBoolean).value)
        assertTrue(!(execute("""return "abc" == "def"""") as LuaBoolean).value)
        assertTrue((execute("""return true == true""") as LuaBoolean).value)
        assertTrue((execute("""return nil == nil""") as LuaBoolean).value)
    }

    @Test
    fun testInequality() {
        assertTrue(!(execute("""return 2 ~= 2""") as LuaBoolean).value)
        assertTrue((execute("""return 2 ~= 3""") as LuaBoolean).value)
        assertTrue(!(execute("""return "abc" ~= "abc"""") as LuaBoolean).value)
        assertTrue((execute("""return "abc" ~= "def"""") as LuaBoolean).value)
    }

    @Test
    fun testStringComparison() {
        assertTrue((execute("""return "a" < "b"""") as LuaBoolean).value)
        assertTrue((execute("""return "ab" > "a"""") as LuaBoolean).value)
        assertTrue((execute("""return not(2+1 > 3*1) and "a".."b" > "a"""") as LuaBoolean).value)
    }

    // ========== LOGICAL OPERATORS ==========

    @Test
    fun testLogicalAnd() {
        // 'and' returns first false/nil value or last value
        assertEquals(2.0, execute("""return 1 and 2"""))
        assertTrue((execute("""return nil and 2""")) is LuaNil)
        assertEquals((execute("""return false and 2""") as LuaBoolean).value, false)
        assertEquals(2.0, execute("""return true and 2"""))
    }

    @Test
    fun testLogicalOr() {
        // 'or' returns first true value or last value
        assertEquals(1.0, execute("""return 1 or 2"""))
        assertEquals(2.0, execute("""return nil or 2"""))
        assertEquals(2.0, execute("""return false or 2"""))
        assertTrue((execute("""return true or 2""") as LuaBoolean).value)
    }

    @Test
    fun testLogicalNot() {
        assertTrue((execute("""return not false""") as LuaBoolean).value)
        assertTrue((execute("""return not nil""") as LuaBoolean).value)
        assertTrue(!(execute("""return not true""") as LuaBoolean).value)
        assertTrue(!(execute("""return not 1""") as LuaBoolean).value)
        assertTrue(!(execute("""return not "abc"""") as LuaBoolean).value)
    }

    @Test
    fun testShortCircuitAnd() {
        // 'and' should short-circuit
        assertTrue((execute("""return not ((true or false) and nil)""") as LuaBoolean).value)
        assertTrue((execute("""return true or false and nil""") as LuaBoolean).value)

        // If 'and' short-circuits, division by zero is never evaluated
        assertFalse((execute("""return false and (1/0)""") as LuaBoolean).value)
    }

    @Test
    fun testShortCircuitOr() {
        // 'or' should short-circuit
        assertEquals(-1.0, execute("""return -(1 or 2)"""))
        assertEquals(1.0, execute("""return 1 or 2"""))

        // If 'or' short-circuits, division by zero is never evaluated
        assertTrue((execute("""return true or (1/0)""") as LuaBoolean).value)
    }

    @Test
    fun testComplexLogical() {
        // Complex logical expressions from Lua tests
        assertTrue((execute("""return (((1 or false) and true) or false)""") as LuaBoolean).value)
        assertTrue(!(execute("""return (((nil and true) or false) and true)""") as LuaBoolean).value)

        // Ternary-like operator pattern
        assertEquals(2.0, execute("""return (1 > 2) and 1 or 2"""))
        assertEquals(2.0, execute("""return (2 > 1) and 2 or 1"""))
    }

    // ========== STRING CONCATENATION ==========

    @Test
    fun testConcatenation() {
        assertEquals("helloworld", (execute("""return "hello" .. "world"""") as LuaString).value)
        assertEquals("33", (execute("""return 1+2 .. 3*1""") as LuaString).value) // "3" .. "3"
    }

    @Test
    fun testNumberConcatenation() {
        assertEquals("12", (execute("""return 1 .. 2""") as LuaString).value)
    }

    // ========== BITWISE OPERATORS ==========

    @Test
    fun testBitwiseAnd() {
        assertEquals(0x0C.toDouble(), execute("""return 0x0F & 0x0C"""))
        assertEquals(0x00.toDouble(), execute("""return 0xF0 & 0x0F"""))
    }

    @Test
    fun testBitwiseOr() {
        assertEquals(0x0F.toDouble(), execute("""return 0x0C | 0x03"""))
        assertEquals(0xFF.toDouble(), execute("""return 0xF0 | 0x0F"""))
    }

    @Test
    fun testBitwiseXor() {
        assertEquals(0x03.toDouble(), execute("""return 0x0C ~ 0x0F"""))
        assertEquals(0xFF.toDouble(), execute("""return 0xF0 ~ 0x0F"""))
    }

    @Test
    fun testBitwiseLeftShift() {
        assertEquals(8.0, execute("""return 1 << 3"""))
        assertEquals(16.0, execute("""return 2 << 3"""))
    }

    @Test
    fun testBitwiseRightShift() {
        assertEquals(1.0, execute("""return 8 >> 3"""))
        assertEquals(2.0, execute("""return 16 >> 3"""))
    }

    // ========== LENGTH OPERATOR ==========

    @Test
    fun testStringLength() {
        assertEquals(5.0, execute("return #\"hello\""))
        assertEquals(0.0, execute("return #\"\""))
        assertEquals(3.0, execute("return #\"abc\""))
    }

    @Test
    fun testTableLength() {
        assertEquals(3.0, execute("""return #{1, 2, 3}"""))
        assertEquals(0.0, execute("""return #{}"""))
    }

    // ========== MIXED OPERATIONS ==========

    @Test
    fun testMixedArithmeticAndLogical() {
        // From Lua tests: (1 and 2)+(-1.25 or -4) == 0.75
        assertEquals(0.75, execute("""return (1 and 2)+(-1.25 or -4)"""))
    }

    @Test
    fun testMixedComparison() {
        // 2<3 should be true, true and 4 should be 4
        assertEquals(4.0, execute("""return 2<3 and 4"""))
        assertTrue((execute("""return 2<3""") as LuaBoolean).value)
    }

    @Test
    fun testMinusZeroTableIndexing() {
        // From math.lua:87 - minus zero should be treated as zero for table indexing
        val result =
            execute(
                """
            local x = -1
            local mz = 0/x   -- minus zero
            local t = {[0] = 10, 20, 30, 40, 50}
            return t[mz] == t[0] and t[-0] == t[0]
        """,
            )
        assertTrue((result as LuaBoolean).value, "Minus zero should equal zero in table indexing")
    }

    @Test
    fun testMinIntegerAdditionOverflow() {
        // Test case from math.lua:116 - minint + 1 should be greater than minint
        // This tests integer overflow wrapping behavior
        val result =
            execute(
                """
            local minint = math.mininteger
            return minint < minint + 1
        """,
            )
        assertTrue((result as LuaBoolean).value, "minint < minint + 1 should be true (overflow wraps around)")
    }

    @Test
    fun testMaxIntLessThanMinIntNegatedFloat() {
        // Test case from math.lua:211 - maxint < minint * -1.0
        // minint * -1.0 produces a float that's slightly larger than maxint due to precision
        val result =
            execute(
                """
            local minint = math.mininteger
            local maxint = math.maxinteger
            return maxint < minint * -1.0
        """,
            )
        assertTrue((result as LuaBoolean).value, "maxint < minint * -1.0 should be true")
    }

    @Test
    fun testLogicalAndWithMultipleReturnValues() {
        // Test from constructs.lua:230
        // When 'and' returns right operand (a function call with multiple returns),
        // only the first return value should be used in assignment context

        vm.debugEnabled = true
        execute(
            """
            local function f() return 1, 2, 3 end
            local a, b = 3 and f()
            -- a should be 1 (first return value from f())
            -- b should be nil (assignment only gets one value from 'and' expression)
            assert(a == 1, "a should be 1 but got " .. tostring(a))
            assert(b == nil, "b should be nil but got " .. tostring(b))
        """,
        )
    }

    @Test
    fun testLogicalAndWithFunctionCallInCondition() {
        // Test from errors.lua line 55 - compound AND expression
        execute(
            """
            local function doit(s)
              local f, msg = load(s)
              if not f then return msg end
              local cond, msg = pcall(f)
              return (not cond) and msg
            end
            
            local result = not doit("tostring(1)") and doit("tostring()")
            assert(result, "should return error message from tostring()")
        """,
        )
    }

    @Test
    fun testLoadRejectsDoubleSemicolonAfterReturn() {
        // Test from errors.lua line 58 - parser should reject "return;;"
        // In Lua, return must be the last statement in a block
        // The syntax "return;;" is invalid because it implies another statement after return
        execute(
            """
            local f, msg = load("return;;")
            assert(f == nil, "load('return;;') should return nil")
            assert(type(msg) == "string", "load should return error message")
            assert(string.find(msg, "expected") ~= nil or string.find(msg, "near") ~= nil, 
                   "error message should indicate syntax error, got: " .. msg)
        """,
        )
    }
}
