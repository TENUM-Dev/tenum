package com.myorg

import io.kotest.matchers.string.shouldContain
import org.junit.Before

class CommonConventionPluginTest : PluginTest() {
    @Before
    fun init() {
        buildFile.appendText(
            """
            plugins {
                id("tenum.conventions.common")
            }
        """,
        )
    }

    // @Test
    fun `test if build is fails correct`() {
        val result = runTask("build")
        result.output shouldContain
            """
            w: The following Kotlin source sets were configured but not added to any Kotlin compilation:
             * commonMain
             * commonTest
            """.trimIndent()
    }
}
