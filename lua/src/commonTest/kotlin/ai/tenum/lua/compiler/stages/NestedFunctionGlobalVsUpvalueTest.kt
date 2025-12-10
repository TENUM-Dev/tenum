package ai.tenum.lua.compiler.stages

import ai.tenum.lua.compiler.CompileContext
import ai.tenum.lua.compiler.helper.ConstantPool
import ai.tenum.lua.compiler.helper.InstructionBuilder
import ai.tenum.lua.compiler.helper.RegisterAllocator
import ai.tenum.lua.compiler.helper.ScopeManager
import ai.tenum.lua.compiler.helper.UpvalueResolver
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.parser.ast.ExpressionStatement
import ai.tenum.lua.parser.ast.FunctionCall
import ai.tenum.lua.parser.ast.LocalDeclaration
import ai.tenum.lua.parser.ast.LocalVariableInfo
import ai.tenum.lua.parser.ast.TableConstructor
import ai.tenum.lua.parser.ast.TableField
import ai.tenum.lua.parser.ast.Variable
import ai.tenum.lua.vm.OpCode
import kotlin.test.Test
import kotlin.test.assertTrue

class NestedFunctionGlobalVsUpvalueTest {
    private fun makeParentContext(): CompileContext {
        val cp = ConstantPool()
        val ib = InstructionBuilder()
        val sm = ScopeManager()
        val ra = RegisterAllocator()
        val up = UpvalueResolver(null, null) // top-level has no parent

        return CompileContext(
            functionName = "main",
            constantPool = cp,
            instructionBuilder = ib,
            scopeManager = sm,
            upvalueResolver = up,
            registerAllocator = ra,
            debugEnabled = false,
        )
    }

    @Test
    fun `setmetatable is compiled as global and lib as upvalue in nested create`() {
        val parentCtx = makeParentContext()

        // Simulate: local lib = {}
        val libReg = parentCtx.registerAllocator.allocateLocal()
        parentCtx.scopeManager.declareLocal(
            name = "lib",
            register = libReg,
            startPc = 0,
            isConst = false,
            isClose = false,
        )

        // Body of: function lib.create(value)
        //   local obj = { value = value }
        //   setmetatable(obj, { __index = lib })
        // end

        val objLocalDecl =
            LocalDeclaration(
                variables = listOf(LocalVariableInfo("obj", isConst = false, isClose = false)),
                expressions =
                    listOf(
                        TableConstructor(
                            fields =
                                listOf(
                                    TableField.NamedField(
                                        name = "value",
                                        value = Variable("value", line = 1),
                                    ),
                                ),
                            line = 1,
                        ),
                    ),
                line = 1,
            )

        val setmetatableCall =
            ExpressionStatement(
                FunctionCall(
                    function = Variable("setmetatable", line = 1),
                    arguments =
                        listOf(
                            Variable("obj", line = 1),
                            TableConstructor(
                                fields =
                                    listOf(
                                        TableField.NamedField(
                                            name = "__index",
                                            value = Variable("lib", line = 1),
                                        ),
                                    ),
                                line = 1,
                            ),
                        ),
                    line = 1,
                ),
                line = 1,
            )

        // Compile the nested function "create"
        val createProto: Proto =
            parentCtx.compileFunction(
                params = listOf("value"),
                hasVararg = false,
                body = listOf(objLocalDecl, setmetatableCall),
                name = "create",
            )

        val instructions = createProto.instructions

        // Lua 5.2+ semantics: globals accessed via _ENV upvalue + GETTABLE
        // We expect GETUPVAL for both _ENV and 'lib'
        assertTrue(
            instructions.any { it.opcode == OpCode.GETUPVAL },
            "Expected GETUPVAL for captured 'lib' and '_ENV' in create()",
        )
        assertTrue(
            instructions.any { it.opcode == OpCode.GETTABLE },
            "Expected GETTABLE for accessing 'setmetatable' via _ENV",
        )

        // Check that we have an upvalue named "lib"
        val libUpvalInfo = createProto.upvalueInfo.firstOrNull { it.name == "lib" }
        assertTrue(
            libUpvalInfo != null,
            "GETUPVAL in create() should capture upvalue 'lib'",
        )
    }
}
