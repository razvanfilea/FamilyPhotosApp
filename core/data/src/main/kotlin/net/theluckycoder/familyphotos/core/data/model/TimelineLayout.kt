package net.theluckycoder.familyphotos.core.data.model

import androidx.compose.runtime.Immutable
import net.theluckycoder.familyphotos.core.data.model.db.MonthSummary

/**
 * Represents the virtual grid layout for a photo timeline.
 *
 * This class decouples the grid layout from Paging data by providing a mathematical
 * mapping between grid indices (which include headers) and paging indices (photos only).
 *
 * @property monthSummaries The list of month summaries this layout is built from
 * @property headerGridIndices Base grid index (without offset) for each header position
 * @property totalGridItemCount Total number of items in the grid (headers + photos), excluding offset
 * @property gridOffset Number of items before the timeline starts (e.g., a header at index 0)
 */
@Immutable
data class TimelineLayout(
    val monthSummaries: List<MonthSummary>,
    private val headerGridIndices: IntArray,
    val totalGridItemCount: Int,
    val gridOffset: Int = 0
) {
    companion object {
        val EMPTY = TimelineLayout(emptyList(), intArrayOf(), 0)

        fun build(summaries: List<MonthSummary>): TimelineLayout {
            if (summaries.isEmpty()) return EMPTY

            val headerIndices = IntArray(summaries.size)
            var currentIndex = 0

            for (i in summaries.indices) {
                headerIndices[i] = currentIndex
                currentIndex += 1 + summaries[i].photoCount // header + photos
            }

            return TimelineLayout(summaries, headerIndices, currentIndex)
        }

        fun provisional(photoCount: Int): TimelineLayout {
            if (photoCount <= 0) return EMPTY
            val fakeSummary =
                MonthSummary(timeCreated = 0L, coverPhotoId = 0L, photoCount = photoCount)
            return TimelineLayout(listOf(fakeSummary), intArrayOf(0), 1 + photoCount)
        }
    }

    /** Total items in the grid including offset */
    val totalItemCount: Int get() = totalGridItemCount + gridOffset

    fun isNotEmpty(): Boolean = monthSummaries.isNotEmpty()

    /** Returns a copy with the specified grid offset */
    fun withOffset(offset: Int): TimelineLayout = copy(gridOffset = offset)

    /** Returns the grid index for a given MonthSummary (includes offset), or -1 if not found */
    fun gridIndexOf(summary: MonthSummary): Int {
        val index = monthSummaries.indexOfFirst { it.timeCreated == summary.timeCreated }
        if (index == -1) return -1
        return headerGridIndices[index] + gridOffset
    }

    /** Returns the paging index for a given grid index (only valid for photo cells) */
    fun pagingIndexOf(gridIndex: Int): Int {
        val adjusted = gridIndex - gridOffset
        if (adjusted < 0) return -1
        val floorIdx = indexOfFloor(headerGridIndices, adjusted)
        if (floorIdx < 0) return adjusted
        val headersBeforeThis = floorIdx + 1
        return adjusted - headersBeforeThis
    }

    /** Returns true if this grid index is a month header */
    fun isHeader(gridIndex: Int): Boolean {
        val adjusted = gridIndex - gridOffset
        if (adjusted < 0) return false
        return headerGridIndices.binarySearch(adjusted) >= 0
    }

    /** Returns the MonthSummary at this grid index, or null if it's a photo or offset item */
    fun getHeaderAt(gridIndex: Int): MonthSummary? {
        val adjusted = gridIndex - gridOffset
        if (adjusted < 0) return null
        val idx = headerGridIndices.binarySearch(adjusted)
        return if (idx >= 0) monthSummaries[idx] else null
    }

    /** Cumulative item counts for each month (includes offset) */
    val monthCumulativeCounts: IntArray = run {
        val result = IntArray(monthSummaries.size + 1)
        result[0] = gridOffset
        for (i in 1..monthSummaries.size) {
            result[i] = result[i - 1] + monthSummaries[i - 1].photoCount + 1
        }
        result
    }

    val totalPhotoCount: Int = monthSummaries.sumOf { it.photoCount }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimelineLayout

        if (monthSummaries != other.monthSummaries) return false
        if (!headerGridIndices.contentEquals(other.headerGridIndices)) return false
        if (totalGridItemCount != other.totalGridItemCount) return false
        if (gridOffset != other.gridOffset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = monthSummaries.hashCode()
        result = 31 * result + headerGridIndices.contentHashCode()
        result = 31 * result + totalGridItemCount
        result = 31 * result + gridOffset
        return result
    }
}

/** Returns the index of the largest element <= key, or -1 if no such element exists */
private fun indexOfFloor(array: IntArray, key: Int): Int {
    val idx = array.binarySearch(key)
    return if (idx >= 0) idx else -idx - 2
}