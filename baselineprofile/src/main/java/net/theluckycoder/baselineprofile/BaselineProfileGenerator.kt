package net.theluckycoder.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a baseline profile for startup and gallery scrolling.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw Exception("targetAppId not passed as instrumentation runner arg")
        val launchIntent = createBenchmarkLaunchIntent(packageName)

        rule.collect(
            packageName = packageName,
            includeInStartupProfile = true,
            maxIterations = 5,
        ) {
            pressHome()
            startActivityAndWait(launchIntent)

            waitForGalleryContent()
            scrollGalleryGrid()
            scrollWithMonthIndicator()
            viewAndScrollPhotos()
            selectMonth()
        }
    }
}
