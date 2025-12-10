package com.myorg

import io.kotest.matchers.string.shouldContain
import org.junit.Before
import org.junit.Test

class MppLibConventionPluginTest : PluginTest() {
    @Before
    fun init() {
        buildFile.appendText(
            """
            plugins {
                id("tenum.conventions.mpplib")
            }
        """,
        )
    }

    @Test
    fun `test if build runs`() {
        val result = runTask("build")
        result.output shouldContain "BUILD SUCCESSFUL"
    }
}
