package ai.tenum.lua.stdlib.string.unpack.handlers

import ai.tenum.lua.stdlib.string.NumericUnpackHelpers
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler

/**
 * Unpack float (f)
 */
class FloatHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(4)
        context.result.add(NumericUnpackHelpers.unpackFloat(context::readByte, context.parser.littleEndian))
    }
}

/**
 * Unpack double (d, n)
 */
class DoubleHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.autoAlign(8)
        context.result.add(NumericUnpackHelpers.unpackDouble(context::readByte, context.parser.littleEndian))
    }
}
