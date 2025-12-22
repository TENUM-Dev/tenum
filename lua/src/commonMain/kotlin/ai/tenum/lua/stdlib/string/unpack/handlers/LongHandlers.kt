package ai.tenum.lua.stdlib.string.unpack.handlers

import ai.tenum.lua.stdlib.string.NumericUnpackHelpers
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler

/**
 * Unpack signed long (j)
 */
class SignedLongHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(8)
        context.result.add(NumericUnpackHelpers.unpackLong(context::readByte, context.parser.littleEndian, signed = true))
    }
}

/**
 * Unpack unsigned long (J)
 */
class UnsignedLongHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(8)
        context.result.add(NumericUnpackHelpers.unpackLong(context::readByte, context.parser.littleEndian, signed = false))
    }
}

/**
 * Unpack size_t (T) - unsigned long
 */
class SizeTHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(8)
        context.result.add(NumericUnpackHelpers.unpackLong(context::readByte, context.parser.littleEndian, signed = false))
    }
}
