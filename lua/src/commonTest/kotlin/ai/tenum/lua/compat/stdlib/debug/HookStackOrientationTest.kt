package ai.tenum.lua.compat.stdlib.debug

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test

/**
 * Isolated unit tests for debug hook stack orientation and ftransfer/ntransfer fields.
 * These tests help identify the exact issue with hook execution.
 */
class HookStackOrientationTest : LuaCompatTestBase() {
    @Test
    fun testHookReceivesCorrectStackLevel() =
        runTest {
            // Test that debug.getinfo(2) from inside a hook points to the correct function
            execute(
                """
                local function target()
                    return 42
                end
                
                local hookCalled = false
                local correctLevel = false
                
                local function hook(event)
                    hookCalled = true
                    local info = debug.getinfo(2, "n")
                    if info and info.name == "target" then
                        correctLevel = true
                    end
                end
                
                debug.sethook(hook, "c")
                target()
                debug.sethook()
                
                assert(hookCalled, "hook should have been called")
                assert(correctLevel, "level 2 should point to target function")
                """,
            )
        }

    @Test
    fun testNativeFunctionCallHookBasic() =
        runTest {
            // Test that a CALL hook fires for native functions
            execute(
                """
                local callHookFired = false
                
                local function hook(event)
                    if event == "call" then
                        callHookFired = true
                    end
                end
                
                debug.sethook(hook, "c")
                math.sin(3)
                debug.sethook()
                
                assert(callHookFired, "CALL hook should fire for math.sin")
                """,
            )
        }

    @Test
    fun testNativeFunctionReturnHookBasic() =
        runTest {
            // Test that a RETURN hook fires for native functions
            execute(
                """
                local returnHookFired = false
                
                local function hook(event)
                    if event == "return" then
                        returnHookFired = true
                    end
                end
                
                debug.sethook(hook, "r")
                math.sin(3)
                debug.sethook()
                
                assert(returnHookFired, "RETURN hook should fire for math.sin")
                """,
            )
        }

    @Test
    fun testCallHookCanAccessParameter() =
        runTest {
            // Test that parameters are accessible via getlocal in CALL hook
            execute(
                """
                local paramValue = nil
                local captured = false
                
                local function hook(event)
                    if event == "call" and not captured then
                        local _, v = debug.getlocal(2, 1)
                        paramValue = v
                        captured = true
                    end
                end
                
                debug.sethook(hook, "c")
                math.sin(3.14)
                debug.sethook()
                
                assert(paramValue == 3.14, "should access parameter via getlocal(2, 1)")
                """,
            )
        }

    @Test
    fun testReturnHookCanAccessReturnValue() =
        runTest {
            // Test that return values are accessible via getlocal in RETURN hook
            execute(
                """
                local returnValue = nil
                local expectedValue = math.sin(3.14)
                
                local function hook(event)
                    if event == "return" then
                        local _, v = debug.getlocal(2, 2)
                        returnValue = v
                    end
                end
                
                debug.sethook(hook, "r")
                math.sin(3.14)
                debug.sethook()
                
                assert(returnValue == expectedValue, "should access return value via getlocal")
                """,
            )
        }

    @Test
    fun testCallHookFtransferNtransferSingleParam() =
        runTest {
            // Test ftransfer and ntransfer for single parameter
            execute(
                """
                local ft, nt = nil, nil
                local captured = false
                
                local function hook(event)
                    if event == "call" and not captured then
                        local ar = debug.getinfo(2, "r")
                        ft = ar.ftransfer
                        nt = ar.ntransfer
                        captured = true  -- Only capture first call
                    end
                end
                
                debug.sethook(hook, "c")
                math.sin(3)
                debug.sethook()
                
                assert(ft == 1, "ftransfer should be 1 for first parameter")
                assert(nt == 1, "ntransfer should be 1 for single parameter")
                """,
            )
        }

    @Test
    fun testCallHookFtransferNtransferMultipleParams() =
        runTest {
            // Test ftransfer and ntransfer for multiple parameters
            execute(
                """
                local ft, nt = nil, nil
                local captured = false
                
                local function hook(event)
                    if event == "call" and not captured then
                        local ar = debug.getinfo(2, "r")
                        ft = ar.ftransfer
                        nt = ar.ntransfer
                        captured = true  -- Only capture first call
                    end
                end
                
                debug.sethook(hook, "c")
                select(2, 10, 20, 30, 40)
                debug.sethook()
                
                assert(ft == 1, "ftransfer should be 1")
                assert(nt == 5, "ntransfer should be 5 for five parameters")
                """,
            )
        }

    @Test
    fun testReturnHookFtransferNtransferSingleReturn() =
        runTest {
            // Test ftransfer and ntransfer for single return value
            execute(
                """
                local ft, nt = nil, nil
                
                local function hook(event)
                    if event == "return" then
                        local ar = debug.getinfo(2, "r")
                        ft = ar.ftransfer
                        nt = ar.ntransfer
                    end
                end
                
                debug.sethook(hook, "r")
                math.sin(3)
                debug.sethook()
                
                print("RETURN hook: ftransfer=" .. tostring(ft) .. ", ntransfer=" .. tostring(nt))
                assert(ft == 2, "ftransfer should be 2 (1 param + 1)")
                assert(nt == 1, "ntransfer should be 1 for single return value")
                """,
            )
        }

    @Test
    fun testReturnHookFtransferNtransferMultipleReturns() =
        runTest {
            // Test ftransfer and ntransfer for multiple return values
            execute(
                """
                local ft, nt = nil, nil
                
                local function hook(event)
                    if event == "return" then
                        local ar = debug.getinfo(2, "r")
                        ft = ar.ftransfer
                        nt = ar.ntransfer
                    end
                end
                
                debug.sethook(hook, "r")
                select(2, 10, 20, 30, 40)
                debug.sethook()
                
                print("RETURN hook for select: ftransfer=" .. tostring(ft) .. ", ntransfer=" .. tostring(nt))
                -- select(2, 10, 20, 30, 40) returns 20, 30, 40 (3 values)
                -- ftransfer should point after the 5 parameters + any locals
                assert(nt == 3, "ntransfer should be 3 for three return values")
                """,
            )
        }

    @Test
    fun testCallAndReturnHookSequence() =
        runTest {
            // Test that both CALL and RETURN hooks fire in sequence
            execute(
                """
                local callFt, callNt = nil, nil
                local returnFt, returnNt = nil, nil
                local capturedCall = false
                local capturedReturn = false
                
                local function hook(event)
                    -- Get info about the function that triggered the hook
                    local ar = debug.getinfo(2, "nSr")
                    
                    -- Skip if this is the hook function itself
                    if ar.name == "hook" then return end
                    
                    -- Skip debug.sethook calls
                    if ar.name == "sethook" then return end
                    
                    if event == "call" and not capturedCall then
                        callFt = ar.ftransfer
                        callNt = ar.ntransfer
                        capturedCall = true
                    elseif event == "return" and not capturedReturn then
                        returnFt = ar.ftransfer
                        returnNt = ar.ntransfer
                        capturedReturn = true
                    end
                end
                
                debug.sethook(hook, "cr")
                math.sin(3)
                debug.sethook()
                
                assert(callFt == 1 and callNt == 1, "CALL hook should have ft=1, nt=1")
                assert(returnFt == 2 and returnNt == 1, "RETURN hook should have ft=2, nt=1")
                """,
            )
        }

    @Test
    fun testGetLocalWithFtransferNtransferCall() =
        runTest {
            // Test collecting parameters using ftransfer/ntransfer in CALL hook
            execute(
                """
                local collected = {}
                
                local function hook(event)
                    if event == "call" then
                        local ar = debug.getinfo(2, "r")
                        for i = ar.ftransfer, ar.ftransfer + ar.ntransfer - 1 do
                            local _, v = debug.getlocal(2, i)
                            table.insert(collected, v)
                        end
                    end
                end
                
                debug.sethook(hook, "c")
                select(2, 10, 20, 30, 40)
                debug.sethook()
                
                print("Collected " .. #collected .. " parameters")
                assert(#collected == 5, "should collect 5 parameters")
                assert(collected[1] == 2, "first param should be 2")
                assert(collected[2] == 10, "second param should be 10")
                assert(collected[5] == 40, "fifth param should be 40")
                """,
            )
        }

    @Test
    fun testGetLocalWithFtransferNtransferReturn() =
        runTest {
            // Test collecting return values using ftransfer/ntransfer in RETURN hook
            execute(
                """
                local collected = {}
                
                local function hook(event)
                    if event == "return" then
                        local ar = debug.getinfo(2, "r")
                        for i = ar.ftransfer, ar.ftransfer + ar.ntransfer - 1 do
                            local _, v = debug.getlocal(2, i)
                            table.insert(collected, v)
                        end
                    end
                end
                
                debug.sethook(hook, "r")
                select(2, 10, 20, 30, 40)
                debug.sethook()
                
                print("Collected " .. #collected .. " return values")
                assert(#collected == 3, "should collect 3 return values")
                assert(collected[1] == 20, "first return should be 20")
                assert(collected[2] == 30, "second return should be 30")
                assert(collected[3] == 40, "third return should be 40")
                """,
            )
        }

    @Test
    fun testConditionalHookDoesNotCorruptState() =
        runTest {
            // Test that conditionally enabling/disabling hooks doesn't corrupt state
            execute(
                """
                local on = false
                local inp, out = nil, nil
                
                local function hook(event)
                    if not on then return end
                    local ar = debug.getinfo(2, "r")
                    local t = {}
                    for i = ar.ftransfer, ar.ftransfer + ar.ntransfer - 1 do
                        local _, v = debug.getlocal(2, i)
                        table.insert(t, v)
                    end
                    if event == "return" then
                        out = t
                    else
                        inp = t
                    end
                end
                
                debug.sethook(hook, "cr")
                
                -- First call with hook enabled
                on = true
                math.sin(3)
                on = false
                
                assert(inp and #inp == 1 and inp[1] == 3, "first call: inp should be {3}")
                assert(out and #out == 1, "first call: out should have 1 value")
                
                -- Second call with hook disabled
                math.sin(5)
                
                -- Values should not have changed
                assert(inp[1] == 3, "inp should still be {3} after disabled call")
                
                -- Third call with hook enabled again
                on = true
                math.sin(7)
                on = false
                
                assert(inp and #inp == 1 and inp[1] == 7, "third call: inp should be {7}")
                
                debug.sethook()
                """,
            )
        }

    @Test
    fun testMultipleConsecutiveHookedCalls() =
        runTest {
            // Test multiple hooked calls in sequence
            execute(
                """
                local calls = {}
                local returns = {}
                
                local function hook(event)
                    local ar = debug.getinfo(2, "nSr")
                    
                    -- Skip hook function itself and debug.sethook
                    if ar.name == "hook" or ar.name == "sethook" then return end
                    
                    local t = {}
                    for i = ar.ftransfer, ar.ftransfer + ar.ntransfer - 1 do
                        local _, v = debug.getlocal(2, i)
                        table.insert(t, v)
                    end
                    
                    if event == "call" then
                        table.insert(calls, {ft=ar.ftransfer, nt=ar.ntransfer, values=t})
                    elseif event == "return" then
                        table.insert(returns, {ft=ar.ftransfer, nt=ar.ntransfer, values=t})
                    end
                end
                
                debug.sethook(hook, "cr")
                
                math.sin(3)
                math.sin(5)
                select(2, 10, 20)
                
                debug.sethook()
                
                assert(#calls == 3, "should have 3 CALL hooks")
                assert(#returns == 3, "should have 3 RETURN hooks")
                
                -- Check first call
                assert(calls[1].nt == 1 and calls[1].values[1] == 3, "first call should have param 3")
                
                -- Check second call
                assert(calls[2].nt == 1 and calls[2].values[1] == 5, "second call should have param 5")
                
                -- Check third call
                assert(calls[3].nt == 3, "third call should have 3 params")
                assert(calls[3].values[1] == 2 and calls[3].values[2] == 10, "third call params")
                """,
            )
        }

    @Test
    fun testLuaFunctionVsNativeFunctionHooks() =
        runTest {
            // Test that both Lua and native functions report ftransfer/ntransfer correctly
            execute(
                """
                local luaCallFt, luaCallNt = nil, nil
                local nativeCallFt, nativeCallNt = nil, nil
                local capturedLua = false
                local capturedNative = false
                
                local function luaFunc(a, b)
                    return a + b
                end
                
                local function hook(event)
                    if event == "call" then
                        local ar = debug.getinfo(2, "nSr")
                        if ar.name == "luaFunc" and not capturedLua then
                            luaCallFt = ar.ftransfer
                            luaCallNt = ar.ntransfer
                            capturedLua = true
                        elseif ar.what == "C" and ar.name ~= "sethook" and not capturedNative then
                            nativeCallFt = ar.ftransfer
                            nativeCallNt = ar.ntransfer
                            capturedNative = true
                        end
                    end
                end
                
                debug.sethook(hook, "c")
                
                luaFunc(10, 20)
                math.sin(3)
                
                debug.sethook()
                
                assert(luaCallFt == 1 and luaCallNt == 2, "Lua function should have ft=1, nt=2")
                assert(nativeCallFt == 1 and nativeCallNt == 1, "Native function should have ft=1, nt=1")
                """,
            )
        }

    @Test
    fun testLuaFunctionTailCall() =
        runTest {
            // Test Lua function with tail call using db.lua pattern
            // Matches official Lua 5.4.8 test suite behavior
            execute(
                """
                local function eqseq (t1, t2)
                  assert(#t1 == #t2, "tables have different sizes: " .. #t1 .. " vs " .. #t2)
                  for i = 1, #t1 do
                    assert(t1[i] == t2[i], "difference at index " .. i .. ": " .. tostring(t1[i]) .. " vs " .. tostring(t2[i]))
                  end
                end

                local on = false
                local inp, out

                local function hook (event)
                  if not on then return end
                  local ar = debug.getinfo(2, "ruS")
                  local t = {}
                  for i = ar.ftransfer, ar.ftransfer + ar.ntransfer - 1 do
                    local _, v = debug.getlocal(2, i)
                    t[#t + 1] = v 
                  end
                  if event == "return" then
                    out = t
                  else
                    inp = t
                  end
                end

                debug.sethook(hook, "cr")

                local function foo (a, ...) return ... end
                local function foo1 () on = not on; return foo(20, 10, 0) end
                foo1(); on = false

                -- Tail call transfers only fixed parameters: inp = {20} (parameter 'a')
                -- Return transfers varargs: out = {10, 0}
                eqseq(inp, {20})
                eqseq(out, {10, 0})

                debug.sethook()
                """,
            )
        }
}
