package ai.tenum.lua.compiler.api

/**
 * Shared constants for Lua 5.4 binary chunk format.
 */
object ChunkFormat {
    // Signature bytes
    const val LUA_SIGNATURE = "\u001bLua"
    const val LUA_SIGNATURE_BYTE_1 = 0x1B
    const val LUA_SIGNATURE_BYTE_2 = 'L'.code
    const val LUA_SIGNATURE_BYTE_3 = 'u'.code
    const val LUA_SIGNATURE_BYTE_4 = 'a'.code

    // Version and format
    const val LUA_VERSION = 0x54 // Lua 5.4
    const val LUA_FORMAT = 0 // Official format

    // Type tags for constants
    const val LUA_TNIL = 0
    const val LUA_TBOOLEAN = 1
    const val LUA_TNUMBER = 3
    const val LUA_TSTRING = 4
    const val LUA_TFUNCTION = 6

    // Compatibility data (LUAC_DATA)
    const val LUAC_DATA_1 = 0x19
    const val LUAC_DATA_2 = 0x93
    const val LUAC_DATA_3 = 0x0D // '\r'.code
    const val LUAC_DATA_4 = 0x0A // '\n'.code
    const val LUAC_DATA_5 = 0x1A
    const val LUAC_DATA_6 = 0x0A // '\n'.code

    // Size markers
    const val SIZEOF_INT = 4
    const val SIZEOF_SIZET = 8
    const val SIZEOF_INSTRUCTION = 4
    const val SIZEOF_LUA_INTEGER = 8
    const val SIZEOF_LUA_NUMBER = 8

    // Number format test values
    const val TEST_INTEGER = 0x5678L
    const val TEST_NUMBER = 370.5
}
