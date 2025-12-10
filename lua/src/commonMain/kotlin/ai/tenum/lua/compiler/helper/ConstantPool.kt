package ai.tenum.lua.compiler.helper

import ai.tenum.lua.runtime.LuaValue

class ConstantPool {
    private val constants = mutableListOf<LuaValue<*>>()
    private val indices = mutableMapOf<LuaValue<*>, Int>()

    fun getIndex(value: LuaValue<*>): Int =
        indices.getOrPut(value) {
            constants.add(value)
            constants.lastIndex
        }

    fun build(): List<LuaValue<*>> = constants
}
