package net.theluckycoder.familyphotos.core.data.local.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.theluckycoder.familyphotos.core.data.model.db.FavoriteNetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.db.LocalFolderToBackup
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.db.ServerState
import net.theluckycoder.familyphotos.core.data.model.db.UploadQueueEntry

@Database(
    entities = [LocalPhoto::class, NetworkPhoto::class, NetworkFolderEntity::class, LocalFolderToBackup::class, FavoriteNetworkPhoto::class, ServerState::class, UploadQueueEntry::class],
    version = 15,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class PhotosDatabase : RoomDatabase() {

    abstract fun localPhotosDao(): LocalPhotosDao

    abstract fun networkPhotosDao(): NetworkPhotosDao

    abstract fun localFolderBackupDao(): LocalFolderBackupDao

    abstract fun favoritePhotosDao(): FavoritePhotosDao

    abstract fun uploadQueueDao(): UploadQueueDao

    abstract fun networkFoldersDao(): NetworkFoldersDao

    companion object {
        @Volatile
        private var INSTANCE: PhotosDatabase? = null

        private val MIGRATION_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE upload_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        localPhotoId INTEGER NOT NULL,
                        makePublic INTEGER NOT NULL,
                        uploadFolder TEXT,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        maxRetries INTEGER NOT NULL DEFAULT 3,
                        createdAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX index_upload_queue_localPhotoId ON upload_queue(localPhotoId)")
            }
        }

        private val MIGRATION_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE upload_queue ADD COLUMN isManualUpload INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE network_folder (id INTEGER NOT NULL PRIMARY KEY, ownerId TEXT, name TEXT NOT NULL, createdAt INTEGER NOT NULL, latestEventId INTEGER NOT NULL DEFAULT 0, canUpload INTEGER NOT NULL DEFAULT 1, canDelete INTEGER NOT NULL DEFAULT 1)")

                db.execSQL("CREATE TABLE network_photo_new (id INTEGER NOT NULL PRIMARY KEY, userId TEXT, name TEXT NOT NULL, timeCreated INTEGER NOT NULL, fileSize INTEGER NOT NULL, folderId INTEGER, trashedOn INTEGER, thumbHash TEXT)")
                db.execSQL("INSERT INTO network_photo_new (id, userId, name, timeCreated, fileSize, trashedOn, thumbHash) SELECT id, userId, name, timeCreated, fileSize, trashedOn, thumbHash FROM network_photo")
                db.execSQL("DROP TABLE network_photo")
                db.execSQL("ALTER TABLE network_photo_new RENAME TO network_photo")
                db.execSQL("CREATE INDEX index_network_photo_timeCreated ON network_photo(timeCreated DESC)")
                db.execSQL("CREATE INDEX index_network_photo_folderId ON network_photo(folderId)")

                db.execSQL("UPDATE server_state SET eventLogId = 0")
            }
        }

        private val MIGRATION_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS upload_queue")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS upload_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        localPhotoId INTEGER NOT NULL,
                        makePublic INTEGER,
                        folderId INTEGER,
                        newFolderName TEXT,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        maxRetries INTEGER NOT NULL DEFAULT 3,
                        createdAt INTEGER NOT NULL,
                        isManualUpload INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_upload_queue_localPhotoId ON upload_queue(localPhotoId)")
            }
        }

        fun getDatabase(context: Context): PhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos_database"
                ).addMigrations(
                    MIGRATION_12,
                    MIGRATION_13,
                    MIGRATION_14,
                    MIGRATION_15
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Log.d("PhotosDatabase", "Database created")
                        db.execSQL("INSERT INTO server_state VALUES (0, 0)")
                    }
                })
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
