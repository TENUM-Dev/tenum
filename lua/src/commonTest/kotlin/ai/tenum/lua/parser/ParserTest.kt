package ai.tenum.lua.parser

import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.ast.Assignment
import ai.tenum.lua.parser.ast.BinaryOp
import ai.tenum.lua.parser.ast.BooleanLiteral
import ai.tenum.lua.parser.ast.BreakStatement
import ai.tenum.lua.parser.ast.Chunk
import ai.tenum.lua.parser.ast.DoStatement
import ai.tenum.lua.parser.ast.ExpressionStatement
import ai.tenum.lua.parser.ast.FieldAccess
import ai.tenum.lua.parser.ast.ForInStatement
import ai.tenum.lua.parser.ast.ForStatement
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.FunctionDeclaration
import ai.tenum.lua.parser.ast.FunctionExpression
import ai.tenum.lua.parser.ast.IfStatement
import ai.tenum.lua.parser.ast.IndexAccess
import ai.tenum.lua.parser.ast.LocalDeclaration
import ai.tenum.lua.parser.ast.LocalFunctionDeclaration
import ai.tenum.lua.parser.ast.MethodCall
import ai.tenum.lua.parser.ast.NilLiteral
import ai.tenum.lua.parser.ast.NumberLiteral
import ai.tenum.lua.parser.ast.ParenExpression
import ai.tenum.lua.parser.ast.RepeatStatement
import ai.tenum.lua.parser.ast.ReturnStatement
import ai.tenum.lua.parser.ast.StringLiteral
import ai.tenum.lua.parser.ast.TableConstructor
import ai.tenum.lua.parser.ast.TableField
import ai.tenum.lua.parser.ast.UnaryOp
import ai.tenum.lua.parser.ast.VarargExpression
import ai.tenum.lua.parser.ast.Variable
import ai.tenum.lua.parser.ast.WhileStatement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Test suite for the Lua parser
 */
class ParserTest {
    private fun parse(source: String): Chunk {
        val lexer = Lexer(source)
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        return parser.parse()
    }

    @Test
    fun testEmptyChunk() =
        runTest {
            val chunk = parse("")
            assertEquals(0, chunk.statements.size)
        }

    @Test
    fun testNumberLiteral() =
        runTest {
            val chunk = parse("return 42")
            assertEquals(1, chunk.statements.size)

            val stmt = chunk.statements[0]
            assertIs<ReturnStatement>(stmt)
            assertEquals(1, stmt.expressions.size)

            val expr = stmt.expressions[0]
            assertIs<NumberLiteral>(expr)
            assertEquals(42L, expr.value) // Now a Long, not Double
        }

    @Test
    fun testStringLiteral() =
        runTest {
            val chunk = parse("return \"hello\"")
            val stmt = chunk.statements[0] as ReturnStatement
            val expr = stmt.expressions[0] as StringLiteral
            assertEquals("hello", expr.value)
        }

    @Test
    fun testBooleanLiterals() =
        runTest {
            val chunk = parse("return true, false")
            val stmt = chunk.statements[0] as ReturnStatement
            assertEquals(2, stmt.expressions.size)

            val trueExpr = stmt.expressions[0] as BooleanLiteral
            assertTrue(trueExpr.value)

            val falseExpr = stmt.expressions[1] as BooleanLiteral
            assertEquals(false, falseExpr.value)
        }

    @Test
    fun testNilLiteral() =
        runTest {
            val chunk = parse("return nil")
            val stmt = chunk.statements[0] as ReturnStatement
            assertIs<NilLiteral>(stmt.expressions[0])
        }

    @Test
    fun testVariable() =
        runTest {
            val chunk = parse("return x")
            val stmt = chunk.statements[0] as ReturnStatement
            val expr = stmt.expressions[0] as Variable
            assertEquals("x", expr.name)
        }

    @Test
    fun testLocalDeclaration() =
        runTest {
            val chunk = parse("local x = 10")
            val stmt = chunk.statements[0] as LocalDeclaration
            assertEquals(1, stmt.names.size)
            assertEquals("x", stmt.names[0])
            assertEquals(1, stmt.expressions.size)

            val expr = stmt.expressions[0] as NumberLiteral
            assertEquals(10L, expr.value) // Now a Long, not Double
        }

    @Test
    fun testMultipleLocalDeclaration() =
        runTest {
            val chunk = parse("local x, y, z = 1, 2, 3")
            val stmt = chunk.statements[0] as LocalDeclaration
            assertEquals(3, stmt.names.size)
            assertEquals(3, stmt.expressions.size)
        }

    @Test
    fun testLocalWithoutInitializer() =
        runTest {
            val chunk = parse("local x")
            val stmt = chunk.statements[0] as LocalDeclaration
            assertEquals(1, stmt.names.size)
            assertEquals(0, stmt.expressions.size)
        }

    @Test
    fun testAssignment() =
        runTest {
            val chunk = parse("x = 10")
            val stmt = chunk.statements[0] as Assignment
            assertEquals(1, stmt.variables.size)
            assertEquals(1, stmt.expressions.size)

            val variable = stmt.variables[0] as Variable
            assertEquals("x", variable.name)

            val expr = stmt.expressions[0] as NumberLiteral
            assertEquals(10L, expr.value) // Now a Long, not Double
        }

    @Test
    fun testMultipleAssignment() =
        runTest {
            val chunk = parse("x, y = 1, 2")
            val stmt = chunk.statements[0] as Assignment
            assertEquals(2, stmt.variables.size)
            assertEquals(2, stmt.expressions.size)
        }

    @Test
    fun testBinaryAddition() =
        runTest {
            val chunk = parse("return 1 + 2")
            val stmt = chunk.statements[0] as ReturnStatement
            val expr = stmt.expressions[0] as BinaryOp

            val left = expr.left as NumberLiteral
            assertEquals(1L, left.value) // Now a Long, not Double

            val right = expr.right as NumberLiteral
            assertEquals(2L, right.value) // Now a Long, not Double
        }

    @Test
    fun testBinaryOperations() =
        runTest {
            val expressions =
                listOf(
                    "1 + 2",
                    "3 - 4",
                    "5 * 6",
                    "7 / 8",
                    "9 % 10",
                    "2 ^ 3",
                )

            for (expr in expressions) {
                val chunk = parse("return $expr")
                val stmt = chunk.statements[0] as ReturnStatement
                assertIs<BinaryOp>(stmt.expressions[0])
            }
        }

    @Test
    fun testComparisonOperations() =
        runTest {
            val expressions =
                listOf(
                    "x < y",
                    "x <= y",
                    "x > y",
                    "x >= y",
                    "x == y",
                    "x ~= y",
                )

            for (expr in expressions) {
                val chunk = parse("return $expr")
                val stmt = chunk.statements[0] as ReturnStatement
                assertIs<BinaryOp>(stmt.expressions[0])
            }
        }

    @Test
    fun testLogicalOperations() =
        runTest {
            val chunk = parse("return x and y or z")
            val stmt = chunk.statements[0] as ReturnStatement
            assertIs<BinaryOp>(stmt.expressions[0])
        }

    @Test
    fun testUnaryOperations() =
        runTest {
            val chunk = parse("return not x, -y, #z")
            val stmt = chunk.statements[0] as ReturnStatement
            assertEquals(3, stmt.expressions.size)

            assertIs<UnaryOp>(stmt.expressions[0])
            assertIs<UnaryOp>(stmt.expressions[1])
            assertIs<UnaryOp>(stmt.expressions[2])
        }

    @Test
    fun testParenthesizedExpression() =
        runTest {
            val chunk = parse("return (1 + 2) * 3")
            val stmt = chunk.statements[0] as ReturnStatement
            val expr = stmt.expressions[0] as BinaryOp

            assertIs<ParenExpression>(expr.left)
        }

    @Test
    fun testFunctionCall() =
        runTest {
            val chunk = parse("print(\"hello\")")
            val stmt = chunk.statements[0] as ExpressionStatement
            val call = stmt.expression as FunctionCall

            val func = call.function as Variable
            assertEquals("print", func.name)
            assertEquals(1, call.arguments.size)
        }

    @Test
    fun testFunctionCallNoArgs() =
        runTest {
            val chunk = parse("foo()")
            val stmt = chunk.statements[0] as ExpressionStatement
            val call = stmt.expression as FunctionCall
            assertEquals(0, call.arguments.size)
        }

    @Test
    fun testFunctionDeclaration() =
        runTest {
            val chunk = parse("function add(a, b) return a + b end")
            val stmt = chunk.statements[0] as FunctionDeclaration

            assertEquals("add", stmt.name)
            assertEquals(2, stmt.parameters.size)
            assertEquals("a", stmt.parameters[0])
            assertEquals("b", stmt.parameters[1])
            assertEquals(false, stmt.hasVararg)
            assertEquals(1, stmt.body.size)
        }

    @Test
    fun testFunctionWithVararg() =
        runTest {
            val chunk = parse("function test(a, ...) end")
            val stmt = chunk.statements[0] as FunctionDeclaration

            assertEquals(1, stmt.parameters.size)
            assertTrue(stmt.hasVararg)
        }

    @Test
    fun testLocalFunctionDeclaration() =
        runTest {
            val chunk = parse("local function helper() end")
            val stmt = chunk.statements[0] as LocalFunctionDeclaration

            assertEquals("helper", stmt.name)
            assertEquals(0, stmt.parameters.size)
        }

    @Test
    fun testIfStatement() =
        runTest {
            val chunk = parse("if x > 0 then return x end")
            val stmt = chunk.statements[0] as IfStatement

            assertIs<BinaryOp>(stmt.condition)
            assertEquals(1, stmt.thenBlock.size)
            assertEquals(0, stmt.elseIfBlocks.size)
            assertEquals(null, stmt.elseBlock)
        }

    @Test
    fun testIfElseStatement() =
        runTest {
            val chunk = parse("if x > 0 then return x else return -x end")
            val stmt = chunk.statements[0] as IfStatement

            assertEquals(1, stmt.thenBlock.size)
            assertEquals(1, stmt.elseBlock?.size)
        }

    @Test
    fun testIfElseIfStatement() =
        runTest {
            val chunk = parse("if x > 0 then return 1 elseif x < 0 then return -1 else return 0 end")
            val stmt = chunk.statements[0] as IfStatement

            assertEquals(1, stmt.elseIfBlocks.size)
            assertEquals(1, stmt.elseBlock?.size)
        }

    @Test
    fun testWhileLoop() =
        runTest {
            val chunk = parse("while i < 10 do i = i + 1 end")
            val stmt = chunk.statements[0] as WhileStatement

            assertIs<BinaryOp>(stmt.condition)
            assertEquals(1, stmt.block.size)
        }

    @Test
    fun testRepeatLoop() =
        runTest {
            val chunk = parse("repeat i = i + 1 until i >= 10")
            val stmt = chunk.statements[0] as RepeatStatement

            assertEquals(1, stmt.block.size)
            assertIs<BinaryOp>(stmt.condition)
        }

    @Test
    fun testNumericForLoop() =
        runTest {
            val chunk = parse("for i = 1, 10 do print(i) end")
            val stmt = chunk.statements[0] as ForStatement

            assertEquals("i", stmt.variable)
            assertIs<NumberLiteral>(stmt.start)
            assertIs<NumberLiteral>(stmt.end)
            assertEquals(null, stmt.step)
            assertEquals(1, stmt.block.size)
        }

    @Test
    fun testNumericForLoopWithStep() =
        runTest {
            val chunk = parse("for i = 1, 10, 2 do print(i) end")
            val stmt = chunk.statements[0] as ForStatement

            assertIs<NumberLiteral>(stmt.step)
        }

    @Test
    fun testGenericForLoop() =
        runTest {
            val chunk = parse("for k, v in pairs(t) do print(k, v) end")
            val stmt = chunk.statements[0] as ForInStatement

            assertEquals(2, stmt.variables.size)
            assertEquals("k", stmt.variables[0])
            assertEquals("v", stmt.variables[1])
            assertEquals(1, stmt.expressions.size)
            assertEquals(1, stmt.block.size)
        }

    @Test
    fun testDoBlock() =
        runTest {
            val chunk = parse("do local x = 10 end")
            val stmt = chunk.statements[0] as DoStatement
            assertEquals(1, stmt.block.size)
        }

    @Test
    fun testBreakStatement() =
        runTest {
            val chunk = parse("while true do break end")
            val stmt = chunk.statements[0] as WhileStatement
            assertIs<BreakStatement>(stmt.block[0])
        }

    @Test
    fun testTableConstructorEmpty() =
        runTest {
            val chunk = parse("return {}")
            val stmt = chunk.statements[0] as ReturnStatement
            val table = stmt.expressions[0] as TableConstructor
            assertEquals(0, table.fields.size)
        }

    @Test
    fun testTableConstructorList() =
        runTest {
            val chunk = parse("return {1, 2, 3}")
            val stmt = chunk.statements[0] as ReturnStatement
            val table = stmt.expressions[0] as TableConstructor
            assertEquals(3, table.fields.size)

            for (field in table.fields) {
                assertIs<TableField.ListField>(field)
            }
        }

    @Test
    fun testTableConstructorRecord() =
        runTest {
            val chunk = parse("return {x = 10, y = 20}")
            val stmt = chunk.statements[0] as ReturnStatement
            val table = stmt.expressions[0] as TableConstructor
            assertEquals(2, table.fields.size)

            val field1 = table.fields[0] as TableField.NamedField
            assertEquals("x", field1.name)

            val field2 = table.fields[1] as TableField.NamedField
            assertEquals("y", field2.name)
        }

    @Test
    fun testTableConstructorMixed() =
        runTest {
            val chunk = parse("return {1, 2, x = 10, [\"key\"] = \"value\"}")
            val stmt = chunk.statements[0] as ReturnStatement
            val table = stmt.expressions[0] as TableConstructor
            assertEquals(4, table.fields.size)
        }

    @Test
    fun testTableConstructorWithSemicolons() =
        runTest {
            val chunk = parse("return {0;1;2;3}")
            val stmt = chunk.statements[0] as ReturnStatement
            val table = stmt.expressions[0] as TableConstructor
            assertEquals(4, table.fields.size)

            for (field in table.fields) {
                assertIs<TableField.ListField>(field)
            }
        }

    @Test
    fun testTableIndexAccess() =
        runTest {
            val chunk = parse("return t[1]")
            val stmt = chunk.statements[0] as ReturnStatement
            val access = stmt.expressions[0] as IndexAccess

            val table = access.table as Variable
            assertEquals("t", table.name)

            assertIs<NumberLiteral>(access.index)
        }

    @Test
    fun testTableFieldAccess() =
        runTest {
            val chunk = parse("return t.field")
            val stmt = chunk.statements[0] as ReturnStatement
            val access = stmt.expressions[0] as FieldAccess

            val table = access.table as Variable
            assertEquals("t", table.name)
            assertEquals("field", access.field)
        }

    @Test
    fun testMethodCall() =
        runTest {
            val chunk = parse("obj:method(arg)")
            val stmt = chunk.statements[0] as ExpressionStatement
            val call = stmt.expression as MethodCall

            val obj = call.receiver as Variable
            assertEquals("obj", obj.name)
            assertEquals("method", call.method)
            assertEquals(1, call.arguments.size)
        }

    @Test
    fun testFunctionExpression() =
        runTest {
            val chunk = parse("local f = function(x) return x * 2 end")
            val stmt = chunk.statements[0] as LocalDeclaration
            val expr = stmt.expressions[0] as FunctionExpression

            assertEquals(1, expr.parameters.size)
            assertEquals(1, expr.body.size)
        }

    @Test
    fun testStringConcatenation() =
        runTest {
            val chunk = parse("return \"hello\" .. \" \" .. \"world\"")
            val stmt = chunk.statements[0] as ReturnStatement
            assertIs<BinaryOp>(stmt.expressions[0])
        }

    @Test
    fun testComplexExpression() =
        runTest {
            val chunk = parse("return (a + b) * (c - d)")
            val stmt = chunk.statements[0] as ReturnStatement
            val expr = stmt.expressions[0] as BinaryOp

            assertIs<ParenExpression>(expr.left)
            assertIs<ParenExpression>(expr.right)
        }

    @Test
    fun testChainedTableAccess() =
        runTest {
            val chunk = parse("return a.b.c")
            val stmt = chunk.statements[0] as ReturnStatement
            val expr = stmt.expressions[0] as FieldAccess

            assertEquals("c", expr.field)

            val innerAccess = expr.table as FieldAccess
            assertEquals("b", innerAccess.field)
        }

    @Test
    fun testMultipleStatements() =
        runTest {
            val source =
                """
                local x = 10
                local y = 20
                return x + y
                """.trimIndent()

            val chunk = parse(source)
            assertEquals(3, chunk.statements.size)
        }

    @Test
    fun testVarargExpression() =
        runTest {
            val chunk = parse("function test(...) return ... end")
            val stmt = chunk.statements[0] as FunctionDeclaration
            val returnStmt = stmt.body[0] as ReturnStatement
            assertIs<VarargExpression>(returnStmt.expressions[0])
        }

    @Test
    fun testMultilineFunctionCallWithVariable() =
        runTest {
            // In Lua 5.4, a variable followed by ( on next line is treated as a function call
            // This matches errors.lua:413 test case
            val source =
                """
                a
                (23)
                """.trimIndent()

            val chunk = parse(source)

            // Should parse as ONE statement (expression statement with function call)
            assertEquals(1, chunk.statements.size)

            val stmt = chunk.statements[0]
            assertIs<ExpressionStatement>(stmt)

            val call = stmt.expression
            assertIs<FunctionCall>(call)

            // Function should be Variable 'a'
            assertIs<Variable>(call.function)
            assertEquals("a", (call.function as Variable).name)

            // Arguments should be [23]
            assertEquals(1, call.arguments.size)
            val arg = call.arguments[0]
            assertIs<NumberLiteral>(arg)
            assertEquals(23L, arg.value)
        }
}
