package ai.tenum.lua.stdlib.string.unpack

import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatParser

/**
 * Context for binary unpacking operations.
 */
data class UnpackContext(
    val data: String,
    val parser: FormatParser,
    val result: MutableList<LuaValue<*>>,
    var pos: Int,
) {
    fun readByte(): Int {
        if (pos >= data.length) throw RuntimeException("data string too short")
        return data[pos++].code.and(0xFF)
    }

    fun autoAlign(size: Int) {
        val padding =
            ai.tenum.lua.stdlib.string.BinaryOperations
                .autoAlign(pos, size, parser.maxAlign)
        repeat(padding) { readByte() }
    }
}
