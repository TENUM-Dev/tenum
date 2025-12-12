@file:Suppress("NOTHING_TO_INLINE")

package ai.tenum.lua.vm.execution.opcodes

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.ExecutionEnvironment

/**
 * Table operation opcodes: GETTABLE, SETTABLE, NEWTABLE, SELF.
 */
object TableOpcodes {
    /**
     * GETTABLE: Read from table.
     * R[A] := R[B][C]
     * C can be constant (if bit 8 set) or register
     */
    fun executeGetTable(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val table = env.registers[instr.b]
        val key =
            if ((instr.c and 256) != 0) {
                env.constants[instr.c and 255]
            } else {
                env.registers[instr.c]
            }

        val result = getTableValue(table, key, env, instr.b, pc)
        env.setRegister(instr.a, result)

        // Store the name hint for this specific register
        // This enables correct error messages when registers are heavily reused (RK overflow)
        if (key is LuaString) {
            if (table is LuaTable) {
                // Determine if this is a global access (table is _ENV/globals)
                // Check both:
                // 1. Runtime check: is the table the actual globals table?
                // 2. Compile-time check: does the source register hold a local _ENV?
                val isEnvAccess = isGlobalsTable(table, env) || isEnvRegister(instr.b, env, pc)
                val hint =
                    if (isEnvAccess) {
                        "global '${key.value}'"
                    } else {
                        "field '${key.value}'"
                    }
                env.gettableResultHints[instr.a] = hint
            } else {
                env.gettableResultHints.remove(instr.a)
            }
        } else {
            env.gettableResultHints.remove(instr.a)
        }

        env.debug("  R[${instr.a}] = R[${instr.b}][$key] (${env.registers[instr.a]})")
        env.trace(instr.a, env.registers[instr.a], "GETTABLE")
    }

    /**
     * Resolve __index metamethod with error handling.
     * Shared helper to eliminate duplication between getTableValue and SELF.
     *
     * @param value The value being indexed (table or other type)
     * @param key The key to look up
     * @param env Execution environment
     * @param registerIndex Register containing the value (for error hints)
     * @param pc Program counter (for error hints)
     * @param allowRecursion If true, recursively follow __index chains; if false, stop at first non-function __index
     * @return The resolved value or throws an error
     */
    fun resolveIndexMetamethod(
        value: LuaValue<*>,
        key: LuaValue<*>,
        env: ExecutionEnvironment,
        registerIndex: Int,
        pc: Int,
        allowRecursion: Boolean,
    ): LuaValue<*> {
        val metaMethod = env.getMetamethod(value, "__index")
        return when {
            metaMethod is LuaFunction -> {
                env.setMetamethodCallContext("__index")
                val result = env.callFunction(metaMethod, listOf(value, key))
                result.firstOrNull() ?: LuaNil
            }
            metaMethod is LuaTable && !allowRecursion -> {
                // Non-recursive: just look up in the __index table
                metaMethod[key]
            }
            metaMethod != null && metaMethod !is LuaNil && allowRecursion -> {
                // Recursive: follow the __index chain
                getTableValue(metaMethod, key, env, registerIndex, pc)
            }
            else -> {
                // No metamethod - this is an error for non-tables
                if (value !is LuaTable) {
                    val nameHint = env.getRegisterNameHint(registerIndex, pc)
                    val typeName = LoopOpcodes.getTypeName(value, env)
                    val errorMsg =
                        if (nameHint != null) {
                            "attempt to index a $typeName value ($nameHint)"
                        } else {
                            "attempt to index a $typeName value"
                        }
                    env.luaError(errorMsg)
                } else {
                    LuaNil
                }
            }
        }
    }

    /**
     * Recursively resolve table indexing with __index metamethod support.
     * This is extracted to handle the recursive nature of __index when it's not a function.
     */
    fun getTableValue(
        table: LuaValue<*>,
        key: LuaValue<*>,
        env: ExecutionEnvironment,
        registerIndex: Int,
        pc: Int,
    ): LuaValue<*> =
        when (table) {
            is LuaTable -> {
                val rawValue = table[key]
                // If value is nil and there's an __index metamethod, use it
                if (rawValue is LuaNil) {
                    resolveIndexMetamethod(table, key, env, registerIndex, pc, allowRecursion = true)
                } else {
                    rawValue
                }
            }
            is LuaNil -> {
                // Use name hint for the table being indexed
                val nameHint = env.getRegisterNameHint(registerIndex, pc)
                val errorMsg =
                    if (nameHint != null) {
                        "attempt to index a nil value ($nameHint)"
                    } else {
                        "attempt to index a nil value"
                    }
                env.luaError(errorMsg)
            }
            else -> {
                // Non-table, non-nil value - check for __index metamethod
                resolveIndexMetamethod(table, key, env, registerIndex, pc, allowRecursion = true)
            }
        }

    /**
     * SETTABLE: Write to table.
     * R[A][B] := C
     * B and C can be constants (if bit 8 set) or registers
     */
    inline fun executeSetTable(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val table = env.registers[instr.a]
        val key = getRKValue(instr.b, env)
        val value = getRKValue(instr.c, env)

        if (table is LuaTable) {
            // Check if key exists in table
            val existingValue = table.get(key)
            if (existingValue is LuaNil) {
                // Key doesn't exist, check for __newindex metamethod
                val metaMethod = env.getMetamethod(table, "__newindex")
                when (metaMethod) {
                    is LuaTable -> metaMethod.set(key, value)
                    is LuaFunction -> {
                        env.setMetamethodCallContext("__newindex")
                        env.callFunction(metaMethod, listOf(table, key, value))
                    }
                    else -> table.set(key, value)
                }
            } else {
                // Key exists, set directly
                table.set(key, value)
            }
        } else if (table is LuaNil) {
            val nameHint = env.getRegisterNameHint(instr.a, pc)
            val errorMsg =
                if (nameHint != null) {
                    "attempt to index a nil value ($nameHint)"
                } else {
                    "attempt to index a nil value"
                }
            env.luaError(errorMsg)
        } else {
            val nameHint = env.getRegisterNameHint(instr.a, pc)
            val errorMsg =
                if (nameHint != null) {
                    "attempt to index a ${table.type().name.lowercase()} value ($nameHint)"
                } else {
                    "attempt to index a ${table.type().name.lowercase()} value"
                }
            env.luaError(errorMsg)
        }
        env.debug("  R[${instr.a}][$key] = $value")
    }

    /**
     * NEWTABLE: Create new table.
     * R[A] := {} (new empty table)
     */
    inline fun executeNewTable(
        instr: Instruction,
        env: ExecutionEnvironment,
    ) {
        env.setRegister(instr.a, LuaTable())
        env.debug("  R[${instr.a}] = {} (new table)")
        env.trace(instr.a, env.registers[instr.a], "NEWTABLE")
    }

    /**
     * SELF: Prepare for method call.
     * R[A+1] := R[B]
     * R[A] := R[B][C]
     *
     * This is used for method calls like obj:method()
     * It copies the table to A+1 (self parameter) and fetches the method to A
     */
    inline fun executeSelf(
        instr: Instruction,
        env: ExecutionEnvironment,
        pc: Int,
    ) {
        val table = env.registers[instr.b]
        val key = getRKValue(instr.c, env)

        env.registers[instr.a + 1] = table

        // For SELF, we need to handle __index but NOT recursively index non-tables
        // This differs from GETTABLE which recursively follows __index
        val method =
            when (table) {
                is LuaTable -> {
                    val rawValue = table[key]
                    if (rawValue is LuaNil) {
                        resolveIndexMetamethod(table, key, env, instr.b, pc, allowRecursion = false)
                    } else {
                        rawValue
                    }
                }
                else -> {
                    // Non-table: check if it has a metatable with __index
                    val methodResult = resolveIndexMetamethod(table, key, env, instr.b, pc, allowRecursion = false)
                    // If method is nil, store hint about the object being indexed
                    // so CALL can report the correct field name
                    if (methodResult is LuaNil) {
                        env.methodCallErrorHint = env.getRegisterNameHint(instr.b, pc)
                    }
                    methodResult
                }
            }

        env.setRegister(instr.a, method)

        env.debug("  R[${instr.a}], R[${instr.a + 1}] = R[${instr.b}][$key], R[${instr.b}]")
        env.trace(instr.a, env.registers[instr.a], "SELF")
        env.trace(instr.a + 1, env.registers[instr.a + 1], "SELF")

        // Mark that the next CALL will be a method call
        env.isMethodCall = true
    }

    /**
     * Check if a register holds a local _ENV variable.
     * Used to detect `local _ENV = {...}` patterns where access should still report "global".
     */
    private fun isEnvRegister(
        registerIndex: Int,
        env: ExecutionEnvironment,
        currentPc: Int,
    ): Boolean {
        val proto = env.frame.proto

        // Check if this register holds a local variable named "_ENV"
        for (localVar in proto.localVars) {
            if (localVar.name == "_ENV" &&
                localVar.register == registerIndex &&
                currentPc >= localVar.startPc &&
                currentPc < localVar.endPc
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Check if a table is the globals table (_ENV).
     * Used at runtime to distinguish "global 'x'" from "field 'x'".
     */
    private fun isGlobalsTable(
        table: LuaTable,
        env: ExecutionEnvironment,
    ): Boolean {
        // In Lua 5.2+, _ENV is the first upvalue of the main chunk
        // Check if this table IS the _ENV table (not just a value IN globals)
        val envTable = env.currentUpvalues.getOrNull(0)?.get()
        return envTable === table
    }
}
