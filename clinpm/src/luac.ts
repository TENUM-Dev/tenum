#!/usr/bin/env node
// Run the CLI with command line arguments (skip 'node' and script name)
import { } from '@js-joda/core';
import {execLuac} from "./tenum-cli";
const args = process.argv.slice(2);

const exitCode = execLuac(args)
process.exit(exitCode);