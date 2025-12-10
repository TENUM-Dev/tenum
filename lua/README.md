# Lua 5.4.8 Interpreter

A complete Lua 5.4.8 interpreter implementation in Kotlin Multiplatform, powering the [TENUM](../README.md) full-stack ecosystem.

## Overview

LuaK is a full implementation of the Lua 5.4 programming language, written in Kotlin and targeting multiple platforms (JVM, JS, Native) through Kotlin Multiplatform. The project follows a classic interpreter architecture: Lexer → Parser → Compiler → VM.

## Architecture

```
┌─────────┐    ┌─────────┐    ┌──────────┐    ┌──────┐
│ Lexer   │ => │ Parser  │ => │ Compiler │ => │  VM  │
└─────────┘    └─────────┘    └──────────┘    └──────┘
   Tokens         AST          Bytecode      Execution
```