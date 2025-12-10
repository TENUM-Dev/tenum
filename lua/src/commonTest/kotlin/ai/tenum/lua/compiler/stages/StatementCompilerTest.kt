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
import ai.tenum.lua.parser.ast.BreakStatement
import ai.tenum.lua.parser.ast.DoStatement
import ai.tenum.lua.parser.ast.ForInStatement
import ai.tenum.lua.parser.ast.ForStatement
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.FunctionDeclaration
import ai.tenum.lua.parser.ast.GotoStatement
import ai.tenum.lua.parser.ast.IfStatement
import ai.tenum.lua.parser.ast.LabelStatement
import ai.tenum.lua.parser.ast.LocalDeclaration
import ai.tenum.lua.parser.ast.LocalVariableInfo
import ai.tenum.lua.parser.ast.NumberLiteral
import ai.tenum.lua.parser.ast.RepeatStatement
import ai.tenum.lua.parser.ast.ReturnStatement
import ai.tenum.lua.parser.ast.Variable
import ai.tenum.lua.parser.ast.WhileStatement
import ai.tenum.lua.vm.OpCode
import kotlin.test.Test
import kotlin.test.assertTrue

class StatementCompilerTest {
    internal fun fakeCompileContext(): CompileContext =
        CompileContext(
            functionName = "test",
            constantPool = ConstantPool(),
            instructionBuilder = InstructionBuilder(),
            scopeManager = ScopeManager(),
            upvalueResolver = UpvalueResolver(null, null),
            registerAllocator = RegisterAllocator(),
            debugEnabled = false,
        )

    @Test
    fun testCloseEmittedForGotoOverCloseVar() {
        // Simulate a block with a <close> variable and a goto that jumps over its scope
        // The test checks that a CLOSE instruction is emitted at the jump point
        val ctx = fakeCompileContext()
        val closeVar =
            LocalDeclaration(
                variables = listOf(LocalVariableInfo("obj", isClose = true)),
                expressions = listOf(NumberLiteral(42.0, 42L, 1)),
                line = 1,
            )
        val stmts =
            listOf(
                closeVar,
                GotoStatement(label = "after", line = 2),
                Assignment(
                    variables = listOf(Variable("shouldNotRun", 3)),
                    expressions = listOf(NumberLiteral(99.0, 99L, 3)),
                    line = 3,
                ),
                LabelStatement(name = "after", line = 4),
            )
        val block = DoStatement(stmts, line = 1)
        stmtCompiler.compileStatement(block, ctx)
        // Find CLOSE instructions and check their modes
        val closeInstrs = ctx.instructions.filter { it.opcode == OpCode.CLOSE }
        assertTrue(closeInstrs.isNotEmpty(), "CLOSE should be emitted when goto jumps over <close> var")

        // There should be at least two CLOSE instructions: one for declaration (mode 1), one for scope exit (mode 2)
        val modes = closeInstrs.map { it.b }
        assertTrue(modes.contains(1), "CLOSE with mode 1 (declaration) should be emitted")
        assertTrue(modes.contains(2), "CLOSE with mode 2 (scope exit) should be emitted after goto")
        // Optionally, check order: mode 1 should come before mode 2
        val firstMode1 = modes.indexOf(1)
        val firstMode2 = modes.indexOf(2)
        assertTrue(firstMode1 >= 0 && firstMode2 > firstMode1, "CLOSE mode 1 should come before mode 2")
    }

    private fun opcodes(ctx: CompileContext): List<OpCode> = ctx.instructions.map { it.opcode }

    private val callCompiler = CallCompiler()
    private val exprCompiler = ExpressionCompiler(callCompiler)
    private val stmtCompiler = StatementCompiler()

    @Test
    fun testAssignment_SameRegister() {
        // Local and temp register are the same: only LOADK, no MOVE
        val ctx = fakeCompileContext()
        ctx.scopeManager.declareLocal("x", 0, 0)
        val stmt = Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(42.0, 42L, 1)), 1)
        stmtCompiler.compileStatement(stmt, ctx)
        val codes = opcodes(ctx)
        // Integer 42 uses LOADI (immediate load) instead of LOADK
        assertTrue(codes.contains(OpCode.LOADI))
        // MOVE should not be emitted when registers match
        // (if implementation changes, this may need to be updated)
    }

    @Test
    fun testAssignment_DifferentRegister() {
        // Local is at register 1, temp will likely be 0: LOADI and MOVE
        val ctx = fakeCompileContext()
        ctx.scopeManager.declareLocal("y", 1, 0)
        val stmt = Assignment(listOf(Variable("y", 1)), listOf(NumberLiteral(99.0, 99L, 1)), 1)
        stmtCompiler.compileStatement(stmt, ctx)
        val codes = opcodes(ctx)
        // Integer 99 uses LOADI (immediate load) instead of LOADK
        assertTrue(codes.contains(OpCode.LOADI))
        assertTrue(codes.contains(OpCode.MOVE))
    }

    @Test
    fun testLocalDeclaration() {
        val ctx = fakeCompileContext()
        val stmt =
            LocalDeclaration(
                variables = listOf(LocalVariableInfo("y")),
                expressions = listOf(NumberLiteral(5.0, 5L, 1)),
                line = 1,
            )
        stmtCompiler.compileStatement(stmt, ctx)
        // Integer 5 uses LOADI (immediate load) instead of LOADK
        assertTrue(opcodes(ctx).contains(OpCode.LOADI))
    }

    @Test
    fun testIfStatement() {
        val ctx = fakeCompileContext()
        val stmt =
            IfStatement(
                condition = BooleanLiteral(true, 1),
                thenBlock = listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(1.0, 1L, 1)), 1)),
                elseIfBlocks = emptyList(),
                elseBlock = null,
                line = 1,
            )
        stmtCompiler.compileStatement(stmt, ctx)
        // Integer 1 uses LOADI (immediate load) instead of LOADK
        assertTrue(opcodes(ctx).any { it == OpCode.LOADBOOL || it == OpCode.LOADI })
    }

    @Test
    fun testWhileStatement() {
        val ctx = fakeCompileContext()
        val stmt =
            WhileStatement(
                BooleanLiteral(true, 1),
                listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(2.0, 2L, 1)), 1)),
                1,
            )
        stmtCompiler.compileStatement(stmt, ctx)
        // Integer 2 uses LOADI (immediate load) instead of LOADK
        assertTrue(opcodes(ctx).any { it == OpCode.LOADBOOL || it == OpCode.LOADI })
        assertTrue(opcodes(ctx).contains(OpCode.JMP))
    }

    @Test
    fun testRepeatStatement() {
        val ctx = fakeCompileContext()
        val stmt =
            RepeatStatement(
                listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(3.0, 3L, 1)), 1)),
                BooleanLiteral(false, 1),
                1,
            )
        stmtCompiler.compileStatement(stmt, ctx)
        assertTrue(opcodes(ctx).contains(OpCode.JMP))
    }

    @Test
    fun testForNumericStatement() {
        val ctx = fakeCompileContext()
        val stmt =
            ForStatement(
                variable = "i",
                start = NumberLiteral(1.0, 1L, 1),
                end = NumberLiteral(10.0, 10L, 1),
                step = NumberLiteral(1.0, 1L, 1),
                block = listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(4.0, 4L, 1)), 1)),
                line = 1,
            )
        stmtCompiler.compileStatement(stmt, ctx)
        assertTrue(opcodes(ctx).contains(OpCode.FORPREP))
        assertTrue(opcodes(ctx).contains(OpCode.FORLOOP))
    }

    @Test
    fun testForGenericStatement() {
        val ctx = fakeCompileContext()
        val stmt =
            ForInStatement(
                variables = listOf("k", "v"),
                expressions = listOf(FunctionCall(Variable("pairs", 1), listOf(Variable("t", 1)), 1)),
                block = listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(5.0, 5L, 1)), 1)),
                line = 1,
            )
        stmtCompiler.compileStatement(stmt, ctx)
        assertTrue(opcodes(ctx).contains(OpCode.CALL))
        assertTrue(opcodes(ctx).contains(OpCode.TFORCALL))
        assertTrue(opcodes(ctx).contains(OpCode.TFORLOOP))
    }

    @Test
    fun testFunctionDeclaration() {
        val ctx = fakeCompileContext()
        val stmt =
            FunctionDeclaration(
                name = "foo",
                parameters = emptyList(),
                hasVararg = false,
                body = emptyList(),
                tablePath = emptyList(),
                isMethod = false,
                line = 1,
                endLine = 1,
            )
        stmtCompiler.compileStatement(stmt, ctx)
        assertTrue(opcodes(ctx).contains(OpCode.CLOSURE))
        assertTrue(opcodes(ctx).contains(OpCode.SETGLOBAL))
    }

    @Test
    fun testReturnStatement() {
        val ctx = fakeCompileContext()
        val stmt = ReturnStatement(listOf(NumberLiteral(99.0, 99L, 1)), 1)
        stmtCompiler.compileStatement(stmt, ctx)
        // Integer 99 uses LOADI (immediate load) instead of LOADK
        assertTrue(opcodes(ctx).contains(OpCode.LOADI))
        assertTrue(opcodes(ctx).contains(OpCode.RETURN))
    }

    @Test
    fun testBreakStatement() {
        val ctx = fakeCompileContext()
        val stmt = BreakStatement(1)
        stmtCompiler.compileStatement(stmt, ctx)
        assertTrue(opcodes(ctx).contains(OpCode.JMP))
    }

    @Test
    fun testDoStatement() {
        val ctx = fakeCompileContext()
        val stmt = DoStatement(listOf(Assignment(listOf(Variable("x", 1)), listOf(NumberLiteral(7.0, 7L, 1)), 1)), 1)
        stmtCompiler.compileStatement(stmt, ctx)
        // Integer 7 uses LOADI (immediate load) instead of LOADK
        assertTrue(opcodes(ctx).contains(OpCode.LOADI))
    }
}
