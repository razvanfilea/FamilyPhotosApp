package net.theluckycoder.familyphotos.core.data.model

/**
 * A way to resolve a group of duplicates: keep [keep] and move the other copies, [trash], to the trash.
 * [trash] holds every other copy of the group, including ones that look the same as [keep].
 */
data class DuplicateChoice(
    val keep: NetworkPhoto,
    val trash: List<NetworkPhoto>,
) {
    val freedBytes: Long
        get() = trash.sumOf { it.fileSize }
}

/** Resolves the group of [copies] in one step, for a bulk action. */
data class DuplicateResolution(
    val copies: List<NetworkPhoto>,
    val choice: DuplicateChoice,
)

/**
 * The summary of a bulk action: how many photos go to the trash and how much space that frees.
 */
data class DuplicateBulkAction(
    val resolutions: List<DuplicateResolution>,
) {
    val groupCount: Int
        get() = resolutions.size

    val photoCount: Int
        get() = resolutions.sumOf { it.choice.trash.size }

    val freedBytes: Long
        get() = resolutions.sumOf { it.choice.freedBytes }

    val trashIds: List<Long>
        get() = resolutions.flatMap { r -> r.choice.trash.map { it.id } }
}

object DuplicateGroups {

    /**
     * The photos as the server found them, but without the ids the local database doesn't have and
     * without the groups that are left with fewer than 2 photos. Copies are ordered by id.
     */
    fun sanitize(groups: List<List<NetworkPhoto?>>): List<List<NetworkPhoto>> =
        groups.map { group -> group.filterNotNull().sortedBy { it.id } }
            .filter { it.size >= 2 }

    /**
     * The copy to keep, unless the user decides otherwise: the Family copy, then a copy in a folder,
     * then the oldest upload (the lowest id).
     */
    fun suggested(copies: List<NetworkPhoto>): NetworkPhoto =
        copies.minWith(
            compareByDescending<NetworkPhoto> { it.isPublic }
                .thenByDescending { it.folderId != null }
                .thenBy { it.id }
        )

    /** What a user can see of a copy: copies with the same look can't be told apart. */
    private data class Look(val owner: String?, val folderId: Long?, val name: String, val timeCreated: Long)

    private fun NetworkPhoto.look() = Look(userId, folderId, name, timeCreated)

    /**
     * One choice per copy, but copies that look the same (owner, folder, name and date) share one:
     * it keeps the best of them. The suggested choice is first, then the others by id.
     * A single choice means that the copies can't be told apart, so the user only keeps one.
     */
    fun choices(copies: List<NetworkPhoto>): List<DuplicateChoice> {
        val ordered = copies.sortedBy { it.id }
        val suggested = suggested(ordered)
        return ordered.groupBy { it.look() }.values
            .map { sameLook ->
                val keep = if (sameLook.any { it.id == suggested.id }) suggested else sameLook.first()
                DuplicateChoice(keep, ordered.filter { it.id != keep.id })
            }
            .sortedBy { it.keep.id != suggested.id }
    }

    /** The choice that keeps the suggested copy. */
    fun suggestedChoice(copies: List<NetworkPhoto>): DuplicateChoice {
        val keep = suggested(copies)
        return DuplicateChoice(keep, copies.filter { it.id != keep.id })
    }

    /** The space that resolving the group frees, the copies being the same size. */
    fun freedBytes(copies: List<NetworkPhoto>): Long = suggestedChoice(copies).freedBytes

    fun totalFreedBytes(groups: List<List<NetworkPhoto>>): Long = groups.sumOf { freedBytes(it) }

    /**
     * Groups of exactly 2 copies, one Personal and one Family: the Family copy stays and the
     * Personal one goes.
     */
    fun keepFamilyCopy(groups: List<List<NetworkPhoto>>): DuplicateBulkAction =
        DuplicateBulkAction(
            groups.mapNotNull { copies ->
                val family = copies.singleOrNull { it.isPublic }
                if (copies.size != 2 || family == null) return@mapNotNull null
                DuplicateResolution(copies, DuplicateChoice(family, copies.filter { it.id != family.id }))
            }
        )

    /**
     * Groups where all the copies have the same owner and folder: the suggested copy stays and
     * the extra ones go.
     */
    fun removeExtraCopies(groups: List<List<NetworkPhoto>>): DuplicateBulkAction =
        DuplicateBulkAction(
            groups.filter { copies ->
                copies.size >= 2 &&
                        copies.all { it.userId == copies[0].userId && it.folderId == copies[0].folderId }
            }.map { DuplicateResolution(it, suggestedChoice(it)) }
        )
}
