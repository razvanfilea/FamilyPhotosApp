package net.theluckycoder.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolates the cost of the photos grid + [CoilPhoto] composable by measuring
 * frame timing while opening network folders.
 *
 * Opening a folder is a harsher probe than the timeline scroll: it mounts a fresh
 * PhotosList that composes ~100 grid cells in one burst, each performing a
 * synchronous ThumbHash decode and AsyncImage setup on the first frame. This is
 * where the "heavy composable" cost shows up, and it is also the path most
 * sensitive to baseline-profile / JIT warmup of the ThumbHash decoder.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FolderBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Measures the fresh-grid burst when a folder is opened from the folder list. */
    @Test
    fun openFolder() = measureFolders(iterations = 5) {
        openFolderAndWait()
        // Return to the folder list so the next iteration opens the grid fresh.
        device.pressBack()
    }

    /** Measures sustained thumbnail decoding while scrolling an open folder. */
    @Test
    fun openFolderAndScroll() = measureFolders(iterations = 3) {
        if (openFolderAndWait()) {
            scrollFolderGrid()
            device.pressBack()
        }
    }

    /** Measures navigation churn: repeated grid teardown/rebuild across folders. */
    @Test
    fun openMultipleFolders() = measureFolders(iterations = 3) {
        openFoldersSequentially(count = 4)
    }

    private fun measureFolders(
        iterations: Int,
        measureBlock: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit,
    ) {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw Exception("targetAppId not passed as instrumentation runner arg")
        val launchIntent = createBenchmarkLaunchIntent(packageName)

        rule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = iterations,
            setupBlock = {
                pressHome()
                startActivityAndWait(launchIntent)
                waitForGalleryContent()
                openNetworkFoldersTab()
            },
            measureBlock = measureBlock,
        )
    }
}