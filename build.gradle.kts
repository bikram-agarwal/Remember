buildscript {
    configurations.classpath {
        resolutionStrategy {
            force(
                "io.github.detekt.sarif4k:sarif4k:0.7.0",
                "io.github.detekt.sarif4k:sarif4k-jvm:0.7.0",
                "io.github.oshai:kotlin-logging:8.0.03",
            )
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.versions)
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
    outputFormatter = "plain"
    checkForGradleUpdate = true
    rejectVersionIf {
        val candidateVersionText = candidate.version.lowercase()
        val currentVersionText = currentVersion.lowercase()
        val candidateIsUnstable =
            listOf("alpha", "beta", "rc", "cr", "m", "preview", "dev")
                .any { qualifier -> candidateVersionText.contains(qualifier) }
        val currentIsStable =
            listOf("alpha", "beta", "rc", "cr", "m", "preview", "dev")
                .none { qualifier -> currentVersionText.contains(qualifier) }

        candidateIsUnstable && currentIsStable
    }
}

tasks.register<Exec>("checkDependencyUpdates") {
    group = "help"
    description = "Runs dependencyUpdates with the flags required by this project."
    val gradleExecutable =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            ".\\gradlew.bat"
        } else {
            "./gradlew"
        }
    commandLine(gradleExecutable, "--no-parallel", "--no-configuration-cache", "dependencyUpdates")
}
