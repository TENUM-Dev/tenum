package ai.tenum.lua.runtime

import ai.tenum.lua.compiler.model.Proto

/**
 * Represents a Lua function value
 * Can be either a Lua function (compiled bytecode) or a Kotlin function
 */
sealed class LuaFunction : LuaValue<(List<LuaValue<*>>) -> List<LuaValue<*>>> {
    override var metatableStore: LuaValue<*>? = null

    override fun type(): LuaType = LuaType.FUNCTION

    /**
     * Call this function with the given arguments
     * @param args The arguments to pass to the function
     * @return The return values from the function
     */
    fun call(args: List<LuaValue<*>>): List<LuaValue<*>> = value(args)
}

/**
 * A Lua function implemented in Lua (bytecode)
 * @param proto The function prototype containing bytecode and metadata
 * @param upvalues The captured upvalues from outer scopes
 */
class LuaCompiledFunction(
    val proto: Proto,
    val upvalues: MutableList<Upvalue> = mutableListOf(),
) : LuaFunction() {
    // The value is set by the VM when creating the function
    override var value: (List<LuaValue<*>>) -> List<LuaValue<*>> = { _ ->
        error("Function executor not set - this should be set by the VM")
    }

    override fun toString(): String = "function: ${proto.name}"
}

/**
 * A Lua function implemented in Kotlin (native function)
 * @param name The name of the function (for debugging)
 * @param value The Kotlin implementation
 * @param upvalues Optional upvalues for native closures (e.g., string.gmatch iterator)
 */
class LuaNativeFunction(
    val name: String = "native",
    override val value: (List<LuaValue<*>>) -> List<LuaValue<*>>,
    val upvalues: List<Upvalue> = emptyList(),
) : LuaFunction() {
    /**
     * Convenience constructor for lambdas without upvalues
     */
    constructor(
        value: (List<LuaValue<*>>) -> List<LuaValue<*>>,
    ) : this("native", value, emptyList())

    /**
     * Convenience constructor for named functions without upvalues
     */
    constructor(
        name: String,
        value: (List<LuaValue<*>>) -> List<LuaValue<*>>,
    ) : this(name, value, emptyList())

    fun function(args: List<LuaValue<*>>): List<LuaValue<*>> = value(args)

    override fun toString(): String = "function: $name"
}
