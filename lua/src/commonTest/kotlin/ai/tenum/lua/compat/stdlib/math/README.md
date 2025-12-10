# Math Library Test Suite Organization

This document describes the comprehensive test organization for the Lua math library compatibility tests in `ai.tenum.lua.compat.stdlib.math` package.

## Test File Structure

The math library tests have been split into 8 focused test files, each covering specific aspects of Lua 5.4's math library behavior:

### 1. MathBasicFunctionsCompatTest.kt
**Scope**: Core mathematical functions
- `math.abs` - absolute value
- `math.ceil`, `math.floor` - rounding functions  
- `math.max`, `math.min` - extrema functions
- `math.modf` - integer and fractional parts
- `math.fmod` - floating-point modulo
- `math.sqrt`, `math.exp`, `math.log` - power and logarithm functions

**Key Test Areas**:
- Basic functionality with various number types
- Edge cases with extreme values (infinity, very large/small numbers)
- Type preservation (integer vs float results)
- Error conditions and boundary behavior

### 2. MathTrigonometricCompatTest.kt  
**Scope**: Trigonometric and angle functions
- `math.sin`, `math.cos`, `math.tan` - basic trigonometric functions
- `math.asin`, `math.acos`, `math.atan` - inverse trigonometric functions  
- `math.deg`, `math.rad` - angle conversion functions

**Key Test Areas**:
- Standard trigonometric identities (sin² + cos² = 1)
- Special angle values (π/2, π, etc.)
- Inverse function domains and ranges
- Two-argument atan2 functionality
- Angle conversion consistency

### 3. MathTypeAndConversionCompatTest.kt
**Scope**: Type checking and number conversion
- `math.type` - check number type (integer/float)
- `math.tointeger` - convert to integer if possible
- `math.ult` - unsigned less than comparison
- `tonumber` - string to number conversion with various bases

**Key Test Areas**:
- Integer vs float type detection
- String parsing with different number formats
- Base conversion (binary, octal, hex, up to base 36)
- Invalid format handling
- Overflow and precision behavior

### 4. MathRandomCompatTest.kt
**Scope**: Random number generation
- `math.random()` - float random [0, 1)
- `math.random(n)` - integer random [1, n]  
- `math.random(m, n)` - integer random [m, n]
- `math.randomseed(seed)` - set random seed

**Key Test Areas**:
- Deterministic behavior with same seeds
- Range validation for different random modes
- Statistical distribution properties
- Edge cases with extreme integer bounds
- Error conditions (invalid ranges, too many arguments)

### 5. MathArithmeticCompatTest.kt
**Scope**: Arithmetic operations and edge cases  
- Modulo operator (%) with various types and signs
- Floor division (//) operations
- Integer overflow and underflow behavior
- Float/integer comparison semantics
- NaN comparison behavior

**Key Test Areas**:
- Lua modulo semantics (different from many other languages)
- Type preservation in arithmetic operations
- Boundary conditions between float and integer precision
- Extreme value arithmetic (minint/maxint operations)
- Cross-type comparisons

### 6. MathConstantsCompatTest.kt
**Scope**: Mathematical constants
- `math.pi` - π constant
- `math.huge` - positive infinity
- `math.mininteger` - minimum integer value
- `math.maxinteger` - maximum integer value

**Key Test Areas**:
- Constant value accuracy  
- Type properties (integer vs float)
- Arithmetic with constants
- Boundary relationships (2's complement integer limits)
- Immutability testing

### 7. MathFloatIntegerCompatTest.kt
**Scope**: Float/integer precision and special values
- NaN detection and arithmetic behavior
- Positive/negative zero handling
- Infinity arithmetic and comparisons  
- Float precision boundary detection
- Special values in table operations

**Key Test Areas**:
- IEEE 754 special value semantics
- Precision loss scenarios
- Float mantissa bit detection
- Cross-type boundary conditions
- Table key behavior with special values

### 8. MathHexadecimalCompatTest.kt  
**Scope**: Hexadecimal number support
- Hexadecimal integer literals (0x...)
- Hexadecimal floating-point literals (0x...p...)  
- Hexadecimal string parsing with `tonumber()`
- Error conditions and edge cases

**Key Test Areas**:
- Basic hex literal parsing
- Hex float notation with exponents
- Case sensitivity and formatting
- Very long hex numerals
- Invalid format detection

## Coverage Mapping to math.lua

Each test file maps to specific sections of the official Lua 5.4 `math.lua` test suite:

- **Lines 7-13, 107-112**: Integer bounds and constants → MathConstantsCompatTest
- **Lines 20-33, 62-85**: NaN/infinity/zero handling → MathFloatIntegerCompatTest  
- **Lines 86-105**: modf function → MathBasicFunctionsCompatTest
- **Lines 114-161**: Integer arithmetic and floor division → MathArithmeticCompatTest
- **Lines 174-285**: Float/integer boundaries and comparisons → MathArithmeticCompatTest, MathFloatIntegerCompatTest
- **Lines 397-517**: tonumber and string conversion → MathTypeAndConversionCompatTest
- **Lines 520-567**: Hexadecimal notation → MathHexadecimalCompatTest  
- **Lines 519-637**: Modulo operations → MathArithmeticCompatTest
- **Lines 638-653**: Unsigned comparisons → MathTypeAndConversionCompatTest
- **Lines 654-669**: Trigonometric functions → MathTrigonometricCompatTest
- **Lines 694-744**: Floor/ceil/min/max functions → MathBasicFunctionsCompatTest
- **Lines 752-779**: Special values behavior → MathFloatIntegerCompatTest
- **Lines 780-1024**: Random number generation → MathRandomCompatTest

## Test Methodology

All tests follow consistent patterns:

1. **Behavioral Testing**: Tests verify Lua semantics, not implementation details
2. **Edge Case Coverage**: Extensive testing of boundary conditions and special values  
3. **Cross-Type Testing**: Validation of integer/float interactions
4. **Error Condition Testing**: Proper error handling for invalid inputs
5. **Statistical Testing**: Distribution validation for random functions
6. **Precision Testing**: Float precision and rounding behavior

## Legacy Compatibility

The original `MathLibCompatTest.kt` is preserved for compatibility, with updated documentation pointing to the new specialized test files for comprehensive coverage.

## Usage

Run all math tests:
```kotlin
// Individual test classes can be run separately for focused testing
```

Run specific topic areas:
```bash
# Example: Run only basic math function tests
./gradlew test --tests "*MathBasicFunctionsCompatTest*"
```

This organization allows for:
- **Focused Development**: Work on specific math areas independently
- **Targeted Debugging**: Isolate issues to specific mathematical domains
- **Maintainable Coverage**: Each file has clear scope and responsibility  
- **Comprehensive Testing**: Full coverage of Lua 5.4 math.lua test behaviors