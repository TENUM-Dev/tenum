package ai.tenum.lua.repl

import ai.tenum.lua.runtime.LuaValue

interface Repl {
    fun eval(input: String): LuaValue<*>
}
