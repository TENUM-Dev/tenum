package ai.tenum.lua.stdlib.internal

import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.compiler.io.ChunkReader
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.lexer.LexerException
import ai.tenum.lua.lexer.TokenType
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.parser.ParserException
import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCompiledFunction
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNativeFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.runtime.Upvalue
import ai.tenum.lua.stdlib.getMemoryUsageKB
import ai.tenum.lua.stdlib.restartGC
import ai.tenum.lua.stdlib.stopGC
import ai.tenum.lua.stdlib.triggerGC
import ai.tenum.lua.vm.debug.DebugTracer
import ai.tenum.lua.vm.errorhandling.LuaRuntimeError
import ai.tenum.lua.vm.library.LuaLibraryContext
import ai.tenum.lua.vm.library.RegisterGlobalCallback
import okio.Path.Companion.toPath

private const val MAX_CHUNK_NAME_LENGTH = 15
private const val BINARY_CHUNK_MARKER = '\u001b'

/**
 * Module loading and package management functions for BasicLib
 * Implements: load, loadfile, dofile, require, package.*, collectgarbage
 */
internal class BasicLibLoading(
    val debugTracer: DebugTracer,
) {
    /**
     * Formats a parser error in Lua's standard format:
     * [string "CHUNKNAME"]:LINE: MESSAGE near TOKEN
     */
    private fun formatLuaError(
        e: ParserException,
        chunkname: String,
    ): String {
        val shortenedChunk =
            if (chunkname.length > MAX_CHUNK_NAME_LENGTH) {
                chunkname.take(MAX_CHUNK_NAME_LENGTH) + "..."
            } else {
                chunkname
            }

        val tokenName =
            when (e.token.type) {
                TokenType.EOF -> "<eof>"
                TokenType.IDENTIFIER -> {
                    // For IDENTIFIER tokens, show the actual lexeme in quotes (e.g., 'error')
                    // This matches Lua 5.4 behavior for syntax errors with unexpected identifiers
                    if (e.token.lexeme.isNotEmpty()) {
                        "'${e.token.lexeme}'"
                    } else {
                        "<name>"
                    }
                }
                TokenType.STRING -> {
                    // For STRING tokens, show the actual lexeme (with quotes) in quotes (e.g., ''aa'' or '[[a]]')
                    // This matches Lua 5.4 behavior for syntax errors with unexpected string literals
                    if (e.token.lexeme.isNotEmpty()) {
                        "'${e.token.lexeme}'"
                    } else {
                        "<string>"
                    }
                }
                TokenType.NUMBER -> {
                    // For NUMBER tokens, show the actual lexeme in quotes (e.g., '1.000')
                    // This matches Lua 5.4 behavior for syntax errors with unexpected literals
                    if (e.token.lexeme.isNotEmpty()) {
                        "'${e.token.lexeme}'"
                    } else {
                        "<number>"
                    }
                }
                TokenType.ERROR ->
                    // ERROR tokens should show the malformed lexeme in quotes
                    // Non-printable characters are formatted as <\NUM> (e.g., <\1>, <\255>)
                    if (e.token.lexeme.isNotEmpty()) {
                        "'${formatNonPrintableChar(e.token.lexeme)}'"
                    } else {
                        "''"
                    }
                else -> {
                    // For all other tokens (operators, keywords, etc.), show the actual lexeme in quotes
                    // This matches Lua 5.4 behavior for syntax errors with unexpected tokens
                    if (e.token.lexeme.isNotEmpty()) {
                        "'${e.token.lexeme}'"
                    } else {
                        // Fallback to lowercase token type name if no lexeme
                        e.token.type.name
                            .lowercase()
                    }
                }
            }

        return "[string \"$shortenedChunk\"]:${e.token.line}: ${e.message} near $tokenName"
    }

    /**
     * Format a lexer error into a Lua-compatible error message.
     * LexerException messages already contain the "near 'token'" part.
     */
    private fun formatLuaError(
        e: LexerException,
        chunkname: String,
    ): String {
        val shortenedChunk =
            if (chunkname.length > MAX_CHUNK_NAME_LENGTH) {
                chunkname.take(MAX_CHUNK_NAME_LENGTH) + "..."
            } else {
                chunkname
            }

        return "[string \"$shortenedChunk\"]:${e.line}: ${e.message}"
    }

    /**
     * Handles compilation errors from load/loadfile operations.
     * Returns (nil, error_message) pair as required by Lua semantics.
     *
     * @param chunkname The name of the chunk being compiled (for error messages)
     * @param defaultErrorMsg Fallback error message for generic exceptions without a message
     * @param block The compilation operation to execute
     * @return List containing either [LuaCompiledFunction] on success or [LuaNil, LuaString(error)] on failure
     */
    private inline fun handleCompilationError(
        chunkname: String,
        defaultErrorMsg: String = "syntax error",
        block: () -> List<LuaValue<*>>,
    ): List<LuaValue<*>> =
        try {
            block()
        } catch (e: LexerException) {
            // Format lexer errors with proper location
            val errorMsg = formatLuaError(e, chunkname)
            listOf(LuaNil, LuaString(errorMsg))
        } catch (e: ParserException) {
            val errorMsg = formatLuaError(e, chunkname)
            listOf(LuaNil, LuaString(errorMsg))
        } catch (e: Exception) {
            // Format compiler errors (e.g., const assignment violations) with proper location
            val errorMsg =
                if (e.message != null) {
                    val shortenedChunk =
                        if (chunkname.length > MAX_CHUNK_NAME_LENGTH) {
                            chunkname.take(MAX_CHUNK_NAME_LENGTH) + "..."
                        } else {
                            chunkname
                        }
                    "[string \"$shortenedChunk\"]:1: ${e.message}"
                } else {
                    defaultErrorMsg
                }
            listOf(LuaNil, LuaString(errorMsg))
        }

    /**
     * Formats non-printable characters in the Lua 5.4 style: <\NUM>
     * where NUM is the decimal value of the character.
     * For example: char(1) -> <\1>, char(127) -> <\127>
     */
    private fun formatNonPrintableChar(lexeme: String): String {
        // Lua considers characters with codes 0-31 and 127-255 as non-printable
        // and displays them as <\NUM> where NUM is the decimal character code
        if (lexeme.length == 1) {
            val ch = lexeme[0]
            val code = ch.code
            // Check if character is non-printable (control characters or extended ASCII)
            if (code < 32 || code == 127 || code > 127) {
                return "<\\$code>"
            }
        }
        return lexeme
    }

    /**
     * Validates mode string for binary/text chunk loading
     * Returns error message if mode is invalid, null if valid
     */
    private fun validateMode(
        mode: String,
        isBinary: Boolean,
    ): String? {
        val allowText = mode.contains('t')
        val allowBinary = mode.contains('b')

        return when {
            isBinary && !allowBinary -> "attempt to load a binary chunk (mode is '$mode')"
            !isBinary && !allowText -> "attempt to load a text chunk (mode is '$mode')"
            else -> null
        }
    }

    /**
     * Strips shebang line (lines starting with #) from file content.
     * This matches Lua 5.4 behavior where files can start with #!/path/to/lua
     * but load() does not support this syntax.
     */
    private fun stripShebang(content: String): String {
        if (content.startsWith("#")) {
            val newlineIndex = content.indexOf('\n')
            return if (newlineIndex >= 0) {
                content.substring(newlineIndex + 1)
            } else {
                // Entire file is just the shebang line, return empty
                ""
            }
        }
        return content
    }

    /**
     * Compiles Lua source or binary chunk to Proto
     */
    private fun compileChunk(
        content: String,
        chunkname: String = "=(load)",
        hasVararg: Boolean = false,
    ): Proto {
        val isBinary = content.isNotEmpty() && content[0] == BINARY_CHUNK_MARKER

        return if (isBinary) {
            val bytes = content.map { it.code.toByte() }.toByteArray()
            ChunkReader.load(bytes)
                ?: throw IllegalStateException("error loading binary chunk")
        } else {
            val lexer = Lexer(content)
            val tokens = lexer.scanTokens()
            val parser = Parser(tokens)
            val ast = parser.parse()
            val compiler = Compiler(sourceFilename = chunkname, debugEnabled = debugTracer.debugEnabled)
            compiler.compile(ast, hasVararg = hasVararg)
        }
    }

    fun registerFunctions(
        registerGlobal: RegisterGlobalCallback,
        context: LuaLibraryContext,
    ) {
        // load(chunk [, chunkname [, mode [, env]]]) - compiles Lua code to function
        registerGlobal(
            "load",
            LuaNativeFunction { args ->
                loadImpl(args, context)
            },
        )

        // loadfile([filename [, mode [, env]]]) - loads file and compiles to function
        registerGlobal(
            "loadfile",
            LuaNativeFunction { args ->
                loadfileImpl(args, context)
            },
        )

        // dofile([filename]) - loads and executes file
        registerGlobal(
            "dofile",
            LuaNativeFunction { args ->
                dofileImpl(args, context)
            },
        )

        // require(modname) - loads module with caching
        registerGlobal(
            "require",
            LuaNativeFunction { args ->
                requireImpl(args, context)
            },
        )

        // collectgarbage([opt [, arg]]) - garbage collector control
        registerGlobal(
            "collectgarbage",
            LuaNativeFunction { args ->
                collectgarbageImpl(args)
            },
        )

        // Setup package table
        val packageTable = LuaTable()

        // package.path - semicolon-separated search paths for Lua modules
        packageTable[LuaString("path")] = LuaString(context.packagePath.joinToString(";"))

        // package.cpath - C module search paths (not used in multiplatform, empty string)
        packageTable[LuaString("cpath")] = LuaString("")

        // package.config - configuration string describing directory and path separators
        // Format: dir_sep \n path_sep \n template_sep \n wildcards \n ignore_mark
        packageTable[LuaString("config")] = LuaString("/\n;\n?\n!\n-")

        // package.loaded - cache of loaded modules (reference to VM's loadedModules)
        val loadedTable = LuaTable()
        context.loadedModules.forEach { (name, value) ->
            loadedTable[LuaString(name)] = value
        }
        packageTable[LuaString("loaded")] = loadedTable

        // package.preload - table of loader functions for modules
        packageTable[LuaString("preload")] = LuaTable()

        // package.searchpath(name, path [, sep [, rep]]) - searches for file in path
        packageTable[LuaString("searchpath")] =
            LuaNativeFunction { args ->
                searchpathImpl(args, context)
            }

        registerGlobal("package", packageTable)
    }

    /**
     * load(chunk [, chunkname [, mode [, env]]]) - compiles Lua code string or reader function to function
     * Returns: compiled function or (nil, error message)
     */
    private fun loadImpl(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val chunk = args.getOrNull(0) ?: return listOf(LuaNil, LuaString("chunk expected"))

        // Get chunk source code - either directly from string or by calling reader function
        val sourceCode: String =
            when (chunk) {
                is LuaString -> chunk.value
                is LuaFunction -> {
                    // Call reader function repeatedly to build source
                    val builder = StringBuilder()
                    while (true) {
                        val result =
                            try {
                                chunk.call(emptyList())
                            } catch (e: Exception) {
                                return listOf(LuaNil, LuaString("error calling chunk reader: ${e.message}"))
                            }

                        // Check what the reader returned
                        val part = result.firstOrNull()
                        when {
                            part == null || part is LuaNil -> break // nil or no return = end of chunk
                            part is LuaString -> {
                                if (part.value.isEmpty()) break // empty string = end of chunk
                                builder.append(part.value)
                            }
                            else -> return listOf(LuaNil, LuaString("reader function must return a string"))
                        }
                    }
                    builder.toString()
                }
                else -> return listOf(LuaNil, LuaString("chunk must be a string or function"))
            }

        val defaultChunkname = if (chunk is LuaString) chunk.value else "=(load)"
        val chunkname = (args.getOrNull(1) as? LuaString)?.value ?: defaultChunkname
        val mode = (args.getOrNull(2) as? LuaString)?.value ?: "bt"
        val hasExplicitEnv = args.size >= 4
        val env = if (hasExplicitEnv) args[3] else null

        val isBinary = sourceCode.isNotEmpty() && sourceCode[0] == BINARY_CHUNK_MARKER

        validateMode(mode, isBinary)?.let { errorMsg ->
            return listOf(LuaNil, LuaString(errorMsg))
        }

        return handleCompilationError(chunkname, "syntax error") {
            val proto = compileChunk(sourceCode, chunkname, hasVararg = true)

            val compiledFunc = LuaCompiledFunction(proto)

            // Initialize upvalues based on proto.upvalueInfo
            // For binary chunks loaded from string.dump, we need to create placeholder upvalues
            // that can be set later via debug.setupvalue
            for (upvalInfo in proto.upvalueInfo) {
                // Create a closed upvalue with nil value as placeholder
                val upvalue = Upvalue(closedValue = LuaNil)
                upvalue.isClosed = true
                compiledFunc.upvalues.add(upvalue)
            }

            // Override _ENV upvalue if explicitly provided or inherited from caller
            val envUpvalueIndex = proto.upvalueInfo.indexOfFirst { it.name == "_ENV" }
            if (envUpvalueIndex >= 0) {
                if (hasExplicitEnv) {
                    // Environment was explicitly provided (even if nil)
                    if (env != null && env !is LuaNil) {
                        // Use non-nil environment
                        val envUpvalue = Upvalue(closedValue = env)
                        envUpvalue.isClosed = true
                        compiledFunc.upvalues[envUpvalueIndex] = envUpvalue
                    }
                    // If env is nil, leave the upvalue as nil placeholder
                } else {
                    // No environment parameter provided - inherit from caller
                    val callerEnv = context.getCurrentEnv?.invoke()
                    if (callerEnv != null) {
                        compiledFunc.upvalues[envUpvalueIndex] = callerEnv
                    }
                }
            }

            compiledFunc.value = { args ->
                context.executeProto?.invoke(compiledFunc.proto, args, compiledFunc.upvalues) ?: emptyList()
            }

            listOf(compiledFunc)
        }
    }

    /**
     * loadfile([filename [, mode [, env]]]) - loads file and compiles to function
     */
    private fun loadfileImpl(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val filename = args.getOrNull(0) as? LuaString
        if (filename == null) {
            return listOf(LuaNil, LuaString("filename expected"))
        }

        val mode = (args.getOrNull(1) as? LuaString)?.value ?: "bt"
        val env = args.getOrNull(2)

        return handleCompilationError(filename.value, "error loading file") {
            val path = filename.value.toPath()
            if (!context.fileSystem.exists(path)) {
                return listOf(LuaNil, LuaString("cannot open ${filename.value}: No such file or directory"))
            }

            var content = context.fileSystem.read(path) { readUtf8() }

            // Strip shebang if present (Lua 5.4 behavior for files)
            content = stripShebang(content)

            val isBinary = content.isNotEmpty() && content[0] == BINARY_CHUNK_MARKER

            validateMode(mode, isBinary)?.let { errorMsg ->
                return listOf(LuaNil, LuaString(errorMsg))
            }

            val proto = compileChunk(content, filename.value, hasVararg = true)

            val compiledFunc = LuaCompiledFunction(proto)

            if (env != null && env !is LuaNil) {
                val envUpvalue = Upvalue(closedValue = env)
                envUpvalue.isClosed = true
                compiledFunc.upvalues.add(envUpvalue)
            }

            compiledFunc.value = { args ->
                context.executeProto?.invoke(compiledFunc.proto, args, compiledFunc.upvalues) ?: emptyList()
            }

            listOf(compiledFunc)
        }
    }

    /**
     * dofile([filename]) - loads and executes file immediately
     */
    private fun dofileImpl(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val filename = args.getOrNull(0) as? LuaString
        if (filename == null) {
            throw RuntimeException("filename expected")
        }

        val path = filename.value.toPath()
        if (!context.fileSystem.exists(path)) {
            throw RuntimeException("cannot open ${filename.value}: No such file or directory")
        }

        var content = context.fileSystem.read(path) { readUtf8() }

        // Strip shebang if present (Lua 5.4 behavior for files)
        content = stripShebang(content)

        return listOf(context.executeChunk(content, filename.value))
    }

    /**
     * package.searchpath(name, path [, sep [, rep]]) - searches for file in path
     */
    private fun searchpathImpl(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val name =
            (args.getOrNull(0) as? LuaString)?.value
                ?: throw RuntimeException("bad argument #1 to 'searchpath' (string expected)")

        val path =
            (args.getOrNull(1) as? LuaString)?.value
                ?: throw RuntimeException("bad argument #2 to 'searchpath' (string expected)")

        val sep = (args.getOrNull(2) as? LuaString)?.value ?: "."
        val rep = (args.getOrNull(3) as? LuaString)?.value ?: "/"

        val searchName = name.replace(sep, rep)
        val templates = path.split(";")
        val attemptedPaths = mutableListOf<String>()

        for (template in templates) {
            val filePath = template.replace("?", searchName)
            attemptedPaths.add(filePath)

            if (filePath.isEmpty()) continue

            try {
                val pathObj = filePath.toPath()
                if (context.fileSystem.exists(pathObj)) {
                    return listOf(LuaString(filePath))
                }
            } catch (e: Exception) {
                // Invalid path, continue
            }
        }

        val errorMessage = attemptedPaths.joinToString("\n") { "\tno file '$it'" }
        return listOf(LuaNil, LuaString(errorMessage))
    }

    /**
     * require(modname) - loads module with caching
     */
    private fun requireImpl(
        args: List<LuaValue<*>>,
        context: LuaLibraryContext,
    ): List<LuaValue<*>> {
        val modname = args.getOrNull(0) as? LuaString
        if (modname == null) {
            throw RuntimeException("module name expected")
        }

        val moduleName = modname.value

        context.loadedModules[moduleName]?.let { cached ->
            return listOf(cached)
        }

        // Check package.preload
        val packageTable = context.getGlobal("package") as? LuaTable
        if (packageTable != null) {
            val preloadTable = packageTable[LuaString("preload")] as? LuaTable
            if (preloadTable != null) {
                val loader = preloadTable[LuaString(moduleName)]
                if (loader is LuaFunction) {
                    val result = context.callFunction(loader, listOf(modname))
                    val moduleValue = result.firstOrNull() ?: LuaBoolean.TRUE
                    context.loadedModules[moduleName] = moduleValue
                    return listOf(moduleValue)
                }
            }
        }

        // Search filesystem
        val runtimePackageTable = context.getGlobal("package") as? LuaTable
        val runtimePathString: String =
            if (runtimePackageTable != null) {
                val pathVal = runtimePackageTable[LuaString("path")]
                when (pathVal) {
                    is LuaString -> pathVal.value
                    null -> context.packagePath.joinToString(";")
                    else -> throw RuntimeException("package.path must be a string")
                }
            } else {
                context.packagePath.joinToString(";")
            }

        var foundPath: okio.Path? = null
        var content: String? = null

        val templates = runtimePathString.split(";")
        val errorLines = mutableListOf<String>()
        errorLines.add("\tno field package.preload['$moduleName']")

        for (template in templates) {
            val candidate = template.replace("?", moduleName)
            errorLines.add("\tno file '$candidate'")
            try {
                val p = candidate.toPath()
                if (context.fileSystem.exists(p)) {
                    foundPath = p
                    content = context.fileSystem.read(p) { readUtf8() }
                    break
                }
            } catch (e: Exception) {
                // Invalid template
            }
        }

        // Include C module paths in error
        if (runtimePackageTable != null) {
            val cpathVal = runtimePackageTable[LuaString("cpath")]
            if (cpathVal is LuaString && cpathVal.value.isNotEmpty()) {
                val ctemplates = cpathVal.value.split(";")
                for (ct in ctemplates) {
                    val ccandidate = ct.replace("?", moduleName)
                    errorLines.add("\tno file '$ccandidate'")
                }
            }
        }

        if (content == null) {
            throw RuntimeException("module '$moduleName' not found:\n" + errorLines.joinToString("\n"))
        }

        // Strip shebang if present (Lua 5.4 behavior for files)
        content = stripShebang(content)

        val sourceName = "@$foundPath"
        val result = context.executeChunk(content, sourceName)
        val moduleValue = if (result is LuaNil) LuaBoolean.TRUE else result

        context.loadedModules[moduleName] = moduleValue
        return listOf(moduleValue)
    }

    /**
     * collectgarbage([opt [, arg]]) - garbage collector control
     */
    private fun collectgarbageImpl(args: List<LuaValue<*>>): List<LuaValue<*>> {
        // Validate first argument must be string or nil
        val firstArg = args.getOrNull(0)
        if (firstArg != null && firstArg !is LuaString && firstArg !is LuaNil) {
            val typeName = firstArg.type().name.lowercase()
            throw LuaRuntimeError("bad argument #1 to 'collectgarbage' (string expected, got $typeName)")
        }

        val opt = (firstArg as? LuaString)?.value ?: "collect"

        return when (opt) {
            "collect" -> {
                triggerGC()
                listOf(LuaNumber.of(0))
            }
            "count" -> {
                val memoryKB = getMemoryUsageKB()
                listOf(LuaNumber.of(memoryKB))
            }
            "stop" -> {
                stopGC()
                listOf(LuaNumber.of(0))
            }
            "restart" -> {
                restartGC()
                listOf(LuaNumber.of(0))
            }
            "step" -> {
                triggerGC()
                listOf(LuaBoolean.TRUE)
            }
            "isrunning" -> listOf(LuaBoolean.TRUE)
            "setpause", "setstepmul" -> listOf(LuaNumber.of(200))
            else -> throw RuntimeException("collectgarbage: invalid option '$opt'")
        }
    }
}
