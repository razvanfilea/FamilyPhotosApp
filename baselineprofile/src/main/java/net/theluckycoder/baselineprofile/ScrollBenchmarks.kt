package net.theluckycoder.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class ScrollBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scroll() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw Exception("targetAppId not passed as instrumentation runner arg")
        val launchIntent = createBenchmarkLaunchIntent(packageName)

        rule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 3,
            setupBlock = {
                pressHome()
                startActivityAndWait(launchIntent)
                waitForGalleryContent()
            },
            measureBlock = {
                scrollGalleryGrid()
            }
        )
    }

    @Test
    fun scrollWithIndicator() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw Exception("targetAppId not passed as instrumentation runner arg")
        val launchIntent = createBenchmarkLaunchIntent(packageName)

        rule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 3,
            setupBlock = {
                pressHome()
                startActivityAndWait(launchIntent)
                waitForGalleryContent()
                // Scroll a bit first to make indicator visible
                val grid = device.findObject(By.res("photos_list"))
                grid.fling(Direction.DOWN)
                Thread.sleep(500)
            },
            measureBlock = {
                scrollWithMonthIndicator()
            }
        )
    }
}
