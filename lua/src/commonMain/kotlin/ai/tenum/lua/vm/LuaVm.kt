package ai.tenum.lua.vm

import ai.tenum.lua.runtime.LuaValue

interface LuaVm {
    fun execute(
        chunk: String,
        source: String = "=(load)",
    ): LuaValue<*>
}
