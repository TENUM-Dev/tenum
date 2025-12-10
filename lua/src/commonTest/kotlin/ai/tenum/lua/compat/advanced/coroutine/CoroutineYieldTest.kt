package ai.tenum.lua.compat.advanced.coroutine

import ai.tenum.lua.compat.LuaCompatTestBase
import ai.tenum.lua.runtime.LuaNil
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for coroutine yielding behavior (coroutine.lua line 56 issue)
 *
 * The issue: When yielding with table.unpack of an empty table, the coroutine
 * is returning values from varargs passed to create instead of returning nothing.
 */
class CoroutineYieldTest : LuaCompatTestBase() {
    @Test
    fun testYieldWithEmptyTableUnpack() {
        val result =
            execute(
                """
            f = coroutine.create(function()
                coroutine.yield(table.unpack({}))
            end)
            
            local s, a, b = coroutine.resume(f)
            return a
        """,
            )

        // Unpacking empty table should yield nothing, so a should be nil
        assertEquals(LuaNil, result, "Yielding with empty table.unpack should return nil")
    }

    @Test
    fun testYieldWithTableUnpackFromVarargs() {
        val result =
            execute(
                """
            local function foo (a, ...)
              local arg = {...}
              for i=1,#arg do
                _G.x = {coroutine.yield(table.unpack(arg[i]))}
              end
              return table.unpack(a)
            end
            
            f = coroutine.create(foo)
            local s,a,b,c,d = coroutine.resume(f, {1,2,3}, {}, {1}, {'a', 'b', 'c'})
            
            -- First yield should unpack arg[1] which is {}, yielding nothing
            return a
        """,
            )

        // First yield unpacks {} which should yield nothing
        assertEquals(LuaNil, result, "First yield with empty table should return nil, got: $result")
    }

    @Test
    fun testExactCoroutineLuaLine50Scenario() {
        // Enable VM debug output
        vm.debugEnabled = true

        // This reproduces the exact scenario from coroutine.lua line 50-56
        execute(
            """
            local function foo (a, ...)
              local arg = {...}
              for i=1,#arg do
                print('=== Iteration ' .. i .. ' ===')
                print('About to yield with table.unpack(arg[' .. i .. '])')
                print('arg[' .. i .. '] length: ' .. #arg[i])
                _G.x = {coroutine.yield(table.unpack(arg[i]))}
                print('After yield ' .. i .. ', _G.x has ' .. #_G.x .. ' elements')
                if #_G.x > 0 then
                  for j=1,#_G.x do
                    print('  _G.x[' .. j .. '] = ' .. tostring(_G.x[j]))
                  end
                end
              end
              return table.unpack(a)
            end
            
            f = coroutine.create(foo)
            local s,a,b,c,d
            s,a,b,c,d = coroutine.resume(f, {1,2,3}, {}, {1}, {'a', 'b', 'c'})
            print('First resume returned: s=' .. tostring(s) .. ' a=' .. tostring(a))
            
            -- Line 56 in coroutine.lua: assert(s and a == nil and coroutine.status(f) == "suspended")
            assert(s, "First resume should succeed")
            assert(a == nil, "First yield should return nil, got: " .. tostring(a))
            assert(coroutine.status(f) == "suspended", "Coroutine should be suspended")
            
            -- Continue with second resume (line 57-59)
            print('=== Second resume with no args ===')
            s,a,b,c,d = coroutine.resume(f)
            print('Second resume returned: s=' .. tostring(s) .. ' a=' .. tostring(a) .. ' b=' .. tostring(b))
            -- _G.x should be {} (empty table from resume with no args)
            print('_G.x length: ' .. #_G.x)
            assert(#_G.x == 0, "_G.x should be empty table, got length " .. #_G.x)
            assert(s and a == 1 and b == nil, "Second yield should return 1")
            
            return true
        """,
        )
    }

    @Test
    fun testCoroutineLuaExactFoo() {
        // This is the EXACT foo function from coroutine.lua (simplified)
        execute(
            """
            _G.x = nil
            _G.f = nil
            local function foo (a, ...)
              local arg = {...}
              for i=1,#arg do
                _G.x = {coroutine.yield(table.unpack(arg[i]))}
              end
              return table.unpack(a)
            end
            
            f = coroutine.create(foo)
            local s,a,b,c,d
            s,a,b,c,d = coroutine.resume(f, {1,2,3}, {}, {1}, {'a', 'b', 'c'})
            assert(s and a == nil, "First yield should return nil")
            s,a,b,c,d = coroutine.resume(f)
            print("_G.x length: " .. #_G.x)
            assert(#_G.x == 0, "_G.x should be empty table, got length " .. #_G.x)
            
            return true
        """,
        )
    }
}
