package net.theluckycoder.familyphotos.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateGroupsTest {

    private fun photo(
        id: Long,
        userId: String? = "me",
        folderId: Long? = null,
        name: String = "IMG_$id.jpg",
        timeCreated: Long = 1_000L,
        fileSize: Long = 100L,
    ) = NetworkPhoto(
        id = id,
        userId = userId,
        name = name,
        timeCreated = timeCreated,
        fileSize = fileSize,
        folderId = folderId,
    )

    @Test
    fun `sanitize drops unknown ids and groups left with fewer than 2 photos`() {
        val groups = listOf(
            listOf(photo(5), null, photo(2)),
            listOf(photo(7), null),
            listOf(null, null),
            listOf(photo(9), photo(8), photo(10)),
        )

        val result = DuplicateGroups.sanitize(groups)

        assertEquals(listOf(listOf(2L, 5L), listOf(8L, 9L, 10L)), result.map { g -> g.map { it.id } })
    }

    @Test
    fun `the Family copy is suggested over a Personal one in a folder with a lower id`() {
        val personal = photo(1, userId = "me", folderId = 4)
        val family = photo(2, userId = null)

        assertEquals(2L, DuplicateGroups.suggested(listOf(personal, family)).id)
        assertEquals(2L, DuplicateGroups.suggested(listOf(family, personal)).id)
    }

    @Test
    fun `without a Family copy a copy in a folder is suggested, then the lowest id`() {
        val noFolder = photo(1)
        val inFolder = photo(3, folderId = 4)
        val inFolderLater = photo(5, folderId = 6)

        assertEquals(3L, DuplicateGroups.suggested(listOf(noFolder, inFolderLater, inFolder)).id)
        assertEquals(1L, DuplicateGroups.suggested(listOf(photo(2), photo(1), photo(3))).id)
    }

    @Test
    fun `among Family copies a copy in a folder wins, then the lowest id`() {
        val a = photo(1, userId = null)
        val b = photo(2, userId = null, folderId = 3)
        val c = photo(3, userId = null, folderId = 3)

        assertEquals(2L, DuplicateGroups.suggested(listOf(a, c, b)).id)
    }

    @Test
    fun `the suggestion never trashes the Family copy for a Personal one`() {
        val groups = listOf(
            listOf(photo(1, folderId = 2), photo(2, userId = null)),
            listOf(photo(3), photo(4, userId = null, folderId = 5)),
            listOf(photo(5, folderId = 1), photo(6, folderId = 1), photo(7, userId = null)),
        )

        for (group in groups) {
            val choice = DuplicateGroups.suggestedChoice(group)
            assertTrue(choice.keep.isPublic)
            assertTrue(choice.trash.none { it.isPublic })
        }
    }

    @Test
    fun `copies that differ get one choice each, the suggested one first`() {
        val personal = photo(1, folderId = 2)
        val family = photo(2, userId = null)

        val choices = DuplicateGroups.choices(listOf(personal, family))

        assertEquals(listOf(2L, 1L), choices.map { it.keep.id })
        assertEquals(listOf(listOf(1L), listOf(2L)), choices.map { c -> c.trash.map { it.id } })
    }

    @Test
    fun `copies with the same owner, folder, name and date collapse into a single choice`() {
        val a = photo(1, folderId = 2, name = "a.jpg")
        val b = photo(2, folderId = 2, name = "a.jpg")

        val choices = DuplicateGroups.choices(listOf(b, a))

        assertEquals(1, choices.size)
        assertEquals(1L, choices[0].keep.id)
        assertEquals(listOf(2L), choices[0].trash.map { it.id })
    }

    @Test
    fun `a different name or date makes copies distinguishable`() {
        assertEquals(2, DuplicateGroups.choices(listOf(photo(1, name = "a.jpg"), photo(2, name = "b.jpg"))).size)
        assertEquals(
            2,
            DuplicateGroups.choices(listOf(photo(1, name = "a", timeCreated = 1), photo(2, name = "a", timeCreated = 2))).size
        )
    }

    @Test
    fun `identical copies next to a different one share a choice that trashes all the others`() {
        val twinA = photo(1, name = "a.jpg")
        val twinB = photo(2, name = "a.jpg")
        val other = photo(3, userId = null, name = "a.jpg")

        val choices = DuplicateGroups.choices(listOf(twinA, twinB, other))

        assertEquals(listOf(3L, 1L), choices.map { it.keep.id })
        assertEquals(listOf(1L, 2L), choices[0].trash.map { it.id })
        assertEquals(listOf(2L, 3L), choices[1].trash.map { it.id })
    }

    @Test
    fun `freed space counts the copies that go, once per group`() {
        val group = listOf(photo(1, fileSize = 300), photo(2, userId = null, fileSize = 300))
        val three = listOf(photo(3, fileSize = 50), photo(4, fileSize = 50), photo(5, fileSize = 50))

        assertEquals(300L, DuplicateGroups.freedBytes(group))
        assertEquals(100L, DuplicateGroups.freedBytes(three))
        assertEquals(400L, DuplicateGroups.totalFreedBytes(listOf(group, three)))
    }

    @Test
    fun `keep the Family copy applies to pairs of one Personal and one Family copy only`() {
        val pair = listOf(photo(1), photo(2, userId = null))
        val twoPersonal = listOf(photo(3), photo(4))
        val twoFamily = listOf(photo(5, userId = null), photo(6, userId = null))
        val triple = listOf(photo(7), photo(8), photo(9, userId = null))
        val pair2 = listOf(photo(11, folderId = 1, fileSize = 10), photo(10, userId = null, fileSize = 10))

        val action = DuplicateGroups.keepFamilyCopy(listOf(pair, twoPersonal, twoFamily, triple, pair2))

        assertEquals(2, action.groupCount)
        assertEquals(2, action.photoCount)
        assertEquals(listOf(1L, 11L), action.trashIds)
        assertEquals(110L, action.freedBytes)
    }

    @Test
    fun `remove extra copies applies to groups with one owner and one folder and keeps the suggestion`() {
        val sameFolder = listOf(photo(3, folderId = 1), photo(1, folderId = 1), photo(2, folderId = 1))
        val noFolder = listOf(photo(4), photo(5))
        val otherFolder = listOf(photo(6, folderId = 1), photo(7, folderId = 2))
        val otherOwner = listOf(photo(8), photo(9, userId = null))

        val action = DuplicateGroups.removeExtraCopies(listOf(sameFolder, noFolder, otherFolder, otherOwner))

        assertEquals(2, action.groupCount)
        assertEquals(3, action.photoCount)
        assertEquals(listOf(2L, 3L, 5L), action.trashIds.sorted())
        assertEquals(listOf(1L, 4L), action.resolutions.map { it.choice.keep.id })
        assertEquals(300L, action.freedBytes)
    }

    @Test
    fun `bulk actions are empty when nothing applies`() {
        val action = DuplicateGroups.keepFamilyCopy(emptyList())

        assertEquals(0, action.groupCount)
        assertEquals(0L, action.freedBytes)
    }
}
