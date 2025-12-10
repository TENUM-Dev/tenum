package ai.tenum.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import ai.tenum.lua.vm.LuaVmImpl
import ai.tenum.lua.runtime.*
import ai.tenum.lua.runtime.LuaTable
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * LuaK2 interpreter CLI command.
 * Supports:
 *  -e chunk        Execute inline chunk (can be repeated)
 *  -l name         Preload library/module via require (can be repeated)
 *  script.lua args Execute script file with positional args (arg table populated)
 */
class Lua(
    private val fileSystem: FileSystem,
) : CliktCommand(name = "lua") {
    private val inlineChunks by option("-e", "--execute", help = "Execute inline Lua chunk").multiple()
    private val preloadLibs by option("-l", "--library", help = "Preload library/module (require)").multiple()
    private val script by argument("SCRIPT").optional()
    private val scriptArgs by argument("ARGS").multiple()
    private val showVersion by option("-v", "--version", help = "Show version and exit").flag(default = false)
    private val debug by option("--debug").flag()

    override fun run() {
        if (showVersion) {
            echo("LuaK2 CLI (Lua 5.4.8 compatible)")
            return
        }

        // No actions: show help
        if (inlineChunks.isEmpty() && preloadLibs.isEmpty() && script == null) {
            echo(getFormattedHelp())
            return
        }

        val vm = LuaVmImpl(fileSystem = fileSystem)
        if (debug){
            vm.debugEnabled = true
        }

        // Preload libraries
        for (lib in preloadLibs) {
            val chunk = "require('${escape(lib)}')"
            if (!executeChunk(vm, chunk, printResult = false)) return
        }

        // If script provided, set up arg table BEFORE running inline chunks or script
        if (script != null) {
            val argsChunk = buildArgTableChunk(script!!, scriptArgs)
            if (!executeChunk(vm, argsChunk, printResult = false)) return
        }

        // Execute inline chunks in order
        for (chunk in inlineChunks) {
            if (!executeChunk(vm, chunk, printResult = true)) return
        }

        // Execute script file if provided
        if (script != null) {
            val path = script!!.toPath()
            if (!fileSystem.exists(path)) {
                echo("lua: cannot open ${script}: No such file or directory", err = true)
                return
            }
            val content = try {
                fileSystem.read(path) { readUtf8() }
            } catch (e: Exception) {
                echo("lua: ${e.message ?: "error reading file"}", err = true)
                return
            }
            executeChunk(vm, content, printResult = true)
        }
    }

    private fun executeChunk(vm: LuaVmImpl, source: String, printResult: Boolean): Boolean {
        return try {
            val value = vm.execute(source) // use public execute API
            if (printResult && value !is LuaNil) {
                echo(formatValue(value))
            }
            true
        } catch (e: Exception) {
            echo("lua: ${e.message ?: e::class.simpleName}", err = true)
            false
        }
    }

    private fun buildArgTableChunk(scriptName: String, args: List<String>): String {
        // Lua semantics: arg[0] = scriptName; arg[1].. = args
        // Build table literal then set arg[0]
        val entries = args.mapIndexed { idx, v -> "[${idx + 1}]='${escape(v)}'" }
        val body = entries.joinToString(",")
        return "arg={${body}};arg[0]='${escape(scriptName)}'"
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun formatValue(v: LuaValue<*>): String = when (v) {
        is LuaString -> v.value
        is LuaLong -> v.value.toString()
        is LuaDouble -> {
            // Match Lua: show integral without .0 when possible
            val longCandidate = v.value.toLong()
            if (v.value == longCandidate.toDouble()) longCandidate.toString() else v.value.toString()
        }
        is LuaBoolean -> if (v === LuaBoolean.TRUE) "true" else "false"
        is LuaTable -> "table:${v.hashCode()}"
        is LuaFunction -> "function:${v.hashCode()}"
        is LuaNil -> "nil"
        else -> v.toString()
    }
}