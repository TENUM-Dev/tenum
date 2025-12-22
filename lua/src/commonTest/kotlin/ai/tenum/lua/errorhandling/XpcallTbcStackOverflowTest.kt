package ai.tenum.lua.errorhandling

import ai.tenum.lua.compat.LuaCompatTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class XpcallTbcStackOverflowTest : LuaCompatTestBase() {
    @Test
    fun testXpcallWithTbcErrorHandlerDuringStackOverflow() {
        val result =
            execute(
                """
                local obj, flag
                
                -- function to force a stack overflow
                local function overflow()
                    overflow() -- infinite recursion
                end
                
                -- error handler will create tbc variable handling a stack overflow,
                -- and will set flag[1] when closed
                local function errorh(m)
                    assert(string.find(m, "stack overflow"))
                    -- Return a table with a __close metamethod
                    local x <close> = setmetatable({}, {
                        __close = function(o)
                            o[1] = 10 -- Set o[1] to 10 when __close is called
                        end
                    })
                    return x
                end
                
                local function foo()
                    flag = setmetatable({}, {
                        __close = function(o) o[1] = 100 end
                    })
                    local y <close> = flag
                    local st, o = xpcall(overflow, errorh)
                    obj = o
                    return st, obj
                end
                
                local co = coroutine.wrap(foo)
                co()
                
                -- After coroutine completes, check values
                assert(not st and obj[1] == 10 and flag[1] == 100)
                
                return "PASS"
                """.trimIndent(),
            )

        assertEquals("PASS", result.toString())
    }

    @Test
    fun testXpcallTbcSimpleCase() {
        // This test should already pass - simpler case without stack overflow
        val result =
            execute(
                """
                local function errorh(m)
                    -- Return a table with a __close metamethod
                    local x <close> = setmetatable({}, {
                        __close = function(o)
                            o[1] = 10
                        end
                    })
                    return x
                end
                
                local function fail()
                    error("test error")
                end
                
                local st, obj = xpcall(fail, errorh)
                
                assert(st == false, "xpcall should return false")
                assert(obj[1] == 10, "obj[1] should be 10 from __close")
                
                return "PASS"
                """.trimIndent(),
            )

        assertEquals("PASS", result.toString())
    }
}
