package net.theluckycoder.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

fun MacrobenchmarkScope.waitForGalleryContent() {
    device.wait(Until.hasObject(By.res("photos_list")), 10_000)
}

fun MacrobenchmarkScope.scrollGalleryGrid() {
    val margin = device.displayWidth / 5
    val grid = device.findObject(By.res("photos_list"))
    grid.setGestureMargin(margin)

    grid.fling(Direction.DOWN)
    Thread.sleep(500)

    // Pinch to zoom out (more columns)
    grid.pinchClose(0.5f)
    Thread.sleep(500)

    grid.fling(Direction.DOWN)
    Thread.sleep(500)

    grid.pinchClose(1f)
    Thread.sleep(500)

    grid.fling(Direction.DOWN)
    Thread.sleep(500)

    // Pinch to zoom back in (fewer columns)
    grid.pinchOpen(0.75f)
    Thread.sleep(500)

    grid.fling(Direction.UP)
    Thread.sleep(500)
}
