import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("org.zkovari.changelog")
    id("com.diffplug.spotless-changelog")
    id("org.nosphere.gradle.github.actions")
}

tasks.create("createInitialChangelogEntry") {
    group = "changelog"
    description = "Creates the initial changelog entry"
    doLast {
        if (githubActions.running.get()) {
            val env = githubActions.environment
            val eventData =
                Json.parseToJsonElement(
                    file(env.eventPath.get().toString()).readText(),
                )
            if (eventData.jsonObject["pull_request"] == null) {
                throw IllegalStateException("This is not a pull request")
            }
            val pullRequestData = eventData.jsonObject["pull_request"]!!.jsonObject
            val branchName =
                githubActions.environment.headRef
                    .get()
                    .split("/")
                    .last()
            val branchType =
                githubActions.environment.headRef
                    .get()
                    .split("/")
                    .first()
            val entryType =
                when (branchType) {
                    "feature" -> "added"
                    "bugfix" -> "fixed"
                    "hotfix" -> "fixed"
                    "renovate" -> "changed"
                    else -> "added"
                }
            val pullRequestLink = pullRequestData["html_url"]!!.jsonPrimitive.content
            val timestamp = SimpleDateFormat("yyyy-MM-dd-SS-mm").format(Date())
            val entryFile =
                file(
                    "./changelogs/unreleased/$branchName-$timestamp.yml",
                )
            entryFile.parentFile.mkdirs()
            val entry =
                """
                # Automatic changelog entry
                ---
                title: ${pullRequestData["title"]!!.jsonPrimitive.content}
                reference: '[\#${pullRequestLink.split("/").last()}]($pullRequestLink)'
                author: ${githubActions.environment.actor.get()}
                type: $entryType
                """.trimIndent()
            println(entryFile.name)
            println(entry)
            entryFile.writeText(entry)
        } else {
            val entryFile =
                file(
                    "./changelogs/unreleased/init.yml",
                )
            entryFile.parentFile.mkdirs()
            entryFile.writeText(
                """
                # Automatic changelog entry
                ---
                title: Automatic changelog entry
                reference: 
                author: Jochen Guck
                type: added
                """.trimIndent(),
            )
        }
    }
}

tasks.create("addChangelogEntriesAsUnreleased") {
    group = "changelog"
    description = "Fixes the changelog"
    doLast {
        val originalSegment =
            "\n" +
                "## [$version] - " +
                SimpleDateFormat("yyyy-MM-dd").format(Date()) +
                "\n"
        println(originalSegment)
        val changelog = file("CHANGELOG.md")
        val changelogContent = changelog.readText()
        val newChangelogContent =
            changelogContent
                .replace(originalSegment, "")
                .replace(originalSegment.replace("\n", "\r\n"), "")
        changelog.writeText(newChangelogContent)
    }
    dependsOn(
        "processChangelogEntries",
    )
}

spotlessChangelog {
    changelogFile("CHANGELOG.md")
    enforceCheck(true)
}
