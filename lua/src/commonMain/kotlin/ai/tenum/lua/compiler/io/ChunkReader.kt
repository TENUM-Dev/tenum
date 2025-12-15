package ai.tenum.lua.compiler.io

import ai.tenum.lua.compiler.model.Instruction
import ai.tenum.lua.compiler.model.LineEvent
import ai.tenum.lua.compiler.model.LocalVarInfo
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.compiler.model.UpvalueInfo
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.OpCode
import okio.Buffer
import okio.BufferedSource

/**
 * Lua 5.4 binary chunk format deserializer
 */
object ChunkReader {
    private const val LUA_SIGNATURE = "\u001bLua"
    private const val LUA_VERSION = 0x54

    // Type tags for constants
    private const val LUA_TNIL = 0
    private const val LUA_TBOOLEAN = 1
    private const val LUA_TNUMBER = 3
    private const val LUA_TSTRING = 4
    private const val LUA_TFUNCTION = 6

    /**
     * Deserialize a binary chunk to Proto
     * Returns null if the chunk is invalid
     */
    private var sizeTSize: Int = 0

    fun load(bytes: ByteArray): Proto? {
        // Do not swallow exceptions here - let caller handle them so we can surface
        // useful error messages when a binary chunk fails to load.
        val buffer = Buffer()
        buffer.write(bytes)
        return loadImpl(buffer)
    }

    private fun loadImpl(source: BufferedSource): Proto {
        // Read and verify header
        val sig1 = source.readByte().toInt() and 0xFF
        val sig2 = source.readByte().toInt() and 0xFF
        val sig3 = source.readByte().toInt() and 0xFF
        val sig4 = source.readByte().toInt() and 0xFF

        if (sig1 != 0x1B || sig2 != 'L'.code || sig3 != 'u'.code || sig4 != 'a'.code) {
            throw IllegalArgumentException("Invalid Lua binary chunk signature")
        }

        val version = source.readByte().toInt() and 0xFF
        if (version != LUA_VERSION) {
            throw IllegalArgumentException("Incompatible Lua version")
        }

        val format = source.readByte().toInt() and 0xFF
        if (format != 0) {
            throw IllegalArgumentException("Incompatible format")
        }

        // Read LUAC_DATA
        source.skip(6)

        // Read size markers (int, size_t, Instruction, lua_Integer, lua_Number sizes)
        val intSize = source.readByte().toInt() and 0xFF
        sizeTSize = source.readByte().toInt() and 0xFF
        val instructionSize = source.readByte().toInt() and 0xFF
        val luaIntegerSize = source.readByte().toInt() and 0xFF
        val luaNumberSize = source.readByte().toInt() and 0xFF

        // Read test values according to sizes (common cases: 4 or 8)
        if (luaIntegerSize == 8) {
            source.readLongLe()
        } else if (luaIntegerSize == 4) {
            source.readIntLe().toLong()
        } else {
            // Fallback: try to read 8
            source.readLongLe()
        }

        // lua_Number - writer uses big-endian writeLong for float bits; match that
        if (luaNumberSize == 8) {
            source.readLong()
        } else if (luaNumberSize == 4) {
            source.readInt()
        } else {
            source.readLong()
        }

        // Read the function
        return readFunction(source, parentSource = null)
    }

    private fun readFunction(
        source: BufferedSource,
        parentSource: String?,
    ): Proto {
        // Source name (this is the source file/chunk name, not the function name)
        val rawSourceName = readString(source)
        // If empty and we have a parent source, inherit it (Lua 5.4 deduplication)
        val sourceName = if (rawSourceName.isEmpty() && parentSource != null) parentSource else rawSourceName
        // debug prints removed

        // Line info
        val lineDefined = source.readIntLe()
        val lastLineDefined = source.readIntLe()

        // Function info
        val numParams = source.readByte().toInt() and 0xFF
        val isVararg = source.readByte().toInt() != 0
        val maxStackSize = source.readByte().toInt() and 0xFF

        // Code (instructions)
        val codeSize = source.readIntLe()
        // debug prints removed
        val instructions = mutableListOf<Instruction>()
        for (i in 0 until codeSize) {
            val encoded = source.readIntLe()
            instructions.add(decodeInstruction(encoded))
        }

        // Constants
        val constantsSize = source.readIntLe()
        // debug prints removed
        val constants = mutableListOf<LuaValue<*>>()
        for (i in 0 until constantsSize) {
            constants.add(readConstant(source, sourceName))
        }

        // Upvalues
        val upvaluesSize = source.readIntLe()
        val upvalueInfo = mutableListOf<UpvalueInfo>()
        for (i in 0 until upvaluesSize) {
            val inStack = source.readByte().toInt() != 0
            val index = source.readByte().toInt() and 0xFF
            source.readByte() // kind (not used)
            // Store with generic name for now, will be replaced by debug section
            upvalueInfo.add(UpvalueInfo("upval$i", inStack, index))
        }

        // Nested protos
        val nestedCount = source.readIntLe()
        source.skip((nestedCount * 100).toLong()) // Skip nested functions for now

        // Debug info (local variables, skip advanced line info and upvalue names for simplicity)
        val lineInfoCount = source.readIntLe()
        source.skip((lineInfoCount * 4).toLong())

        val absLineInfoCount = source.readIntLe()
        source.skip((absLineInfoCount * 8).toLong())

        // Read local variable debug info
        val localVarCount = source.readIntLe()
        val localVars = mutableListOf<LocalVarInfo>()
        for (i in 0 until localVarCount) {
            val varName = readString(source)
            val startPc = source.readIntLe()
            val endPc = source.readIntLe()
            localVars.add(LocalVarInfo.of(varName, i, startPc, endPc, false)) // register = i, isConst = false for now
        }

        // Upvalue names - update the upvalueInfo with actual names
        val upvalueNameCount = source.readIntLe()
        for (i in 0 until upvalueNameCount) {
            val upvalueName = readString(source)
            if (i < upvalueInfo.size) {
                val originalInfo = upvalueInfo[i]
                upvalueInfo[i] = UpvalueInfo(upvalueName, originalInfo.inStack, originalInfo.index)
            }
        }

        // Build parameter list
        val parameters = (0 until numParams).map { "param$it" }

        // Build line info
        // Note: We don't reconstruct lineInfo from lineDefined because stripped functions
        // should have no line events (currentline will be -1 during execution) even though
        // lineDefined/lastLineDefined are preserved for debug.getinfo.
        val lineInfo = emptyList<LineEvent>()

        return Proto(
            name = "", // Function name is inferred from context, not stored in chunk
            instructions = instructions,
            constants = constants,
            upvalueInfo = upvalueInfo,
            parameters = parameters,
            hasVararg = isVararg,
            maxStackSize = maxStackSize,
            localVars = localVars,
            lineEvents = lineInfo,
            source = sourceName,
            lineDefined = lineDefined,
            lastLineDefined = lastLineDefined,
        )
    }

    private fun readString(source: BufferedSource): String {
        // Read size according to size_t width declared in header
        val size: Long =
            when (sizeTSize) {
                1 -> (source.readByte().toLong() and 0xFFL)
                4 -> (source.readIntLe().toLong() and 0xFFFFFFFFL)
                8 -> (source.readLongLe())
                else -> (source.readLongLe())
            }

        return if (size == 0L) {
            ""
        } else {
            val actual = (size - 1).toInt()
            val bytes = ByteArray(actual)
            source.readFully(bytes)
            bytes.decodeToString()
        }
    }

    private fun readConstant(
        source: BufferedSource,
        parentSource: String,
    ): LuaValue<*> {
        val type = source.readByte().toInt() and 0xFF
        return when (type) {
            LUA_TNIL -> LuaNil
            LUA_TBOOLEAN -> {
                val value = source.readByte().toInt() != 0
                LuaBoolean.of(value)
            }
            LUA_TNUMBER -> {
                val value = Double.fromBits(source.readLongLe())
                LuaNumber.of(value)
            }
            LUA_TSTRING -> {
                val value = readString(source)
                LuaString(value)
            }
            LUA_TFUNCTION -> {
                // Read an inline serialized function Proto and wrap as LuaCompiledFunction
                // Nested functions inherit parent source if they have empty source
                val proto = readFunction(source, parentSource = parentSource)
                val luaCompiledFunction = LuaCompiledFunction(proto)
                luaCompiledFunction
            }
            else -> LuaNil
        }
    }

    private fun decodeInstruction(encoded: Int): Instruction {
        val opCodeOrdinal = encoded and 0x3F
        val a = (encoded shr 6) and 0xFF
        val b = (encoded shr 14) and 0x1FF
        val c = (encoded shr 23) and 0x1FF

        val opCode = OpCode.entries.getOrNull(opCodeOrdinal) ?: OpCode.MOVE

        return Instruction(opCode, a, b, c)
    }
}
