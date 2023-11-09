package net.theluckycoder.familyphotos.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.theluckycoder.familyphotos.db.dao.LocalPhotosDao
import net.theluckycoder.familyphotos.db.dao.NetworkPhotosDao
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto

@Database(
    entities = [LocalPhoto::class, NetworkPhoto::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PhotosDatabase : RoomDatabase() {

    abstract fun localPhotosDao(): LocalPhotosDao

    abstract fun networkPhotosDao(): NetworkPhotosDao

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

        fun getDatabase(context: Context): PhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos_database"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
