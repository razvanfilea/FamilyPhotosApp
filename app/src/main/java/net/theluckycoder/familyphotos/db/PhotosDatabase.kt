package net.theluckycoder.familyphotos.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto

@Database(
    entities = [LocalPhoto::class, NetworkPhoto::class],
    version = 3,
    autoMigrations = [
        AutoMigration(
            from = 2,
            to = 3,
            spec = PhotosDatabase.AutoMigrationV3::class
        )
    ],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PhotosDatabase : RoomDatabase() {

    abstract fun localPhotosDao(): LocalPhotosDao

    abstract fun networkPhotosDao(): NetworkPhotosDao

    @DeleteColumn(tableName = "network_photo", columnName = "fileSize")
    class AutoMigrationV3 : AutoMigrationSpec

    companion object {
        @Volatile
        private var INSTANCE: PhotosDatabase? = null

        fun getDatabase(context: Context): PhotosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return it }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos_database"
                ).build()

                INSTANCE = instance

                instance
            }
        }
    }
}
