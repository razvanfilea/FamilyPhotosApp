package net.theluckycoder.familyphotos.data.model.db

data class PhotoStatistics(
    val familyCount: Int,
    val personalCount: Int,
    val familySize: Long,
    val personalSize: Long,
    val imageCount: Int,
    val videoCount: Int,
    val imageSize: Long,
    val videoSize: Long,
) {
    val totalCount: Int get() = imageCount + videoCount
    val totalSize: Long get() = imageSize + videoSize

    companion object {
        val Empty = PhotoStatistics(0, 0, 0L, 0L, 0, 0, 0L, 0L)
    }
}
