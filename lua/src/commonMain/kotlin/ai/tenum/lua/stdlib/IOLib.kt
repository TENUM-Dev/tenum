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
import ai.tenum.lua.vm.library.LuaLibrary
import ai.tenum.lua.vm.library.LuaLibraryContext
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * IO Library (io.*)
 *
 * Provides file I/O operations using Okio filesystem.
 * Based on Lua 5.4 io library specification.
 */
class IOLib : LuaLibrary {
    override val name: String = "io"

    // Store stdin/stdout/stderr for io.input()/io.output() to return
    private var stdinUserdata: LuaUserdata<StandardStreamHandle>? = null
    private var stdoutUserdata: LuaUserdata<StandardStreamHandle>? = null

    override fun register(context: LuaLibraryContext) {
        val lib = LuaTable()

        // Create standard stream handles
        // These can be mocked by providing custom reader/writer functions
        val stdinHandle =
            StandardStreamHandle(
                name = "stdin",
                reader = { "" }, // Default: empty input
                writer = null, // stdin is read-only
            )
        val stdoutHandle =
            StandardStreamHandle(
                name = "stdout",
                reader = null, // stdout is write-only
                writer = { /* Default: no-op */ },
            )
        val stderrHandle =
            StandardStreamHandle(
                name = "stderr",
                reader = null, // stderr is write-only
                writer = { /* Default: no-op */ },
            )

        // Store stdin/stdout for io.input()/io.output()
        stdinUserdata = stdinHandle.toLuaUserdata()
        stdoutUserdata = stdoutHandle.toLuaUserdata()

        // Register standard streams as userdata (Lua 5.4 behavior)
        lib[LuaString("stdin")] = stdinUserdata!!
        lib[LuaString("stdout")] = stdoutUserdata!!
        lib[LuaString("stderr")] = stderrHandle.toLuaUserdata()

        // io.open(filename [, mode]) - Open file
        lib[LuaString("open")] =
            LuaNativeFunction { args ->
                buildList {
                    add(ioOpen(args, context.fileSystem))
                }
            }

        // io.close([file]) - Close file
        lib[LuaString("close")] =
            LuaNativeFunction { args ->
                buildList {
                    add(ioClose(args))
                }
            }

        // io.read(...) - Read from default input
        lib[LuaString("read")] =
            LuaNativeFunction { args ->
                ioRead(args, context.fileSystem)
            }

        // io.write(...) - Write to default output
        lib[LuaString("write")] =
            LuaNativeFunction { args ->
                buildList {
                    add(ioWrite(args, context.fileSystem))
                }
            }

        // io.lines([filename]) - Iterator for reading lines
        lib[LuaString("lines")] =
            LuaNativeFunction { args ->
                buildList {
                    add(ioLines(args, context.fileSystem))
                }
            }

        // io.input([file]) - Set default input file
        lib[LuaString("input")] =
            LuaNativeFunction { args ->
                buildList {
                    add(ioInput(args, context.fileSystem))
                }
            }

        // io.output([file]) - Set default output file
        lib[LuaString("output")] =
            LuaNativeFunction { args ->
                buildList {
                    add(ioOutput(args, context.fileSystem))
                }
            }

        // io.flush() - Flush default output
        lib[LuaString("flush")] =
            LuaNativeFunction { args ->
                buildList {
                    add(ioFlush())
                }
            }

        // io.type(obj) - Check if object is a file handle
        lib[LuaString("type")] =
            LuaNativeFunction { args ->
                buildList {
                    add(ioType(args))
                }
            }

        context.registerGlobal("io", lib)
    }

    /**
     * io.open(filename [, mode]) - Open a file
     * Modes: "r" (read), "w" (write), "a" (append), "r+" (read/write), etc.
     */
    private fun ioOpen(
        args: List<LuaValue<*>>,
        fileSystem: FileSystem,
    ): LuaValue<*> {
        val filename =
            (args.getOrNull(0) as? LuaString)?.value
                ?: return LuaNil

        val mode = (args.getOrNull(1) as? LuaString)?.value ?: "r"

        return try {
            val path = filename.toPath()

            // Create file handle based on mode
            val handle =
                when {
                    mode.startsWith("r") -> {
                        if (!fileSystem.exists(path)) {
                            return LuaNil
                        }
                        LuaFileHandle(path, mode, fileSystem)
                    }
                    mode.startsWith("w") -> {
                        // Create or truncate file
                        LuaFileHandle(path, mode, fileSystem)
                    }
                    mode.startsWith("a") -> {
                        // Append mode
                        LuaFileHandle(path, mode, fileSystem)
                    }
                    else -> return LuaNil
                }

            handle.toLuaTable()
        } catch (e: Exception) {
            LuaNil
        }
    }

    /**
     * io.close([file]) - Close file handle
     */
    private fun ioClose(args: List<LuaValue<*>>): LuaValue<*> {
        val table =
            args.getOrNull(0) as? LuaTable
                ?: return LuaBoolean.TRUE

        val handle =
            getFileHandle(table)
                ?: return LuaBoolean.TRUE

        return try {
            handle.close()
            LuaBoolean.TRUE
        } catch (e: Exception) {
            LuaNil
        }
    }

    /**
     * Extract LuaFileHandle from LuaTable
     */
    private fun getFileHandle(table: LuaTable): LuaFileHandle? {
        val handleValue = table.rawGet(LuaString("__filehandle"))
        return if (handleValue is LuaUserdata && handleValue.value is LuaFileHandle) {
            handleValue.value as LuaFileHandle
        } else {
            null
        }
    }

    private fun getStreamHandle(table: LuaTable): StandardStreamHandle? {
        val handleValue = table.rawGet(LuaString("__filehandle"))
        return if (handleValue is LuaUserdata && handleValue.value is StandardStreamHandle) {
            handleValue.value as StandardStreamHandle
        } else {
            null
        }
    }

    /**
     * io.read(...) - Read from default input
     */
    private fun ioRead(
        args: List<LuaValue<*>>,
        fileSystem: FileSystem,
    ): List<LuaValue<*>> {
        // For now, return empty string (stdin not implemented)
        return listOf(LuaString(""))
    }

    /**
     * io.write(...) - Write to default output
     */
    private fun ioWrite(
        args: List<LuaValue<*>>,
        fileSystem: FileSystem,
    ): LuaValue<*> {
        // Validate arguments - io.write only accepts strings or numbers
        for ((index, arg) in args.withIndex()) {
            when (arg) {
                is LuaString -> print(arg.value)
                is LuaNumber -> print(arg.value)
                else -> {
                    val typeName = arg.type().name.lowercase()
                    throw LuaRuntimeError("bad argument #${index + 1} to 'io.write' (string expected, got $typeName)")
                }
            }
        }
        return LuaBoolean.TRUE
    }

    /**
     * io.lines([filename]) - Iterator for reading lines
     */
    private fun ioLines(
        args: List<LuaValue<*>>,
        fileSystem: FileSystem,
    ): LuaValue<*> {
        val filename = (args.getOrNull(0) as? LuaString)?.value

        if (filename == null) {
            // Return iterator for stdin (not implemented)
            return LuaNil
        }

        return try {
            val path = filename.toPath()
            if (!fileSystem.exists(path)) {
                return LuaNil
            }

            val lines =
                fileSystem.read(path) {
                    readUtf8().lines()
                }

            // Return iterator function
            var index = 0
            LuaNativeFunction { _ ->
                if (index < lines.size) {
                    listOf(LuaString(lines[index++]))
                } else {
                    listOf(LuaNil)
                }
            }
        } catch (e: Exception) {
            LuaNil
        }
    }

    /**
     * Validates that a table is a valid FILE* handle (has __filehandle or stream userdata).
     * Throws LuaRuntimeError with appropriate message if not valid.
     */
    private fun validateFileHandle(
        arg: LuaTable,
        functionName: String,
    ) {
        val fileHandle = getFileHandle(arg)
        val streamHandle = getStreamHandle(arg)
        if (fileHandle == null && streamHandle == null) {
            // Not a FILE* - get the type name for error message
            val metatable = arg.metatable
            val typeStr =
                if (metatable is LuaTable) {
                    val nameValue = metatable.rawGet(LuaString("__name"))
                    if (nameValue is LuaString) nameValue.value else "table"
                } else {
                    "table"
                }
            throw LuaRuntimeError("bad argument #1 to '$functionName' (FILE* expected, got $typeStr)")
        }
    }

    /**
     * io.input([file]) - Set/get default input file
     */
    private fun ioInput(
        args: List<LuaValue<*>>,
        fileSystem: FileSystem,
    ): LuaValue<*> {
        // When called without arguments, return stdin
        if (args.isEmpty()) {
            return stdinUserdata ?: LuaNil
        }

        // When called with argument, validate it's a FILE*
        val arg = args[0]
        when (arg) {
            is LuaTable -> {
                validateFileHandle(arg, "input")
                // TODO: Actually set this as default input
                return arg
            }
            is LuaString -> {
                // TODO: Open file by name and set as default input
                return arg
            }
            else -> {
                throw LuaRuntimeError("bad argument #1 to 'input' (string or FILE* expected)")
            }
        }
    }

    /**
     * io.output([file]) - Set/get default output file
     */
    private fun ioOutput(
        args: List<LuaValue<*>>,
        fileSystem: FileSystem,
    ): LuaValue<*> {
        // When called without arguments, return stdout
        if (args.isEmpty()) {
            return stdoutUserdata ?: LuaNil
        }

        // When called with argument, validate it's a FILE*
        val arg = args[0]
        when (arg) {
            is LuaTable -> {
                validateFileHandle(arg, "output")
                // TODO: Actually set this as default output
                return arg
            }
            is LuaString -> {
                // TODO: Open file by name and set as default output
                return arg
            }
            else -> {
                throw LuaRuntimeError("bad argument #1 to 'output' (string or FILE* expected)")
            }
        }
    }

    /**
     * io.flush() - Flush default output
     */
    private fun ioFlush(): LuaValue<*> {
        // For now, just return true (flushing not needed for print)
        return LuaBoolean.TRUE
    }

    /**
     * io.type(obj) - Check if object is a file handle
     */
    private fun ioType(args: List<LuaValue<*>>): LuaValue<*> {
        val obj = args.getOrNull(0)

        return when (obj) {
            is LuaUserdata<*> -> {
                // Check if it's a StandardStreamHandle or LuaFileHandle
                when (val handle = obj.value) {
                    is StandardStreamHandle -> {
                        if (handle.isClosed) {
                            LuaString("closed file")
                        } else {
                            LuaString("file")
                        }
                    }
                    is LuaFileHandle -> {
                        if (handle.isClosed) {
                            LuaString("closed file")
                        } else {
                            LuaString("file")
                        }
                    }
                    else -> LuaNil
                }
            }
            is LuaTable -> {
                // Check for regular file handle (legacy table-based)
                val fileHandle = getFileHandle(obj)
                if (fileHandle != null) {
                    return if (fileHandle.isClosed) {
                        LuaString("closed file")
                    } else {
                        LuaString("file")
                    }
                }

                // Check for standard stream handle (legacy table-based)
                val streamHandle = getStreamHandle(obj)
                if (streamHandle != null) {
                    return if (streamHandle.isClosed) {
                        LuaString("closed file")
                    } else {
                        LuaString("file")
                    }
                }

                LuaNil
            }
            else -> LuaNil
        }
    }
}
