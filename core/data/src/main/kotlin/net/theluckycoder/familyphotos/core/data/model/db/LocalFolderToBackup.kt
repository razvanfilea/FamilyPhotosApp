package net.theluckycoder.familyphotos.core.data.model.db

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "backup_local_folders",
)
data class LocalFolderToBackup(
    @PrimaryKey
    @ColumnInfo("name")
    val name: String
)