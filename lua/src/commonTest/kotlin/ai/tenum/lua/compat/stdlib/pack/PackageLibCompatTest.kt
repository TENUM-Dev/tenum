package ai.tenum.lua.compat.stdlib.pack

import ai.tenum.lua.compat.LuaCompatTestBase
import okio.Path.Companion.toPath
import kotlin.test.Test

/**
 * Compatibility tests for the `package` library (require, package.loaded, package.preload, package.path)
 */
class PackageLibCompatTest : LuaCompatTestBase() {
    @Test
    fun testPackageTablesExist() =
        runTest {
            // Ensure package tables and fields exist
            execute("assert(type(package) == 'table')")
            execute("assert(type(package.loaded) == 'table')")
            execute("assert(type(package.preload) == 'table')")
            execute("assert(type(package.path) == 'string')")
            execute("assert(type(package.cpath) == 'string' or package.cpath == nil)")
            execute("assert(type(package.config) == 'string' or package.config == nil)")
        }

    @Test
    fun testRequireBuiltinModules() =
        runTest {
            // require builtin libraries should return their tables
            execute("assert(require('string') == string)")
            execute("assert(require('math') == math)")
        }

    @Test
    fun testRequireLoadsFromPreload() =
        runTest {
            // Use package.preload to register a module loader
            execute("package.preload['mymod'] = function() return {value = 123} end")
            execute("local m = require('mymod'); assert(type(m) == 'table' and m.value == 123)")
        }

    @Test
    fun testRequireLoadsFromFilesystem() =
        runTest {
            // Write a module file into the fake filesystem and require it
            val content =
                """
                return { answer = 42 }
                """.trimIndent()
            fileSystem.write("mymod.lua".toPath()) { writeUtf8(content) }

            // require should find and load the module from package.path
            execute("local m = require('mymod'); assert(type(m) == 'table' and m.answer == 42)")
        }

    @Test
    fun testRequireCachesModule() =
        runTest {
            val content =
                """
                local t = {}
                t.count = 0
                t.inc = function() t.count = t.count + 1 end
                return t
                """.trimIndent()
            fileSystem.write("counter.lua".toPath()) { writeUtf8(content) }

            execute("local a = require('counter'); a.inc(); local b = require('counter'); assert(a == b and b.count == 1)")
        }
}
