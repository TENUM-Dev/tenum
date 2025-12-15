package ai.tenum.cli

import com.github.ajalt.clikt.core.main
import okio.FileSystem

/**
 * Entry point for the Tenum CLI.
 * Exposes `tenum lua` and `tenum luac` subcommands.
 */
fun main(args: Array<String>) {
    createCli(FileSystem.SYSTEM).main(args)
}