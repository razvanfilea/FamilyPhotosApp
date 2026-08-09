package net.theluckycoder.baselineprofile

import android.content.Intent
import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
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

/**
 * Switches to the Timeline tab and waits for its photo grid to appear.
 * Used to return to a known top-level state after folder exploration.
 */
fun MacrobenchmarkScope.openTimelineTab() {
    val timelineTab = device.wait(Until.findObject(By.res("tab_Timeline")), 5_000)
        ?: throw AssertionError("Timeline tab (tab_Timeline) not found")
    timelineTab.click()
    check(device.wait(Until.hasObject(By.res("photos_list")), 10_000)) {
        "photos_list did not appear after opening the Timeline tab"
    }
    device.waitForIdle()
}

/**
 * Switches to the Network Folders tab and waits for the folder grid to appear.
 * The tab is selected via its testTag ("tab_NetworkFolders"), which is exposed as a
 * resource-id under the testTagsAsResourceId benchmark flag.
 */
fun MacrobenchmarkScope.openNetworkFoldersTab() {
    val foldersTab = device.wait(Until.findObject(By.res("tab_NetworkFolders")), 5_000)
        ?: throw AssertionError("Network Folders tab (tab_NetworkFolders) not found")
    foldersTab.click()
    check(device.wait(Until.hasObject(By.res("folder_item")), 10_000)) {
        "folder_item did not appear after opening the Network Folders tab"
    }
    device.waitForIdle()
}

/**
 * Opens the folder at [index] in the folder grid and waits for its photo grid
 * ("photos_list") to be laid out. Returns false if the folder could not be found.
 *
 * This is the key probe for the grid + CoilPhoto cost: opening a folder mounts a
 * fresh PhotosList that composes ~100 cells at once, each doing a ThumbHash decode
 * plus AsyncImage setup on the first frame.
 */
fun MacrobenchmarkScope.openFolderAndWait(index: Int = 0): Boolean {
    val folders = device.findObjects(By.res("folder_item"))
    val folder = folders.getOrNull(index) ?: return false
    folder.click()
    device.wait(Until.hasObject(By.res("photos_list")), 10_000)
    device.waitForIdle()
    return true
}

/**
 * Flings the currently open folder's photo grid to exercise sustained thumbnail
 * decoding under scroll.
 */
fun MacrobenchmarkScope.scrollFolderGrid() {
    val grid = device.findObject(By.res("photos_list")) ?: return
    grid.setGestureMargin(device.displayWidth / 5)
    grid.fling(Direction.DOWN)
    Thread.sleep(100)
    grid.fling(Direction.UP)
    device.waitForIdle()
}

/**
 * Opens [count] different folders back-to-back, returning to the folder list
 * between each. Exercises repeated grid teardown/rebuild and navigation churn.
 * Assumes the Network Folders tab is already open.
 */
fun MacrobenchmarkScope.openFoldersSequentially(count: Int) {
    repeat(count) { index ->
        if (!openFolderAndWait(index)) return
        device.pressBack()
        device.wait(Until.hasObject(By.res("folder_item")), 5_000)
        device.waitForIdle()
    }
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

    val targetY = device.displayHeight / 4
    thumb.drag(Point(thumb.visibleCenter.x, targetY), 60)
    device.wait(Until.gone(By.res("month_scroll_indicator")), 2_000)
    device.waitForIdle()
}

/**
 * Returns the visible [photo_item] whose center is closest to the screen center,
 * or null if none are visible. [By.res] with [findObject] returns the first match
 * in the hierarchy (top-left cell); this picks a mid-screen photo instead, which is
 * more representative of a real tap and avoids the app bar / status bar edges.
 */
private fun MacrobenchmarkScope.findCenterPhoto(): UiObject2? {
    val photos = device.findObjects(By.res("photo_item"))
    if (photos.isEmpty()) return null

    val cx = device.displayWidth / 2
    val cy = device.displayHeight / 2
    return photos.minByOrNull { photo ->
        val c = photo.visibleCenter
        val dx = (c.x - cx).toLong()
        val dy = (c.y - cy).toLong()
        dx * dx + dy * dy
    }
}

fun MacrobenchmarkScope.viewAndScrollPhotos() {
    device.waitForIdle()

    val photo = findCenterPhoto()
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
    val photo = findCenterPhoto()
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
