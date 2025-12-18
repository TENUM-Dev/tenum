package ai.tenum.lua.vm

import ai.tenum.lua.compiler.model.Proto

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
