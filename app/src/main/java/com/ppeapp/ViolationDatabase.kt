package com.ppeapp

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "violations")
data class ViolationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,
    val timestamp: Long,
    val confidence: Float,
    val imagePath: String,
    val trackId: Int
)

@Dao
interface ViolationDao {
    @Insert
    suspend fun insert(violation: ViolationRecord)

    @Query("SELECT * FROM violations ORDER BY timestamp DESC")
    fun getAllViolations(): Flow<List<ViolationRecord>>

    @Query("DELETE FROM violations WHERE timestamp < :threshold")
    suspend fun deleteOldViolations(threshold: Long)

    @Query("SELECT imagePath FROM violations WHERE timestamp < :threshold")
    suspend fun getOldImagePaths(threshold: Long): List<String>
}

@Database(entities = [ViolationRecord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun violationDao(): ViolationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ppe_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
