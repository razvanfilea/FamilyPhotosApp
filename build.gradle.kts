plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("com.github.ben-manes.versions") version "0.52.0"
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.baselineprofile) apply false
}

tasks.named(
    "dependencyUpdates",
    com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java
).configure {
    rejectVersionIf {
        (candidate.version.contains("alpha", true) && !currentVersion.contains("alpha", true)) ||
                (candidate.version.contains("beta", true) && !currentVersion.contains("beta", true))
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}