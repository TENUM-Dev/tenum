import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.`maven-publish`

plugins {
    `maven-publish`
}

val newVersion = project.version
println("Used Version: $newVersion")

allprojects {
    version = newVersion
    if (!this.file("src").exists()) {
        return@allprojects
    }
    apply(plugin = "maven-publish")
    configure<PublishingExtension> {
        repositories {
            maven {
                name = "Gitea"
                url = uri("https://git.plantiwork.i234.me/api/packages/Plentitude.ai/maven")
                credentials(HttpHeaderCredentials::class) {
                    name = "Authorization"
                    value = "token " + System.getenv("PUBLICATION_TOKEN")
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
        }
    }
}
