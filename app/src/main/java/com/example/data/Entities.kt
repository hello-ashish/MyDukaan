package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val googleId: String,
    val name: String,
    val email: String,
    val profilePhotoUrl: String,
    val lastLoginTime: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ownerName: String,
    val phone: String,
    val email: String,
    val address: String,
    val gstNumber: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "inventory")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val storeId: Int = 1,
    val productName: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val barcode: String = "",
    val barcodeType: String = "", // e.g. EAN_13, QR_CODE
    val purchasePrice: Double,
    val sellingPrice: Double,
    val supplier: String = "",
    val batchNumber: String = "",
    val hsnCode: String = "",
    val manufacturingDate: String = "", // YYYY-MM-DD
    val expiryDate: String = "", // YYYY-MM-DD
    val minStock: Double = 0.0,
    val notes: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val marginPercent: Double = 0.0
) {
    fun toCsvRow(): String {
        return "$productName,$category,$quantity,$unit,$barcode,$purchasePrice,$sellingPrice,$supplier,$batchNumber,$hsnCode,$manufacturingDate,$expiryDate,$minStock,$notes"
    }

    companion object {
        fun csvHeader(): String {
            return "productName,category,quantity,unit,barcode,purchasePrice,sellingPrice,supplier,batchNumber,hsnCode,manufacturingDate,expiryDate,minStock,notes"
        }
    }
}

@Entity(tableName = "inventory_transactions")
data class InventoryTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val productName: String, // Kept denormalized for history if item is hard-deleted or to show directly
    val transactionType: String, // "ADD", "REDUCE", "SALE", "RETURN", "DAMAGE", "ADJUST"
    val quantity: Double,
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val action: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entityId: Int,
    val entityType: String, // "INVENTORY", "STORE", "TRANSACTION"
    val action: String, // "INSERT", "UPDATE", "DELETE"
    val status: String = "PENDING", // "PENDING", "SYNCED", "FAILED"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val autoBackup: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val expiryAlertDays: Int = 30,
    val darkMode: Boolean = false
)
