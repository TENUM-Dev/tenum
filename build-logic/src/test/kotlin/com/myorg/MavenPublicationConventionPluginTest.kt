package com.myorg

import io.kotest.matchers.string.shouldContain
import org.junit.Before
import org.junit.Test

class MavenPublicationConventionPluginTest : PluginTest() {
    @Before
    fun init() {
        buildFile.appendText(
            """
            plugins {
                id("tenum.conventions.maven-publication")
            }
        """,
        )
    }

    @Test
    fun `test if publishToMavenLocal is working`() {
        val result = runTask("publishToMavenLocal")
        result.output shouldContain "BUILD SUCCESSFUL"
    }
}
