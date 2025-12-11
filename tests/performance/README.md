# TENUM Performance Benchmarks

Performance testing and benchmarking suite for the TENUM Lua interpreter.

## Overview

This module contains benchmarks and performance tests to ensure TENUM maintains high performance across different workloads and platforms. Built with [kotlinx-benchmark](https://github.com/Kotlin/kotlinx-benchmark), it provides consistent cross-platform benchmarking.

## Features

- 📊 **Micro-benchmarks** - Measure individual operations and opcodes
- 🎯 **Macro-benchmarks** - Test real-world application patterns
- 📈 **Regression Detection** - Track performance across commits
- 🔍 **Profiling Support** - JMH integration for deep analysis
- 🌐 **Multi-Platform** - JVM benchmarks (JS/Native coming soon)

## Running Benchmarks

### Quick Run

```bash
# Run all benchmarks
./gradlew :performance:benchmark

# Run specific benchmark
./gradlew :performance:jvmBenchmark
```

### With Options

```bash
# Run with custom iterations
./gradlew :performance:benchmark -Pbenchmark.iterations=10

# Run specific benchmark class
./gradlew :performance:benchmark -Pbenchmark.filter=ArithmeticBenchmark
```

## Benchmark Categories

### Opcode Benchmarks
Measure performance of individual VM operations:

- Arithmetic operations (ADD, SUB, MUL, DIV, MOD, POW)
- Bitwise operations (BAND, BOR, BXOR, SHL, SHR)
- Table operations (GETTABLE, SETTABLE, NEWTABLE)
- Function calls (CALL, TAILCALL, RETURN)
- Control flow (JMP, TEST, loops)

### Stdlib Benchmarks
Test standard library performance:

- String operations (concat, substring, pattern matching)
- Math functions (trig, exponentials, random)
- Table functions (sort, concat, insert, remove)

### Real-World Scenarios
Application-level benchmarks:

- JSON parsing and serialization
- Template rendering
- Algorithm implementations
- Data structure operations

## Writing Benchmarks

### Basic Structure

```kotlin
@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
class MyBenchmark {
    
    private lateinit var vm: LuaVM
    
    @Setup
    fun setup() {
        vm = LuaVM()
        // Setup code
    }
    
    @Benchmark
    fun benchmarkOperation(): Any {
        return vm.execute("return 1 + 1")
    }
}
```

### Best Practices

1. **Isolate what you measure** - Benchmark only the operation you care about
2. **Use proper warmup** - JIT needs time to optimize
3. **Avoid allocations** - Reuse objects when possible
4. **Measure consistently** - Use the same machine and conditions
5. **Compare fairly** - Benchmark against reference implementations (Lua 5.4)

## Architecture

```
performance/
├── src/commonMain/kotlin/org/
│   └── (benchmark source files)
├── build.gradle.kts           # kotlinx-benchmark config
└── README.md
```

### Build Configuration

The module uses:
- **kotlinx-benchmark** - Cross-platform benchmarking framework
- **JMH** (JVM) - Java Microbenchmark Harness for detailed profiling
- **allopen plugin** - Required for JMH to work with Kotlin

## Interpreting Results

Benchmark output includes:

```
Benchmark                          Mode  Cnt    Score    Error  Units
ArithmeticBenchmark.addition       avgt   10    0.123 ±  0.001  us/op
ArithmeticBenchmark.multiplication avgt   10    0.145 ±  0.002  us/op
```

- **Mode**: `avgt` (average time), `thrpt` (throughput), `ss` (single shot)
- **Cnt**: Number of iterations
- **Score**: Average time per operation
- **Error**: Margin of error
- **Units**: `us/op` (microseconds per operation), `ops/s` (operations per second)

## Performance Goals

### Target Performance
- Simple arithmetic: < 0.2 µs/op
- Function calls: < 1.0 µs/op
- Table operations: < 0.5 µs/op
- String operations: < 2.0 µs/op

### Comparison Baseline
TENUM aims to be competitive with:
- Official Lua 5.4 (C implementation)
- LuaJIT (where applicable)
- Other Kotlin VM implementations

## Continuous Monitoring

Performance benchmarks should be run:
- Before/after major VM changes
- When optimizing opcodes or stdlib
- Before releases
- When investigating performance regressions

## Integration with CI

```yaml
# Example GitHub Actions workflow
- name: Run Performance Benchmarks
  run: ./gradlew :performance:benchmark
  
- name: Compare with Baseline
  run: ./scripts/compare-benchmarks.sh
```

## Part of TENUM

Performance testing ensures [TENUM](../README.md) delivers fast, responsive applications across all platforms. Benchmarks validate that the Lua interpreter maintains high performance while adding full-stack capabilities.

Related modules:
- [lua](../../lua/) - Core Lua 5.4.8 interpreter being benchmarked
- [cli](../../cli/) - CLI tools for running benchmarks

## Contributing

When adding new features to the Lua interpreter:
1. Add corresponding benchmarks
2. Ensure no performance regressions
3. Document performance characteristics
4. Compare with reference implementations

See [Development Guidelines](../.github/copilot-instructions.md) for coding standards and workflow.

## Resources

- [kotlinx-benchmark Documentation](https://github.com/Kotlin/kotlinx-benchmark)
- [JMH Documentation](https://openjdk.java.net/projects/code-tools/jmh/)
- [Lua Performance Tips](https://www.lua.org/gems/sample.pdf)

## License

Part of the TENUM project. See root LICENSE for details.
