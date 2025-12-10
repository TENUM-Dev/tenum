package ai.tenum.lua.vm.execution

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.OpCode

/**
 * Infers function names and calling context for debug.getinfo.
 *
 * In Lua, function names are determined by analyzing how the function
 * was loaded before being called. For example:
 * - `g.x()` → GETTABLE loads x from g, so name='x', namewhat='field'
 * - `local f = ...; f()` → MOVE from local, so name='f', namewhat='local'
 * - `foo()` → GETGLOBAL loads global, so name='foo', namewhat='global'
 */
object FunctionNameInference {
    /**
     * Extract a string key from an RK operand (either constant or register).
     * Returns the string value if it's a constant string, null otherwise.
     */
    private fun getStringFromRK(
        rkValue: Int,
        constants: List<LuaValue<*>>,
    ): String? {
        if (rkValue >= 256) {
            // Constant index (RK encoding)
            val key = constants.getOrNull(rkValue - 256)
            if (key is LuaString) {
                return key.value
            }
        }
        // Register or not a string constant
        return null
    }

    /**
     * Infer function name by analyzing the instructions before a CALL.
     *
     * @param instructions The bytecode of the current function
     * @param callPc The PC of the CALL instruction
     * @param funcRegister The register containing the function being called
     * @param constants The constant pool for this function
     * @param localVars The local variable information from Proto
     * @return InferredFunctionName with name and source context
     */
    fun inferFunctionName(
        instructions: List<Instruction>,
        callPc: Int,
        funcRegister: Int,
        constants: List<LuaValue<*>>,
        localVars: List<ai.tenum.lua.compiler.model.LocalVarInfo> = emptyList(),
    ): InferredFunctionName {
        // Look back at the previous instructions to see how the function was loaded
        // We need to find the instruction that loaded into funcRegister, which may not be
        // the immediately previous instruction (e.g., in "f(g).x = 42", the previous
        // instruction loads the argument g, not the function f)
        if (callPc <= 0) return InferredFunctionName.UNKNOWN

        // Search backwards for the instruction that loads into funcRegister
        for (lookback in 1..minOf(5, callPc)) {
            val prevInstr = instructions[callPc - lookback]

            // Skip instructions that don't write to funcRegister
            if (prevInstr.a != funcRegister) continue

            // GETTABLE: field or method access (e.g., t.field or t:method)
            if (prevInstr.opcode == OpCode.GETTABLE) {
                val name = getStringFromRK(prevInstr.c, constants)
                if (name != null) {
                    // Try to get the table name by looking back further
                    val tableReg = prevInstr.b
                    val tableName = findTableName(instructions, callPc - lookback, tableReg, constants, localVars)
                    val fullName = if (tableName != null) "$tableName.$name" else name
                    return InferredFunctionName(fullName, FunctionNameSource.Field)
                }
            }

            // SELF: method call (e.g., t:method())
            // SELF loads both the table and method, setting up for a method call
            if (prevInstr.opcode == OpCode.SELF) {
                val name = getStringFromRK(prevInstr.c, constants)
                if (name != null) {
                    return InferredFunctionName(name, FunctionNameSource.Method)
                }
            }

            // GETGLOBAL: global function call
            if (prevInstr.opcode == OpCode.GETGLOBAL) {
                val name = getStringFromRK(prevInstr.b, constants)
                if (name != null) {
                    return InferredFunctionName(name, FunctionNameSource.Global)
                }
            }

            // MOVE: could be a local variable or parameter
            // Look up the source register in local variable info
            if (prevInstr.opcode == OpCode.MOVE) {
                val sourceReg = prevInstr.b
                // Check if sourceReg matches any local variable
                for (localVar in localVars) {
                    if (localVar.isAliveAt(callPc) && localVar.register == sourceReg) {
                        return InferredFunctionName(localVar.name, FunctionNameSource.Local)
                    }
                }
                // No local variable info found, just return "local" as source
                return InferredFunctionName(null, FunctionNameSource.Local)
            }

            // CLOSURE: function literal being immediately called
            if (prevInstr.opcode == OpCode.CLOSURE) {
                return InferredFunctionName.UNKNOWN
            }

            // If we found an instruction writing to funcRegister but couldn't infer a name, stop searching
            break
        }

        // Default: no name inference
        return InferredFunctionName.UNKNOWN
    }

    /**
     * Try to find the name of a table by looking back at how it was loaded into a register.
     * This helps create full names like "debug.traceback" instead of just "traceback".
     */
    private fun findTableName(
        instructions: List<Instruction>,
        startPc: Int,
        tableRegister: Int,
        constants: List<LuaValue<*>>,
        localVars: List<ai.tenum.lua.compiler.model.LocalVarInfo>,
    ): String? {
        if (startPc <= 0) return null

        // Look back a few instructions to see how the table was loaded
        for (lookback in 1..minOf(3, startPc)) {
            val instr = instructions[startPc - lookback]

            // Skip instructions that don't write to tableRegister
            if (instr.a != tableRegister) continue

            // GETGLOBAL: table is a global variable
            if (instr.opcode == OpCode.GETGLOBAL) {
                return getStringFromRK(instr.b, constants)
            }

            // MOVE: table is from a local variable
            if (instr.opcode == OpCode.MOVE) {
                val sourceReg = instr.b
                for (localVar in localVars) {
                    if (localVar.isAliveAt(startPc) && localVar.register == sourceReg) {
                        return localVar.name
                    }
                }
            }

            // Found the instruction that loads the table but couldn't get a name
            break
        }

        return null
    }
}
