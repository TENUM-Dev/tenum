package ai.tenum.lua.compat

import kotlin.test.Test

/**
 * Test for db.lua:1033 - binary chunk with long source name should not duplicate source in nested functions
 */
class BinaryChunkSizeTest : LuaCompatTestBase() {
    @Test
    fun testBinaryChunkSizeWithLongSourceName() =
        runTest {
            // Test from db.lua:1022-1039
            // Binary chunk should not repeat the source name for each nested function
            execute(
                """
                local prog = [[
                  return function (x)
                    return function (y) 
                      return x + y
                    end
                  end
                ]]
                local name = string.rep("x", 1000)
                local p = assert(load(prog, name))
                
                -- Load 'p' as a binary chunk with debug information
                local c = string.dump(p)
                
                -- The chunk should be > 1000 bytes but < 2000 bytes
                -- This ensures that the source name (1000 'x' chars) is not repeated for each nested function
                assert(#c > 1000, "Chunk size should be > 1000, got: " .. #c)
                assert(#c < 2000, "Chunk size should be < 2000, got: " .. #c .. " (source name may be duplicated for nested functions)")
                
                -- Verify the function still works
                local f = assert(load(c))
                local g = f()
                local h = g(3)
                assert(h(5) == 8, "Function should work correctly")
                
                -- Verify all functions have the correct source
                assert(debug.getinfo(f).source == name, "f should have source = name")
                assert(debug.getinfo(g).source == name, "g should have source = name")
                assert(debug.getinfo(h).source == name, "h should have source = name")
                """,
            )
        }
}
