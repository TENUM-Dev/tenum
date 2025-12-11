package ai.tenum.lua.compiler.stages

// CPD-OFF: test file with intentional test fixture duplications

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.MethodCall
import ai.tenum.lua.parser.ast.NumberLiteral
import ai.tenum.lua.parser.ast.VarargExpression
import ai.tenum.lua.parser.ast.Variable
import ai.tenum.lua.vm.OpCode
import kotlin.test.Test
import kotlin.test.assertTrue

class CallCompilerTest {
    private fun fakeCompileContext(): CompileContext =
        CompileContext(
            functionName = "test",
            constantPool =
                ai.tenum.lua.compiler.helper
                    .ConstantPool(),
            instructionBuilder =
                ai.tenum.lua.compiler.helper
                    .InstructionBuilder(),
            scopeManager =
                ai.tenum.lua.compiler.helper
                    .ScopeManager(),
            upvalueResolver =
                ai.tenum.lua.compiler.helper
                    .UpvalueResolver(null, null),
            registerAllocator =
                ai.tenum.lua.compiler.helper
                    .RegisterAllocator(),
            debugEnabled = false,
        )

    @Test
    fun testSimpleFunctionCall() {
        val ctx = fakeCompileContext()
        val callCompiler = CallCompiler()
        val func = Variable("foo", 1)
        val call = FunctionCall(func, listOf(NumberLiteral(42.0, 42.0, 1)), 1)
        callCompiler.compileFunctionCall(call, 0, ctx, { _, _, _ -> })
        val opcodes = ctx.instructions.map { it.opcode }
        assertTrue(opcodes.contains(OpCode.CALL), "Should emit CALL opcode for simple call")
    }

    @Test
    fun testFunctionCallWithVararg() {
        val ctx = fakeCompileContext()
        val callCompiler = CallCompiler()
        val func = Variable("foo", 1)
        val call = FunctionCall(func, listOf(NumberLiteral(1.0, 1.0, 1), VarargExpression(1)), 1)
        callCompiler.compileFunctionCall(call, 0, ctx, { _, _, _ -> })
        val opcodes = ctx.instructions.map { it.opcode }
        assertTrue(opcodes.contains(OpCode.VARARG), "Should emit VARARG for vararg call")
        assertTrue(opcodes.contains(OpCode.CALL), "Should emit CALL for vararg call")
    }

    @Test
    fun testFunctionCallWithNestedCall() {
        val ctx = fakeCompileContext()
        val callCompiler = CallCompiler()
        val innerFunc = Variable("bar", 1)
        val innerCall = FunctionCall(innerFunc, listOf(NumberLiteral(2.0, 2.0, 1)), 1)
        val outerFunc = Variable("foo", 1)
        val call = FunctionCall(outerFunc, listOf(NumberLiteral(1.0, 1.0, 1), innerCall), 1)
        callCompiler.compileFunctionCall(call, 0, ctx, { _, _, _ -> })
        val opcodes = ctx.instructions.map { it.opcode }
        assertTrue(opcodes.count { it == OpCode.CALL } >= 2, "Should emit two CALLs for nested call")
    }

    @Test
    fun testMethodCall() {
        val ctx = fakeCompileContext()
        val callCompiler = CallCompiler()
        val receiver = Variable("obj", 1)
        val methodCall = MethodCall(receiver, "doit", listOf(NumberLiteral(5.0, 5.0, 1)), 1)
        callCompiler.compileMethodCall(methodCall, 0, ctx, { _, _, _ -> })
        val opcodes = ctx.instructions.map { it.opcode }
        assertTrue(opcodes.contains(OpCode.SELF), "Should emit SELF for method call")
        assertTrue(opcodes.contains(OpCode.CALL), "Should emit CALL for method call")
    }

    @Test
    fun testMultiReturnCall() {
        val ctx = fakeCompileContext()
        val callCompiler = CallCompiler()
        val func = Variable("foo", 1)
        val call = FunctionCall(func, listOf(NumberLiteral(1.0, 1.0, 1), VarargExpression(1)), 1)
        callCompiler.compileFunctionCallForMultiReturn(call, 0, ctx, { _, _, _ -> })
        val opcodes = ctx.instructions.map { it.opcode }
        assertTrue(opcodes.contains(OpCode.VARARG), "Should emit VARARG for multi-return call")
        assertTrue(opcodes.contains(OpCode.CALL), "Should emit CALL for multi-return call")
    }
}
