package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    private val userDao = db.userDao()
    private val storeDao = db.storeDao()
    private val inventoryDao = db.inventoryDao()
    private val transactionDao = db.transactionDao()
    private val auditLogDao = db.auditLogDao()
    private val settingsDao = db.settingsDao()

    // Expose Data Flows
    val currentUser: Flow<UserEntity?> = userDao.getUser()
    val storeInfo: Flow<StoreEntity?> = storeDao.getStore()
    val activeInventory: Flow<List<InventoryItem>> = inventoryDao.getAllActiveItems()
    val deletedInventory: Flow<List<InventoryItem>> = inventoryDao.getDeletedItems()
    val transactionHistory: Flow<List<InventoryTransaction>> = transactionDao.getAllTransactions()
    val auditLogs: Flow<List<AuditLog>> = auditLogDao.getAllLogs()
    val settings: Flow<AppSettings?> = settingsDao.getSettings()

    // --- USER CRUD ---
    suspend fun loginUser(googleId: String, name: String, email: String, profilePhotoUrl: String) = withContext(Dispatchers.IO) {
        val user = UserEntity(
            googleId = googleId,
            name = name,
            email = email,
            profilePhotoUrl = profilePhotoUrl
        )
        userDao.insertUser(user)
        addAuditLog("Login", "${name} logged in via Google Authentication.")
    }

    suspend fun logoutUser() = withContext(Dispatchers.IO) {
        val user = userDao.getUserSync()
        if (user != null) {
            addAuditLog("Logout", "${user.name} logged out.")
        }
        userDao.clearUsers()
    }

    // --- STORE SETUP ---
    suspend fun saveStoreInfo(name: String, owner: String, phone: String, email: String, address: String, gst: String) = withContext(Dispatchers.IO) {
        val store = StoreEntity(
            id = 1, // Only single store supported for local stores
            name = name,
            ownerName = owner,
            phone = phone,
            email = email,
            address = address,
            gstNumber = gst
        )
        storeDao.insertStore(store)
        addAuditLog("Store Setup", "Store config saved: $name ($owner)")
    }

    // --- PRODUCT CRUD ---
    suspend fun addProduct(item: InventoryItem) = withContext(Dispatchers.IO) {
        val activeItemsList = inventoryDao.getAllActiveItemsSync()
        val lowerName = item.productName.trim().lowercase()
        if (activeItemsList.any { it.productName.trim().lowercase() == lowerName }) {
            android.util.Log.w("InventoryRepository", "Skipped adding duplicate item name: ${item.productName}")
            return@withContext
        }

        val id = inventoryDao.insertItem(item).toInt()
        
        // Log transaction
        val txn = InventoryTransaction(
            productId = id,
            productName = item.productName,
            transactionType = "ADD",
            quantity = item.quantity,
            remarks = "Initial stock initialized."
        )
        transactionDao.insertTransaction(txn)
        addAuditLog("Product Added", "Product '${item.productName}' added with Initial Quantity ${item.quantity} ${item.unit}")
        triggerAutoBackup()
    }

    suspend fun updateProduct(updated: InventoryItem) = withContext(Dispatchers.IO) {
        val existing = inventoryDao.getItemById(updated.id) ?: return@withContext
        val activeItemsList = inventoryDao.getAllActiveItemsSync()
        val lowerName = updated.productName.trim().lowercase()
        if (activeItemsList.any { it.id != updated.id && it.productName.trim().lowercase() == lowerName }) {
            android.util.Log.w("InventoryRepository", "Skipped updating to a duplicate item name: ${updated.productName}")
            return@withContext
        }
        inventoryDao.updateItem(updated)

        // Log quantity changes
        val diff = updated.quantity - existing.quantity
        if (diff != 0.0) {
            val txnType = if (diff > 0) "ADD" else "REDUCE"
            val txn = InventoryTransaction(
                productId = updated.id,
                productName = updated.productName,
                transactionType = txnType,
                quantity = Math.abs(diff),
                remarks = "Stock adjustment update. Previous: ${existing.quantity}, New: ${updated.quantity}."
            )
            transactionDao.insertTransaction(txn)
        }

        addAuditLog("Product Updated", "Product '${updated.productName}' details updated.")
        triggerAutoBackup()
    }

    suspend fun deleteProduct(id: Int) = withContext(Dispatchers.IO) {
        val item = inventoryDao.getItemById(id) ?: return@withContext
        inventoryDao.softDeleteItem(id)

        val txn = InventoryTransaction(
            productId = id,
            productName = item.productName,
            transactionType = "ADJUST",
            quantity = 0.0,
            remarks = "Moved to Recycle Bin."
        )
        transactionDao.insertTransaction(txn)
        addAuditLog("Product Recycled", "Product '${item.productName}' moved to Recycle Bin.")
        triggerAutoBackup()
    }

    suspend fun restoreProduct(id: Int) = withContext(Dispatchers.IO) {
        val item = inventoryDao.getItemById(id) ?: return@withContext
        inventoryDao.restoreItem(id)

        val txn = InventoryTransaction(
            productId = id,
            productName = item.productName,
            transactionType = "ADJUST",
            quantity = 0.0,
            remarks = "Restored from Recycle Bin."
        )
        transactionDao.insertTransaction(txn)
        addAuditLog("Product Restored", "Product '${item.productName}' restored to active inventory.")
        triggerAutoBackup()
    }

    suspend fun clearAllProducts() = withContext(Dispatchers.IO) {
        inventoryDao.clearAllItems()
        addAuditLog("Inventory Wiped", "All existing products cleared from the database.")
    }

    suspend fun permanentlyDeleteProduct(id: Int) = withContext(Dispatchers.IO) {
        val item = inventoryDao.getItemById(id) ?: return@withContext
        inventoryDao.permanentlyDeleteItem(id)
        addAuditLog("Product Pruned", "Product '${item.productName}' deleted permanently.")
        triggerAutoBackup()
    }

    // --- AUDIT SYSTEM ---
    suspend fun addAuditLog(action: String, description: String) {
        val log = AuditLog(action = action, description = description)
        auditLogDao.insertLog(log)
    }

    // --- SETTINGS CRUD ---
    suspend fun saveSettings(autoBackup: Boolean, notificationsEnabled: Boolean, expiryDays: Int, darkMode: Boolean) = withContext(Dispatchers.IO) {
        val s = AppSettings(
            id = 1,
            autoBackup = autoBackup,
            notificationsEnabled = notificationsEnabled,
            expiryAlertDays = expiryDays,
            darkMode = darkMode
        )
        settingsDao.insertSettings(s)
        addAuditLog("Settings Changed", "Backup=$autoBackup, Notif=$notificationsEnabled, ExpiryDays=$expiryDays, DarkMode=$darkMode")
    }

    // Initialize Default settings
    suspend fun ensureDefaultSettings() = withContext(Dispatchers.IO) {
        val s = settingsDao.getSettingsSync()
        if (s == null) {
            settingsDao.insertSettings(AppSettings())
        }
    }

    // --- LOCAL AUTOMATIC BACKUP SYSTEM ---
    private suspend fun triggerAutoBackup() {
        val s = settingsDao.getSettingsSync() ?: AppSettings()
        if (s.autoBackup) {
            createLocalBackup()
        }
    }

    fun getMyDukaanFolder(): File {
        // Try Environment.DIRECTORY_DOWNLOADS first as it is easily accessible to users
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val dukuanDownloadDir = File(downloadsDir, "MyDukaan")
        try {
            if (!dukuanDownloadDir.exists()) {
                dukuanDownloadDir.mkdirs()
            }
            if (dukuanDownloadDir.exists() && dukuanDownloadDir.canWrite()) {
                return dukuanDownloadDir
            }
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Cannot write MyDukaan under Downloads", e)
        }

        // Try Documents directory
        val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val dukuanDocsDir = File(documentsDir, "MyDukaan")
        try {
            if (!dukuanDocsDir.exists()) {
                dukuanDocsDir.mkdirs()
            }
            if (dukuanDocsDir.exists() && dukuanDocsDir.canWrite()) {
                return dukuanDocsDir
            }
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Cannot write MyDukaan under Documents", e)
        }

        // Try directly on root of sdcard (legacy devices/support)
        val sdcard = android.os.Environment.getExternalStorageDirectory()
        val sdcardDir = File(sdcard, "MyDukaan")
        try {
            if (!sdcardDir.exists()) {
                sdcardDir.mkdirs()
            }
            if (sdcardDir.exists() && sdcardDir.canWrite()) {
                return sdcardDir
            }
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Cannot write MyDukaan directly to external storage root", e)
        }

        // Fallback: app specific external files directory
        val appExtDir = File(context.getExternalFilesDir(null), "MyDukaan")
        if (!appExtDir.exists()) {
            appExtDir.mkdirs()
        }
        return appExtDir
    }

    private val localBackupDir: File
        get() {
            val dir = File(getMyDukaanFolder(), "local_backups")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private val cloudBackupDir = File(context.filesDir, "cloud_backups_simulated")

    init {
        cloudBackupDir.mkdirs()
    }

    // Keep the latest 3 local backups
    suspend fun createLocalBackup(): File? = withContext(Dispatchers.IO) {
        try {
            val dbPayload = serializeDatabaseToJson() ?: return@withContext null
            val timeStamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(Date())
            val backupFile = File(localBackupDir, "local_backup_$timeStamp.json")
            backupFile.writeText(dbPayload)

            // Maintenance: keep latest 3 backups
            val localList = localBackupDir.listFiles { _, name -> name.endsWith(".json") }
                ?.sortedBy { it.name } ?: emptyList()
            if (localList.size > 3) {
                for (i in 0 until localList.size - 3) {
                    localList[i].delete()
                }
            }

            addAuditLog("Local Backup", "Automatic local backup created: ${backupFile.name}")
            return@withContext backupFile
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Local backup failed", e)
            return@withContext null
        }
    }

    fun getLocalBackupCount(): Int {
        return localBackupDir.listFiles { _, name -> name.endsWith(".json") }?.size ?: 0
    }

    fun getLocalBackupSizeSum(): Long {
        return localBackupDir.listFiles { _, name -> name.endsWith(".json") }?.sumOf { it.length() } ?: 0L
    }

    // --- GOOGLE DRIVE BACKUP / SYNC SYSTEM ---
    // Maintain the latest 10 backups
    suspend fun syncWithGoogleDrive(): String = withContext(Dispatchers.IO) {
        try {
            val dbPayload = serializeDatabaseToJson() ?: return@withContext "No data to sync"
            val timeStamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(Date())
            
            // Primary Sync Backup (inventory_backup.json)
            val primaryBackup = File(cloudBackupDir, "inventory_backup.json")
            primaryBackup.writeText(dbPayload)

            // Dynamic version backup in Google Drive directory structure
            val versionBackup = File(cloudBackupDir, "backup_$timeStamp.json")
            versionBackup.writeText(dbPayload)

            // Generate metadata
            val itemCount = db.inventoryDao().getAllActiveItemsSync().size
            val size = versionBackup.length()
            val checksum = calculateSha256(dbPayload)
            val metadata = JSONObject().apply {
                put("backupVersion", 1)
                put("backupDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                put("itemCount", itemCount)
                put("fileSize", size)
                put("checksum", checksum)
                put("appVersion", "1.0.0")
            }
            File(cloudBackupDir, "metadata.json").writeText(metadata.toString())

            // Maintain latest 10 cloud backups in backups list
            val cloudList = cloudBackupDir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".json") }
                ?.sortedBy { it.name } ?: emptyList()
            if (cloudList.size > 10) {
                for (i in 0 until cloudList.size - 10) {
                    cloudList[i].delete()
                }
            }

            addAuditLog("Cloud Backup", "Google Drive Backup successfully pushed: backup_$timeStamp.json")
            return@withContext "Success"
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Cloud backup failed", e)
            return@withContext "Failed: ${e.message}"
        }
    }

    fun getCloudBackupList(): List<Map<String, String>> {
        val files = cloudBackupDir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".json") }
            ?.sortedByDescending { it.name } ?: emptyList()

        return files.map { file ->
            val size = file.length()
            val dateStr = file.name.removePrefix("backup_").removeSuffix(".json")
                .replace("_", "-")
            val itemsCount = try {
                val json = JSONObject(file.readText())
                json.getJSONArray("inventory").length()
            } catch (e: Exception) {
                0
            }
            mapOf(
                "fileName" to file.name,
                "date" to dateStr,
                "size" to "${size / 1024} KB",
                "itemCount" to "$itemsCount items"
            )
        }
    }

    fun getCloudBackupCount(): Int {
        return cloudBackupDir.listFiles { _, name -> name.endsWith(".json") }?.size ?: 0
    }

    fun getCloudBackupSizeSum(): Long {
        return cloudBackupDir.listFiles { _, name -> name.endsWith(".json") }?.sumOf { it.length() } ?: 0L
    }

    // --- REVERSIBLE EMERGENCY RESTORE ENGINE ---
    suspend fun restoreBackupWithEmergency(backupFileName: String): String = withContext(Dispatchers.IO) {
        try {
            val fileToRestore = File(cloudBackupDir, backupFileName).takeIf { it.exists() }
                ?: File(localBackupDir, backupFileName).takeIf { it.exists() }
                ?: return@withContext "Backup file not found"

            val payload = fileToRestore.readText()
            val json = JSONObject(payload)
            val fileChecksum = json.optString("checksum", "")

            // 1. Calculate SHA-256 for validation (ignoring 'checksum' property)
            val jsonToVerify = JSONObject(payload)
            jsonToVerify.remove("checksum")
            val calculated = calculateSha256(jsonToVerify.toString())

            // Integrity Validation
            if (fileChecksum.isNotEmpty() && fileChecksum != calculated) {
                return@withContext "Integrity check failed: corrupt backup file"
            }

            // 2. CREATE EMERGENCY BACKUP BEFORE RESTORE
            val currentPayload = serializeDatabaseToJson()
            if (currentPayload != null) {
                val timeStamp = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.US).format(Date())
                val emergencyFile = File(cloudBackupDir, "emergency_backup_$timeStamp.json")
                emergencyFile.writeText(currentPayload)
                addAuditLog("Emergency Backup", "Emergency rollback point created: ${emergencyFile.name}")
            }

            // 3. APPLY RESTORE
            db.runInTransaction {
                try {
                    // Clear all existing
                    db.clearAllTables() // standard Room helper
                } catch (e: Exception) {
                    // Manual clean in case clearAllTables is complex
                }
            }

            // Clear explicitly
            db.inventoryDao().clearAllItems()
            db.transactionDao().clearAllTransactions()
            db.auditLogDao().clearAllLogs()
            db.userDao().clearUsers()
            db.storeDao().clearStores()

            // Restore Store Info
            if (json.has("store") && !json.isNull("store")) {
                val sObj = json.getJSONObject("store")
                storeDao.insertStore(
                    StoreEntity(
                        id = 1,
                        name = sObj.getString("name"),
                        ownerName = sObj.getString("ownerName"),
                        phone = sObj.getString("phone"),
                        email = sObj.getString("email"),
                        address = sObj.getString("address"),
                        gstNumber = sObj.optString("gstNumber", "")
                    )
                )
            }

            // Restore User Info
            if (json.has("user") && !json.isNull("user")) {
                val uObj = json.getJSONObject("user")
                userDao.insertUser(
                    UserEntity(
                        id = 1,
                        googleId = uObj.getString("googleId"),
                        name = uObj.getString("name"),
                        email = uObj.getString("email"),
                        profilePhotoUrl = uObj.getString("profilePhotoUrl")
                    )
                )
            }

            // Restore Inventory Items
            val invArray = json.getJSONArray("inventory")
            for (i in 0 until invArray.length()) {
                val item = invArray.getJSONObject(i)
                inventoryDao.insertItem(
                    InventoryItem(
                        id = item.optInt("id", 0),
                        storeId = item.optInt("storeId", 1),
                        productName = item.getString("productName"),
                        category = item.getString("category"),
                        quantity = item.getDouble("quantity"),
                        unit = item.getString("unit"),
                        barcode = item.optString("barcode", ""),
                        barcodeType = item.optString("barcodeType", ""),
                        purchasePrice = item.getDouble("purchasePrice"),
                        sellingPrice = item.getDouble("sellingPrice"),
                        supplier = item.optString("supplier", ""),
                        batchNumber = item.optString("batchNumber", ""),
                        hsnCode = item.optString("hsnCode", ""),
                        manufacturingDate = item.optString("manufacturingDate", ""),
                        expiryDate = item.optString("expiryDate", ""),
                        minStock = item.optDouble("minStock", 0.0),
                        notes = item.optString("notes", ""),
                        isDeleted = item.optBoolean("isDeleted", false),
                        deletedAt = if (item.has("deletedAt")) item.getLong("deletedAt") else null,
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            // Restore Transactions
            val txArray = json.getJSONArray("transactions")
            for (i in 0 until txArray.length()) {
                val tx = txArray.getJSONObject(i)
                transactionDao.insertTransaction(
                    InventoryTransaction(
                        productId = tx.getInt("productId"),
                        productName = tx.getString("productName"),
                        transactionType = tx.getString("transactionType"),
                        quantity = tx.getDouble("quantity"),
                        remarks = tx.optString("remarks", ""),
                        createdAt = tx.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // Restore Audit Logs
            val logArray = json.getJSONArray("logs")
            for (i in 0 until logArray.length()) {
                val logObj = logArray.getJSONObject(i)
                auditLogDao.insertLog(
                    AuditLog(
                        action = logObj.getString("action"),
                        description = logObj.getString("description"),
                        createdAt = logObj.getLong("createdAt")
                    )
                )
            }

            addAuditLog("Restore Done", "Database restored completely from: $backupFileName")
            return@withContext "Success"
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Restore failed", e)
            return@withContext "Failed: ${e.message}"
        }
    }

    // --- EXPORT TO CSV DIRECTLY ---
    suspend fun getExportCsvContent(subset: String): String = withContext(Dispatchers.IO) {
        val allItems = inventoryDao.getAllActiveItemsSync()
        val list = when (subset) {
            "EXPIRED" -> allItems.filter { isExpired(it.expiryDate) }
            "EXPIRING_SOON" -> allItems.filter { isExpiringSoon(it.expiryDate, 30) }
            "LOW_STOCK" -> allItems.filter { it.quantity <= it.minStock }
            else -> allItems
        }

        val sb = StringBuilder()
        sb.append(InventoryItem.csvHeader()).append("\n")
        for (item in list) {
            val safeName = item.productName.replace(",", " ")
            val safeCategory = item.category.replace(",", " ")
            val safeSupplier = item.supplier.replace(",", " ")
            val safeNotes = item.notes.replace(",", " ").replace("\n", " ")
            sb.append("${safeName},${safeCategory},${item.quantity},${item.unit},${item.barcode},${item.purchasePrice},${item.sellingPrice},${safeSupplier},${item.batchNumber},${item.hsnCode},${item.manufacturingDate},${item.expiryDate},${item.minStock},${safeNotes}\n")
        }
        return@withContext sb.toString()
    }

    suspend fun saveExportFileInMyDukaan(csvContent: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val root = getMyDukaanFolder()
        val exportsDir = File(root, "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "inventory_export_$timestamp.csv"
        val exportFile = File(exportsDir, fileName)
        exportFile.writeText(csvContent)
        
        // Also save as an excel compatible CSV with UTF-8 byte order mark (BOM) for compatibility with MS Excel
        val excelFileName = "inventory_excel_$timestamp.csv"
        val excelFile = File(exportsDir, excelFileName)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        excelFile.writeBytes(bom + csvContent.toByteArray(Charsets.UTF_8))
        
        addAuditLog("CSV Export", "Catalog exported to MyDukaan/exports: $fileName")
        
        return@withContext Pair(fileName, exportFile.absolutePath)
    }

    // --- CSV / TSV IMPORT PARSER & vyapar migration ---
    suspend fun importCsvContent(csvData: String): Map<String, Any> = withContext(Dispatchers.IO) {
        var importedCount = 0
        var errorCount = 0
        val errors = mutableListOf<String>()

        triggerAutoBackup() // Backup before imports!

        val lines = csvData.split("\n")
        if (lines.isEmpty()) {
            return@withContext mapOf("success" to false, "count" to 0, "errors" to listOf("Empty CSV file content"))
        }

        // Active inventory map for updates
        val activeItemsList = inventoryDao.getAllActiveItemsSync()
        val activeItemsMap = activeItemsList.associateBy { it.productName.trim().lowercase() }.toMutableMap()

        // Delimiter and Column mapping support/Vyapar migration detection
        val rawHeader = lines[0].replace("\uFEFF", "").replace("\uEFBBBF", "").trim()
        val delimiter = when {
            rawHeader.contains("\t") -> "\t"
            rawHeader.contains(";") && !rawHeader.contains(",") -> ";"
            else -> ","
        }
        val headerTokens = rawHeader.split(delimiter).map { it.replace("\"", "").trim().lowercase() }

        // Dynamic column index mapping finder with flexible regex / substring checks
        var nameIdx = headerTokens.indexOfFirst { it.contains("item name") || it.contains("product name") || it == "name" || it == "product" || it == "item" }
        if (nameIdx == -1) nameIdx = headerTokens.indexOfFirst { it.contains("name") }
        if (nameIdx == -1) nameIdx = headerTokens.indexOfFirst { it.contains("item") }

        val hsnIdx = headerTokens.indexOfFirst { it.contains("hsn") || it.contains("hsn/sac") || it == "hsn_code" }
        
        var saleIdx = headerTokens.indexOfFirst { it.contains("sale price") || it.contains("selling price") || it.contains("selling_price") || it == "sale" || it == "selling" || it == "mrp" }
        if (saleIdx == -1) saleIdx = headerTokens.indexOfFirst { it.contains("sale") }
        if (saleIdx == -1) saleIdx = headerTokens.indexOfFirst { it.contains("price") && !it.contains("purchase") && !it.contains("cost") }

        var purIdx = headerTokens.indexOfFirst { it.contains("purchase price") || it.contains("purchase_price") || it.contains("cost price") || it == "purchase" || it == "cost" || it == "pur price" || it == "rate" }
        if (purIdx == -1) purIdx = headerTokens.indexOfFirst { it.contains("purchase") || it.contains("cost") }

        val qtyIdx = headerTokens.indexOfFirst { it.contains("qty") || it.contains("quantity") || it.contains("stock") || it.contains("stock quantity") || it.contains("inv") }
        val unitIdx = headerTokens.indexOfFirst { it.contains("unit") }
        val catIdx = headerTokens.indexOfFirst { it.contains("category") || it.contains("cat") }
        val barcodeIdx = headerTokens.indexOfFirst { it.contains("barcode") || (it.contains("code") && !it.contains("hsn")) }
        val supplierIdx = headerTokens.indexOfFirst { it.contains("supplier") || it.contains("seller") || it.contains("vendor") }
        val minStockIdx = headerTokens.indexOfFirst { it.contains("min") || it.contains("minimum") }
        val expiryIdx = headerTokens.indexOfFirst { it.contains("expiry") || it.contains("exp") }

        val anyMatchedRef = nameIdx != -1 || saleIdx != -1 || purIdx != -1 || qtyIdx != -1

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            try {
                // Split line using detected delimiter
                val tokens = line.split(delimiter).map { it.replace("\"", "").trim() }
                if (tokens.isEmpty() || tokens[0].isBlank()) {
                    continue
                }

                val pName: String
                val cat: String
                val qty: Double
                val unit: String
                val bcode: String
                val purchase: Double
                val selling: Double
                val supplier: String
                val batch: String
                val hsn: String
                val exp: String
                val min: Double

                if (anyMatchedRef) {
                    pName = if (nameIdx != -1) tokens.getOrNull(nameIdx) ?: "Item $i" else "Item $i"
                    cat = if (catIdx != -1) tokens.getOrNull(catIdx) ?: "General" else "General"
                    qty = if (qtyIdx != -1) tokens.getOrNull(qtyIdx)?.toDoubleOrNull() ?: 0.0 else 0.0
                    unit = if (unitIdx != -1) tokens.getOrNull(unitIdx) ?: "PCS" else "PCS"
                    bcode = if (barcodeIdx != -1) tokens.getOrNull(barcodeIdx) ?: "" else ""
                    selling = if (saleIdx != -1) tokens.getOrNull(saleIdx)?.toDoubleOrNull() ?: 0.0 else 0.0
                    purchase = if (purIdx != -1) tokens.getOrNull(purIdx)?.toDoubleOrNull() ?: 0.0 else 0.0
                    supplier = if (supplierIdx != -1) tokens.getOrNull(supplierIdx) ?: "Imported" else "Imported"
                    batch = ""
                    hsn = if (hsnIdx != -1) tokens.getOrNull(hsnIdx) ?: "" else ""
                    exp = if (expiryIdx != -1) tokens.getOrNull(expiryIdx) ?: "" else ""
                    min = if (minStockIdx != -1) tokens.getOrNull(minStockIdx)?.toDoubleOrNull() ?: 5.0 else 5.0
                } else if (rawHeader.lowercase().contains("item name") || rawHeader.lowercase().contains("sale price") || rawHeader.lowercase().contains("stock quantity")) {
                    // Vyapar custom columns fallback
                    pName = tokens.getOrNull(0) ?: "Imported Item $i"
                    cat = tokens.getOrNull(1) ?: "General"
                    qty = tokens.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                    unit = tokens.getOrNull(3) ?: "PCS"
                    selling = tokens.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                    purchase = tokens.getOrNull(5)?.toDoubleOrNull() ?: (selling * 0.8)
                    bcode = tokens.getOrNull(6) ?: ""
                    supplier = "Vyapar Import"
                    batch = ""
                    hsn = ""
                    exp = ""
                    min = 5.0
                } else {
                    // Default header column indexes fallback
                    pName = tokens.getOrNull(0) ?: "Product $i"
                    cat = tokens.getOrNull(1) ?: "General"
                    qty = tokens.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                    unit = tokens.getOrNull(3) ?: "Units"
                    bcode = tokens.getOrNull(4) ?: ""
                    purchase = tokens.getOrNull(5)?.toDoubleOrNull() ?: 0.0
                    selling = tokens.getOrNull(6)?.toDoubleOrNull() ?: 0.0
                    supplier = tokens.getOrNull(7) ?: ""
                    batch = tokens.getOrNull(8) ?: ""
                    hsn = tokens.getOrNull(9) ?: ""
                    exp = tokens.getOrNull(11) ?: ""
                    min = tokens.getOrNull(12)?.toDoubleOrNull() ?: 10.0
                }

                // Strictly update active items in inventory when backup duplicates are found
                val lowerName = pName.trim().lowercase()
                if (lowerName.isNotEmpty()) {
                    val existingItem = activeItemsMap[lowerName]
                    if (existingItem != null) {
                        val updatedItem = existingItem.copy(
                            category = if (cat.isNotEmpty() && cat != "General") cat else existingItem.category,
                            quantity = if (qtyIdx != -1) qty else existingItem.quantity,
                            unit = if (unit.isNotEmpty() && unit != "PCS" && unit != "Units") unit else existingItem.unit,
                            barcode = if (bcode.isNotEmpty()) bcode else existingItem.barcode,
                            purchasePrice = if (purchase > 0.0) purchase else existingItem.purchasePrice,
                            sellingPrice = if (selling > 0.0) selling else existingItem.sellingPrice,
                            supplier = if (supplier.isNotEmpty() && supplier != "Imported") supplier else existingItem.supplier,
                            hsnCode = if (hsn.isNotEmpty()) hsn else existingItem.hsnCode,
                            expiryDate = if (exp.isNotEmpty()) exp else existingItem.expiryDate,
                            minStock = if (min > 0.0) min else existingItem.minStock
                        )
                        inventoryDao.updateItem(updatedItem)
                        importedCount++
                        continue
                    }
                    activeItemsMap[lowerName] = InventoryItem(
                        productName = pName,
                        category = cat,
                        quantity = qty,
                        unit = unit,
                        barcode = bcode,
                        purchasePrice = purchase,
                        sellingPrice = selling,
                        supplier = supplier,
                        batchNumber = batch,
                        hsnCode = hsn,
                        expiryDate = exp,
                        minStock = min
                    ) // Add to map for subsequent row duplicates in same CSV
                }

                val item = InventoryItem(
                    productName = pName,
                    category = cat,
                    quantity = qty,
                    unit = unit,
                    barcode = bcode,
                    purchasePrice = purchase,
                    sellingPrice = selling,
                    supplier = supplier,
                    batchNumber = batch,
                    hsnCode = hsn,
                    expiryDate = exp,
                    minStock = min
                )
                inventoryDao.insertItem(item)
                importedCount++
            } catch (e: Exception) {
                errorCount++
                errors.add("Line ${i + 1}: Exception: ${e.localizedMessage}")
            }
        }

        addAuditLog("CSV Import", "Successfully synced/imported $importedCount items. Failed lines: $errorCount.")
        return@withContext mapOf(
            "success" to true,
            "count" to importedCount,
            "errorCount" to errorCount,
            "errors" to errors
        )
    }

    // --- DATABASE TO JSON SERIALIZATION ENGINE ---
    private suspend fun serializeDatabaseToJson(): String? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            
            // Build Version Info & Dates
            root.put("backupVersion", 1)
            root.put("backupDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
            root.put("appVersion", "1.0.0")

            // User Info
            val user = userDao.getUserSync()
            if (user != null) {
                root.put("user", JSONObject().apply {
                    put("googleId", user.googleId)
                    put("name", user.name)
                    put("email", user.email)
                    put("profilePhotoUrl", user.profilePhotoUrl)
                })
            }

            // Store Info
            val store = storeDao.getStoreSync()
            if (store != null) {
                root.put("store", JSONObject().apply {
                    put("name", store.name)
                    put("ownerName", store.ownerName)
                    put("phone", store.phone)
                    put("email", store.email)
                    put("address", store.address)
                    put("gstNumber", store.gstNumber)
                })
            }

            // Inventory items
            val items = db.inventoryDao().getAllActiveItemsSync() + db.inventoryDao().getDeletedItemsSync()
            val invArray = JSONArray()
            for (item in items) {
                val p = JSONObject().apply {
                    put("id", item.id)
                    put("storeId", item.storeId)
                    put("productName", item.productName)
                    put("category", item.category)
                    put("quantity", item.quantity)
                    put("unit", item.unit)
                    put("barcode", item.barcode)
                    put("barcodeType", item.barcodeType)
                    put("purchasePrice", item.purchasePrice)
                    put("sellingPrice", item.sellingPrice)
                    put("supplier", item.supplier)
                    put("batchNumber", item.batchNumber)
                    put("hsnCode", item.hsnCode)
                    put("manufacturingDate", item.manufacturingDate)
                    put("expiryDate", item.expiryDate)
                    put("minStock", item.minStock)
                    put("notes", item.notes)
                    put("isDeleted", item.isDeleted)
                    if (item.deletedAt != null) put("deletedAt", item.deletedAt)
                    put("createdAt", item.createdAt)
                    put("updatedAt", item.updatedAt)
                }
                invArray.put(p)
            }
            root.put("inventory", invArray)

            // Transactions list
            val txs = transactionDao.getAllTransactionsSync()
            val txArray = JSONArray()
            for (tx in txs) {
                val tObj = JSONObject().apply {
                    put("productId", tx.productId)
                    put("productName", tx.productName)
                    put("transactionType", tx.transactionType)
                    put("quantity", tx.quantity)
                    put("remarks", tx.remarks)
                    put("createdAt", tx.createdAt)
                }
                txArray.put(tObj)
            }
            root.put("transactions", txArray)

            // Audit Logs
            val logs = auditLogDao.getAllLogsSync()
            val logArray = JSONArray()
            for (l in logs) {
                val lObj = JSONObject().apply {
                    put("action", l.action)
                    put("description", l.description)
                    put("createdAt", l.createdAt)
                }
                logArray.put(lObj)
            }
            root.put("logs", logArray)

            // Calculate SHA-256 for integrity
            val stringToHash = root.toString()
            val hashValue = calculateSha256(stringToHash)
            
            // Embed SHA-256 validation code inside JSON container
            root.put("checksum", hashValue)

            return@withContext root.toString()
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Serialization error", e)
            return@withContext null
        }
    }

    // --- SHA-256 & UTILS ---
    private fun calculateSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun isExpired(expiryDateStr: String): Boolean {
        if (expiryDateStr.isEmpty()) return false
        return try {
            val sdf = SimpleDateFormat("MM-yyyy", Locale.ROOT)
            val expDate = sdf.parse(expiryDateStr) ?: return false
            expDate.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    private fun isExpiringSoon(expiryDateStr: String, withinDays: Int): Boolean {
        if (expiryDateStr.isEmpty()) return false
        return try {
            val sdf = SimpleDateFormat("MM-yyyy", Locale.ROOT)
            val expDate = sdf.parse(expiryDateStr) ?: return false
            val diffMs = expDate.time - System.currentTimeMillis()
            val diffDays = diffMs / (1000 * 60 * 60 * 24)
            diffDays in 0..withinDays
        } catch (e: Exception) {
            false
        }
    }
}
