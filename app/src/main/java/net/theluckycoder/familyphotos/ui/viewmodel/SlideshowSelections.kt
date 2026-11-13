package net.theluckycoder.familyphotos.ui.viewmodel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The photos of the slideshows of selected photos, which [net.theluckycoder.familyphotos.ui.SlideshowNav]
 * only refers to: the back stack is saved in the saved state, which a large selection would overflow.
 * In memory only: after process death the slideshow finds nothing and closes.
 */
@Singleton
class SlideshowSelections @Inject constructor() {

    private val nextId = AtomicLong(1)
    private val selections = ConcurrentHashMap<Long, List<Long>>()

    fun add(photoIds: List<Long>): Long {
        val id = nextId.getAndIncrement()
        selections[id] = photoIds
        return id
    }

    operator fun get(selectionId: Long): List<Long>? = selections[selectionId]

    fun remove(selectionId: Long) {
        selections.remove(selectionId)
    }
}
