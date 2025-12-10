package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaUserdata
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.errorhandling.LuaRuntimeError

/**
 * Factory for creating IO handle tables with shared method-table setup and __gc validation.
 *
 * Consolidates duplicated toLuaTable() logic found in:
 * - LuaFileHandle.toLuaTable() (~50-115)
 * - StandardStreamHandle.toLuaTable() (~35-95)
 *
 * Both file handles and standard streams share the same structure:
 * - Table with __filehandle userdata reference
 * - Metatable with __index pointing to methods table
 * - Common methods: read, write, close, flush
 * - __gc finalizer with FILE* validation
 * - __name set to "FILE*" for type display
 */
object IOHandleTableFactory {
    /**
     * Parses read format from Lua arguments.
     *
     * Consolidates the duplicated argument parsing logic found in:
     * - LuaFileHandle.read() (lines ~64-79)
     * - StandardStreamHandle.read() (lines ~47-62)
     *
     * When called as method (file:read(arg)), args = [self, arg]
     * arg can be a string ("*a", "*l", "*n") or a number (n characters)
     *
     * @param args Lua arguments list
     * @return Format string (e.g., "*l", "*a", "*n", or "5" for 5 characters)
     */
    fun parseReadFormat(args: List<LuaValue<*>>): String {
        val stringArg = args.filterIsInstance<LuaString>().firstOrNull()?.value
        val numberArg =
            args
                .filterIsInstance<LuaNumber>()
                .firstOrNull()
                ?.value
                ?.toInt()

        return stringArg ?: numberArg?.toString() ?: "*l"
    }

    /**
     * Reads a number from content string.
     *
     * Consolidates the duplicated number reading logic found in:
     * - LuaFileHandle.read() (lines ~93-101)
     * - StandardStreamHandle.read() (lines ~74-82)
     *
     * @param content Content string to parse
     * @return List with LuaNumber if successful, LuaNil otherwise
     */
    fun readNumber(content: String): List<LuaValue<*>> {
        val num = content.trim().toDoubleOrNull()
        return if (num != null) {
            listOf(LuaNumber.of(num))
        } else {
            listOf(LuaNil)
        }
    }

    /**
     * Reads n characters from content string.
     *
     * Consolidates the duplicated character reading logic found in:
     * - LuaFileHandle.read() (lines ~102-110)
     * - StandardStreamHandle.read() (lines ~83-91)
     *
     * @param content Content string to read from
     * @param format Format string (should be a number like "5")
     * @return List with LuaString if successful, LuaNil otherwise
     */
    fun readCharacters(
        content: String,
        format: String,
    ): List<LuaValue<*>> {
        val n = format.toIntOrNull()
        return if (n != null && n > 0) {
            val text = content.take(n)
            listOf(LuaString(text))
        } else {
            listOf(LuaNil)
        }
    }

    /**
     * Creates a LuaTable with file/stream methods accessible via metatable.
     *
     * @param handle The handle instance (LuaFileHandle or StandardStreamHandle)
     * @param readFn Function that implements read(args)
     * @param writeFn Function that implements write(args)
     * @param closeFn Function that implements close()
     * @param flushFn Function that implements flush()
     * @param linesFn Optional function that implements lines() (file handles only)
     * @param gcAction Action to perform in __gc (e.g., close for files, no-op for streams)
     */
    fun createHandleTable(
        handle: Any,
        readFn: (List<LuaValue<*>>) -> List<LuaValue<*>>,
        writeFn: (List<LuaValue<*>>) -> List<LuaValue<*>>,
        closeFn: () -> Unit,
        flushFn: () -> Unit,
        linesFn: (() -> List<LuaValue<*>>)? = null,
        gcAction: () -> Unit,
    ): LuaTable {
        val table = LuaTable()

        // Store handle reference as a LuaUserdata for io.type() checks
        table[LuaString("__filehandle")] = LuaUserdata(handle)

        // Create metatable with __index that provides methods
        val metatable = LuaTable()
        val methods = LuaTable()

        // Common methods
        methods[LuaString("read")] = LuaNativeFunction { args -> readFn(args) }
        methods[LuaString("write")] = LuaNativeFunction { args -> writeFn(args) }
        methods[LuaString("close")] =
            LuaNativeFunction { _ ->
                closeFn()
                listOf(LuaBoolean.TRUE)
            }
        methods[LuaString("flush")] =
            LuaNativeFunction { _ ->
                flushFn()
                listOf(LuaBoolean.TRUE)
            }

        // Optional lines() method for file handles
        if (linesFn != null) {
            methods[LuaString("lines")] = LuaNativeFunction { _ -> linesFn() }
        }

        // __gc() - Finalizer (validates FILE* argument)
        // When called without self (e.g., getmetatable(io.stdin).__gc()),
        // should error with "bad argument #1 to '__gc' (FILE* expected, got no value)"
        metatable[LuaString("__gc")] =
            LuaNativeFunction { args ->
                // Validate first argument is a FILE* userdata
                val firstArg = args.firstOrNull()
                if (firstArg == null || firstArg is LuaNil) {
                    throw LuaRuntimeError("bad argument #1 to '__gc' (FILE* expected, got no value)")
                }
                if (firstArg !is LuaTable) {
                    val typeName = firstArg.type().name.lowercase()
                    throw LuaRuntimeError("bad argument #1 to '__gc' (FILE* expected, got $typeName)")
                }

                // Check if it's actually a FILE* by looking for __filehandle
                val handleValue = firstArg.rawGet(LuaString("__filehandle"))
                if (handleValue !is LuaUserdata ||
                    (handleValue.value !is StandardStreamHandle && handleValue.value !is LuaFileHandle)
                ) {
                    throw LuaRuntimeError("bad argument #1 to '__gc' (FILE* expected, got table)")
                }

                // Perform handle-specific cleanup
                gcAction()
                listOf(LuaBoolean.TRUE)
            }

        // Set __name for proper type display in error messages
        metatable[LuaString("__name")] = LuaString("FILE*")
        metatable[LuaString("__index")] = methods

        table.metatable = metatable
        return table
    }
}
