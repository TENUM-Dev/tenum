package com.myorg

import io.kotest.matchers.string.shouldContain
import org.junit.Before
import org.junit.Test

class NpmLibConventionPluginTest : PluginTest() {
    @Before
    fun init() {
        buildFile.appendText(
            """
            plugins {
                id("tenum.conventions.npmlib")
            }
        """,
        )
        testProjectDir.newFolder("src", "commonMain", "kotlin")
        testProjectDir.newFile("src/commonMain/kotlin/Hello.kt").writeText(
            """
            fun hello(): String = "Hello, World!"
        """,
        )
    }

    @Test
    fun `test if build runs`() {
        val result = runTask("packJsPackage")
        result.output shouldContain "BUILD SUCCESSFUL"
    }
}
