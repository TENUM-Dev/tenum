package ai.tenum.cli.commands

import ai.tenum.lua.compiler.Compiler
import ai.tenum.lua.compiler.io.ChunkWriter
import ai.tenum.lua.compiler.model.Proto
import ai.tenum.lua.compiler.util.DebugInfoStripping
import ai.tenum.lua.lexer.Lexer
import ai.tenum.lua.parser.Parser
import ai.tenum.lua.runtime.LuaCompiledFunction
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
class Luac(
    private val fileSystem: FileSystem,
) : CliktCommand(name = "luac") {
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

        val sourcePath = inputFile.toPath()
        if (!fileSystem.exists(sourcePath)) {
            echo("luac: cannot open $inputFile: No such file or directory", err = true)
            return
        }

        val source =
            try {
                fileSystem.read(sourcePath) { readUtf8() }
            } catch (e: Exception) {
                echo("luac: ${e.message ?: "error reading input"}", err = true)
                return
            }

        val chunkName = "@$inputFile"
        val proto =
            try {
                compile(source, chunkName)
            } catch (e: Exception) {
                echo("luac: ${e.message ?: "compilation failed"}", err = true)
                return
            }

        val finalProto = if (stripDebug) stripDebugInfo(proto) else proto

        if (listChunk) {
            echo("Listing for $inputFile")
            listProto(finalProto)
        }

        val bytes =
            try {
                ChunkWriter.dump(finalProto)
            } catch (e: Exception) {
                echo("luac: ${e.message ?: "failed to write chunk"}", err = true)
                return
            }

        val outputPath = output.toPath()
        try {
            fileSystem.write(outputPath) {
                write(bytes)
            }
        } catch (e: Exception) {
            echo("luac: ${e.message ?: "error writing output"}", err = true)
            return
        }

        echo("luac: wrote ${bytes.size} bytes to $output")
    }

    private fun compile(
        source: String,
        sourceName: String,
    ): Proto {
        val lexer = Lexer(source, sourceName)
        val tokens = lexer.scanTokens()
        val parser = Parser(tokens)
        val ast = parser.parse()
        val compiler = Compiler(sourceFilename = sourceName)
        return compiler.compile(ast, name = sourceName)
    }

    private fun stripDebugInfo(proto: Proto): Proto = DebugInfoStripping.stripDebugInfo(proto)

    private fun listProto(
        proto: Proto,
        depth: Int = 0,
    ) {
        val indent = "  ".repeat(depth)
        val header =
            buildString {
                append(indent)
                append("Function ${proto.name.ifEmpty { "main" }} ")
                append("<${proto.source}:${proto.lineDefined},${proto.lastLineDefined}> ")
                append("(${proto.instructions.size} instructions)")
            }
        echo(header)

        if (proto.constants.isNotEmpty()) {
            echo("${indent}  constants (${proto.constants.size}):")
            proto.constants.forEachIndexed { idx, const ->
                val label = when (const) {
                    is LuaCompiledFunction -> "function"
                    else -> formatConstant(const)
                }
                echo("${indent}    [${idx}] $label")
            }
        }

        if (proto.instructions.isNotEmpty()) {
            echo("${indent}  instructions:")
            proto.instructions.forEachIndexed { idx, instr ->
                echo(
                    "${indent}    ${idx + 1}: ${instr.opcode} a=${instr.a} b=${instr.b} c=${instr.c}",
                )
            }
        }

        proto.constants
            .filterIsInstance<LuaCompiledFunction>()
            .forEach { nested ->
                listProto(nested.proto, depth + 1)
            }
    }

    private fun formatConstant(constant: Any?): String =
        when (constant) {
            is String -> "\"$constant\""
            is Number -> constant.toString()
            is Boolean -> constant.toString()
            null -> "nil"
            else -> constant.toString()
        }
}
