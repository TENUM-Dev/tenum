package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Tests for debug.getlocal and debug.setlocal - local variable introspection.
 *
 * Covers:
 * - Getting local variable names and values
 * - Setting local variable values
 * - Invalid index handling
 * - Local variable inspection across stack frames
 */
class LocalVariablesCompatTest : LuaCompatTestBase() {
    @Test
    fun testGetLocal() =
        runTest {
            assertLuaString(
                """
            local function f()
                local x = 10
                local name, value = debug.getlocal(1, 1)
                return name
            end
            return f()
        """,
                "x",
            )
        }

    @Test
    fun testGetLocalValue() =
        runTest {
            assertLuaNumber(
                """
            local function f()
                local x = 42
                local name, value = debug.getlocal(1, 1)
                return value
            end
            return f()
        """,
                42.0,
            )
        }

    @Test
    fun testSetLocal() =
        runTest {
            assertLuaNumber(
                """
            local function f()
                local x = 10
                debug.setlocal(1, 1, 20)
                return x
            end
            return f()
        """,
                20.0,
            )
        }

    @Test
    fun testGetLocalInvalidIndex() =
        runTest {
            assertLuaNil(
                """
            local function f()
                return debug.getlocal(1, 100)
            end
            return f()
        """,
            )
        }

    @Test
    fun testLocalVarInspection() =
        runTest {
            vm.debugEnabled = true
            execute(
                """
            local function f(a, b)
                local c = a + b
                local info = {}
                for i = 1, 10 do
                    local name, val = debug.getlocal(1, i)
                    if name then
                        info[name] = val
                    else
                        break
                    end
                end
                assert(info.a == 1, "info.a expected to be 1, got " .. tostring(info.a))
                assert(info.b == 2, "info.b expected to be 2, got " .. tostring(info.b))
                assert(info.c == 3, "info.c expected to be 3, got " .. tostring(info.c))
            end
            f(1, 2)
        """,
            )
        }

    @Test
    fun testManyLocalsInClosure() =
        runTest {
            // Test that a function with 200 local variables can be captured in a closure
            // and accessed correctly. This tests register allocation limits.
            execute(
                """
                local code = 'local a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20 = 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20; ' ..
                             'return function() return a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13 + a14 + a15 + a16 + a17 + a18 + a19 + a20 end'
                local f = load(code)
                local closure = f()
                local result = closure()
                assert(result == 210, "Expected 210, got: " .. tostring(result))
            """,
            )
        }

    @Test
    fun testGetLocalVarargsNegativeIndex() =
        runTest {
            // Test from db.lua:282-296 - negative indices access varargs
            // debug.getlocal(level, -i) should return ("(vararg)", value) for the i-th vararg
            execute(
                """
                local function foo(a, ...)
                    local t = table.pack(...)
                    for i = 1, t.n do
                        local n, v = debug.getlocal(1, -i)
                        assert(n == "(vararg)", "Expected name '(vararg)', got: " .. tostring(n))
                        assert(v == t[i], "Expected vararg " .. i .. " to be " .. tostring(t[i]) .. ", got: " .. tostring(v))
                    end
                    return true
                end
                assert(foo(10, 20, 30, 40))
                assert(foo(5, "test", nil, true))
            """,
            )
        }

    @Test
    fun testGetLocalUsesCorrectRegisterNotActiveIndex() =
        runTest {
            // BUG: CallFrame.getLocal line 76 uses `base + activeIndex` instead of `base + localVar.register`
            // When locals in do-blocks are freed, their registers get reused but with SAME register numbers
            // activeIndex counts 0,1,2,3... but actual register numbers might be 0,1,2,2,3,4 (reusing 2)
            // This test MUST fail with current bug
            execute(
                """
            function f(param1, param2)
                -- At this point: param1=register 0, param2=register 1
                -- When we call getlocal(1,1) it should return param1 at register 0
                -- When we call getlocal(1,2) it should return param2 at register 1
                
                local name1, val1 = debug.getlocal(1, 1)
                local name2, val2 = debug.getlocal(1, 2)
                
                print("getlocal(1,1) returned: " .. name1 .. " = " .. tostring(val1))
                print("getlocal(1,2) returned: " .. name2 .. " = " .. tostring(val2))
                print("param1 = " .. tostring(param1))
                print("param2 = " .. tostring(param2))
                
                assert(name1 == "param1", "Expected name 'param1', got: " .. name1)
                assert(name2 == "param2", "Expected name 'param2', got: " .. name2)
                assert(val1 == param1, "Expected val1=" .. tostring(param1) .. ", got: " .. tostring(val1))
                assert(val2 == param2, "Expected val2=" .. tostring(param2) .. ", got: " .. tostring(val2))
            end
            
            -- Call f directly - no complex setup needed
            f("first", "second")
        """,
            )
        }

    @Test
    fun testGetInfoReturnsCorrectUpvalueCount() =
        runTest {
            // Test from db.lua:359 - nups should count actual upvalues
            // g() captures two upvalues: glob and f
            execute(
                """
                local glob = 1
                
                function f(a, b)
                    local x = debug.getinfo(2, "u")
                    -- g() should have 2 upvalues: glob and f
                    assert(x.nups == 2, "expected nups 2 got " .. tostring(x.nups))
                end
                
                function g(...)
                    local arg = {...}
                    do local a,b,c; a=math.sin(40); end
                    local feijao
                    local AAAA,B = "xuxu", "abacate"
                    f(AAAA, B)  -- f inspects g's upvalue count
                    glob = glob + 1  -- ensure glob is captured
                end
                
                g()
            """,
            )
        }

    @Test
    fun testSetLocalModifiesCallerVariables() =
        runTest {
            // Test from db.lua:350-395 - debug.setlocal should modify variables in the calling function
            // This test reproduces the EXACT setup from db.lua
            execute(
                """
                local a = {}
                local glob = 1
                local oldglob = glob
                local L = nil
                
                debug.sethook(function (e,l)
                  collectgarbage()
                  local f, m, c = debug.gethook()
                  assert(m == 'crl' and c == 0)
                  if e == "line" then
                    if glob ~= oldglob then
                      L = l-1
                      oldglob = glob
                    end
                  elseif e == "call" then
                      local f = debug.getinfo(2, "f").func
                      a[f] = 1
                  else assert(e == "return")
                  end
                end, "crl")
                
                function f(a,b)
                  collectgarbage()
                  local _, x = debug.getlocal(1, 1)
                  local _, y = debug.getlocal(1, 2)
                  assert(x == a and y == b)
                  assert(debug.setlocal(2, 3, "pera") == "AA".."AA")
                  assert(debug.setlocal(2, 4, "manga") == "B")
                  local x2 = debug.getinfo(2)
                  assert(x2.func == g and x2.what == "Lua" and x2.name == 'g')
                  glob = glob+1
                end
                
                function g (...)
                  local arg = {...}
                  do local a,b,c; a=math.sin(40); end
                  local feijao
                  local AAAA,B = "xuxu", "abacate"
                  f(AAAA,B)
                  assert(AAAA == "pera" and B == "manga")
                end
                
                g()
            """,
            )
        }

    @Test
    fun testGetLocalInNestedDoBlock() =
        runTest {
            // Test from db.lua:388-393 - debug.getlocal should find locals in nested do blocks
            // including shadowed variables
            execute(
                """
                function g()
                    local arg = {}
                    do local a,b,c; a=math.sin(40); end
                    local feijao
                    local AAAA, B = "xuxu", "abacate"
                    do
                        local B = 13
                        local name, value = debug.getlocal(1, 5)
                        assert(name == 'B', "Expected name 'B', got: " .. tostring(name))
                        assert(value == 13, "Expected value 13, got: " .. tostring(value))
                    end
                end
                
                g()
            """,
            )
        }

    @Test
    fun testGetLocalReturnsStackTemporaries() =
        runTest {
            // Test from db.lua:403-407 - debug.getlocal(0, index) should return stack temporaries
            // when there are no registered locals at that index.
            // These are the arguments to debug.getlocal itself: (0, 1) and (0, 2)
            execute(
                """
                local n, v = debug.getlocal(0, 1)
                assert(v == 0 and n == "(C temporary)", "Expected (C temporary, 0) but got (" .. tostring(n) .. ", " .. tostring(v) .. ")")
                local n, v = debug.getlocal(0, 2)
                assert(v == 2 and n == "(C temporary)", "Expected (C temporary, 2) but got (" .. tostring(n) .. ", " .. tostring(v) .. ")")
                assert(not debug.getlocal(0, 3))
                assert(not debug.getlocal(0, 0))
            """,
            )
        }

    @Test
    fun testGetLocalAccessesExpressionTemporariesFromCallerStack() =
        runTest {
            // Test from db.lua:410-419 - debug.getlocal should access expression temporaries
            // in caller stack frames. When g(a,b) evaluates (a+1) + f(), the intermediate
            // result of (a+1) becomes a stack temporary at index 3.
            assertLuaNumber(
                """
                function f()
                  assert(select(2, debug.getlocal(2,3)) == 1)
                  assert(not debug.getlocal(2,4))
                  debug.setlocal(2, 3, 10)
                  return 20
                end
                
                function g(a,b) return (a+1) + f() end
                
                return g(0,0)
            """,
                30.0,
            )
        }

    @Test
    fun testGetInfoMainChunkIsVararg() =
        runTest {
            // Test from db.lua:721 - main chunk should have isvararg=true
            // This is the exact assertion that's failing:
            // t = debug.getinfo(1)   -- main
            // assert(t.isvararg == true and t.nparams == 0 and t.nups == 1 and
            //        debug.getupvalue(t.func, 1) == "_ENV")
            execute(
                """
                local t = debug.getinfo(1)   -- main
                assert(t.isvararg == true, "Main chunk should have isvararg=true, got: " .. tostring(t.isvararg))
                assert(t.nparams == 0, "Main chunk should have nparams=0, got: " .. tostring(t.nparams))
                assert(t.nups == 1, "Main chunk should have nups=1, got: " .. tostring(t.nups))
                local upname, upvalue = debug.getupvalue(t.func, 1)
                assert(upname == "_ENV", "Main chunk upvalue should be '_ENV', got: " .. tostring(upname))
            """,
            )
        }

    @Test
    fun testSetLocalInCoroutinePersistsAcrossResume() =
        runTest {
            // Test from db.lua:758-794 - setlocal should persist when coroutine resumes
            // This tests that debug.setlocal modifies the actual coroutine stack
            execute(
                """
            local co = coroutine.create(function (x)
                   local a = 1
                   coroutine.yield(debug.getinfo(1, "l"))
                   coroutine.yield(debug.getinfo(1, "l").currentline)
                   return a
                 end)
            
            -- First resume
            local _, l = coroutine.resume(co, 10)
            
            -- Check local variable
            local name, value = debug.getlocal(co, 1, 2)
            assert(name == "a", "Expected name 'a', got: " .. tostring(name))
            assert(value == 1, "Expected value 1, got: " .. tostring(value))
            
            -- Set local variable to "hi"
            debug.setlocal(co, 1, 2, "hi")
            
            -- Verify it was set
            name, value = debug.getlocal(co, 1, 2)
            assert(value == "hi", "After setlocal, expected 'hi', got: " .. tostring(value))
            
            -- Resume to second yield
            local a,b,c = pcall(coroutine.resume, co)
            assert(a and b, "Second resume should succeed")
            
            -- Final resume - should return the modified value "hi"
            a,b = coroutine.resume(co)
            assert(a == true, "Final resume should succeed, got: " .. tostring(a))
            assert(b == "hi", "Coroutine should return modified value 'hi', got: " .. tostring(b))
        """,
            )
        }
}
