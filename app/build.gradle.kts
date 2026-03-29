plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.baselineprofile)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)

    alias(libs.plugins.hilt)
}

android {
    compileSdk = 36
    namespace = "net.theluckycoder.familyphotos"

    defaultConfig {
        applicationId = "net.theluckycoder.familyphotos"
        minSdk = 30
        targetSdk = 35

        versionCode = 29
        versionName = "2.9.8"

        buildConfigField("boolean", "BENCHMARK", "false")
    }

    androidResources {
        localeFilters += listOf("en", "ro")
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("nonMinifiedRelease") {
            applicationIdSuffix = ".benchmark"
            buildConfigField("boolean", "BENCHMARK", "true")
        }
        create("benchmarkRelease") {
            applicationIdSuffix = ".benchmark"
            buildConfigField("boolean", "BENCHMARK", "true")
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

    baselineProfile {
        dexLayoutOptimization = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", true.toString())
    arg("room.expandProjection", true.toString())
    arg("room.generateKotlin", true.toString())
}

dependencies {
    implementation(project(":camera"))

    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    // Kotlin
    debugImplementation(libs.kotlin.reflect)
    implementation(libs.kotlin.coroutinesAndroid)
    implementation(libs.kotlin.serializationJson)
    implementation(libs.kotlin.dateTime)
    implementation(libs.kotlin.collections)

    // AndroidX
    implementation(libs.androidx.dataStore)
    implementation(libs.androidx.exif)

    // Media 3
    implementation(libs.media.exoplayer)
    implementation(libs.media.okhttp)
    implementation(libs.media.compose)
    implementation(libs.media.compose.material3)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.toolingPreview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3Adaptive)
    implementation(libs.compose.animation)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycleViewmodel)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
//    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

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
    implementation(libs.telephoto)
}
