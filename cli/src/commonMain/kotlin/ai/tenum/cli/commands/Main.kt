package ai.tenum.cli.commands

import com.github.ajalt.clikt.core.NoOpCliktCommand

/**
 * Root command for the Tenum CLI. It delegates to subcommands.
 */
class Tenum :
    NoOpCliktCommand(
        name = "tenum",
    )
