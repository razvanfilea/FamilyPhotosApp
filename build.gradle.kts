plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    id("com.github.ben-manes.versions") version "0.54.0"
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.baselineprofile) apply false
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            jvmToolchain(21)
        }
    }
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
