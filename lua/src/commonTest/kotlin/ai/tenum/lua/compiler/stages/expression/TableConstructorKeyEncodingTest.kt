package ai.tenum.lua.compiler.stages

// CPD-OFF: test file with intentional test fixture duplications for readability

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.compiler.helper.ConstantPool
import ai.tenum.lua.compiler.helper.InstructionBuilder
import ai.tenum.lua.compiler.helper.RegisterAllocator
import ai.tenum.lua.compiler.helper.ScopeManager
import ai.tenum.lua.compiler.helper.UpvalueResolver
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.parser.ast.NumberLiteral
import ai.tenum.lua.parser.ast.StringLiteral
import ai.tenum.lua.parser.ast.TableConstructor
import ai.tenum.lua.parser.ast.TableField
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.vm.OpCode
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class TableConstructorKeyEncodingTest {
    private fun fakeCompileContext(): CompileContext =
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
    fun testNamedFieldKeyIsConstantOperand() {
        val ctx = fakeCompileContext()
        val exprCompiler = ExpressionCompiler(CallCompiler())
        val table =
            TableConstructor(
                fields = listOf(TableField.NamedField("foo", NumberLiteral(1.0, 1.0, 1))),
                line = 1,
            )
        exprCompiler.compileExpression(table, 0, ctx)
        val settable = ctx.instructions.firstOrNull { it.opcode == OpCode.SETTABLE }
        assertTrue(settable != null, "SETTABLE should be emitted")
        // The key operand should have the 256 bit set (constant key)
        val keyOperand = settable!!.b
        assertTrue(keyOperand and 256 != 0, "SETTABLE key operand should have 256 bit set for constant key")
    }

    @Test
    fun testRecordFieldKeyIsRegisterOperand() {
        val ctx = fakeCompileContext()
        val exprCompiler = ExpressionCompiler(CallCompiler())
        val table =
            TableConstructor(
                fields = listOf(TableField.RecordField(StringLiteral("bar", 1), NumberLiteral(2.0, 2.0, 1))),
                line = 1,
            )
        exprCompiler.compileExpression(table, 0, ctx)
        val settable = ctx.instructions.firstOrNull { it.opcode == OpCode.SETTABLE }
        assertTrue(settable != null, "SETTABLE should be emitted")
        // The key operand should NOT have the 256 bit set (register key)
        val keyOperand = settable!!.b
        assertTrue(keyOperand and 256 == 0, "SETTABLE key operand should NOT have 256 bit set for register key")
    }

    @Test
    fun namedTableFieldUsesConstantKey() {
        val src =
            """
            local function create(value)
                local obj = { value = value }
                return obj
            end
            return create
            """.trimIndent()

        val lexer = Lexer(src)
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        val chunk = parser.parse()

        val compiler = Compiler("=(test)", debugEnabled = false)
        val proto = compiler.compile(chunk)

        // Find the nested function proto for 'create'
        val createProto =
            proto.constants
                .filterIsInstance<ai.tenum.lua.runtime.LuaCompiledFunction>()
                .map { it.proto }
                .firstOrNull { it.name == "create" }
                ?: fail("Expected a nested proto named 'create'")

        val valueConstIndex =
            createProto.constants.indexOfFirst {
                it is LuaString && it.value == "value"
            }
        assertTrue(valueConstIndex >= 0, "Constant pool must contain LuaString(\"value\")")

        fun isConstOperand(
            op: Int,
            expectedConstIndex: Int,
        ): Boolean = (op and 256) != 0 && (op and 255) == expectedConstIndex

        val setTable =
            createProto.instructions.firstOrNull { it.opcode == OpCode.SETTABLE }
                ?: fail("Expected a SETTABLE instruction for obj.value")

        assertTrue(
            isConstOperand(setTable.b, valueConstIndex),
            "SETTABLE key for named field 'value' must be a constant via RK encoding",
        )
    }
}
