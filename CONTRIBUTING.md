# Contributing to TENUM

Thank you for your interest in contributing to TENUM! We're building a full-stack Lua ecosystem, and we welcome contributions from developers of all skill levels.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [How to Contribute](#how-to-contribute)
- [Development Workflow](#development-workflow)
- [Testing Guidelines](#testing-guidelines)
- [Code Style](#code-style)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Community](#community)

## Code of Conduct

We are committed to providing a welcoming and inclusive environment. Please be respectful, constructive, and collaborative in all interactions.

## Getting Started

### Prerequisites

- **JDK 17 or higher** (for JVM target)
- **Gradle 9.1+** (wrapper included)
- **Git**
- **IDE**: IntelliJ IDEA or VS Code with Kotlin support recommended

### Development Setup

1. **Fork and clone the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/tenum.git
   cd tenum
   ```

2. **Build the project**
   ```bash
   ./gradlew build
   ```

3. **Run tests**
   ```bash
   ./gradlew test
   ```

4. **Try the CLI**
   ```bash
   ./gradlew :cli:installDist
   ./cli/build/install/tenum/bin/tenum --version
   ```

## Project Structure

```
tenum/
├── lua/                 # Lua 5.4 interpreter (Kotlin Multiplatform)
│   ├── src/commonMain/ # Core VM, compiler, standard library
│   ├── src/jvmMain/    # JVM-specific code
│   ├── src/jsMain/     # JS-specific code
│   └── src/nativeMain/ # Native-specific code
├── cli/                # TENUM CLI tool
├── clinpm/            # NPM package for CLI distribution
├── tests/
│   ├── integration/   # Integration tests (including Lua 5.4.8 test suite)
│   └── performance/   # Performance benchmarks
└── build-logic/       # Custom Gradle plugins
```

## How to Contribute

### Types of Contributions

We welcome many types of contributions:

- 🐛 **Bug fixes** - Fix issues in the VM, compiler, or standard library
- ✨ **Features** - Implement missing Lua 5.4 features or TENUM-specific functionality
- 📚 **Documentation** - Improve README, API docs, or code comments
- 🧪 **Tests** - Add test coverage, especially for edge cases
- 🚀 **Performance** - Optimize VM execution, memory usage, or compilation
- 🎨 **Examples** - Create example apps or tutorials

### Finding Work

- Check the [Issues](https://github.com/TENUM-Dev/tenum/issues) page for open tasks
- Look for issues labeled `good first issue` or `help wanted`
- Join our [Discord](https://discord.gg/nZwkhNj8) to discuss ideas

## Development Workflow

### 1. Create a Branch

```bash
git checkout -b feature/my-feature
# or
git checkout -b fix/issue-123
```

Branch naming conventions:
- `feature/` - New features
- `fix/` - Bug fixes
- `refactor/` - Code refactoring
- `docs/` - Documentation changes
- `test/` - Test additions/improvements

### 2. Make Your Changes

- Write clean, idiomatic Kotlin code
- Follow existing code patterns and architecture
- Add tests for new functionality
- Update documentation as needed

### 3. Test Your Changes

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :lua:jvmTest

# Run integration tests (Lua 5.4.8 test suite)
./gradlew :tests:integration:jvmTest
```

### 4. Commit Your Changes

Follow our [commit guidelines](#commit-guidelines) below.

## Testing Guidelines

### Test Organization

- **Unit tests**: In `src/commonTest/` for platform-independent tests
- **Platform-specific tests**: In `src/jvmTest/`, `src/jsTest/`, etc.
- **Compatibility tests**: Tests that verify Lua 5.4 compatibility
- **Integration tests**: In `tests/integration/` for the official Lua test suite

### Writing Tests

```kotlin
class MyFeatureTest : LuaCompatTestBase() {
    @Test
    fun testMyFeature() = runTest {
        val result = execute("""
            local x = 10
            return x * 2
        """)
        assertLuaNumber(result, 20.0)
    }
}
```

### Running Specific Tests

```bash
# Run tests for a specific class
./gradlew :lua:jvmTest --tests "MyFeatureTest"

# Run tests matching a pattern
./gradlew :lua:jvmTest --tests "*Tail*"
```

## Code Style

### Kotlin Guidelines

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions focused and concise
- Document complex logic with comments
- Use inline documentation for public APIs

### VM Architecture Principles

The Lua VM follows **Domain-Driven Design** principles:

1. **Separation of Concerns**: Each component has a single responsibility
   - `Lexer` → tokenization
   - `Parser` → AST construction
   - `Compiler` → bytecode generation
   - `VM` → execution

2. **Bounded Contexts**: Components are organized into cohesive modules
   - `execution/` - Opcode execution
   - `debug/` - Debug hooks and tracing
   - `stdlib/` - Standard library implementation

3. **Immutable Data Structures**: Prefer immutable data where possible
   - `Proto`, `Instruction`, `CallFrame` are data classes

4. **Explicit State Management**: State changes are explicit and traceable
   - `ExecutionEnvironment`, `CallStackManager`, `DebugTracer`

### Example: Good vs Bad

❌ **Bad** - Unclear, mixed responsibilities:
```kotlin
fun doStuff(x: Int): Int {
    val y = x * 2
    // Some random logic
    return y + 5
}
```

✅ **Good** - Clear purpose, documented:
```kotlin
/**
 * Calculate adjusted value for loop iteration.
 * Applies step multiplier and offset per Lua 5.4 spec.
 */
fun calculateLoopValue(iteration: Int): Int {
    val stepped = iteration * STEP_MULTIPLIER
    return stepped + LOOP_OFFSET
}
```

## Commit Guidelines

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `test`: Test additions/changes
- `docs`: Documentation changes
- `perf`: Performance improvements
- `chore`: Maintenance tasks

**Examples:**

```
feat(vm): implement trampolining for all function calls

Simplifies VM execution by making trampolining the only behavior:
- Removes trampoline flag and toggle
- Unifies CALL and TAILCALL result types
- Extracts common __call resolution logic

Closes #123
```

```
fix(stdlib): correct table.sort stability for equal elements

Lua 5.4 requires stable sort behavior. Updated comparison
logic to preserve original order for equal elements.

Fixes #456
```

## Pull Request Process

### Before Submitting

1. ✅ All tests pass locally
2. ✅ Code follows style guidelines
3. ✅ New tests added for new functionality
4. ✅ Documentation updated if needed
5. ✅ Commit messages follow guidelines
6. ✅ Branch is up to date with `main`

### Submitting a PR

1. **Push your branch**
   ```bash
   git push origin feature/my-feature
   ```

2. **Create Pull Request** on GitHub
   - Use a clear, descriptive title
   - Reference related issues (`Closes #123`, `Fixes #456`)
   - Describe what changed and why
   - Include test results if applicable

3. **PR Template**
   ```markdown
   ## Description
   Brief description of changes

   ## Type of Change
   - [ ] Bug fix
   - [ ] New feature
   - [ ] Refactoring
   - [ ] Documentation
   - [ ] Performance improvement

   ## Testing
   - [ ] All existing tests pass
   - [ ] New tests added for new functionality
   - [ ] Manual testing performed

   ## Related Issues
   Closes #XXX
   ```

### Review Process

- Maintainers will review your PR within a few days
- Address feedback and push updates
- Once approved, your PR will be merged
- Your contribution will be acknowledged in release notes!

## Areas Needing Help

### High Priority

- 🔴 **Lua 5.4 Compatibility**: Complete standard library functions
- 🔴 **Performance**: Optimize hot paths in VM execution
- 🔴 **Documentation**: API documentation and usage examples

### Good First Issues

- ✅ Add missing test cases for edge conditions
- ✅ Improve error messages for better debugging
- ✅ Document existing functions and modules
- ✅ Fix small bugs or inconsistencies

## Community

- **Discord**: [Join our community](https://discord.gg/nZwkhNj8)
- **GitHub Discussions**: Ask questions, share ideas
- **Issues**: Report bugs or suggest features

## Questions?

If you have questions about contributing, feel free to:
- Open a GitHub Discussion
- Ask in our Discord server
- Comment on relevant issues

---

**Thank you for contributing to TENUM! 🎉**

Every contribution, big or small, helps us build a better full-stack Lua ecosystem.
