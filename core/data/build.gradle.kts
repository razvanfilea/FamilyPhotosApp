plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.theluckycoder.familyphotos.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", true.toString())
    arg("room.expandProjection", true.toString())
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.coroutinesAndroid)
    implementation(libs.kotlin.serializationJson)
    implementation(libs.kotlin.dateTime)

    // AndroidX
    implementation(libs.androidx.core)
    implementation(libs.androidx.dataStore)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofitSerializer)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.brotli)

    // Paging
    implementation(libs.paging.runtime)

    // Hilt
    implementation(libs.dagger.android)
    ksp(libs.kotlin.metadataJvm)
    ksp(libs.dagger.compiler)
    ksp(libs.dagger.hilt.compiler)
    implementation(libs.dagger.hilt.work)

    // Compose (for @Immutable annotation only)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)

    // Cryptography / Hash Functions
    implementation(libs.blake.hash)
}