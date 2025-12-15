package ai.tenum.cli

import ai.tenum.cli.commands.Lua
import ai.tenum.cli.commands.Luac
import ai.tenum.cli.commands.Tenum
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import okio.FileSystem

/**
 * Build the CLI instance. Exposed so tests can inject a fake FileSystem.
 */
fun createCli(
    fileSystem: FileSystem
): CliktCommand =
    Tenum()
        .subcommands(
            Lua(fileSystem),
            Luac(fileSystem),
        )
