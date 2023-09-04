plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlinAndroid)

    kotlin("plugin.serialization") version libs.versions.kotlin.base.get()
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")

    alias(libs.plugins.hilt)
}

android {
    compileSdk = 34
    namespace = "net.theluckycoder.familyphotos"

    defaultConfig {
        applicationId = "net.theluckycoder.familyphotos"
        minSdk = 30
        targetSdk = 33
        versionCode = 25
        versionName = "2.5.2"
        resourceConfigurations += listOf("en", "ro")
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

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", true.toString())
    arg("room.expandProjection", true.toString())
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).configureEach {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
    }
}

dependencies {
    implementation(project(":camera"))

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
    implementation(libs.media.ui)
    implementation(libs.media.exoplayer)
    implementation(libs.media.okhttp)

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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // Voyager
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.tabNavigator)
//    implementation(libs.voyager.transitions)
    implementation(libs.voyager.androidx)

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
    implementation(libs.coil.base)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)

    // Other
    implementation("com.jakewharton:process-phoenix:2.1.2")
    implementation("me.saket.telephoto:zoomable-image-coil:0.5.0")
}
