package ai.tenum.lua.compiler

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for label shadowing behavior.
 * Labels in nested scopes should shadow labels with the same name in outer scopes.
 * When the nested scope exits, the outer label becomes visible again.
 */
class GotoLabelShadowingTest : LuaCompatTestBase() {
    @Test
    fun `goto from outer scope should find outer label not inner label`() =
        runTest {
            // This is the pattern that's failing
            val code =
                """
                local function testLabelShadowing(a)
                  if a == 1 then
                    goto l1  -- Should jump to outer ::l1:: (line 9)
                  elseif a == 2 then
                    goto l1  -- Should jump to inner ::l1:: (line 6)
                    ::l1:: return "inner"
                  end
                  do return a end  
                  ::l1:: return "outer"
                end
                
                assert(testLabelShadowing(1) == "outer", "a==1 should jump to outer l1")
                assert(testLabelShadowing(2) == "inner", "a==2 should jump to inner l1")
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `simple label shadowing with do blocks`() =
        runTest {
            val code =
                """
                local result = "none"
                goto l1
                do
                    ::l1::
                    result = "inner"
                    goto done
                end
                ::l1::
                result = "outer"
                ::done::
                assert(result == "outer", "goto from outside should jump to outer label")
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `forward goto in do block`() =
        runTest {
            val code =
                """
                local result = "none"
                do
                  goto skip
                  result = "skipped"
                  ::skip::
                  result = "reached"
                end
                assert(result == "reached", "forward goto in do block should work")
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `label shadowing with multiple nesting levels`() =
        runTest {
            val code =
                """
                local result = ""
                goto l1
                do
                    do
                        ::l1::
                        result = "deepest"
                        goto done
                    end
                    ::l1::
                    result = "middle"
                    goto done
                end
                ::l1::
                result = "outer"
                ::done::
                assert(result == "outer", "goto should jump to outermost visible label")
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `goto within same scope should find local label`() =
        runTest {
            val code =
                """
                local result = ""
                do
                    goto l1
                    ::l1::
                    result = "inner"
                end
                ::l2::
                result = result .. "outer"
                assert(result == "innerouter", "goto in same scope should find local label, then continue")
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `backward goto should find nearest visible label`() =
        runTest(timeout = 5.seconds) {
            val code =
                """
                local count = 0
                local safety = 0  -- Safety counter to prevent infinite loops
                ::l1::
                safety = safety + 1
                if safety > 100 then error("Safety abort: infinite loop detected") end
                count = count + 1
                if count == 1 then
                    do
                        ::l2::  -- Different label name to avoid duplicate error
                        count = count + 10
                        if count == 11 then goto l2 end -- backward to inner l2
                    end
                end
                if count == 21 then goto done end
                goto l1 -- backward to outer l1
                ::done::
                assert(count == 21, "expected count to be 21, got " .. count)
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `testG function from goto_lua line 220-247`() =
        runTest(timeout = 5.seconds) {
            // This is the actual failing test case from goto.lua
            val code =
                """
                local function testG (a)
                  local safety = 0  -- Safety counter to prevent infinite loops
                  if a == 1 then
                    goto l1
                    error("should never be here!")
                  elseif a == 2 then goto l2
                  elseif a == 3 then goto l3
                  elseif a == 4 then
                    goto l1  -- go to inside the block
                    error("should never be here!")
                    ::l1:: a = a + 1   -- must go to 'if' end
                  else
                    safety = safety + 1
                    if safety > 100 then error("Safety abort: infinite loop detected") end
                    goto l4
                    ::l4a:: a = a * 2; goto l4b
                    error("should never be here!")
                    ::l4:: goto l4a
                    error("should never be here!")
                    ::l4b::
                  end
                  do return a end
                  ::l2:: do return "2" end
                  ::l3:: do return "3" end
                  ::l1:: return "1"
                end

                assert(testG(1) == "1", "testG(1) should return '1'")
                assert(testG(2) == "2", "testG(2) should return '2'")
                assert(testG(3) == "3", "testG(3) should return '3'")
                assert(testG(4) == 5, "testG(4) should return 5")
                assert(testG(5) == 10, "testG(5) should return 10")
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `label shadowing with if-elseif chains`() =
        runTest {
            val code =
                """
                local function test(n)
                    if n == 1 then
                        goto label
                        ::label:: return "first"
                    elseif n == 2 then
                        goto label
                        ::label:: return "second"
                    elseif n == 3 then
                        goto label
                    end
                    ::label:: return "outer"
                end
                
                assert(test(1) == "first")
                assert(test(2) == "second")
                assert(test(3) == "outer")
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `forward goto should skip nested labels it cannot see`() =
        runTest {
            val code =
                """
                local x = 0
                goto skip
                
                if false then
                    ::skip::  -- This label is not visible from outer scope
                    x = 100
                end
                
                ::skip::  -- This is the visible label
                x = 1
                
                assert(x == 1, "Should jump to outer skip label")
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `label at end of scope should be reachable from that scope`() =
        runTest {
            val code =
                """
                local x = 0
                do
                    if true then goto last end
                    x = 99
                    ::last::
                    x = 5
                end
                assert(x == 5)
                """.trimIndent()

            execute(code)
        }

    @Test
    fun `multiple labels with same name in sequential blocks`() =
        runTest(timeout = 5.seconds) {
            val code =
                """
                local x = 0
                local safety = 0  -- Safety counter to prevent infinite loops
                do
                    safety = safety + 1
                    if safety > 100 then error("Safety abort: infinite loop detected in first block") end
                    goto finish
                    x = 1
                    ::finish::
                    x = x + 10
                end
                do
                    safety = safety + 1
                    if safety > 100 then error("Safety abort: infinite loop detected in second block") end
                    goto finish
                    x = x + 100
                    ::finish::
                    x = x + 1000
                end
                assert(x == 1010, "Expected 1010, got " .. x)
                """.trimIndent()

            execute(code)
        }
}
