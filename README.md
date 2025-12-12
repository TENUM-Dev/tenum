# TENUM – Lua Full Stack Ecosystem

**Build Full-Stack Apps in Lua**

Build apps faster. Share them easily. Get paid fairly.

[![Discord](https://img.shields.io/badge/Discord-Join%20us-5865F2?logo=discord&logoColor=white)](https://discord.gg/nZwkhNj8)
[![GitHub](https://img.shields.io/badge/GitHub-Star%20us-181717?logo=github)](https://github.com/TENUM-Dev/tenum)

---

## Why TENUM? Our Vision!

Modern app development is fast—until it isn't. Boilerplate, infrastructure, and glue code slow everyone down, and open-source maintainers rarely share in the value they enable. **TENUM changes that.**

### One language, front to back

Write full-stack apps in Lua — UI + backend logic, one codebase.

### Zero-to-running in minutes

Local dev with the CLI; optional cloud runtime or host yourself — no DevOps friction.

### Auth built-in

Sessions/JWT, social login, access control decorators.

### Real-time Apps by default

Your app's state auto-syncs instantly across every browser and device.

### Community at the center

An open ecosystem designed to grow with—and reward—its builders.

### Like npm—but fair

We don't count downloads. We attribute actual runtime usage and share revenue where value is created.

--

## Project Structure

This repository contains:

### 🔧 **[lua/](lua/)** – Lua 5.4.8 Interpreter
A complete Lua 5.4 interpreter written in Kotlin Multiplatform. This is the runtime foundation that executes your Lua code across JVM, JS, and Native targets.

- Full Lua 5.4.8 compatibility (in progress)
- Register-based VM with domain-driven architecture
- Standard library support
- See [lua/README.md](lua/README.md) for details

### 📦 **[cli/](cli/)** – TENUM CLI
Command-line tools for creating, running, and deploying TENUM apps.

### 🚀 **[clinpm/](clinpm/)** – NPM Integration
Node.js bridge for seamless integration with JavaScript tooling.

### ⚡ **[performance/](performance/)** – Performance Benchmarks
Performance testing suite to ensure TENUM stays fast.

---

## Getting Started

### Prerequisites

- JDK 11 or higher
- Gradle (wrapper included)

### Build

```bash
# Build all modules
./gradlew build

# Run Lua interpreter tests
./gradlew :lua:jvmTest

# Install local CLI tools
./gradlew :clinpm:installLocal
```

### Or Install via npm

```bash
npm install -g @tenum-dev/tenum
```

### Run a Lua script

```bash
# After building
tlua your-script.lua
tluac your-script.lua
```

---

## License & Revenue Share (TSALv1)

We use an [**TSALv1 license**](LICENSE.md) for the hosting/runtime components so we can measure real usage and fairly share revenue with contributors, while keeping the developer tooling open for community contributions. Controlling hosting lets us guarantee accurate attribution and payouts.

### Own your code

Keep your repos; use our hosting only if you want it.

> *"Open source powers everything we do, but maintainers rarely share in the value they create. TENUM's mission is to fix that."*  
> — Federico and Jochen, founders of TENUM

---

## Our Roadmap

### Now
- ✅ Kotlin Multiplatform Lua interpreter
- ✅ Continious diployment to npm
- 🚧 Fix lua [Test suite](https://www.lua.org/tests/)
  - ✅ **api.lua** - API compatibility tests
  - ✅ **attrib.lua** - Attribute and metamethod tests
  - ✅ **big.lua** - Large number handling
  - ✅ **bitwise.lua** - Bitwise operations
  - ✅ **bwcoercion.lua** - Bitwise coercion
  - ✅ **calls.lua** - Function calls (partial - bytecode differences)
  - ✅ **closure.lua** - Closures and upvalues
  - ✅ **constructs.lua** - Language constructs
  - ✅ **db.lua** - Debug library (partial - instruction count differences)
  - ✅ **errors.lua** - Error handling (partial - parser limit tests skipped)
  - ✅ **literals.lua** - Literal values (partial - string internalization differences)
  - ✅ **main.lua** - Main entry point tests
  - ✅ **sort.lua** - Table sorting
  - ✅ **strings.lua** - String operations (partial - formatting differences)
  - ✅ **tpack.lua** - Table pack/unpack
  - ✅ **tracegc.lua** - Garbage collection traces
  - ✅ **vararg.lua** - Variable arguments
  - ✅ **verybig.lua** - Very large number operations
  - ⏸️ **coroutine.lua** - Coroutines (failing at eqtab check)
  - ❌ **code.lua** - Code generation tests
  - ❌ **cstack.lua** - C stack tests (not applicable)
  - ❌ **events.lua** - Event handling
  - ❌ **files.lua** - File I/O operations
  - ❌ **gc.lua** - Garbage collection (not implemented)
  - ❌ **gengc.lua** - Generational GC (not implemented)
  - ✅ **goto.lua** - Goto statements
  - ❌ **heavy.lua** - Heavy computation tests
  - ❌ **locals.lua** - Local variable tests
  - ✅ **math.lua** - Math library
  - ❌ **nextvar.lua** - Next variable iteration
  - ❌ **pm.lua** - Pattern matching
  - ❌ **utf8.lua** - UTF-8 support
  - ⏭️ **all.lua** - Complete test orchestrator (skipped)
- 🚧 Create binarry installer
  - 🚧 Windows
  - 🚧 MacOs
  - 🚧 Linux

### Next
- TDM v1 (public/private)
- Cloud Runtime (pay-as-you-go)
- Revenue-Share Alpha

### Later
- Granular attribution (file/LOC)
- iOS/Android & Desktop targets
- OpenAPI bridges
- App Directory ("YouTube for Apps") with one-click install

---

## Build once – launch anywhere

**Today**, TENUM lets you ship collaborative SaaS apps, PWAs, and microservices on our cloud.  
**Soon** you can export the Docker container to run it wherever you want.

TENUM is built on **Kotlin Multiplatform**, so you'll be able to run your apps anywhere:

- 🌐 Web (Browser)
- ☁️ Cloud (Linux servers)
- 📱 Mobile (iOS, Android)
- 🖥️ Desktop (macOS, Windows, Linux)

---

## Contributing

We welcome contributions! Please see:

- [Changelog](CHANGELOG.md) - Project history

---

## Community

- 💬 [Join us on Discord](https://discord.gg/nZwkhNj8)
- 🐙 [Star us on GitHub](https://github.com/TENUM-Dev/tenum)
- 🌍 [Visit our website](https://tenum.ai)

---

## About

**TENUM** is developed by [Plentitude AI GmbH](https://tenum.ai) in Munich, Germany.

Federico and Jochen, who hold several software patents, are the founders committed to building a fairer open-source ecosystem.

---

### Legal

- [Imprint](https://tenum.ai/imprint.html)
- [Data Protection & Privacy](https://tenum.ai/privacy-policy.html)
- [Cookies](https://tenum.ai/cookie-policy.html)

---

Made with ❤️ in Munich, Germany  
Copyright © 2025, Plentitude AI GmbH

