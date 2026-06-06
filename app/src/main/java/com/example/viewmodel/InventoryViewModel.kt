package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Clean State structure to bundle search parameters together
// This avoids arity limits of flow combine() in Kotlin (max 5)
data class InventoryFilterState(
    val query: String = "",
    val category: String? = null,
    val supplier: String? = null,
    val lowStock: Boolean = false,
    val expiring: Boolean = false,
    val expired: Boolean = false,
    val sortBy: String = "NAME_ASC"
)

class InventoryViewModel(
    application: Application,
    private val repository: InventoryRepository
) : AndroidViewModel(application) {

    // --- REPOS FLOW ---
    val currentUser = repository.currentUser.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val storeInfo = repository.storeInfo.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val activeItems = repository.activeInventory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recycleBinItems = repository.deletedInventory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactionHistory = repository.transactionHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val auditLogs = repository.auditLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settingsState = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- BUNDLED FILTER STATE FLOW ---
    private val _filterState = MutableStateFlow(InventoryFilterState())
    val filterState: StateFlow<InventoryFilterState> = _filterState

    // Map public fields to state values so our screens stay unchanged
    val searchQuery = _filterState.map { it.query }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val selectedCategory = _filterState.map { it.category }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selectedSupplier = _filterState.map { it.supplier }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val filterLowStock = _filterState.map { it.lowStock }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val filterExpiringSoon = _filterState.map { it.expiring }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val filterExpired = _filterState.map { it.expired }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val sortBy = _filterState.map { it.sortBy }.stateIn(viewModelScope, SharingStarted.Eagerly, "NAME_ASC")

    // --- SEARCH / FILTERED RESULT PIPELINE ---
    val filteredInventory: StateFlow<List<InventoryItem>> = combine(
        activeItems,
        _filterState,
        settingsState
    ) { items, filters, settings ->
        var list = items

        // 1. Text Search
        if (filters.query.isNotEmpty()) {
            list = list.filter {
                it.productName.contains(filters.query, ignoreCase = true) ||
                        it.barcode.contains(filters.query, ignoreCase = true) ||
                        it.supplier.contains(filters.query, ignoreCase = true) ||
                        it.batchNumber.contains(filters.query, ignoreCase = true) ||
                        it.hsnCode.contains(filters.query, ignoreCase = true)
            }
        }

        // 2. Category Filter
        if (filters.category != null) {
            list = list.filter { it.category == filters.category }
        }

        // 3. Supplier Filter
        if (filters.supplier != null) {
            list = list.filter { it.supplier == filters.supplier }
        }

        // 4. Low Stock Filter
        if (filters.lowStock) {
            list = list.filter { it.quantity <= it.minStock }
        }

        // 5. Expired & Expiring Soon Filter
        val alertDays = settings?.expiryAlertDays ?: 30
        if (filters.expired) {
            list = list.filter { isItemExpired(it.expiryDate) }
        } else if (filters.expiring) {
            list = list.filter { isItemExpiringSoon(it.expiryDate, alertDays) }
        }

        // 6. Sorting
        when (filters.sortBy) {
            "NAME_ASC" -> list.sortedBy { it.productName.lowercase(Locale.ROOT) }
            "NAME_DESC" -> list.sortedByDescending { it.productName.lowercase(Locale.ROOT) }
            "QTY_ASC" -> list.sortedBy { it.quantity }
            "QTY_DESC" -> list.sortedByDescending { it.quantity }
            "EXPIRY_ASC" -> list.sortedWith(compareBy<InventoryItem> { it.expiryDate.isEmpty() }.thenBy { it.expiryDate })
            "PRICE_DESC" -> list.sortedByDescending { it.sellingPrice }
            "RECENT" -> list.sortedByDescending { it.updatedAt }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- SYSTEM CLOUD SYNC & PUSH COUNTER ---
    private val _syncPendingChangesCount = MutableStateFlow(0)
    val syncPendingChangesCount: StateFlow<Int> = _syncPendingChangesCount

    private val _syncStatus = MutableStateFlow("SYNCED") // "SYNCED", "PENDING", "FAILED"
    val syncStatus: StateFlow<String> = _syncStatus

    private val _lastSyncTime = MutableStateFlow<String?>(null)
    val lastSyncTime: StateFlow<String?> = _lastSyncTime

    // --- STORAGE METRICS PANEL ---
    private val _storageStats = MutableStateFlow<Map<String, String>>(emptyMap())
    val storageStats: StateFlow<Map<String, String>> = _storageStats

    private val _cloudBackups = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val cloudBackups: StateFlow<List<Map<String, String>>> = _cloudBackups

    init {
        // Build initial configs
        viewModelScope.launch {
            repository.ensureDefaultSettings()
            calculateStorageMetrics()
            updateBackupHistory()
            
            // Random mock cloud backup to let user restore immediately if they want
            if (repository.getCloudBackupCount() == 0) {
                repository.syncWithGoogleDrive()
                updateBackupHistory()
            }
        }
    }

    // --- FILTER TRIGGERS ---
    fun setSearchQuery(q: String) {
        _filterState.value = _filterState.value.copy(query = q)
    }
    fun setCategoryFilter(cat: String?) {
        _filterState.value = _filterState.value.copy(category = cat)
    }
    fun setSupplierFilter(sup: String?) {
        _filterState.value = _filterState.value.copy(supplier = sup)
    }
    fun toggleLowStockFilter() {
        _filterState.value = _filterState.value.copy(lowStock = !_filterState.value.lowStock)
    }
    fun toggleExpiringSoon() {
        val currExpiring = _filterState.value.expiring
        _filterState.value = _filterState.value.copy(
            expiring = !currExpiring,
            expired = if (!currExpiring) false else _filterState.value.expired
        )
    }
    fun toggleExpired() {
        val currExpired = _filterState.value.expired
        _filterState.value = _filterState.value.copy(
            expired = !currExpired,
            expiring = if (!currExpired) false else _filterState.value.expiring
        )
    }
    fun setSorting(mode: String) {
        _filterState.value = _filterState.value.copy(sortBy = mode)
    }

    // --- USER PROFILE & CORE GOOGLE SIGN IN ---
    fun handleGoogleSignIn(id: String, name: String, email: String, avatarUrl: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.loginUser(id, name, email, avatarUrl)
            onComplete(true)
        }
    }

    fun handleGoogleSignOut() {
        viewModelScope.launch {
            repository.logoutUser()
        }
    }

    // --- STORE INFORMATION SETUP ---
    fun registerStore(name: String, owner: String, phone: String, email: String, address: String, gst: String) {
        viewModelScope.launch {
            repository.saveStoreInfo(name, owner, phone, email, address, gst)
        }
    }

    // --- PRODUCTS MANAGEMENT WRAPPERS ---
    fun addProduct(
        name: String, category: String, qty: Double, unit: String,
        purchasePrice: Double, sellingPrice: Double, minStock: Double,
        barcode: String = "", batch: String = "", hsn: String = "",
        mfg: String = "", exp: String = "", supplier: String = "", notes: String = "",
        marginPercent: Double = 0.0
    ) {
        viewModelScope.launch {
            val item = InventoryItem(
                productName = name,
                category = category,
                quantity = qty,
                unit = unit,
                purchasePrice = purchasePrice,
                sellingPrice = sellingPrice,
                minStock = minStock,
                barcode = barcode,
                batchNumber = batch,
                hsnCode = hsn,
                manufacturingDate = mfg,
                expiryDate = exp,
                supplier = supplier,
                notes = notes,
                marginPercent = marginPercent
            )
            repository.addProduct(item)
            incrementPendingChanges()
            calculateStorageMetrics()
        }
    }

    fun updateProductDetails(
        id: Int, name: String, category: String, qty: Double, unit: String,
        purchasePrice: Double, sellingPrice: Double, minStock: Double,
        barcode: String = "", batch: String = "", hsn: String = "",
        mfg: String = "", exp: String = "", supplier: String = "", notes: String = "",
        marginPercent: Double = 0.0
    ) {
        viewModelScope.launch {
            val item = InventoryItem(
                id = id,
                productName = name,
                category = category,
                quantity = qty,
                unit = unit,
                purchasePrice = purchasePrice,
                sellingPrice = sellingPrice,
                minStock = minStock,
                barcode = barcode,
                batchNumber = batch,
                hsnCode = hsn,
                manufacturingDate = mfg,
                expiryDate = exp,
                supplier = supplier,
                notes = notes,
                marginPercent = marginPercent,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateProduct(item)
            incrementPendingChanges()
            calculateStorageMetrics()
        }
    }

    fun recycleProduct(id: Int) {
        viewModelScope.launch {
            repository.deleteProduct(id)
            incrementPendingChanges()
            calculateStorageMetrics()
        }
    }

    fun restoreRecycledProduct(id: Int) {
        viewModelScope.launch {
            repository.restoreProduct(id)
            incrementPendingChanges()
            calculateStorageMetrics()
        }
    }

    fun deleteProductPermanently(id: Int) {
        viewModelScope.launch {
            repository.permanentlyDeleteProduct(id)
            incrementPendingChanges()
            calculateStorageMetrics()
        }
    }

    fun clearInventoryAll() {
        viewModelScope.launch {
            repository.clearAllProducts()
            incrementPendingChanges()
            calculateStorageMetrics()
        }
    }

    // --- QUICK RE-ADJUST STOCK FROM DETAIL VIEW ---
    fun adjustStockLevelBy(id: Int, diff: Double, remarks: String) {
        viewModelScope.launch {
            val item = repository.activeInventory.firstOrNull()?.find { it.id == id } ?: return@launch
            val newQty = Math.max(0.0, item.quantity + diff)
            updateProductDetails(
                id = item.id,
                name = item.productName,
                category = item.category,
                qty = newQty,
                unit = item.unit,
                purchasePrice = item.purchasePrice,
                sellingPrice = item.sellingPrice,
                minStock = item.minStock,
                barcode = item.barcode,
                batch = item.batchNumber,
                hsn = item.hsnCode,
                mfg = item.manufacturingDate,
                exp = item.expiryDate,
                supplier = item.supplier,
                notes = remarks.takeIf { it.isNotEmpty() } ?: item.notes
            )
        }
    }

    // --- PREFERENCES WRAPPERS ---
    fun saveUserPreferences(autoBackup: Boolean, notify: Boolean, expiryDays: Int, dark: Boolean) {
        viewModelScope.launch {
            repository.saveSettings(autoBackup, notify, expiryDays, dark)
        }
    }

    // --- CSV IMPORT / EXPORT WRAPPERS ---
    fun exportInventoryToMyDukaan(onComplete: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val csvContent = repository.getExportCsvContent("ALL")
                val result = repository.saveExportFileInMyDukaan(csvContent)
                calculateStorageMetrics()
                onComplete(result.first, result.second)
            } catch (e: Exception) {
                onComplete(null, e.message)
            }
        }
    }

    fun getMyDukaanFolderAbsolutePath(): String {
        return repository.getMyDukaanFolder().absolutePath
    }

    fun generateExportFileString(subset: String, onFinished: (String) -> Unit) {
        viewModelScope.launch {
            val content = repository.getExportCsvContent(subset)
            repository.addAuditLog("CSV Export", "CSV Export triggered for category: $subset.")
            onFinished(content)
        }
    }

    fun importCsvData(text: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.importCsvContent(text)
            val success = res["success"] as? Boolean ?: false
            if (success) {
                val count = res["count"] as? Int ?: 0
                val errMsg = res["errorCount"] as? Int ?: 0
                onComplete("Successfully imported $count items! Errors: $errMsg")
                incrementPendingChanges()
                calculateStorageMetrics()
            } else {
                val errs = res["errors"] as? List<*>
                onComplete("Failed: ${errs?.firstOrNull() ?: "Unknown error"}")
            }
        }
    }

    // Seed dummy products for testing and retail business store layout presentation
    fun seedTemplateData() {
        viewModelScope.launch {
            repository.clearAllProducts()
            val csvDemo = """productName,category,quantity,unit,barcode,purchasePrice,sellingPrice,supplier,batchNumber,hsnCode,manufacturingDate,expiryDate,minStock,notes
Amoxicillin 500mg,General,150.0,Units,,12.5,22.0,Cipla Pharmaceuticals,AMX-402,,,10-2026,20.0,Keep in a dry place.
Paracetamol 650mg,General,500.0,Units,,1.2,3.0,GlaxoSmithKline,PCM-911,,,06-2027,50.0,Store below 30C.
Metformin 850mg,General,8.0,Units,,8.0,15.5,Merck,MET-221,,,05-2026,15.0,Requires Rx. Low Stock Warning!
Cetrizine 10mg,General,180.0,Units,,0.5,1.5,Dr Reddys,CET-088,,,03-2025,30.0,EXPIRED! High priority alert.
Atorvastatin 20mg,General,90.0,Units,,18.0,32.0,Pfizer,ATV-334,,,06-2026,20.0,Expiring soon warning.
Multivitamins Softgel,General,120.0,Units,,4.5,9.0,Abbott Labs,MVI-512,,,12-2027,25.0,Daily food supplement.
"""
            repository.importCsvContent(csvDemo)
            repository.addAuditLog("Demo Data Initialized", "Sample pharmaceutical items seeded into SQLite Database.")
            incrementPendingChanges()
            calculateStorageMetrics()
        }
    }

    // --- GOOGLE DRIVE SYNC ENGINE ---
    fun triggerManualSync() {
        _syncStatus.value = "PENDING"
        viewModelScope.launch {
            val res = repository.syncWithGoogleDrive()
            if (res == "Success") {
                _syncStatus.value = "SYNCED"
                _syncPendingChangesCount.value = 0
                _lastSyncTime.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                updateBackupHistory()
                calculateStorageMetrics()
            } else {
                _syncStatus.value = "FAILED"
            }
        }
    }

    private fun incrementPendingChanges() {
        _syncPendingChangesCount.value += 1
        _syncStatus.value = "PENDING"
    }

    fun updateBackupHistory() {
        viewModelScope.launch {
            _cloudBackups.value = repository.getCloudBackupList()
        }
    }

    fun restoreSelectedBackup(fileName: String, onFinished: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.restoreBackupWithEmergency(fileName)
            if (res == "Success") {
                _syncPendingChangesCount.value = 0
                _syncStatus.value = "SYNCED"
                _lastSyncTime.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                calculateStorageMetrics()
                updateBackupHistory()
                onFinished("Backup successfully restored! Dynamic emergency safety point created.")
            } else {
                onFinished("Failed: $res")
            }
        }
    }

    // --- DYNAMIC STORAGE ESTIMATION PANEL ---
    suspend fun calculateStorageMetrics() = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val dbFile = app.getDatabasePath("inventory_manager_db")
        val dbSizeStr = if (dbFile.exists()) "${dbFile.length() / 1024} KB" else "8 KB"
        
        val localCount = repository.getLocalBackupCount()
        val localSize = repository.getLocalBackupSizeSum()
        val localSizeStr = "${localSize / 1024} KB"

        val cloudCount = repository.getCloudBackupCount()
        val cloudSize = repository.getCloudBackupSizeSum()
        val cloudSizeStr = "${cloudSize / 1024} KB"

        val actives = repository.activeInventory.firstOrNull()?.size ?: 0
        val recycles = repository.deletedInventory.firstOrNull()?.size ?: 0
        val txns = repository.transactionHistory.firstOrNull()?.size ?: 0

        _storageStats.value = mapOf(
            "dbSize" to dbSizeStr,
            "localBackupCount" to "$localCount backups",
            "localBackupSize" to localSizeStr,
            "cloudBackupCount" to "$cloudCount cloud records",
            "cloudBackupSize" to cloudSizeStr,
            "totalProducts" to "$actives products active",
            "totalRecycle" to "$recycles items in Bin",
            "totalTransactions" to "$txns audit movements"
        )
    }

    // --- UTILS HELPER FOR FLOW CHECKS ---
    private fun isItemExpired(expiryDateStr: String): Boolean {
        if (expiryDateStr.isEmpty()) return false
        return try {
            val sdf = SimpleDateFormat("MM-yyyy", Locale.ROOT)
            val expDate = sdf.parse(expiryDateStr) ?: return false
            expDate.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    private fun isItemExpiringSoon(expiryDateStr: String, alertDays: Int): Boolean {
        if (expiryDateStr.isEmpty()) return false
        return try {
            val sdf = SimpleDateFormat("MM-yyyy", Locale.ROOT)
            val expDate = sdf.parse(expiryDateStr) ?: return false
            val diffMs = expDate.time - System.currentTimeMillis()
            val diffDays = diffMs / (1000 * 60 * 60 * 24)
            diffDays in 0..alertDays
        } catch (e: Exception) {
            false
        }
    }
}
