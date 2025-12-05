# TENUM CLI

Command-line interface for the TENUM Lua full-stack ecosystem.

## Overview

The TENUM CLI provides tools for creating, running, and managing TENUM applications. Built with Kotlin Multiplatform, it runs on JVM and compiles to JavaScript for Node.js integration.

## Features

- 🚀 **Quick Start** - Scaffold new TENUM apps in seconds
- 🔧 **Development Tools** - Run and test your apps locally
- 📦 **Deployment** - Deploy to TENUM cloud or export containers
- 🎯 **Multi-Platform** - JVM and Node.js support

## Installation

### From npm (when published)

```bash
npm install -g @tenum/cli
```

### Local Development Build

```bash
# From repository root
./gradlew :cli:build
```

## Usage

### Running Lua Scripts

The CLI provides direct Lua execution:

```bash
# Execute inline Lua code
tlua -e "print('Hello from TENUM')"

# Run a Lua script
tlua script.lua

# Run with arguments
tlua app.lua arg1 arg2
```

### Command Options

- `-e <chunk>` - Execute inline Lua chunk (can be repeated)
- `-l <name>` - Preload library/module via require (can be repeated)
- `-v, --version` - Show version information
- `script.lua [args...]` - Execute script file with arguments (available via `arg` table)

### Creating TENUM Apps

```bash
# Create a new TENUM app (coming soon)
tenum new my-app

# Run development server (coming soon)
tenum dev

# Build for production (coming soon)
tenum build

# Deploy to TENUM cloud (coming soon)
tenum deploy
```

## Architecture

The CLI is built with:

- **[Clikt](https://ajalt.github.io/clikt/)** - Multiplatform command-line parser
- **[LuaK](../luak/)** - Lua 5.4.8 interpreter (Kotlin Multiplatform)
- **Kotlin/JS** - Node.js integration via JavaScript compilation

### Structure

```
cli/
├── src/
│   ├── commonMain/kotlin/ai/tenum/cli/
│   │   ├── Main.kt
│   │   └── commands/
│   ├── jsMain/kotlin/
│   │   └── Export.kt          # Node.js entry point
│   └── jvmTest/kotlin/
│       └── ai/tenum/cli/
│           └── commands/
└── build.gradle.kts
```

## Building

```bash
# Build all CLI artifacts
./gradlew :cli:build

# Run JVM tests
./gradlew :cli:jvmTest

# Build JavaScript bundle
./gradlew :cli:jsProductionExecutableCompileSync
```

## Development

The CLI is designed to be extended with new commands. To add a command:

1. Create a new command class extending `CliktCommand`
2. Register it in the main command hierarchy
3. Add tests in `jvmTest`

Example:

```kotlin
class MyCommand : CliktCommand(name = "mycommand") {
    override fun run() {
        echo("Hello from my command!")
    }
}
```

## Integration with Node.js

The CLI compiles to JavaScript and can be used directly from Node.js applications. See [clinpm](../clinpm/) for the npm packaging and distribution.

## Part of TENUM

The CLI is the developer interface for [TENUM](../README.md) – a full-stack Lua ecosystem. It provides:

- Local development environment
- Script execution and testing
- App scaffolding and deployment
- Integration with TENUM cloud runtime

## Contributing

See [Development Guidelines](../.github/copilot-instructions.md) for coding standards and workflow.

## License

Part of the TENUM project. See root LICENSE for details.
