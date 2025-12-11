package ai.tenum.cli

import ai.tenum.cli.commands.Lua
import ai.tenum.cli.commands.Luac
import ai.tenum.cli.commands.Tenum
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import okio.FileSystem

/**
 * Entry point for the Tenum CLI.
 * Exposes `tenum lua` and `tenum luac` subcommands.
 */
fun main(args: Array<String>) {
    createCli().main(args)
}

/**
 * Build the CLI instance. Exposed so tests can inject a fake FileSystem.
 */
fun createCli(
    fileSystem: FileSystem = createFileSystem(),
): CliktCommand =
    Tenum()
        .subcommands(
            Lua(fileSystem),
            Luac(fileSystem),
        )

expect fun createFileSystem(): FileSystem
