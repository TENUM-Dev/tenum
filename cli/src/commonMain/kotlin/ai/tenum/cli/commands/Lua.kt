package ai.tenum.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * LuaK2 interpreter CLI command.
 * Supports:
 *  -e chunk        Execute inline chunk (can be repeated)
 *  -l name         Preload library/module via require (can be repeated)
 *  script.lua args Execute script file with positional args (arg table populated)
 */
class Lua() : CliktCommand(name = "lua") {
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
    }
}