package ai.tenum.lua.compiler

import ai.tenum.lua.parser.ParserException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

/**
 * Tests for goto/label scope validation rules.
 * These tests verify Lua 5.4.8 semantics for goto statements.
 */
class GotoLabelScopeValidationTest : CompilerTestBase() {
    @Test
    fun `cannot jump over local when there are statements after label`() {
        // This is the pattern from goto.lua line 21
        // goto l1; local aa ::l1:: ::l2:: print(3)
        // The label ::l1:: has code after it (::l2:: and print(3))
        // So jumping over 'local aa' is forbidden
        val code =
            """
            goto l1
            local aa
            ::l1::
            ::l2::
            print(3)
            """.trimIndent()

        val exception =
            shouldThrow<ParserException> {
                compile(code)
            }
        exception.message shouldContain "local 'aa'"
    }

    @Test
    fun `can jump over local when label is at end of block`() {
        // This is the pattern from goto.lua line 73-78
        // The label is the last statement before 'end'
        // So jumping over locals is OK because nothing executes in their scope
        val code =
            """
            do
                goto l1
                local a = 23
                local x = a
                ::l1::
            end
            """.trimIndent()

        // Should compile successfully
        compile(code)
    }

    @Test
    fun `cannot jump over local in repeat-until when condition uses the local`() {
        // This is the pattern from goto.lua line 34-41
        // In repeat-until, the 'until' condition is semantically part of the block scope
        // So jumping over locals to ANY label in a repeat block is forbidden
        val code =
            """
            repeat
                if x then goto cont end
                local xuxu = 10
                ::cont::
            until xuxu < x
            """.trimIndent()

        val exception =
            shouldThrow<ParserException> {
                compile(code)
            }
        exception.message shouldContain "local 'xuxu'"
    }

    @Test
    fun `cannot jump over local in repeat-until even without using the local in condition`() {
        // Even if the condition doesn't use the local, jumping over it is still forbidden
        // because the condition is part of the block's scope
        val code =
            """
            repeat
                if true then goto cont end
                local x = 10
                ::cont::
            until false
            """.trimIndent()

        val exception =
            shouldThrow<ParserException> {
                compile(code)
            }
        exception.message shouldContain "local 'x'"
    }

    @Test
    fun `cannot jump over local when there is code after label in same scope`() {
        // Simple case: goto over local, then execute code that uses that scope
        val code =
            """
            goto l1
            local x = 5
            ::l1::
            print(x)
            """.trimIndent()

        val exception =
            shouldThrow<ParserException> {
                compile(code)
            }
        exception.message shouldContain "local 'x'"
    }

    @Test
    fun `can jump over multiple locals when label is at end`() {
        val code =
            """
            do
                goto finish
                local a = 1
                local b = 2
                local c = 3
                ::finish::
            end
            print('ok')
            """.trimIndent()

        // Should compile successfully
        compile(code)
    }

    @Test
    fun `cannot jump over local with assignment after label`() {
        val code =
            """
            goto l1
            local a
            ::l1::
            a = 5
            """.trimIndent()

        val exception =
            shouldThrow<ParserException> {
                compile(code)
            }
        exception.message shouldContain "local 'a'"
    }

    @Test
    fun `can jump forward to label at block end even with locals`() {
        // Edge case: multiple labels at end
        val code =
            """
            do
                goto l2
                local x = 1
                ::l1::
                ::l2::
            end
            """.trimIndent()

        // Should compile successfully
        compile(code)
    }

    @Test
    fun `backward jump over local is always ok`() {
        // Backward jumps are always allowed
        val code =
            """
            ::back::
            local x = 1
            if x then goto back end
            """.trimIndent()

        // Should compile successfully
        compile(code)
    }

    @Test
    fun `label shadowing - inner label shadows outer in nested scope`() {
        // When a label with the same name is defined in a nested scope,
        // gotos within that scope should jump to the inner label,
        // but gotos from outer scopes should jump to the outer label
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
            
            assert(testLabelShadowing(1) == "outer")
            assert(testLabelShadowing(2) == "inner")
            """.trimIndent()

        // Should compile successfully
        compile(code)
    }
}
