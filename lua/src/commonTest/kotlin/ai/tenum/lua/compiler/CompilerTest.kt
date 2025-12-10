package ai.tenum.lua.compiler

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.OpCode
import io.kotest.matchers.collections.shouldContain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompilerTest {
    private fun compile(source: String): Proto {
        val lexer = Lexer(source)
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        val chunk = parser.parse()
        val compiler = Compiler(debugEnabled = true)
        return compiler.compile(chunk)
    }

    @Test
    fun testCompileNilLiteral() =
        runTest {
            val proto = compile("return nil")
            assertTrue(proto.instructions.size >= 2)
            assertEquals(OpCode.LOADNIL, proto.instructions[0].opcode)
            assertEquals(OpCode.RETURN, proto.instructions[1].opcode)
        }

    @Test
    fun testCompileBooleanLiteral() =
        runTest {
            val proto = compile("return true")
            assertTrue(proto.instructions.size >= 2)
            assertEquals(OpCode.LOADBOOL, proto.instructions[0].opcode)
            assertEquals(1, proto.instructions[0].b)
            assertEquals(OpCode.RETURN, proto.instructions[1].opcode)
        }

    @Test
    fun testCompileNumberLiteral() =
        runTest {
            val proto = compile("return 42")
            assertTrue(proto.instructions.size >= 2)
            // Integer 42 uses LOADI (immediate load) instead of LOADK
            assertEquals(OpCode.LOADI, proto.instructions[0].opcode)
            assertEquals(OpCode.RETURN, proto.instructions[1].opcode)
        }

    @Test
    fun testCompileStringLiteral() =
        runTest {
            val proto = compile("return \"hello\"")
            assertTrue(proto.instructions.size >= 2)
            assertEquals(OpCode.LOADK, proto.instructions[0].opcode)
            assertEquals(LuaString("hello"), proto.constants[proto.instructions[0].b])
        }

    @Test
    fun testCompileLocalDeclaration() =
        runTest {
            val proto = compile("local x = 10")
            // Integer 10 uses LOADI (immediate load) instead of LOADK
            assertTrue(proto.instructions.any { it.opcode == OpCode.LOADI })
        }

    @Test
    fun testCompileLocalVariable() =
        runTest {
            val proto =
                compile(
                    """
                    local x = 5
                    return x
                    """.trimIndent(),
                )

            // Integer 5 uses LOADI (immediate load) instead of LOADK
            assertTrue(proto.instructions.any { it.opcode == OpCode.LOADI })
            assertTrue(proto.instructions.any { it.opcode == OpCode.RETURN })
        }

    @Test
    fun testCompileGlobalVariable() =
        runTest {
            val proto = compile("return x")
            // In Lua 5.2+, global access is compiled to GETUPVAL(_ENV) + GETTABLE.
            // The test now reflects this modern behavior.
            proto.instructions.map { it.opcode } shouldContain OpCode.GETUPVAL
            proto.constants shouldContain LuaString("x")
        }

    @Test
    fun testCompileAssignment() =
        runTest {
            val proto =
                compile(
                    """
                    local x
                    x = 10
                    """.trimIndent(),
                )

            // Integer 10 uses LOADI (immediate load) instead of LOADK
            assertTrue(proto.instructions.any { it.opcode == OpCode.LOADI || it.opcode == OpCode.MOVE })
        }

    @Test
    fun testCompileGlobalAssignment() =
        runTest {
            val proto = compile("x = 10")
            // Lua 5.2+ uses _ENV table for globals: GETUPVAL + SETTABLE instead of SETGLOBAL
            assertTrue(proto.instructions.any { it.opcode == OpCode.SETTABLE })
            assertTrue(proto.constants.contains(LuaString("x")))
        }

    @Test
    fun testCompileAddition() =
        runTest {
            val proto = compile("return 1 + 2")
            assertTrue(proto.instructions.any { it.opcode == OpCode.ADD })
        }

    @Test
    fun testCompileSubtraction() =
        runTest {
            val proto = compile("return 5 - 3")
            assertTrue(proto.instructions.any { it.opcode == OpCode.SUB })
        }

    @Test
    fun testCompileMultiplication() =
        runTest {
            val proto = compile("return 4 * 3")
            assertTrue(proto.instructions.any { it.opcode == OpCode.MUL })
        }

    @Test
    fun testCompileDivision() =
        runTest {
            val proto = compile("return 10 / 2")
            assertTrue(proto.instructions.any { it.opcode == OpCode.DIV })
        }

    @Test
    fun testCompilePower() =
        runTest {
            val proto = compile("return 2 ^ 3")
            assertTrue(proto.instructions.any { it.opcode == OpCode.POW })
        }

    @Test
    fun testCompileModulo() =
        runTest {
            val proto = compile("return 10 % 3")
            assertTrue(proto.instructions.any { it.opcode == OpCode.MOD })
        }

    @Test
    fun testCompileConcat() =
        runTest {
            val proto = compile("return \"hello\" .. \"world\"")
            assertTrue(proto.instructions.any { it.opcode == OpCode.CONCAT })
        }

    @Test
    fun testCompileUnaryMinus() =
        runTest {
            val proto = compile("return -5")
            assertTrue(proto.instructions.any { it.opcode == OpCode.UNM })
        }

    @Test
    fun testCompileNot() =
        runTest {
            val proto = compile("return not true")
            assertTrue(proto.instructions.any { it.opcode == OpCode.NOT })
        }

    @Test
    fun testCompileLength() =
        runTest {
            val proto = compile("return #\"hello\"")
            assertTrue(proto.instructions.any { it.opcode == OpCode.LEN })
        }

    @Test
    fun testCompileComplexExpression() =
        runTest {
            val proto = compile("return (1 + 2) * 3")
            assertTrue(proto.instructions.any { it.opcode == OpCode.ADD })
            assertTrue(proto.instructions.any { it.opcode == OpCode.MUL })
        }

    @Test
    fun testCompileTableConstructorEmpty() =
        runTest {
            val proto = compile("return {}")
            assertTrue(proto.instructions.any { it.opcode == OpCode.NEWTABLE })
        }

    @Test
    fun testCompileTableConstructorList() =
        runTest {
            val proto = compile("return {1, 2, 3}")
            assertTrue(proto.instructions.any { it.opcode == OpCode.NEWTABLE })
            // List fields now use SETLIST instead of individual SETTABLE
            assertTrue(proto.instructions.any { it.opcode == OpCode.SETLIST })
        }

    @Test
    fun testCompileTableConstructorNamed() =
        runTest {
            val proto = compile("return {x = 10, y = 20}")
            assertTrue(proto.instructions.any { it.opcode == OpCode.NEWTABLE })
            assertTrue(proto.instructions.count { it.opcode == OpCode.SETTABLE } >= 2)
        }

    @Test
    fun testCompileTableConstructorMixed() =
        runTest {
            val proto = compile("return {1, 2, x = 10}")
            assertTrue(proto.instructions.any { it.opcode == OpCode.NEWTABLE })
            // List fields use SETLIST, named fields use SETTABLE
            assertTrue(proto.instructions.any { it.opcode == OpCode.SETLIST })
            assertTrue(proto.instructions.any { it.opcode == OpCode.SETTABLE })
        }

    @Test
    fun testCompileFieldAccess() =
        runTest {
            val proto = compile("return t.x")
            assertTrue(proto.instructions.any { it.opcode == OpCode.GETGLOBAL || it.opcode == OpCode.GETTABLE })
        }

    @Test
    fun testCompileIndexAccess() =
        runTest {
            val proto = compile("return t[1]")
            assertTrue(proto.instructions.any { it.opcode == OpCode.GETTABLE })
        }

    @Test
    fun testCompileFunctionDeclaration() =
        runTest {
            val proto =
                compile(
                    """
                    function add(a, b)
                        return a + b
                    end
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.CLOSURE })
            assertTrue(proto.instructions.any { it.opcode == OpCode.SETGLOBAL })
        }

    @Test
    fun testCompileLocalFunctionDeclaration() =
        runTest {
            val proto =
                compile(
                    """
                    local function add(a, b)
                        return a + b
                    end
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.CLOSURE })
        }

    @Test
    fun testCompileFunctionCall() =
        runTest {
            val proto = compile("return add(1, 2)")
            // Tail call optimization: return f() becomes TAILCALL instead of CALL+RETURN
            assertTrue(proto.instructions.any { it.opcode == OpCode.TAILCALL })
        }

    @Test
    fun testCompileIfStatement() =
        runTest {
            val proto =
                compile(
                    """
                    if x then
                        return 1
                    end
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.TEST })
            assertTrue(proto.instructions.any { it.opcode == OpCode.JMP })
        }

    @Test
    fun testCompileIfElseStatement() =
        runTest {
            val proto =
                compile(
                    """
                    if x then
                        return 1
                    else
                        return 2
                    end
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.TEST })
            assertTrue(proto.instructions.count { it.opcode == OpCode.JMP } >= 2)
        }

    @Test
    fun testCompileWhileLoop() =
        runTest {
            val proto =
                compile(
                    """
                    while x do
                        x = x - 1
                    end
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.TEST })
            assertTrue(proto.instructions.any { it.opcode == OpCode.JMP })
        }

    @Test
    fun testCompileRepeatLoop() =
        runTest {
            val proto =
                compile(
                    """
                    repeat
                        x = x - 1
                    until x == 0
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.TEST })
            assertTrue(proto.instructions.any { it.opcode == OpCode.JMP })
        }

    @Test
    fun testCompileForLoop() =
        runTest {
            val proto =
                compile(
                    """
                    for i = 1, 10 do
                        print(i)
                    end
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.FORPREP })
            assertTrue(proto.instructions.any { it.opcode == OpCode.FORLOOP })
        }

    @Test
    fun testCompileForLoopWithStep() =
        runTest {
            val proto =
                compile(
                    """
                    for i = 1, 10, 2 do
                        print(i)
                    end
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.FORPREP })
            assertTrue(proto.instructions.any { it.opcode == OpCode.FORLOOP })
        }

    @Test
    fun testCompileForInLoop() =
        runTest {
            val proto =
                compile(
                    """
                    for k, v in pairs(t) do
                        print(k, v)
                    end
                    """.trimIndent(),
                )

            val opcodes = proto.instructions.map { it.opcode }

            // Modern Lua (5.2+) compiles `for ... in` by first resolving the iterators
            // (e.g., `pairs` and `t`) and then using TFORCALL/TFORLOOP.
            // Let's check for the essential opcodes.
            opcodes shouldContain OpCode.GETUPVAL // For _ENV
            opcodes shouldContain OpCode.GETTABLE // For 'pairs' and 't'
            opcodes shouldContain OpCode.CALL // For calling pairs(t)
            opcodes shouldContain OpCode.TFORCALL
            opcodes shouldContain OpCode.TFORLOOP
        }

    @Test
    fun testCompileDoBlock() =
        runTest {
            val proto =
                compile(
                    """
                    do
                        local x = 10
                    end
                    """.trimIndent(),
                )

            // Should have instructions for local variable
            assertTrue(proto.instructions.isNotEmpty())
        }

    @Test
    fun testCompileMultipleStatements() =
        runTest {
            val proto =
                compile(
                    """
                    local x = 10
                    local y = 20
                    return x + y
                    """.trimIndent(),
                )

            // Integers 10 and 20 use LOADI (immediate load) instead of LOADK
            assertTrue(proto.instructions.count { it.opcode == OpCode.LOADI } >= 2)
            assertTrue(proto.instructions.any { it.opcode == OpCode.ADD })
            assertTrue(proto.instructions.any { it.opcode == OpCode.RETURN })
        }

    @Test
    fun testCompileEmptyReturn() =
        runTest {
            val proto =
                compile(
                    """
                    do
                        return
                    end
                    """.trimIndent(),
                )
            assertTrue(proto.instructions.any { it.opcode == OpCode.RETURN })
        }

    @Test
    fun testCompileMultipleReturnValues() =
        runTest {
            val proto = compile("return 1, 2, 3")
            val returnInstr = proto.instructions.find { it.opcode == OpCode.RETURN }
            assertNotNull(returnInstr)
            assertEquals(4, returnInstr.b) // 3 values + 1
        }

    @Test
    fun testConstantPoolOptimization() =
        runTest {
            val proto =
                compile(
                    """
                    local x = 10
                    local y = 10
                    """.trimIndent(),
                )

            // With LOADI optimization, small integers (like 10) are not in the constant pool
            // They're encoded directly in LOADI instructions
            // Test that LOADI is used instead
            val loadiInstructions = proto.instructions.filter { it.opcode == OpCode.LOADI }
            assertTrue(loadiInstructions.size >= 2)
            // Both should load the value 10
            assertTrue(loadiInstructions.all { it.b == 10 })
        }

    @Test
    fun testMaxStackSize() =
        runTest {
            val proto =
                compile(
                    """
                    local a = 1
                    local b = 2
                    local c = 3
                    return a + b + c
                    """.trimIndent(),
                )

            assertTrue(proto.maxStackSize > 0)
        }

    @Test
    fun testFunctionExpression() =
        runTest {
            val proto =
                compile(
                    """
                    local f = function(x) return x * 2 end
                    """.trimIndent(),
                )

            assertTrue(proto.instructions.any { it.opcode == OpCode.CLOSURE })
        }

    @Test
    fun testMethodCall() =
        runTest {
            val proto = compile("return obj:method()")
            assertTrue(proto.instructions.any { it.opcode == OpCode.SELF })
            // Method calls in return position are tail-call optimized (confirmed in Lua 5.4)
            assertTrue(proto.instructions.any { it.opcode == OpCode.TAILCALL })
        }

    @Test
    fun testInstructionToString() =
        runTest {
            val instr = Instruction(OpCode.LOADK, a = 0, b = 5)
            assertEquals("LOADK A=0 B=5 C=0", instr.toString())
        }

    @Test
    fun testInstructionToStringBx() =
        runTest {
            val instr = Instruction(OpCode.LOADK, a = 0, bx = 1000)
            assertEquals("LOADK A=0 Bx=1000", instr.toString())
        }

    @Test
    fun testProtoParameters() =
        runTest {
            val proto =
                compile(
                    """
                    function add(a, b)
                        return a + b
                    end
                    """.trimIndent(),
                )

            // Main chunk shouldn't have parameters
            assertEquals(emptyList(), proto.parameters)
        }
}
