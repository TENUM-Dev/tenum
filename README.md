# TENUM – Lua Full Stack Ecosystem

**Build Full-Stack Apps in Lua**

Build apps faster. Share them easily. Get paid fairly.

[![Discord](https://img.shields.io/badge/Discord-Join%20us-5865F2?logo=discord&logoColor=white)](https://discord.gg/your-invite-link)
[![GitHub](https://img.shields.io/badge/GitHub-Star%20us-181717?logo=github)](https://github.com/your-org/tenum)

---

## Why TENUM?

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

---

## Architecture (at a glance)

**Runtime foundation:**  
Kotlin Multiplatform — consistent behavior across web/server today; mobile/desktop next.

**UI engine:**  
Lua with a virtual-DOM-style renderer (React-ish ergonomics).

**Domain model:**  
Entities with Commands, Queries, and Events; realtime syncing and optimistic concurrency.

**Serverless execution:**  
Fine-grained scaling with pay-for-what-runs semantics.

---

## Project Structure

This repository contains:

### 🔧 **[luak/](luak/)** – Lua 5.4.8 Interpreter
A complete Lua 5.4 interpreter written in Kotlin Multiplatform. This is the runtime foundation that executes your Lua code across JVM, JS, and Native targets.

- Full Lua 5.4.8 compatibility (in progress)
- Register-based VM with domain-driven architecture
- Standard library support
- See [luak/README.md](luak/README.md) for details

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
./gradlew :luak:jvmTest

# Install local CLI tools
./gradlew :clinpm:installLocal
```

### Run a Lua script

```bash
# After building
tlua your-script.lua
```

---

## Familiar Patterns

React-like UI building (hooks vibe) in Lua; clean, composable domain logic.

```lua
-- Example: Simple counter component (pseudocode)
function Counter()
  local count, setCount = useState(0)
  
  return div {
    h1 { "Count: " .. count },
    button { 
      text = "Increment",
      onClick = function() setCount(count + 1) end
    }
  }
end
```

---

## License & Revenue Share (RSALv2)

We use an **RSALv2 license** for the hosting/runtime components so we can measure real usage and fairly share revenue with contributors, while keeping the developer tooling open for community contributions. Controlling hosting lets us guarantee accurate attribution and payouts.

### Own your code

Keep your repos; use our hosting only if you want it.

> *"Open source powers everything we do, but maintainers rarely share in the value they create. TENUM's mission is to fix that."*  
> — Federico and Jochen, founders of TENUM

---

## Our Roadmap

### Now
- ✅ Open-source CLI
- ✅ UI & entity model
- 🚧 Realtime sync
- 🚧 Auth
- 🚧 PWA focus

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

- [Development Guidelines](.github/copilot-instructions.md) - Coding standards and workflow
- [Lua Compatibility Roadmap](LUA_COMPAT_ROADMAP.md) - Language feature progress
- [Changelog](CHANGELOG.md) - Project history

---

## Community

- 💬 [Join us on Discord](https://discord.gg/your-invite-link)
- 🐙 [Star us on GitHub](https://github.com/your-org/tenum)
- 🌍 [Visit our website](https://tenum.ai)

---

## About

**TENUM** is developed by [Plantitude AI GmbH](https://plantitude.ai) in Munich, Germany.

Federico and Jochen, who hold several software patents, are the founders committed to building a fairer open-source ecosystem.

---

### Legal

- [Imprint](https://tenum.ai/imprint)
- [Data Protection & Privacy](https://tenum.ai/privacy)
- [Cookies](https://tenum.ai/cookies)

---

Made with ❤️ in Munich, Germany  
Copyright © 2025, Plantitude AI GmbH
