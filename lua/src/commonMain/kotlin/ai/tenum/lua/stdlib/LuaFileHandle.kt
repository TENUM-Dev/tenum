package ai.tenum.lua.stdlib

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import okio.FileSystem
import okio.Path

/**
 * Represents a Lua file handle
 *
 * Handles file I/O operations for read/write/append modes.
 * Uses Okio for cross-platform file access.
 */
class LuaFileHandle(
    val path: Path,
    val mode: String,
    private val fileSystem: FileSystem,
) {
    var isClosed = false
        private set

    private var content: String? = null
    private var writeBuffer: StringBuilder? = null

    init {
        // Read file content for read modes
        if (mode.startsWith("r")) {
            content = fileSystem.read(path) { readUtf8() }
        } else if (mode.startsWith("w") || mode.startsWith("a")) {
            writeBuffer = StringBuilder()
            if (mode.startsWith("a") && fileSystem.exists(path)) {
                // Append mode - load existing content
                writeBuffer!!.append(fileSystem.read(path) { readUtf8() })
            }
        }
    }

    /**
     * Creates a LuaTable with file methods accessible via metatable
     */
    fun toLuaTable(): LuaTable =
        IOHandleTableFactory.createHandleTable(
            handle = this,
            readFn = ::read,
            writeFn = ::write,
            closeFn = ::close,
            flushFn = ::flush,
            linesFn = ::lines,
            gcAction = ::close,
        )

    /**
     * Read from file
     *
     * @param args Arguments from Lua: [self, format?]
     * @return List with read content or LuaNil
     */
    fun read(args: List<LuaValue<*>>): List<LuaValue<*>> {
        if (isClosed || content == null) {
            return listOf(LuaNil)
        }

        val format = IOHandleTableFactory.parseReadFormat(args)

        return when (format) {
            "*a", "*all" -> listOf(LuaString(content!!))
            "*l", "*line" -> {
                // Read one line
                val lines = content!!.lines()
                if (lines.isNotEmpty()) {
                    listOf(LuaString(lines.first()))
                } else {
                    listOf(LuaNil)
                }
            }
            "*n", "*number" -> IOHandleTableFactory.readNumber(content!!)
            else -> IOHandleTableFactory.readCharacters(content!!, format)
        }
    }

    /**
     * Write to file
     *
     * @param args Arguments from Lua: [self, value1, value2, ...]
     * @return List with LuaBoolean.TRUE or LuaNil
     */
    fun write(args: List<LuaValue<*>>): List<LuaValue<*>> {
        if (isClosed || writeBuffer == null) {
            return listOf(LuaNil)
        }

        // When called as method (file:write(...)), args[0] is self, skip it
        // Filter out the self parameter (table) and only process string/number args
        for (arg in args) {
            when (arg) {
                is LuaString -> writeBuffer!!.append(arg.value)
                is LuaNumber -> writeBuffer!!.append(arg.value)
                is LuaTable -> {} // Skip self parameter
                else -> writeBuffer!!.append(arg.toString())
            }
        }

        return listOf(LuaBoolean.TRUE)
    }

    /**
     * Flush write buffer to file
     */
    fun flush() {
        if (!isClosed && writeBuffer != null) {
            fileSystem.write(path) {
                writeUtf8(writeBuffer.toString())
            }
        }
    }

    /**
     * Close file and flush any pending writes
     */
    fun close() {
        if (!isClosed) {
            flush()
            isClosed = true
            content = null
            writeBuffer = null
        }
    }

    /**
     * Get iterator for reading lines
     *
     * @return List with iterator function
     */
    fun lines(): List<LuaValue<*>> {
        if (isClosed || content == null) {
            return listOf(LuaNil)
        }

        val lines = content!!.lines()
        var currentIndex = 0

        val iterator =
            LuaNativeFunction { _ ->
                if (currentIndex < lines.size) {
                    listOf(LuaString(lines[currentIndex++]))
                } else {
                    listOf(LuaNil)
                }
            }

        return listOf(iterator)
    }
}
