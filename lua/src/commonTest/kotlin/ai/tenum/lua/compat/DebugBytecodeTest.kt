package ai.tenum.lua.compat

// CPD-OFF: test file with intentional compilation test setup duplications

import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.vm.LuaVmImpl
import kotlin.test.Test
import kotlin.test.assertTrue

class DebugBytecodeTest {
    @Test
    fun testSetmetatableAvailableInTest() {
        val vm = LuaVmImpl()
        val code =
            """
            return type(setmetatable)
            """.trimIndent()

        val result = vm.execute(code)
        println("setmetatable type: $result")
        assertTrue(result is ai.tenum.lua.runtime.LuaString && result.value == "function", "setmetatable should be a function")
    }

    @Test
    fun testSimpleTableModification() {
        // This should work: modify local table
        val code =
            """
            local lib = {}
            function lib:test() return 42 end
            return lib.test
            """.trimIndent()

        val lexer = Lexer(code)
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        val ast = parser.parse()
        val compiler = Compiler()
        val proto = compiler.compile(ast)

        println("\n=== Simple Table Modification ===")
        println("Instructions:")
        proto.instructions.forEachIndexed { index, instr ->
            println("  $index: $instr")
        }
        println("Constants:")
        proto.constants.forEachIndexed { index, const ->
            println("  $index: $const")
        }

        val vm = LuaVmImpl()
        vm.debugEnabled = true
        val result = vm.execute(code)

        println("\nResult: $result")
        println("Result type: ${result::class.simpleName}")

        assertTrue(result !is LuaNil, "lib.test should not be nil")
    }

    @Test
    fun testTableModificationAfterNestedFunction() {
        // This is the failing case
        val code =
            """
            local lib = {}
            function lib.create(value)
                local obj = {value = value}
                setmetatable(obj, {__index = lib})
                return obj
            end
            local x = lib.create(5)
            function lib:double() return 42 end
            return lib.double
            """.trimIndent()

        val vm = LuaVmImpl()
        vm.debugEnabled = true
        val result = vm.execute(code)

        println("\nResult: $result")
        println("Result type: ${result::class.simpleName}")

        assertTrue(result !is LuaNil, "lib.double should not be nil after lib.create was called")
    }
}
