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
import net.theluckycoder.familyphotos.data.model.LocalFolderToBackup
import net.theluckycoder.familyphotos.data.model.LocalPhoto
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.ServerState

@Database(
    entities = [LocalPhoto::class, NetworkPhoto::class, LocalFolderToBackup::class, ServerState::class],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PhotosDatabase : RoomDatabase() {

    abstract fun localPhotosDao(): LocalPhotosDao

    abstract fun networkPhotosDao(): NetworkPhotosDao

    abstract fun localFolderBackupDao(): LocalFolderBackupDao

    companion object {
        @Volatile
        private var INSTANCE: PhotosDatabase? = null

        private val MIGRATION_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX index_network_photo_timeCreated ON network_photo(timeCreated DESC)")
            }
        }

        private val MIGRATION_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE network_photo ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE backup_local_folders(name TEXT NOT NULL PRIMARY KEY)")
            }
        }

        private val MIGRATION_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE server_state(id INTEGER NOT NULL PRIMARY KEY CHECK (id = 0), eventLogId INTEGER NOT NULL)")
                db.execSQL("INSERT INTO server_state VALUES (0, 0)")
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
                    MIGRATION_6,
                    MIGRATION_7,
                    MIGRATION_8,
                    MIGRATION_9,
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
