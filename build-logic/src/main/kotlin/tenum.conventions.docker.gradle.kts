import org.jetbrains.kotlin.gradle.targets.js.toHex
import java.security.MessageDigest

plugins {
    id("com.palantir.docker")
    id("org.nosphere.gradle.github.actions")
}

val env = System.getenv()
val isCiServer = env.containsKey("CI")

docker {
    if (isCiServer) {
        val inputFiles =
            fileTree(layout.buildDirectory.dir("docker"))
                .sortedBy { it.name }
        val digest = MessageDigest.getInstance("SHA-256")
        inputFiles.forEach { digest.update(it.readBytes()) }
        val shaHash = "sha-${digest.digest().toHex()}"
        val imageName = "git.plantiwork.i234.me/plentitude.ai/${project.name}"
        val ovhImageName = "svgnok0u.c1.de1.container-registry.ovh.net/tenum/${project.name}"
        name = imageName
        tag("hash", "$imageName:$shaHash")
        tag("latest", "$imageName:latest")
        tag("ovhHash", "$ovhImageName:$shaHash")
        tag("ovhLatest", "$ovhImageName:latest")
        if (githubActions.running.get()) {
            githubActions.environment.runNumber.orNull?.let { runNumber ->
                tag("runNumber", "$imageName:$runNumber")
                tag("ovhRunNumber", "$ovhImageName:$runNumber")
            }
        }
    } else {
        val imageName = project.name
        name = imageName
        tag("latest", "$imageName:latest")
    }
    this.setDockerfile(File("Dockerfile"))
}
