package net.theluckycoder.familyphotos.data.model.db

data class PhotoStatistics(
    val totalCount: Int,
    val familyCount: Int,
    val personalCount: Int,
    val totalSize: Long,
    val familySize: Long,
    val personalSize: Long,
) {
    companion object {
        val Empty = PhotoStatistics(0, 0, 0, 0L, 0L, 0L)
    }
}
