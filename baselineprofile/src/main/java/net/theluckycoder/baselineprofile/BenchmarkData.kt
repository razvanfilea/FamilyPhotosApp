package net.theluckycoder.baselineprofile

import android.content.Intent
import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val EXTRA_BENCHMARK_SESSION_COOKIE = "benchmark_session_cookie"
const val EXTRA_BENCHMARK_USERNAME = "benchmark_username"
const val EXTRA_BENCHMARK_SERVER_ADDRESS = "benchmark_server_address"

fun createBenchmarkLaunchIntent(packageName: String): Intent {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val args = InstrumentationRegistry.getArguments()

    return context.packageManager.getLaunchIntentForPackage(packageName)!!.apply {
        args.getString("benchmarkSessionCookie")?.let { putExtra(EXTRA_BENCHMARK_SESSION_COOKIE, it) }
        args.getString("benchmarkUsername")?.let { putExtra(EXTRA_BENCHMARK_USERNAME, it) }
        args.getString("benchmarkServerAddress")?.let { putExtra(EXTRA_BENCHMARK_SERVER_ADDRESS, it) }
    }
}

fun MacrobenchmarkScope.waitForGalleryContent() {
    device.wait(Until.hasObject(By.res("photos_list")), 10_000)
}

fun MacrobenchmarkScope.scrollGalleryGrid() {
    val gestureTimeout = 100L
    val margin = device.displayWidth / 5
    val grid = device.findObject(By.res("photos_list"))
    grid.setGestureMargin(margin)

    grid.fling(Direction.DOWN)
    Thread.sleep(gestureTimeout)

    // Pinch to zoom out (more columns)
    grid.pinchClose(0.5f)
    Thread.sleep(gestureTimeout)

    grid.fling(Direction.DOWN)
    Thread.sleep(gestureTimeout)

    grid.pinchClose(1f)
    Thread.sleep(gestureTimeout)

    grid.fling(Direction.DOWN)
    Thread.sleep(gestureTimeout)

    // Pinch to zoom back in (fewer columns)
    grid.pinchOpen(0.75f)
    Thread.sleep(gestureTimeout)

    grid.fling(Direction.UP)
}

fun MacrobenchmarkScope.scrollWithMonthIndicator() {
    // Scroll the grid enough so the indicator thumb appears away from the status bar
    val grid = device.findObject(By.res("photos_list"))
        ?: return
    grid.scroll(Direction.DOWN, 2f)

    // Wait for indicator thumb to appear
    device.wait(Until.hasObject(By.res("month_scroll_indicator")), 500)
    val thumb = device.findObject(By.res("month_scroll_indicator"))
        ?: return // Exit if indicator not found (e.g., not enough months)

    // Drag upward to avoid triggering the notification shade
    val targetY = device.displayHeight / 4
    thumb.drag(Point(thumb.visibleCenter.x, targetY), 60)
    device.wait(Until.gone(By.res("month_scroll_indicator")), 2_000)
    device.waitForIdle()
}

fun MacrobenchmarkScope.viewAndScrollPhotos() {
    device.waitForIdle()

    val photo = device.findObject(By.res("photo_item"))
        ?: return
    photo.click()

    // Wait for viewer to appear
    device.wait(Until.hasObject(By.res("photo_viewer_pager")), 2_000)
    val pager = device.findObject(By.res("photo_viewer_pager"))
        ?: return

    pager.setGestureMargin(device.displayWidth / 10)

    // Swipe through photos
    repeat(5) {
        pager.swipe(Direction.LEFT, 0.8f)
        Thread.sleep(300)
    }
    repeat(3) {
        pager.swipe(Direction.RIGHT, 0.8f)
        Thread.sleep(300)
    }

    // Press back to return
    device.pressBack()
}

fun MacrobenchmarkScope.selectMonth() {
    // Long press a photo to enter selection mode
    val photo = device.findObject(By.res("photo_item"))
        ?: return
    photo.longClick()
    Thread.sleep(500)

    // Find and click month select button
    device.wait(Until.hasObject(By.res("month_select_button")), 2_000)
    val monthButton = device.findObject(By.res("month_select_button"))
        ?: return
    monthButton.click()
    Thread.sleep(500)

    // Press back to clear selection
    device.pressBack()
}
