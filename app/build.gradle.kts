plugins {
    id("com.android.application")

    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization") version "1.8.10"
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
        versionCode = 18
        versionName = "1.8.4"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).configureEach {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
    }
}

dependencies {
    val voyagerVersion = "1.0.0-rc03"

    // Kotlin
    kotlin("kotlin-stdlib-jdk8")
    debugImplementation(libs.kotlinReflect)
    implementation(libs.kotlinCoroutinesAndroid)
    implementation(libs.kotlinSerializationJson)
    implementation(libs.kotlinDateTime)

    // Arrow
//    implementation(platform("io.arrow-kt:arrow-stack:1.1.3"))
//    implementation("io.arrow-kt:arrow-core")

    // AndroidX
    implementation("androidx.activity:activity-ktx:1.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.exifinterface:exifinterface:1.3.6")

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
    implementation(libs.compose.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.activity)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.1")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    // Voyager
    implementation("cafe.adriel.voyager:voyager-navigator:$voyagerVersion")
    implementation("cafe.adriel.voyager:voyager-tab-navigator:$voyagerVersion")
//    implementation("cafe.adriel.voyager:voyager-bottom-sheet-navigator:$voyagerVersion")
    implementation("cafe.adriel.voyager:voyager-transitions:$voyagerVersion")
    implementation("cafe.adriel.voyager:voyager-androidx:$voyagerVersion")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("de.nycode:retrofit2-kotlinx-serialization-converter:0.11.0")
    implementation("com.squareup.okhttp3:okhttp-tls:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("com.squareup.okhttp3:okhttp-brotli:4.10.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.0")

    // Hilt
    implementation(libs.dagger.android)
    kapt(libs.dagger.compiler)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.dagger.hilt.work)

    // Coil
    implementation(libs.coil.base)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)

    // Accompanist
    implementation(libs.accompanist.systemUi)

    implementation("com.jakewharton:process-phoenix:2.1.2")
}
