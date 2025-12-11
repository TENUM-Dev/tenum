package ai.tenum.lua.compiler.io

import ai.tenum.lua.compiler.api.ChunkFormat
import ai.tenum.lua.compiler.bytecode.InstructionEncoder
import ai.tenum.lua.compiler.io.BufferUtils.writeSizeT
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import okio.Buffer
import okio.BufferedSink

/**
 * Lua 5.4 binary chunk format serializer
 *
 * Binary chunk format:
 * - Header: ESC + "Lua" + version + format + data
 * - Function: Proto serialized recursively
 *
 * This is a simplified implementation that captures the essence of Lua's binary format
 */
object ChunkWriter {
    /**
     * Serialize a Proto to binary chunk format
     */
    fun dump(proto: Proto): ByteArray {
        val buffer = Buffer()
        writeHeader(buffer)
        writeFunction(buffer, proto)
        return buffer.readByteArray()
    }

    private fun writeHeader(sink: BufferedSink) {
        // Signature: ESC + "Lua" (write as individual bytes to avoid UTF-8 encoding issues)
        sink.writeByte(ChunkFormat.LUA_SIGNATURE_BYTE_1) // ESC
        sink.writeByte(ChunkFormat.LUA_SIGNATURE_BYTE_2)
        sink.writeByte(ChunkFormat.LUA_SIGNATURE_BYTE_3)
        sink.writeByte(ChunkFormat.LUA_SIGNATURE_BYTE_4)

        // Version
        sink.writeByte(ChunkFormat.LUA_VERSION)

        // Format
        sink.writeByte(ChunkFormat.LUA_FORMAT)

        // LUAC_DATA (compatibility data)
        sink.writeByte(ChunkFormat.LUAC_DATA_1)
        sink.writeByte(ChunkFormat.LUAC_DATA_2)
        sink.writeByte(ChunkFormat.LUAC_DATA_3)
        sink.writeByte(ChunkFormat.LUAC_DATA_4)
        sink.writeByte(ChunkFormat.LUAC_DATA_5)
        sink.writeByte(ChunkFormat.LUAC_DATA_6)

        // Size markers (int, size_t, Instruction, lua_Integer, lua_Number)
        sink.writeByte(ChunkFormat.SIZEOF_INT)
        sink.writeByte(ChunkFormat.SIZEOF_SIZET)
        sink.writeByte(ChunkFormat.SIZEOF_INSTRUCTION)
        sink.writeByte(ChunkFormat.SIZEOF_LUA_INTEGER)
        sink.writeByte(ChunkFormat.SIZEOF_LUA_NUMBER)

        // Number format test values
        sink.writeLongLe(ChunkFormat.TEST_INTEGER)
        sink.writeLong(ChunkFormat.TEST_NUMBER.toBits())
    }

    private fun writeFunction(
        sink: BufferedSink,
        proto: Proto,
    ) {
        // Source name (write the source, not the function name)
        writeString(sink, proto.source)

        // Line info - write the function definition line numbers
        sink.writeIntLe(proto.lineDefined)
        sink.writeIntLe(proto.lastLineDefined)

        // Function info
        sink.writeByte(proto.parameters.size) // numparams
        sink.writeByte(if (proto.hasVararg) 1 else 0) // is_vararg
        sink.writeByte(proto.maxStackSize) // maxstacksize

        // Code (instructions)
        sink.writeIntLe(proto.instructions.size)
        for (instr in proto.instructions) {
            sink.writeIntLe(InstructionEncoder.encode(instr))
        }

        // Constants
        sink.writeIntLe(proto.constants.size)
        for (constant in proto.constants) {
            writeConstant(sink, constant)
        }

        // Upvalues
        sink.writeIntLe(proto.upvalueInfo.size)
        for (upval in proto.upvalueInfo) {
            sink.writeByte(if (upval.inStack) 1 else 0)
            sink.writeByte(upval.index)
            sink.writeByte(0) // kind (not used in simplified version)
        }

        // Nested protos (functions)
        sink.writeIntLe(0) // No nested functions in simplified version

        // Debug info (simplified - skip most, but keep upvalue names)
        sink.writeIntLe(0) // lineinfo
        sink.writeIntLe(0) // abslineinfo
        sink.writeIntLe(0) // local vars

        // Upvalue names (needed for debug.getupvalue/setupvalue)
        sink.writeIntLe(proto.upvalueInfo.size)
        for (upval in proto.upvalueInfo) {
            writeString(sink, upval.name)
        }
    }

    private fun writeString(
        sink: BufferedSink,
        str: String,
    ) {
        if (str.isEmpty()) {
            // write zero size_t for empty string (size_t = 8 as declared in header)
            sink.writeSizeT(0L)
        } else {
            val bytes = str.encodeToByteArray()
            val size = (bytes.size + 1).toLong()
            // write size as size_t (little-endian) to match header's size_t = 8
            sink.writeSizeT(size)
            sink.write(bytes)
        }
    }

    private fun writeConstant(
        sink: BufferedSink,
        value: LuaValue<*>,
    ) {
        when (value) {
            is LuaNil -> {
                sink.writeByte(ChunkFormat.LUA_TNIL)
            }
            is LuaBoolean -> {
                sink.writeByte(ChunkFormat.LUA_TBOOLEAN)
                sink.writeByte(if (value.value) 1 else 0)
            }
            is LuaNumber -> {
                sink.writeByte(ChunkFormat.LUA_TNUMBER)
                sink.writeLongLe(value.toDouble().toBits())
            }
            is LuaString -> {
                sink.writeByte(ChunkFormat.LUA_TSTRING)
                writeString(sink, value.value)
            }
            is LuaCompiledFunction -> {
                // Serialize a compiled function constant inline
                sink.writeByte(ChunkFormat.LUA_TFUNCTION)
                writeFunction(sink, value.proto)
            }
            else -> {
                // Unsupported constant type - write nil
                sink.writeByte(ChunkFormat.LUA_TNIL)
            }
        }
    }
}
