package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUserSync(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clearUsers()
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores LIMIT 1")
    fun getStore(): Flow<StoreEntity?>

    @Query("SELECT * FROM stores LIMIT 1")
    suspend fun getStoreSync(): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: StoreEntity)

    @Query("DELETE FROM stores")
    suspend fun clearStores()
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory WHERE isDeleted = 0 ORDER BY productName ASC")
    fun getAllActiveItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory WHERE isDeleted = 0 ORDER BY productName ASC")
    suspend fun getAllActiveItemsSync(): List<InventoryItem>

    @Query("SELECT * FROM inventory WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    suspend fun getDeletedItemsSync(): List<InventoryItem>

    @Query("SELECT * FROM inventory WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Query("UPDATE inventory SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteItem(id: Int, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE inventory SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreItem(id: Int)

    @Query("DELETE FROM inventory WHERE id = :id")
    suspend fun permanentlyDeleteItem(id: Int)

    @Query("DELETE FROM inventory WHERE isDeleted = 1 AND deletedAt < :olderThan")
    suspend fun clearOldRecycleBin(olderThan: Long)

    @Query("DELETE FROM inventory")
    suspend fun clearAllItems()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM inventory_transactions ORDER BY createdAt DESC LIMIT 200")
    fun getAllTransactions(): Flow<List<InventoryTransaction>>

    @Query("SELECT * FROM inventory_transactions ORDER BY createdAt DESC LIMIT 200")
    suspend fun getAllTransactionsSync(): List<InventoryTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: InventoryTransaction)

    @Query("DELETE FROM inventory_transactions")
    suspend fun clearAllTransactions()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 250")
    fun getAllLogs(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 250")
    suspend fun getAllLogsSync(): List<AuditLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllLogs()
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingSyncItems(): Flow<List<SyncQueueItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: SyncQueueItem)

    @Query("UPDATE sync_queue SET status = 'SYNCED' WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Query("DELETE FROM sync_queue")
    suspend fun clearQueue()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSync(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)
}
