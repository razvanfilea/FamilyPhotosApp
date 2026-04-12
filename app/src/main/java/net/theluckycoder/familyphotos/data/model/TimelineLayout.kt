package net.theluckycoder.familyphotos.data.model

import androidx.compose.runtime.Immutable
import net.theluckycoder.familyphotos.data.model.db.MonthSummary
import java.util.TreeMap

/**
 * Represents the virtual grid layout for a photo timeline.
 *
 * This class decouples the grid layout from Paging data by providing a mathematical
 * mapping between grid indices (which include headers) and paging indices (photos only).
 *
 * @property monthSummaries The list of month summaries this layout is built from
 * @property headerIndexMap Maps grid index -> (MonthSummary, headerCountBefore)
 * @property totalGridItemCount Total number of items in the grid (headers + photos), excluding offset
 * @property gridOffset Number of items before the timeline starts (e.g., a header at index 0)
 */
@Immutable
data class TimelineLayout(
    val monthSummaries: List<MonthSummary>,
    private val headerIndexMap: TreeMap<Int, Pair<MonthSummary, Int>>,
    val totalGridItemCount: Int,
    val gridOffset: Int = 0
) {
    companion object {
        val EMPTY = TimelineLayout(emptyList(), TreeMap(), 0)

        fun build(summaries: List<MonthSummary>): TimelineLayout {
            if (summaries.isEmpty()) return EMPTY

            val map = TreeMap<Int, Pair<MonthSummary, Int>>()
            var currentIndex = 0

            for ((headerCount, summary) in summaries.withIndex()) {
                map[currentIndex] = summary to headerCount
                currentIndex += 1 + summary.photoCount  // header + photos
            }

            return TimelineLayout(summaries, map, currentIndex)
        }
    }

    /** Total items in the grid including offset */
    val totalItemCount: Int get() = totalGridItemCount + gridOffset

    fun isNotEmpty() = monthSummaries.isNotEmpty()

    /** Returns a copy with the specified grid offset */
    fun withOffset(offset: Int): TimelineLayout = copy(gridOffset = offset)

    /** Returns the grid index for a given MonthSummary (includes offset), or -1 if not found */
    fun gridIndexOf(summary: MonthSummary): Int {
        val baseIndex = headerIndexMap.entries
            .find { it.value.first.timeCreated == summary.timeCreated }
            ?.key ?: return -1
        return baseIndex + gridOffset
    }

    /** Returns the paging index for a given grid index (only valid for photo cells) */
    fun pagingIndexOf(gridIndex: Int): Int {
        val adjusted = gridIndex - gridOffset
        if (adjusted < 0) return -1
        val floorEntry = headerIndexMap.floorEntry(adjusted) ?: return adjusted
        val headersBeforeThis = floorEntry.value.second + 1
        return adjusted - headersBeforeThis
    }

    /** Returns true if this grid index is a month header */
    fun isHeader(gridIndex: Int): Boolean {
        val adjusted = gridIndex - gridOffset
        if (adjusted < 0) return false
        return headerIndexMap.containsKey(adjusted)
    }

    /** Returns the MonthSummary at this grid index, or null if it's a photo or offset item */
    fun getHeaderAt(gridIndex: Int): MonthSummary? {
        val adjusted = gridIndex - gridOffset
        if (adjusted < 0) return null
        return headerIndexMap[adjusted]?.first
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
}
