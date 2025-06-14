package net.theluckycoder.familyphotos.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.theluckycoder.familyphotos.data.model.LocalFolderToBackup
import net.theluckycoder.familyphotos.data.model.LocalPhoto
import net.theluckycoder.familyphotos.data.model.NetworkPhoto

@Database(
    entities = [LocalPhoto::class, NetworkPhoto::class, LocalFolderToBackup::class],
    version = 8,
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE network_photo ADD COLUMN fileSize INTEGER DEFAULT 0 NOT NULL")
                db.execSQL("ALTER TABLE network_photo ADD COLUMN caption TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE network_photo")
                db.execSQL("CREATE TABLE network_photo (name TEXT NOT NULL, timeCreated INTEGER NOT NULL, id INTEGER NOT NULL PRIMARY KEY, userId TEXT NOT NULL, fileSize INTEGER NOT NULL, folder TEXT)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX index_network_photo_timeCreated ON network_photo(timeCreated DESC)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE network_photo ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE backup_local_folders(name TEXT NOT NULL PRIMARY KEY)")
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
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
