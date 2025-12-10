package ai.tenum.lua.compiler.stages

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.vm.LuaVmImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloseUpvalueCaptureTest {
    @Test
    fun testCloseHandlerCapturesParentLocal() {
        val vm = LuaVmImpl()
        // This Lua code mimics the failing pattern: __close handler mutates a parent local
        vm.debugEnabled = true
        val result =
            vm.execute(
                """
            local closed = false
            do
                local obj <close> = setmetatable({}, {
                    __close = function() closed = true end
                })
                goto skip
                local x = 1
                ::skip::
            end
            return closed
        """,
            )
        assertTrue(result is LuaBoolean)
        assertEquals(true, result.value, "__close handler should mutate parent local 'closed'")
    }
}
