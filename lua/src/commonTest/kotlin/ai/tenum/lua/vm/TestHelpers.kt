package ai.tenum.lua.vm

import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.ExecutionFrame

// Creates a dummy Proto for testing purposes
fun createDummyProto(name: String = "test") =
    Proto(
        name = name,
        source = "@$name.lua",
        lineDefined = 0,
        lastLineDefined = 0,
        parameters = emptyList(),
        hasVararg = false,
        maxStackSize = 10,
        instructions = emptyList(),
        constants = emptyList(),
        upvalueInfo = emptyList(),
        localVars = emptyList(),
        lineEvents = emptyList(),
    )

// Creates a dummy ExecutionFrame for testing purposes
fun createDummyFrame(
    proto: Proto,
    pc: Int = 0,
) = ExecutionFrame(
    proto = proto,
    initialArgs = emptyList(),
    upvalues = emptyList(),
    initialPc = pc,
)

// Creates a test close callback that records closed register numbers
fun createTestCloseCallback(closedVars: MutableList<Int>): (Int, LuaValue<*>, LuaValue<*>) -> Unit =
    { reg, _, _ ->
        closedVars.add(reg)
    }

// Creates a test debug sink that captures messages
fun createTestSink(messages: MutableList<String>): VmDebugSink =
    object : VmDebugSink {
        override fun debug(message: String) {
            messages.add(message)
        }

        override fun debug(message: () -> String) {
            messages.add(message())
        }
    }
