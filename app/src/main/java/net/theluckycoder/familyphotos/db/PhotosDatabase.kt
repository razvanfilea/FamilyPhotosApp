package net.theluckycoder.familyphotos.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.theluckycoder.familyphotos.db.dao.LocalPhotosDao
import net.theluckycoder.familyphotos.db.dao.NetworkPhotosDao
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto

@Database(
    entities = [LocalPhoto::class, NetworkPhoto::class],
    version = 4,
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
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE network_photo ADD COLUMN fileSize INTEGER DEFAULT 0 NOT NULL")
                database.execSQL("ALTER TABLE network_photo ADD COLUMN caption TEXT")
            }
        }

        fun getDatabase(context: Context): PhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos_database"
                ).addMigrations(MIGRATION_3_4)
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}
