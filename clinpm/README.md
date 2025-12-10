# TENUM CLI (npm package)

Node.js integration package for the TENUM Lua interpreter and CLI tools.

## Overview

This package provides npm/Node.js integration for TENUM, making it easy to use the Lua 5.4.8 interpreter and CLI tools from JavaScript/TypeScript projects. It bundles the Kotlin/JS compiled output with TypeScript wrappers for seamless Node.js integration.

## Installation

### Published Package (when available)

```bash
npm install -g @tenum/tenum
```

### Local Development Build

```bash
# From repository root
./gradlew :clinpm:installLocal
```

This Gradle task will:
- Build the Kotlin/JS production artifacts for `:cli`
- Run `npm run build:bundle:install` inside `clinpm` (TypeScript build, esbuild bundle, and `npm install -g`)
- Install the `tlua` and `tenum` commands globally

## Usage

### Lua Interpreter (`tlua`)

Run Lua scripts directly from the command line:

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
- `-v, --version` - Show version and exit
- `script.lua [args...]` - Execute script file with arguments


## Architecture

This package bridges Kotlin Multiplatform and Node.js:

```
┌──────────────┐     ┌────────────┐     ┌──────────────┐
│ Kotlin/JS    │ =>  │ TypeScript │ =>  │ esbuild      │
│ (cli module) │     │ Wrapper    │     │ Bundle       │
└──────────────┘     └────────────┘     └──────────────┘
                                              ↓
                                         npm package
```

### Build Process

1. **Kotlin Compilation** - CLI module compiles to JavaScript
3. **Bundling** - esbuild creates optimized bundle
4. **Package** - npm package with executable binaries

### Structure

```
clinpm/
├── src/
│   ├── index.ts          # Main Node.js entry point
│   ├── lua.ts            # Lua VM wrapper
│   ├── luak-cli.d.ts     # TypeScript definitions
│   └── luak-cli.js       # Kotlin/JS output (generated)
├── dist/                 # Built artifacts
├── package.json
├── tsconfig.json
└── README.md
```

## Development

### Building

```bash
# Build TypeScript and bundle
npm run build

# Bundle for production
npm run build:bundle

# Install globally for testing
npm run build:bundle:install
```

### Testing

```bash
# Test installed CLI
tlua -e "print('test')"
```

## Part of TENUM

This is the npm integration layer for [TENUM](../README.md) – a full-stack Lua ecosystem. The package makes TENUM accessible to the JavaScript/TypeScript ecosystem while maintaining full Lua compatibility.

Related modules:
- [lua](../lua/README.md) - Core Lua interpreter
- [cli](../cli/README.md) - Command-line interface


## License

Part of the TENUM project. See root [LICENSE](../LICENSE.md) for details.
