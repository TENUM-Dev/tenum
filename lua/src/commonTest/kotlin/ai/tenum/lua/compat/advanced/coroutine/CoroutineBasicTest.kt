package ai.tenum.lua.compat.advanced.coroutine

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Basic coroutine tests from coroutine.lua
 * Tests coroutine.create, resume, yield, status, running, isyieldable
 */
class CoroutineBasicTest : LuaCompatTestBase() {
    @Test
    fun testCoroutineRunningInMain() {
        // coroutine.lua lines 10-11: local main, ismain = coroutine.running()
        // assert(type(main) == "thread" and ismain)
        assertLuaString(
            """
            local main, ismain = coroutine.running()
            return type(main)
            """.trimIndent(),
            "thread",
        )
        assertLuaBoolean(
            """
            local main, ismain = coroutine.running()
            return ismain
            """.trimIndent(),
            true,
        )
    }

    @Test
    fun testCannotResumeMain() {
        // coroutine.lua line 12: assert(not coroutine.resume(main))
        assertLuaBoolean(
            """
            local main = coroutine.running()
            return coroutine.resume(main)
            """.trimIndent(),
            false,
        )
    }

    @Test
    fun testMainIsNotYieldable() {
        // coroutine.lua line 13: assert(not coroutine.isyieldable(main) and not coroutine.isyieldable())
        assertLuaBoolean("return coroutine.isyieldable()", false)
    }

    @Test
    fun testCannotYieldFromMain() {
        // coroutine.lua line 14: assert(not pcall(coroutine.yield))
        assertLuaBoolean("return pcall(coroutine.yield)", false)
    }

    @Test
    fun testCreateCoroutine() {
        // coroutine.lua line 48: f = coroutine.create(foo)
        // assert(type(f) == "thread" and coroutine.status(f) == "suspended")
        assertLuaString(
            """
            local f = coroutine.create(function() end)
            return type(f)
            """.trimIndent(),
            "thread",
        )
        assertLuaString(
            """
            local f = coroutine.create(function() end)
            return coroutine.status(f)
            """.trimIndent(),
            "suspended",
        )
    }

    @Test
    fun testResumeSimpleCoroutine() {
        // Basic resume test
        assertLuaBoolean(
            """
            local co = coroutine.create(function()
                return 42
            end)
            local success, value = coroutine.resume(co)
            return success
            """.trimIndent(),
            true,
        )
        assertLuaNumber(
            """
            local co = coroutine.create(function()
                return 42
            end)
            local success, value = coroutine.resume(co)
            return value
            """.trimIndent(),
            42.0,
        )
    }

    @Test
    fun testCoroutineStatusAfterCompletion() {
        // Coroutine status should be "dead" after completion
        assertLuaString(
            """
            local co = coroutine.create(function() return 1 end)
            coroutine.resume(co)
            return coroutine.status(co)
            """.trimIndent(),
            "dead",
        )
    }

    @Test
    fun testCoroutineYield() {
        // Test simple yield - first resume
        assertLuaBoolean(
            """
            local co = coroutine.create(function()
                coroutine.yield(10)
                return 20
            end)
            local s1, v1 = coroutine.resume(co)
            return s1
            """.trimIndent(),
            true,
        )
        // First yield value
        assertLuaNumber(
            """
            local co = coroutine.create(function()
                coroutine.yield(10)
                return 20
            end)
            local s1, v1 = coroutine.resume(co)
            return v1
            """.trimIndent(),
            10.0,
        )
        // Second resume
        assertLuaBoolean(
            """
            local co = coroutine.create(function()
                coroutine.yield(10)
                return 20
            end)
            coroutine.resume(co)
            local s2, v2 = coroutine.resume(co)
            return s2
            """.trimIndent(),
            true,
        )
        // Final return value
        assertLuaNumber(
            """
            local co = coroutine.create(function()
                coroutine.yield(10)
                return 20
            end)
            coroutine.resume(co)
            local s2, v2 = coroutine.resume(co)
            return v2
            """.trimIndent(),
            20.0,
        )
    }

    @Test
    fun testCoroutineYieldMultipleValues() {
        // coroutine.lua - test yield with multiple values
        assertLuaTrue(
            """
            local co = coroutine.create(function()
                return coroutine.yield(1, 2, 3)
            end)
            local s, a, b, c = coroutine.resume(co)
            return s == true and a == 1 and b == 2 and c == 3
            """.trimIndent(),
        )
    }

    @Test
    fun testCoroutineWrap() {
        // coroutine.lua line 76: f = coroutine.wrap(pf)
        assertLuaNumber(
            """
            local f = coroutine.wrap(function()
                coroutine.yield(10)
                return 20
            end)
            local v1 = f()
            return v1
            """.trimIndent(),
            10.0,
        )
        assertLuaNumber(
            """
            local f = coroutine.wrap(function()
                coroutine.yield(10)
                return 20
            end)
            f()
            local v2 = f()
            return v2
            """.trimIndent(),
            20.0,
        )
    }

    @Test
    fun testCoroutinePassArgsToResume() {
        // Test passing arguments through resume
        assertLuaBoolean(
            """
            local co = coroutine.create(function(a, b)
                return a + b
            end)
            local success, value = coroutine.resume(co, 5, 7)
            return success
            """.trimIndent(),
            true,
        )
        assertLuaNumber(
            """
            local co = coroutine.create(function(a, b)
                return a + b
            end)
            local success, value = coroutine.resume(co, 5, 7)
            return value
            """.trimIndent(),
            12.0,
        )
    }

    @Test
    fun testCoroutineIsYieldableInCoroutine() {
        // Inside a coroutine, isyieldable should return true
        assertLuaBoolean(
            """
            local co = coroutine.create(function()
                return coroutine.isyieldable()
            end)
            local success, result = coroutine.resume(co)
            return result
            """.trimIndent(),
            true,
        )
    }

    @Test
    fun testCoroutineIsYieldableWithThreadArgument() {
        // Reproduces coroutine.lua behaviour: resume a coroutine that yields,
        // then check that coroutine.isyieldable(f) returns true when called
        // from the main thread.
        assertLuaBoolean(
            """
            local f = coroutine.create(function()
                coroutine.yield()
                return 1
            end)
            coroutine.resume(f)
            return coroutine.isyieldable(f)
            """.trimIndent(),
            true,
        )
    }

    @Test
    fun testComplexYieldResumeSequence() {
        // Reproduce the sequence from coroutine.lua that previously failed
        assertLuaTrue(
            """
            local _G = _G
            _G.x = nil
            _G.f = nil

            local function foo (a, ...)
                local x, y = coroutine.running()
                assert(x == f and y == false)
                assert(coroutine.resume(f) == false)
                assert(coroutine.status(f) == "running")
                local arg = {...}
                assert(coroutine.isyieldable(x))
                for i=1,#arg do
                    _G.x = {coroutine.yield(table.unpack(arg[i]))}
                end
                return table.unpack(a)
            end

            f = coroutine.create(foo)
            local s,a,b,c,d
            s,a,b,c,d = coroutine.resume(f, {1,2,3}, {}, {1}, {'a','b','c'})
            return s == true and a == nil and coroutine.status(f) == "suspended"
            """.trimIndent(),
        )
    }

    @Test
    fun testCannotResumeRunningCoroutine() {
        // coroutine.lua lines 38-39: cannot resume running coroutine
        // Outer resume succeeds (returns true), but inner resume fails
        assertLuaBoolean(
            """
            local co
            co = coroutine.create(function()
                return coroutine.resume(co)
            end)
            local success = coroutine.resume(co)
            return success
            """.trimIndent(),
            true, // Outer resume succeeds
        )

        // The inner resume attempt should fail
        assertLuaBoolean(
            """
            local co
            co = coroutine.create(function()
                local innerSuccess, innerError = coroutine.resume(co)
                return innerSuccess
            end)
            local outerSuccess, result = coroutine.resume(co)
            return result
            """.trimIndent(),
            false, // Inner resume fails (cannot resume running coroutine)
        )
    }

    @Test
    fun testCoroutineTostringContainsThread() {
        assertLuaTrue(
            """
            local f = coroutine.create(function() end)
            return string.find(tostring(f), "thread") ~= nil
            """.trimIndent(),
        )
    }

    @Test
    @Ignore // TODO: enable when coroutines fixed
    fun testWrapYieldResumeInteraction() {
        // Reproduce the wrap + nested yield/resume interaction from coroutine.lua
        assertLuaTrue(
            """
            _G.x = nil
            local function foo (i) return coroutine.yield(i) end
            local f = coroutine.wrap(function ()
                for i=1,10 do
                    assert(foo(i) == _G.x, "Expected foo to return " .. tostring(_G.x) .. ", got: " .. tostring(foo(i)))
                end
                return 'a'
            end)
            for i=1,10 do _G.x = i; assert(f(i) == i, "Error in loop " .. i) end
            _G.x = 'xuxu'; assert(f('xuxu') == 'a', "Expected final return value to be 'a'")
            return true
            """.trimIndent(),
        )
    }

    @Test
    fun testCannotResumeDeadCoroutine() {
        // After coroutine finishes, cannot resume again
        assertLuaBoolean(
            """
            local co = coroutine.create(function() return 1 end)
            coroutine.resume(co)
            local success = coroutine.resume(co)
            return success
            """.trimIndent(),
            false,
        )
    }
}
