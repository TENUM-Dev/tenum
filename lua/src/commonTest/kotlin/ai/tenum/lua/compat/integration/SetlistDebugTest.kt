package ai.tenum.lua.compat.integration

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNumber
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class SetlistDebugTest : LuaCompatTestBase() {
    @Test
    fun testSmallTable() =
        runTest {
            vm.debugEnabled = true
            val result = execute("local t = {0,1,2,3,4}; return t[3]")
            assertTrue(result is LuaNumber)
            kotlin.test.assertEquals(2.0, result.toDouble())
        }

    @Test
    fun testMediumTable() =
        runTest {
            val lua =
                """
                local t = {0,1,2,3,4,5,6,7,8,9,10}
                return t[10]
                """.trimIndent()
            val result = execute(lua)
            assertTrue(result is LuaNumber)
            kotlin.test.assertEquals(9.0, result.toDouble())
        }

    @Test
    fun test51Elements() =
        runTest {
            vm.debugEnabled = true
            val lua =
                """
                local t = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50}
                return t[50]
                """.trimIndent()
            val result = execute(lua)
            assertTrue(result is LuaNumber)
            kotlin.test.assertEquals(49.0, result.toDouble())
        }

    @Test
    fun testDynamicLoad60Elements() =
        runTest {
            val lua =
                """
                local code = "local y = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59}; return y[59]"
                local f = assert(load(code))
                return f()
                """.trimIndent()
            val result = execute(lua)
            assertTrue(result is LuaNumber)
            kotlin.test.assertEquals(58.0, result.toDouble())
        }

    @Test
    @Ignore // TODO: Requires proper SETLIST implementation for large tables (>200 elements due to constant pool limit)
    fun testDynamicTableGeneration() =
        runTest {
            vm.debugEnabled = true
            val lua =
                """
                local lim = 257
                local prog = { "local y = {0" }
                for i = 1, lim do prog[#prog + 1] = i end
                prog[#prog + 1] = "}"
                prog[#prog + 1] = "X = y"
                prog[#prog + 1] = ("assert(X[%d] == %d)"):format(lim - 1, lim - 2)
                prog[#prog + 1] = "return 0"
                prog = table.concat(prog, ";")
                print("Generated code length: " .. #prog)
                local f = assert(load(prog))
                local r = f()
                return r
                """.trimIndent()

            val result = execute(lua)
            assertTrue(result is LuaNumber)
            kotlin.test.assertEquals(0.0, result.toDouble())
        }
}
