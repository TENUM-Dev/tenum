package ai.tenum.lua.stdlib.string.unpack.handlers

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler

/**
 * Unpack signed byte (b)
 */
class SignedByteHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        val v = context.readByte().toByte()
        context.result.add(LuaNumber.of(v.toLong().toDouble()))
    }
}

/**
 * Unpack unsigned byte (B)
 */
class UnsignedByteHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        val v = context.readByte()
        context.result.add(LuaNumber.of(v.toDouble()))
    }
}
