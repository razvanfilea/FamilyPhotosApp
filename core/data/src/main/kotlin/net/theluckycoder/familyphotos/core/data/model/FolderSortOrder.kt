package net.theluckycoder.familyphotos.core.data.model

enum class FolderSortOrder(val id: Int) {
    DATE_DESC(0),
    NAME_ASC(1),
    NAME_DESC(2),
    COUNT_DESC(3);

    companion object {
        fun fromId(id: Int?): FolderSortOrder =
            entries.firstOrNull { it.id == id } ?: NAME_ASC
    }
}
