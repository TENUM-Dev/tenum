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
 * Represents a standard stream handle (stdin, stdout, stderr)
 *
 * Unlike regular file handles, standard streams:
 * - Don't require actual file paths
 * - Support custom read/write behavior for testing
 * - Can be mocked by providing custom reader/writer functions
 */
class StandardStreamHandle(
    val name: String,
    private val reader: (() -> String)? = null,
    private val writer: ((String) -> Unit)? = null,
) {
    var isClosed = false
        private set

    /**
     * Creates a LuaUserdata with stream methods accessible via metatable.
     * This matches Lua 5.4 behavior where io.stdin/stdout/stderr are userdata, not tables.
     */
    fun toLuaUserdata(): LuaUserdata<StandardStreamHandle> {
        val userdata = LuaUserdata(this)

        // Create metatable with FILE* methods
        val metatable = LuaTable()
        val methods = LuaTable()

        // Common methods
        methods[LuaString("read")] = LuaNativeFunction { args -> read(args) }
        methods[LuaString("write")] = LuaNativeFunction { args -> write(args) }
        methods[LuaString("close")] =
            LuaNativeFunction { _ ->
                close()
                listOf(LuaBoolean.TRUE)
            }
        methods[LuaString("flush")] =
            LuaNativeFunction { _ ->
                flush()
                listOf(LuaBoolean.TRUE)
            }

        metatable[LuaString("__index")] = methods
        metatable[LuaString("__name")] = LuaString("FILE*")

        // __gc() - Finalizer (validates FILE* argument, standard streams don't close)
        // When called without self (e.g., getmetatable(io.stdin).__gc()),
        // should error with "bad argument #1 to '__gc' (FILE* expected, got no value)"
        metatable[LuaString("__gc")] =
            LuaNativeFunction { args ->
                // Validate first argument is the FILE* userdata
                val firstArg = args.firstOrNull()
                if (firstArg == null || firstArg is LuaNil) {
                    throw LuaRuntimeError("bad argument #1 to '__gc' (FILE* expected, got no value)")
                }
                if (firstArg !is LuaUserdata<*>) {
                    val typeName = firstArg.type().name.lowercase()
                    throw LuaRuntimeError("bad argument #1 to '__gc' (FILE* expected, got $typeName)")
                }

                // Check if it's actually a FILE* by checking if it's StandardStreamHandle or LuaFileHandle
                if (firstArg.value !is StandardStreamHandle && firstArg.value !is LuaFileHandle) {
                    throw LuaRuntimeError("bad argument #1 to '__gc' (FILE* expected, got userdata)")
                }

                // Standard streams are never truly closed
                emptyList()
            }

        userdata.metatable = metatable
        return userdata
    }

    /**
     * Creates a LuaTable with stream methods accessible via metatable.
     * @deprecated Use toLuaUserdata() instead for Lua 5.4 compatibility
     */
    @Deprecated("Use toLuaUserdata() for Lua 5.4 compatibility")
    fun toLuaTable(): LuaTable =
        IOHandleTableFactory.createHandleTable(
            handle = this,
            readFn = ::read,
            writeFn = ::write,
            closeFn = ::close,
            flushFn = ::flush,
            linesFn = null, // Standard streams don't support lines()
            gcAction = {}, // __gc does nothing for standard streams (they're never truly closed)
        )

    /**
     * Read from stream
     *
     * @param args Arguments from Lua: [self, format?]
     * @return List with read content or LuaNil
     */
    fun read(args: List<LuaValue<*>>): List<LuaValue<*>> {
        if (isClosed || reader == null) {
            return listOf(LuaNil)
        }

        val format = IOHandleTableFactory.parseReadFormat(args)

        return try {
            val content = reader()
            when (format) {
                "*a", "*all" -> listOf(LuaString(content))
                "*l", "*line" -> {
                    val lines = content.lines()
                    if (lines.isNotEmpty()) {
                        listOf(LuaString(lines.first()))
                    } else {
                        listOf(LuaNil)
                    }
                }
                "*n", "*number" -> IOHandleTableFactory.readNumber(content)
                else -> IOHandleTableFactory.readCharacters(content, format)
            }
        } catch (e: Exception) {
            listOf(LuaNil)
        }
    }

    /**
     * Write to stream
     *
     * @param args Arguments from Lua: [self, value1, value2, ...]
     * @return List with LuaBoolean.TRUE or LuaNil
     */
    fun write(args: List<LuaValue<*>>): List<LuaValue<*>> {
        if (isClosed || writer == null) {
            return listOf(LuaNil)
        }

        // Filter out self parameter and collect all values to write
        val output = StringBuilder()
        for (arg in args) {
            when (arg) {
                is LuaString -> output.append(arg.value)
                is LuaNumber -> output.append(arg.value)
                is LuaTable -> {} // Skip self parameter
                else -> output.append(arg.toString())
            }
        }

        return try {
            writer(output.toString())
            listOf(LuaBoolean.TRUE)
        } catch (e: Exception) {
            listOf(LuaNil)
        }
    }

    /**
     * Flush write buffer (no-op for standard streams)
     */
    fun flush(): LuaValue<*> = LuaBoolean.TRUE

    /**
     * Close stream
     */
    fun close() {
        isClosed = true
    }
}
