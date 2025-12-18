package ai.tenum.lua.stdlib.string.unpack.handlers

import ai.tenum.lua.stdlib.string.NumericUnpackHelpers
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler

/**
 * Unpack signed int (l)
 */
class SignedIntHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(4)
        context.result.add(NumericUnpackHelpers.unpackInt(context::readByte, context.parser.littleEndian, signed = true))
    }
}

/**
 * Unpack unsigned int (L)
 */
class UnsignedIntHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(4)
        context.result.add(NumericUnpackHelpers.unpackInt(context::readByte, context.parser.littleEndian, signed = false))
    }
}
