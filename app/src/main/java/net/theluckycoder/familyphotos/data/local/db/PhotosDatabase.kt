package net.theluckycoder.familyphotos.data.local.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomMasterTable.TABLE_NAME
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.theluckycoder.familyphotos.data.model.db.FavoriteNetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.LocalFolderToBackup
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.ServerState
import net.theluckycoder.familyphotos.data.model.db.UploadQueueEntry

@Database(
    entities = [LocalPhoto::class, NetworkPhoto::class, LocalFolderToBackup::class, FavoriteNetworkPhoto::class, ServerState::class, UploadQueueEntry::class],
    version = 12,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PhotosDatabase : RoomDatabase() {

    abstract fun localPhotosDao(): LocalPhotosDao

    abstract fun networkPhotosDao(): NetworkPhotosDao

    abstract fun localFolderBackupDao(): LocalFolderBackupDao

    abstract fun favoritePhotosDao(): FavoritePhotosDao

    abstract fun uploadQueueDao(): UploadQueueDao

    companion object {
        @Volatile
        private var INSTANCE: PhotosDatabase? = null

        private val MIGRATION_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE backup_local_folders(name TEXT NOT NULL PRIMARY KEY)")
            }
        }

        private val MIGRATION_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE server_state(id INTEGER NOT NULL PRIMARY KEY CHECK (id = 0), eventLogId INTEGER NOT NULL)")
                db.execSQL("INSERT INTO server_state VALUES (0, 0)")

                db.execSQL("DROP TABLE network_photo")
                db.execSQL("CREATE TABLE network_photo (id INTEGER NOT NULL PRIMARY KEY, userId TEXT, name TEXT NOT NULL, timeCreated INTEGER NOT NULL, fileSize INTEGER NOT NULL, folder TEXT)")
                db.execSQL("CREATE INDEX index_network_photo_timeCreated ON network_photo(timeCreated DESC)")

                db.execSQL("CREATE TABLE favorite_network_photo (photoId INTEGER NOT NULL PRIMARY KEY REFERENCES network_photo(id) ON DELETE CASCADE)")
            }
        }

        private val MIGRATION_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE network_photo ADD COLUMN trashedOn INTEGER")
            }
        }

        private val MIGRATION_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE network_photo ADD COLUMN thumbHash TEXT")
            }
        }

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

        fun getDatabase(context: Context): PhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos_database"
                ).addMigrations(
                    MIGRATION_8,
                    MIGRATION_9,
                    MIGRATION_10,
                    MIGRATION_11,
                    MIGRATION_12
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
