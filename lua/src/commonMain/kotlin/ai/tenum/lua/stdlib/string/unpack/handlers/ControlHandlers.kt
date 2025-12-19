package ai.tenum.lua.stdlib.string.unpack.handlers

import ai.tenum.lua.stdlib.string.BinaryOperations
import ai.tenum.lua.stdlib.string.unpack.UnpackContext
import ai.tenum.lua.stdlib.string.unpack.UnpackHandler

/**
 * Skip one byte (x)
 */
class SkipByteHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        context.readByte()
    }
}

/**
 * Align to next format size (X)
 */
class AlignHandler : UnpackHandler {
    override fun unpack(context: UnpackContext) {
        val (nextSize, alreadyAdvanced) = context.parser.peekNextFormatSize()
        if (context.parser.maxAlign != null) {
            val alignSize = minOf(nextSize, context.parser.maxAlign!!)
            val padding = BinaryOperations.calculatePadding(context.pos, alignSize)
            repeat(padding) { context.readByte() }
        }
        context.parser.consumeNextFormatChar(alreadyAdvanced)
    }
}
