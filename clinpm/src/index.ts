#!/usr/bin/env node

import * as path from 'path';
import * as Module from 'module';

// Add clinpm's node_modules to the module search paths
const clinpmNodeModules = path.resolve(__dirname, '../node_modules');
const originalResolveFilename = (Module as any)._resolveFilename;

(Module as any)._resolveFilename = function(request: string, parent: any, isMain: boolean) {
  // Try original resolution first
  try {
    return originalResolveFilename(request, parent, isMain);
  } catch (err: any) {
    // If not found, try from clinpm's node_modules
    if (err.code === 'MODULE_NOT_FOUND') {
      try {
        return require.resolve(request, { paths: [clinpmNodeModules] });
      } catch (e) {
        // If still not found, throw original error
        throw err;
      }
    }
    throw err;
  }
};

// Resolve path to the Kotlin/JS build directory
const kotlinBuildDir = path.resolve(__dirname, '../../cli/build/compileSync/js/main/productionLibrary/kotlin');

// Import the compiled Kotlin/JS CLI module
const cliModule = require(path.join(kotlinBuildDir, 'luak-cli.js'));

// Run the CLI with command line arguments (skip 'node' and script name)
const args = process.argv.slice(2);

// Execute the main function
// The exact export name may vary - try different patterns
if (typeof cliModule === 'function') {
    cliModule(args);
} else if (cliModule.main) {
    cliModule.main(args);
} else if (cliModule.execLua) {
    const exitCode = cliModule.execLua(args);
    process.exit(exitCode);
} else {
    console.error('Unable to find CLI entry point in luak-cli module');
    console.error('Available exports:', Object.keys(cliModule));
    process.exit(1);
}

