package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaValue

data class Chunk(
    val sourceName: String,
    val sourcePath: String,
    val instructions: List<OpCode>,
    val constants: List<LuaValue<*>>,
)
