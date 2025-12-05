# TENUM CLI (npm package)

Node.js integration package for the TENUM Lua interpreter and CLI tools.

## Overview

This package provides npm/Node.js integration for TENUM, making it easy to use the Lua 5.4.8 interpreter and CLI tools from JavaScript/TypeScript projects. It bundles the Kotlin/JS compiled output with TypeScript wrappers for seamless Node.js integration.

## Installation

### Published Package (when available)

```bash
npm install -g @tenum/cli
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

### Using from Node.js

```typescript
import { LuaK } from '@tenum/cli';

// Create Lua VM instance
const lua = new LuaK();

// Execute Lua code
const result = lua.execute('return 1 + 1');
console.log(result); // 2

// Load and run scripts
lua.executeFile('./script.lua');
```

## Features

- ✅ Full Lua 5.4.8 compatibility
- ✅ Standard library support
- ✅ Script arguments via `arg` table
- ✅ Module loading with `require()`
- ✅ Error handling and stack traces
- ✅ TypeScript definitions included
- 🚧 TENUM app framework (coming soon)

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
2. **TypeScript Wrapper** - `src/index.ts` provides Node.js interface
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
# Run test script
npm test

# Test installed CLI
tlua -e "print('test')"
```

## Integration with TENUM

This package provides the runtime foundation for TENUM apps on Node.js. It enables:

- Server-side rendering of TENUM UI components
- Backend API execution
- CLI tools for development and deployment
- NPM ecosystem integration

## Part of TENUM

This is the npm integration layer for [TENUM](../README.md) – a full-stack Lua ecosystem. The package makes TENUM accessible to the JavaScript/TypeScript ecosystem while maintaining full Lua compatibility.

Related modules:
- [luak](../luak/) - Core Lua interpreter
- [cli](../cli/) - Command-line interface

## Contributing

See [Development Guidelines](../.github/copilot-instructions.md) for coding standards and workflow.

## License

Part of the TENUM project. See root LICENSE for details.
