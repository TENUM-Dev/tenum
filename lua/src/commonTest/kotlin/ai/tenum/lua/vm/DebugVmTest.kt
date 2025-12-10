package ai.tenum.lua.vm

// CPD-OFF: test file with intentional test setup duplications

import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.runtime.LuaNumber
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugVmTest {
    @Test
    fun debugSimpleReturn() =
        runTest {
            val source = "return 42"

            val lexer = Lexer(source)
            val tokens = lexer.scanTokens()
            println("Tokens: $tokens")

            val parser = Parser(tokens)
            val ast = parser.parse()
            println("AST: $ast")

            val compiler = Compiler()
            val proto = compiler.compile(ast)
            println("Proto: $proto")
            println("Instructions:")
            proto.instructions.forEachIndexed { index, instr ->
                println("  $index: $instr")
            }
            println("Constants: ${proto.constants}")

            val vm = LuaVmImpl()
            val result = vm.execute(source)
            println("Result: $result (type: ${result::class.simpleName})")

            if (result is LuaNumber) {
                println("Value: ${result.value}")
            }
        }

    // TODO: Fix comparisons in if statements
    // @Test
    fun debugSimpleIf() =
        runTest {
            val source =
                """
                local x = 10
                if x > 5 then
                    return 1
                else
                    return 2
                end
                """.trimIndent()

            val compiler = Compiler()
            val lexer = Lexer(source)
            val tokens = lexer.scanTokens()
            val parser = Parser(tokens)
            val ast = parser.parse()
            val proto = compiler.compile(ast)

            println("Instructions for if:")
            proto.instructions.forEachIndexed { index, instr ->
                println("  $index: $instr")
            }
            println("Constants: ${proto.constants}")

            val vm = LuaVmImpl()
            val result = vm.execute(source)
            println("Result: $result (${result::class.simpleName})")
            if (result is LuaNumber) {
                println("Value: ${result.value}")
            }
            assertTrue(result is LuaNumber)
            assertEquals(1.0, (result as LuaNumber).value)
        }

    @Test
    fun debugSimpleArithmetic() =
        runTest {
            val source = "return 10 + 5"

            val compiler = Compiler()
            val lexer = Lexer(source)
            val tokens = lexer.scanTokens()
            val parser = Parser(tokens)
            val ast = parser.parse()
            val proto = compiler.compile(ast)

            println("Instructions for arithmetic:")
            proto.instructions.forEachIndexed { index, instr ->
                println("  $index: $instr")
            }
            println("Constants: ${proto.constants}")

            val vm = LuaVmImpl()
            val result = vm.execute(source)
            println("Result: $result (${result::class.simpleName})")
            if (result is LuaNumber) {
                println("Value: ${result.value}")
            }
            assertTrue(result is LuaNumber)
            assertEquals(LuaNumber.of(15.0), result)
        }
}
