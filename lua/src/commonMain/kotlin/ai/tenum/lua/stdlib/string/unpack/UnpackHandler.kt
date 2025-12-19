package ai.tenum.lua.stdlib.string.unpack

/**
 * Handler for unpacking a specific binary format type.
 */
interface UnpackHandler {
    /**
     * Unpack value(s) from the binary data using this handler.
     * @param context The unpacking context
     */
    fun unpack(context: UnpackContext)
}
