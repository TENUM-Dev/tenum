package ai.tenum.lua.stdlib.string.unpack.handlers

import ai.tenum.lua.stdlib.string.BinaryOperations
import ai.tenum.lua.stdlib.string.NumericUnpackHelpers
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler

/**
 * Base class for variable-size integer handlers.
 */
internal abstract class VariableIntHandlerBase(
    private val signed: Boolean,
) : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        val size = context.parser.readNumber() ?: 4
        BinaryOperations.validateIntegerSize(size)
        val actualSize = if (context.parser.maxAlign != null) minOf(size, context.parser.maxAlign!!) else size
        context.autoAlign(actualSize)
        context.result.add(NumericUnpackHelpers.unpackInteger(context::readByte, actualSize, context.parser.littleEndian, signed))
    }
}

/**
 * Unpack signed variable-size integer (i)
 */
internal class SignedVariableIntHandler : VariableIntHandlerBase(signed = true)

/**
 * Unpack unsigned variable-size integer (I)
 */
internal class UnsignedVariableIntHandler : VariableIntHandlerBase(signed = false)
