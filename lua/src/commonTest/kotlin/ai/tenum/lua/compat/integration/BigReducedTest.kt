package ai.tenum.lua.compat.integration

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNumber
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class BigReducedTest : LuaCompatTestBase() {
    @Test
    fun testBigTiny() =
        runTest {
            val lua =
                """
                local lim = 50
                local prog = { "local y = {0" }
                for i = 1, lim do prog[#prog + 1] = i end
                prog[#prog + 1] = "}"
                prog[#prog + 1] = "X = y"
                prog[#prog + 1] = ("assert(X[%d] == %d)"):format(lim - 1, lim - 2)
                prog[#prog + 1] = "return 0"
                prog = table.concat(prog, ";")
                local f = assert(load(prog))
                local r = f()
                return r
                """.trimIndent()

            val result = execute(lua)
            assertTrue(result is LuaNumber)
        }

    @Test
    fun testBigSmall() =
        runTest {
            vm.debugEnabled = true
            val lua =
                """
                local lim = 256
                local prog = { "local y = {0" }
                for i = 1, lim do prog[#prog + 1] = i end
                prog[#prog + 1] = "}"
                prog[#prog + 1] = "X = y"
                prog[#prog + 1] = ("assert(X[%d] == %d)"):format(lim - 1, lim - 2)
                prog[#prog + 1] = "return 0"
                prog = table.concat(prog, ";")
                local f = assert(load(prog))
                local r = f()
                return r
                """.trimIndent()

            val result = execute(lua)
            assertTrue(result is LuaNumber)
        }

    @Test
    fun testBigSmallPlusOne() =
        runTest {
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
                local f = assert(load(prog))
                local r = f()
                return r
                """.trimIndent()

            val result = execute(lua)
            assertTrue(result is LuaNumber)
        }

    @Test
    fun testBig() =
        runTest {
            val lua =
                """
                local lim = 1000
                local prog = { "local y = {0" }
                for i = 1, lim do prog[#prog + 1] = i end
                prog[#prog + 1] = "}"
                prog[#prog + 1] = "X = y"
                prog[#prog + 1] = ("assert(X[%d] == %d)"):format(lim - 1, lim - 2)
                prog[#prog + 1] = "return 0"
                prog = table.concat(prog, ";")
                local f = assert(load(prog))
                local r = f()
                return r
                """.trimIndent()

            val result = execute(lua)
            assertTrue(result is LuaNumber)
        }

    @Test
    @Ignore
    fun testVeryBig() =
        runTest {
            val lua =
                """
                local lim = 2^15 + 1000
                local prog = { "local y = {0" }
                for i = 1, lim do prog[#prog + 1] = i end
                prog[#prog + 1] = "}"
                prog[#prog + 1] = "X = y"
                prog[#prog + 1] = ("assert(X[%d] == %d)"):format(lim - 1, lim - 2)
                prog[#prog + 1] = "return 0"
                prog = table.concat(prog, ";")
                local f = assert(load(prog))
                local r = f()
                return r
                """.trimIndent()

            val result = execute(lua)
            assertTrue(result is LuaNumber)
        }
}
