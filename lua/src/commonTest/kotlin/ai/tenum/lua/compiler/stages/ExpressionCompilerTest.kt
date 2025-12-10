package ai.tenum.lua.compiler.stages

import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.vm.OpCode
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ExpressionCompilerTest {
    private fun compile(source: String): Proto {
        val lexer = Lexer(source)
        val parser = Parser(lexer.scanTokens())
        val chunk = parser.parse()
        val compiler = Compiler()
        return compiler.compile(chunk)
    }

    private fun opcodes(proto: Proto): List<OpCode> = proto.instructions.map { it.opcode }

    private fun instruction(
        proto: Proto,
        opcode: OpCode,
    ): Instruction = proto.instructions.first { it.opcode == opcode }

    @Test
    fun testNilLiteral() {
        val proto = compile("return nil")
        opcodes(proto) shouldContain OpCode.LOADNIL
    }

    @Test
    fun testBooleanLiteral() {
        val proto = compile("return true")
        opcodes(proto) shouldContain OpCode.LOADBOOL
        instruction(proto, OpCode.LOADBOOL).b shouldBe 1
    }

    @Test
    fun testNumberLiteral() {
        val proto = compile("return 123")
        // Integer 123 uses LOADI (immediate load) instead of LOADK
        opcodes(proto) shouldContain OpCode.LOADI
    }

    @Test
    fun testStringLiteral() {
        val proto = compile("return 'hello'")
        opcodes(proto) shouldContain OpCode.LOADK
    }

    @Test
    fun testVariable() {
        val proto = compile("local a = 1; return a")
        opcodes(proto) shouldContain OpCode.MOVE
    }

    @Test
    fun testGlobalVariable() {
        val proto = compile("return a")
        // Lua 5.2+ semantics: globals are accessed via _ENV upvalue
        opcodes(proto) shouldContain OpCode.GETUPVAL
        opcodes(proto) shouldContain OpCode.GETTABLE
    }

    @Test
    fun testBinaryOp() {
        val proto = compile("return 1 + 2")
        opcodes(proto) shouldContain OpCode.ADD
    }

    @Test
    fun testUnaryOp() {
        val proto = compile("return -1")
        opcodes(proto) shouldContain OpCode.UNM
    }

    @Test
    fun testTableConstructor() {
        val proto = compile("return {1, 2}")
        opcodes(proto) shouldContain OpCode.NEWTABLE
        // List fields now use SETLIST instead of individual SETTABLE
        opcodes(proto) shouldContain OpCode.SETLIST
    }

    @Test
    fun testTableConstructorWithNamedFields() {
        val proto = compile("return {a=1, b=2}")
        opcodes(proto) shouldContain OpCode.NEWTABLE
        opcodes(proto) shouldContain OpCode.SETTABLE
    }

    @Test
    fun testTableConstructorWithRecordFields() {
        val proto = compile("local k = 'a'; return {[k]=1}")
        opcodes(proto) shouldContain OpCode.NEWTABLE
        opcodes(proto) shouldContain OpCode.SETTABLE
    }

    @Test
    fun testTableConstructorLocalRegisterNotReused() {
        val proto =
            compile(
                """
            local i = 1
            local t1 = { i }
            local t2 = { i }
            return t1, t2
        """,
            )

        // With SETLIST implementation, list fields use MOVE + SETLIST instead of SETTABLE
        val moves = proto.instructions.filter { it.opcode == OpCode.MOVE }
        val setlists = proto.instructions.filter { it.opcode == OpCode.SETLIST }

        // Should have 2 SETLIST instructions (one per table)
        setlists shouldHaveSize 2

        // Should have 2 MOVE instructions moving from R0 (local variable 'i')
        val movesFromR0 = moves.filter { it.b == 0 }
        movesFromR0 shouldHaveSize 2
    }

    @Test
    fun testIndexAccess() {
        val proto = compile("local t = {}; return t[1]")
        opcodes(proto) shouldContain OpCode.GETTABLE
    }

    @Test
    fun testFieldAccess() {
        val proto = compile("local t = {}; return t.a")
        opcodes(proto) shouldContain OpCode.GETTABLE
    }

    @Test
    fun testParenExpression() {
        val proto = compile("return (1 + 2)")
        opcodes(proto) shouldContain OpCode.ADD
    }

    @Test
    fun testFunctionCall() {
        val proto = compile("local function f() end; return f()")
        opcodes(proto) shouldContain OpCode.TAILCALL
    }

    @Test
    fun testMethodCall() {
        val proto = compile("local t = {}; function t:f() end; return t:f()")
        opcodes(proto) shouldContain OpCode.SELF
        // Method calls in return position are tail-call optimized (confirmed in Lua 5.4)
        opcodes(proto) shouldContain OpCode.TAILCALL
    }

    @Test
    fun testVarargExpression() {
        val proto = compile("local function f(...) return ... end")
        opcodes(proto) shouldNotContain OpCode.VARARG

        val nestedProto = (proto.constants.first { it is LuaCompiledFunction } as LuaCompiledFunction).proto
        val nestedOpcodes = nestedProto.instructions.map { it.opcode }
        nestedOpcodes shouldContain OpCode.VARARG
    }

    @Test
    fun testFunctionExpression() {
        val proto = compile("return function() end")
        opcodes(proto) shouldContain OpCode.CLOSURE
    }
}
