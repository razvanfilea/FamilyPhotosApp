plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "net.theluckycoder.camera"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    buildFeatures.compose = true
}

dependencies {
    implementation(libs.androidx.core)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.toolingPreview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.activity)

    // CameraX
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.view)
    implementation(libs.camerax.extensions)

    // Coil
    implementation(libs.coil.core)
}