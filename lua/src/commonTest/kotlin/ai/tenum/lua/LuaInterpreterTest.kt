package ai.tenum.lua.runtime

import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.vm.LuaVmImpl
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Simple test suite for the Lua interpreter
 */
class LuaInterpreterTest {
    @Test
    fun testLuaVmExecuteNil() =
        runTest {
            val vm = LuaVmImpl(FakeFileSystem())
            val result = vm.execute("return nil")

            assertNotNull(result)
            assertEquals(LuaType.NIL, result.type())
            assertTrue(result is LuaNil)
        }

    @Test
    fun testLuaVmExecuteBoolean() =
        runTest {
            val vm = LuaVmImpl(FakeFileSystem())

            val trueResult = vm.execute("return true")
            assertNotNull(trueResult)
            assertEquals(LuaType.BOOLEAN, trueResult.type())
            assertTrue(trueResult is LuaBoolean)
            assertEquals(true, (trueResult as LuaBoolean).value)

            val falseResult = vm.execute("return false")
            assertNotNull(falseResult)
            assertEquals(LuaType.BOOLEAN, falseResult.type())
            assertTrue(falseResult is LuaBoolean)
            assertEquals(false, (falseResult as LuaBoolean).value)
        }

    @Test
    fun testLuaVmExecuteNumber() =
        runTest {
            val vm = LuaVmImpl(FakeFileSystem())

            val result = vm.execute("return 42")
            assertNotNull(result)
            assertEquals(LuaType.NUMBER, result.type())
            assertTrue(result is LuaNumber)
            assertEquals(LuaNumber.of(42.0), result)
        }

    @Test
    fun testLuaVmExecuteString() =
        runTest {
            val vm = LuaVmImpl(FakeFileSystem())

            val result = vm.execute("return \"Hello, Lua!\"")
            assertNotNull(result)
            assertEquals(LuaType.STRING, result.type())
            assertTrue(result is LuaString)
            assertEquals("Hello, Lua!", (result as LuaString).value)
        }

    @Test
    fun testLuaVmExecuteStringSingleQuote() =
        runTest {
            val vm = LuaVmImpl(FakeFileSystem())

            val result = vm.execute("return 'Hello, Lua!'")
            assertNotNull(result)
            assertEquals(LuaType.STRING, result.type())
            assertTrue(result is LuaString)
            assertEquals("Hello, Lua!", (result as LuaString).value)
        }

    @Test
    fun testLuaTypeEnum() =
        runTest {
            // Test that all Lua types are defined
            val types = LuaType.entries
            assertEquals(8, types.size)

            // Verify each type exists
            assertNotNull(LuaType.NIL)
            assertNotNull(LuaType.BOOLEAN)
            assertNotNull(LuaType.NUMBER)
            assertNotNull(LuaType.STRING)
            assertNotNull(LuaType.TABLE)
            assertNotNull(LuaType.FUNCTION)
            assertNotNull(LuaType.USERDATA)
            assertNotNull(LuaType.THREAD)
        }

    @Test
    fun testLuaValueTypes() =
        runTest {
            val nilValue = LuaNil
            assertEquals(LuaType.NIL, nilValue.type())
            assertEquals("nil", nilValue.toString())

            val boolValue = LuaBoolean.of(true)
            assertEquals(LuaType.BOOLEAN, boolValue.type())
            assertEquals(true, boolValue.value)

            val numValue = LuaDouble(3.14)
            assertEquals(LuaType.NUMBER, numValue.type())
            assertEquals(3.14, numValue.value)

            val strValue = LuaString("test")
            assertEquals(LuaType.STRING, strValue.type())
            assertEquals("test", strValue.value)

            val tableValue = LuaTable()
            assertEquals(LuaType.TABLE, tableValue.type())
        }

    @Test
    fun testEmptyChunk() =
        runTest {
            val vm = LuaVmImpl(FakeFileSystem())
            val result = vm.execute("")

            assertNotNull(result)
            // Empty chunk returns nil
            assertEquals(LuaType.NIL, result.type())
        }

    @Test
    fun testSimpleArithmetic() =
        runTest {
            val vm = LuaVmImpl(FakeFileSystem())
            val result = vm.execute("return 10 + 5")

            assertNotNull(result)
            assertEquals(LuaType.NUMBER, result.type())
            assertTrue(result is LuaNumber)
            assertEquals(LuaNumber.of(15.0), result)
        }

    @Test
    fun testMultipleNumbers() =
        runTest {
            val vm = LuaVmImpl(FakeFileSystem())

            // Test different numbers
            val result1 = vm.execute("return 0")
            assertTrue(result1 is LuaNumber)
            assertEquals(LuaNumber.of(0.0), result1)

            val result2 = vm.execute("return -5.5")
            assertTrue(result2 is LuaNumber)
            assertEquals(LuaNumber.of(-5.5), result2)

            val result3 = vm.execute("return 1000000")
            assertTrue(result3 is LuaNumber)
            assertEquals(LuaNumber.of(1000000.0), result3)
        }
}
