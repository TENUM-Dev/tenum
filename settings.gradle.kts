rootProject.name = "tenum"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://git.plantiwork.i234.me/api/packages/Plantitude.ai/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Authorization"
                value = "token " +
                    System.getenv("PLANTITUDE_PACKAGE_READ_TOKEN").let {
                        if (it.isNullOrEmpty()) {
                            throw IllegalStateException("PLANTITUDE_PACKAGE_READ_TOKEN environmentVariable not set")
                        } else {
                            it
                        }
                    }
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

val isCiServer = System.getenv().containsKey("CI")

buildCache {
    local {
        isEnabled = false
    }
    remote(HttpBuildCache::class) {
        url = uri("https://buildcache.plantiwork.i234.me/cache/")
        if (isCiServer) {
            isPush = true
            this.credentials.username = System.getenv()["GRADLE_CACHE_USER"]
            this.credentials.password = System.getenv()["GRADLE_CACHE_PASSWORD"]
        } else {
            isPush = false
            this.credentials.username = "developer"
            this.credentials.password = "\$TD6d=%qP6o;LLIkbyQ1"
        }
    }
}

include(
    ":cli",
    ":clinpm",
)
