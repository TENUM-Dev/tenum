#!/usr/bin/env node
// Run the CLI with command line arguments (skip 'node' and script name)
import { } from '@js-joda/core';
import {execTenum} from "./tenum-cli";
const args = process.argv.slice(2);
console.log("haha", args);
const exitCode = execTenum(args)
process.exit(exitCode);