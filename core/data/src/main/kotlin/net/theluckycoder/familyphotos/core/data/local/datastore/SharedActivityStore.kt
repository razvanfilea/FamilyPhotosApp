package net.theluckycoder.familyphotos.core.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.core.data.model.sharing.PendingSharedActivity
import net.theluckycoder.familyphotos.core.data.model.sharing.SharedFolderChange
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared-folder activity found by folder syncs, waiting to be shown as notifications.
 *
 * It is persisted because any caller of the folder sync (app refresh, backup worker) consumes the
 * server-side delta, so the notification worker can't rely on seeing the change itself.
 */
@Singleton
class SharedActivityStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val dataStore = context.sharedActivityDataStore

    suspend fun read(): PendingSharedActivity =
        dataStore.data.first()[PENDING]?.let { decodeOrNull<PendingSharedActivity>(it) }
            ?: PendingSharedActivity()

    internal suspend fun isFirstSync(userId: String) = read().syncedUserId != userId

    internal suspend fun recordSync(userId: String, changes: List<SharedFolderChange>) = update { current ->
        val base = if (current.syncedUserId == userId) current else PendingSharedActivity(userId)
        base.merge(changes)
    }

    suspend fun update(transform: (PendingSharedActivity) -> PendingSharedActivity) {
        dataStore.edit { prefs ->
            val current = prefs[PENDING]?.let { decodeOrNull<PendingSharedActivity>(it) }
                ?: PendingSharedActivity()
            prefs[PENDING] = Json.encodeToString(transform(current))
        }
    }

    suspend fun dismiss(folderId: Long) = update { current ->
        current.copy(folders = current.folders.filterNot { it.folderId == folderId })
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /** Names of the members the app has seen, so a background worker doesn't have to fetch them. */
    suspend fun saveMembers(members: List<UserDto>) {
        dataStore.edit { it[MEMBERS] = Json.encodeToString(members) }
    }

    suspend fun getMemberNames(): Map<String, String> =
        dataStore.data.map { prefs -> prefs[MEMBERS]?.let { decodeOrNull<List<UserDto>>(it) } }
            .first()
            .orEmpty()
            .filter { it.displayName.isNotBlank() }
            .associate { it.userId to it.displayName }

    private inline fun <reified T> decodeOrNull(raw: String): T? =
        try {
            Json.decodeFromString<T>(raw)
        } catch (_: Exception) {
            null
        }

    private companion object {
        private val Context.sharedActivityDataStore by preferencesDataStore("shared_activity_prefs")

        private val PENDING = stringPreferencesKey("pending")
        private val MEMBERS = stringPreferencesKey("members")
    }
}
