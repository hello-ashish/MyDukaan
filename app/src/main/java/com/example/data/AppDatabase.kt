package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        StoreEntity::class,
        InventoryItem::class,
        InventoryTransaction::class,
        AuditLog::class,
        SyncQueueItem::class,
        AppSettings::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun storeDao(): StoreDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventory_manager_db"
                )
                    .fallbackToDestructiveMigration() // safe for local-first/re-sync design
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
