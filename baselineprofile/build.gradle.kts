import java.util.Properties

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.android.baselineprofile)
}

// Load credentials from local.properties for benchmark authentication
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val benchmarkSessionCookie: String? = localProperties.getProperty("benchmark.sessionCookie")
val benchmarkUsername: String? = localProperties.getProperty("benchmark.username")

android {
    namespace = "net.theluckycoder.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Allow running on emulator for development (results won't be production-accurate)
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"

        // Pass benchmark credentials if available
        benchmarkSessionCookie?.let { testInstrumentationRunnerArguments["benchmarkSessionCookie"] = it }
        benchmarkUsername?.let { testInstrumentationRunnerArguments["benchmarkUsername"] = it }
    }

    targetProjectPath = ":app"

    // This code creates the gradle managed device used to generate baseline profiles.
    // To use GMD please invoke generation through the command line:
    // ./gradlew :app:generateBaselineProfile
    testOptions.managedDevices.localDevices {
        create("pixel6Api36") {
            device = "Pixel 6"
            apiLevel = 36
            systemImageSource = "google"
        }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices += "pixel6Api36"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}