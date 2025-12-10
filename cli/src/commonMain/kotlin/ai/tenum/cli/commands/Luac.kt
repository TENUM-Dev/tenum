package ai.tenum.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * LuaK2 bytecode compiler CLI command (luac).
 * Compiles a Lua source file into a binary chunk.
 */
class Luac() : CliktCommand(name = "luac") {
    private val input by argument("INPUT", help = "Lua source file to compile").optional()
    private val output by option("-o", "--output", help = "Output chunk file").default("luac.out")
    private val stripDebug by option("-s", "--strip", help = "Strip debug information").flag(default = false)
    private val showVersion by option("-v", "--version", help = "Show version and exit").flag(default = false)
    private val listChunk by option("-l", "--list", help = "List compiled chunk to stdout").flag(default = false)

    override fun run() {
        if (showVersion) {
            echo("LuaK2 Compiler (Lua 5.4.8 compatible)")
            return
        }

        val inputFile =
            input ?: run {
                echo(getFormattedHelp())
                return
            }
    }

}
