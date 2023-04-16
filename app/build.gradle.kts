plugins {
    id("com.android.application")

    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization") version libs.versions.kotlin.base.get()
//    id("com.google.devtools.ksp") version "1.5.31-1.0.0"
    id("kotlin-parcelize")

    id("dagger.hilt.android.plugin")
}

android {
    compileSdk = 33
    namespace = "net.theluckycoder.familyphotos"

    defaultConfig {
        applicationId = "net.theluckycoder.familyphotos"
        minSdk = 30
        targetSdk = 33
        versionCode = 22
        versionName = "2.2.0"
        resourceConfigurations += listOf("en", "ro")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }
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

    buildFeatures.compose = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).configureEach {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
    }
}

dependencies {
    // Kotlin
    kotlin("kotlin-stdlib-jdk8")
    debugImplementation(libs.kotlin.reflect)
    implementation(libs.kotlin.coroutinesAndroid)
    implementation(libs.kotlin.serializationJson)
    implementation(libs.kotlin.dateTime)
    implementation(libs.kotlin.collections)

    // AndroidX
    implementation(libs.androidx.activity)
    implementation(libs.androidx.dataStore)
    implementation(libs.androidx.exif)

    // Media 3
    implementation(libs.media.ui)
    implementation(libs.media.exoplayer)
    implementation(libs.media.okhttp)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    kapt(libs.room.compiler)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

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
    implementation(libs.voyager.transitions)
    implementation(libs.voyager.androidx)

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("de.nycode:retrofit2-kotlinx-serialization-converter:0.11.0")
    implementation("com.squareup.okhttp3:okhttp-tls:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("com.squareup.okhttp3:okhttp-brotli:4.10.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Hilt
    implementation(libs.dagger.android)
    kapt(libs.dagger.compiler)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.dagger.hilt.work)

    // Coil
    implementation(libs.coil.base)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)

    implementation("com.jakewharton:process-phoenix:2.1.2")
    implementation("com.github.SmartToolFactory:Compose-Zoom:+")
}
