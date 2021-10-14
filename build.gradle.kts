buildscript {
    val kotlinVersion: String by rootProject.extra("1.5.31")
    val composeVersion: String by rootProject.extra("1.1.0-alpha06")
    val hiltVersion: String by rootProject.extra("2.39.1")

    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.0.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${hiltVersion}")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

subprojects {
    // Taken from:
    // https://github.com/chrisbanes/tivi/blob/main/build.gradle
    configurations.configureEach {
        // We forcefully exclude AppCompat + MDC from any transitive dependencies.
        // This is a Compose app, so there's no need for these.
        exclude(group = "androidx.appcompat", module = "appcompat")
        exclude(group = "com.google.android.material", module = "material")
    }
}

plugins {
    id("com.github.ben-manes.versions") version "0.39.0"
}

tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java).configure {
    rejectVersionIf {
        candidate.version.contains("alpha") && !this@rejectVersionIf.currentVersion.contains("alpha")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}