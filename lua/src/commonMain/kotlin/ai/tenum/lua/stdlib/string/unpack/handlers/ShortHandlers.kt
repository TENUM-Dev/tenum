package ai.tenum.lua.stdlib.string.unpack.handlers

import ai.tenum.lua.stdlib.string.NumericUnpackHelpers
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler

/**
 * Unpack signed short (h)
 */
class SignedShortHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(2)
        context.result.add(NumericUnpackHelpers.unpackShort(context::readByte, context.parser.littleEndian, signed = true))
    }
}

/**
 * Unpack unsigned short (H)
 */
class UnsignedShortHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(2)
        context.result.add(NumericUnpackHelpers.unpackShort(context::readByte, context.parser.littleEndian, signed = false))
    }
}
