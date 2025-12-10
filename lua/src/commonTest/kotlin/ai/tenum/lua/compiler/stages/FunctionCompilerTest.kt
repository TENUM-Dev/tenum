package ai.tenum.lua.compiler.stages

// CPD-OFF: test file with intentional AST fixture duplications

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.compiler.helper.ConstantPool
import ai.tenum.lua.compiler.helper.InstructionBuilder
import ai.tenum.lua.compiler.helper.RegisterAllocator
import ai.tenum.lua.compiler.helper.ScopeManager
import ai.tenum.lua.compiler.helper.UpvalueResolver
import ai.tenum.lua.parser.ast.Assignment
import ai.tenum.lua.parser.ast.BooleanLiteral
import ai.tenum.lua.parser.ast.Chunk
import ai.tenum.lua.parser.ast.ForInStatement
import ai.tenum.lua.parser.ast.ForStatement
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.FunctionDeclaration
import ai.tenum.lua.parser.ast.IfStatement
import ai.tenum.lua.parser.ast.LocalDeclaration
import ai.tenum.lua.parser.ast.LocalVariableInfo
import ai.tenum.lua.parser.ast.NumberLiteral
import ai.tenum.lua.parser.ast.RepeatStatement
import ai.tenum.lua.parser.ast.ReturnStatement
import ai.tenum.lua.parser.ast.Variable
import ai.tenum.lua.parser.ast.WhileStatement
import ai.tenum.lua.vm.OpCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionCompilerTest {
    private fun fakeCompileContext(): CompileContext =
        CompileContext(
            functionName = "testFun",
            constantPool = ConstantPool(),
            instructionBuilder = InstructionBuilder(),
            scopeManager = ScopeManager(),
            upvalueResolver = UpvalueResolver(null, null),
            registerAllocator = RegisterAllocator(),
            debugEnabled = false,
        )

    private fun opcodes(proto: ai.tenum.lua.compiler.model.Proto): List<OpCode> = proto.instructions.map { it.opcode }

    @Test
    fun testEmptyChunk() {
        val ctx = fakeCompileContext()
        val chunk = Chunk(emptyList())
        val proto = FunctionCompiler().compile(chunk, "empty", ctx)
        assertEquals(1, proto.instructions.size)
        assertEquals(OpCode.RETURN, proto.instructions[0].opcode)
    }

    @Test
    fun testSingleAssignment() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(42.0, 42L, 1)), 1),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "assign", ctx)
        val codes = opcodes(proto)
        // Integer 42 uses LOADI (immediate load) instead of LOADK
        assertTrue(codes.contains(OpCode.LOADI))
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testMultipleStatements() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(1.0, 1L, 1)), 1),
                    Assignment(listOf(Variable("y", 1)), listOf(NumberLiteral(2.0, 2L, 1)), 1),
                    ReturnStatement(listOf(Variable("x", 1)), 1),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "multi", ctx)
        val codes = opcodes(proto)
        // Integers 1 and 2 use LOADI (immediate load) instead of LOADK
        assertTrue(codes.contains(OpCode.LOADI))
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testLocalDeclaration() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    LocalDeclaration(
                        variables = listOf(LocalVariableInfo("z")),
                        expressions = listOf(NumberLiteral(5.0, 5L, 1)),
                        line = 1,
                    ),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "local", ctx)
        val codes = opcodes(proto)
        // Integer 5 uses LOADI (immediate load) instead of LOADK
        assertTrue(codes.contains(OpCode.LOADI))
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testIfStatement() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    IfStatement(
                        condition = BooleanLiteral(true, 1),
                        thenBlock = listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(1.0, 1L, 1)), 1)),
                        elseIfBlocks = emptyList(),
                        elseBlock = null,
                        line = 1,
                    ),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "if", ctx)
        val codes = opcodes(proto)
        // Integer 1 uses LOADI (immediate load) instead of LOADK
        assertTrue(codes.any { it == OpCode.LOADBOOL || it == OpCode.LOADI })
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testWhileStatement() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    WhileStatement(
                        BooleanLiteral(true, 1),
                        listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(2.0, 2L, 1)), 1)),
                        1,
                    ),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "while", ctx)
        val codes = opcodes(proto)
        // Integer 2 uses LOADI (immediate load) instead of LOADK
        assertTrue(codes.any { it == OpCode.LOADBOOL || it == OpCode.LOADI })
        assertTrue(codes.contains(OpCode.JMP))
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testRepeatStatement() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    RepeatStatement(
                        listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(3.0, 3.0, 1)), 1)),
                        BooleanLiteral(false, 1),
                        1,
                    ),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "repeat", ctx)
        val codes = opcodes(proto)
        assertTrue(codes.contains(OpCode.JMP))
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testForNumericStatement() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    ForStatement(
                        variable = "i",
                        start = NumberLiteral(1.0, 1.0, 1),
                        end = NumberLiteral(10.0, 10.0, 1),
                        step = NumberLiteral(1.0, 1.0, 1),
                        block = listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(4.0, 4.0, 1)), 1)),
                        line = 1,
                    ),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "fornum", ctx)
        val codes = opcodes(proto)
        assertTrue(codes.contains(OpCode.FORPREP))
        assertTrue(codes.contains(OpCode.FORLOOP))
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testForGenericStatement() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    ForInStatement(
                        variables = listOf("k", "v"),
                        expressions = listOf(FunctionCall(Variable("pairs", 1), listOf(Variable("t", 1)), 1)),
                        block = listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(5.0, 5.0, 1)), 1)),
                        line = 1,
                    ),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "forin", ctx)
        val codes = opcodes(proto)
        assertTrue(codes.contains(OpCode.CALL))
        assertTrue(codes.contains(OpCode.TFORCALL))
        assertTrue(codes.contains(OpCode.TFORLOOP))
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testFunctionDeclaration() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    FunctionDeclaration(
                        name = "foo",
                        parameters = emptyList(),
                        hasVararg = false,
                        body = emptyList(),
                        tablePath = emptyList(),
                        isMethod = false,
                        line = 1,
                        endLine = 1,
                    ),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "funcdecl", ctx)
        val codes = opcodes(proto)
        assertTrue(codes.contains(OpCode.CLOSURE))
        assertTrue(codes.contains(OpCode.SETGLOBAL))
        assertTrue(codes.contains(OpCode.RETURN))
    }

    @Test
    fun testReturnStatement() {
        val ctx = fakeCompileContext()
        val chunk =
            Chunk(
                listOf(
                    ReturnStatement(listOf(NumberLiteral(99.0, 99L, 1)), 1),
                ),
            )
        val proto = FunctionCompiler().compile(chunk, "return", ctx)
        val codes = opcodes(proto)
        // Integer 99 uses LOADI (immediate load) instead of LOADK
        assertTrue(codes.contains(OpCode.LOADI))
        assertTrue(codes.contains(OpCode.RETURN))
    }
}
