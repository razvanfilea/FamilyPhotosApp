package net.theluckycoder.familyphotos.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Keep
@Entity(
    tableName = "backup_local_folders",
)
data class LocalFolderToBackup(
    @PrimaryKey
    val name: String
)