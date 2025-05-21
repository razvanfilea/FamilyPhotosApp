import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlinAndroid)

    kotlin("plugin.serialization") version libs.versions.kotlin.base.get()
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    alias(libs.plugins.compose.compiler)

    alias(libs.plugins.hilt)
}

android {
    compileSdk = 35
    namespace = "net.theluckycoder.familyphotos"

    defaultConfig {
        applicationId = "net.theluckycoder.familyphotos"
        minSdk = 30
        targetSdk = 35
        versionCode = 28
        versionName = "2.8.1"
    }

    androidResources {
        localeFilters += listOf("en", "ro")
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"
        }
        create("staging") {
            versionNameSuffix = "-staging"
            applicationIdSuffix = ".debug"

            isDebuggable = true
            buildConfigField("Boolean", "DEBUG", "false")
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")

            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore.jks")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", true.toString())
    arg("room.expandProjection", true.toString())
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":camera"))

    // Kotlin
    debugImplementation(libs.kotlin.reflect)
    implementation(libs.kotlin.coroutinesAndroid)
    implementation(libs.kotlin.serializationJson)
    implementation(libs.kotlin.dateTime)

    // AndroidX
    implementation(libs.androidx.dataStore)
    implementation(libs.androidx.exif)

    // Media 3
    implementation(libs.media.ui)
    implementation(libs.media.exoplayer)
    implementation(libs.media.okhttp)
    implementation(libs.media.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Compose
    implementation(libs.compose.compiler)
    implementation(libs.compose.ui)
    implementation(libs.compose.toolingPreview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycleViewmodel)

    // Voyager
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.tabNavigator)
//    implementation(libs.voyager.transitions)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofitSerializer)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.brotli)

    // WorkManager
    implementation(libs.androidx.work)

    // Hilt
    implementation(libs.dagger.android)
    ksp(libs.dagger.compiler)
    ksp(libs.dagger.hilt.compiler)
    implementation(libs.dagger.hilt.work)

    // Coil
    implementation(libs.coil.core)
    implementation(libs.coil.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)

    // Other
    implementation(libs.processPheonix)
    implementation(libs.telephoto)
}
