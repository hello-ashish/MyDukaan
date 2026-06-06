package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.InventoryItem
import com.example.ui.theme.PrimaryTeal
import com.example.viewmodel.InventoryViewModel
import kotlinx.coroutines.launch

enum class BillImportStep {
    IDLE,
    CROP,
    EXTRACTING,
    REVIEW
}

sealed class ScreenRoute {
    object Onboarding : ScreenRoute()
    object MainHost : ScreenRoute()
    data class ProductDetail(val id: Int) : ScreenRoute()
    object RecycleBin : ScreenRoute()
    object AuditLogs : ScreenRoute()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: InventoryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- VIEWMODEL STATS BINDINGS ---
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val store by viewModel.storeInfo.collectAsStateWithLifecycle()
    val rawItems by viewModel.activeItems.collectAsStateWithLifecycle()
    val filteredItems by viewModel.filteredInventory.collectAsStateWithLifecycle()
    val deletedItems by viewModel.recycleBinItems.collectAsStateWithLifecycle()
    val logs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val cloudBackups by viewModel.cloudBackups.collectAsStateWithLifecycle()
    val transactions by viewModel.transactionHistory.collectAsStateWithLifecycle(initialValue = emptyList())

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedSupplier by viewModel.selectedSupplier.collectAsStateWithLifecycle()
    val filterLowStock by viewModel.filterLowStock.collectAsStateWithLifecycle()
    val filterExpiringSoon by viewModel.filterExpiringSoon.collectAsStateWithLifecycle()
    val filterExpired by viewModel.filterExpired.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()

    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val pendingChangesStr by viewModel.syncPendingChangesCount.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()

    // --- SMART BILL IMPORT FLOW STATES ---
    var importStep by remember { mutableStateOf(BillImportStep.IDLE) }
    var activeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var extractedItemsList by remember { mutableStateOf<List<com.example.data.ExtractedItem>>(emptyList()) }
    var reviewIndex by remember { mutableStateOf(0) }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            val loaded = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, photoUri!!)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, photoUri!!)
                }
            } catch (e: Exception) {
                try {
                    android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(photoUri!!))
                } catch (ex: Exception) {
                    null
                }
            }
            if (loaded != null) {
                activeBitmap = loaded
                importStep = BillImportStep.CROP
            } else {
                scope.launch { snackbarHostState.showSnackbar("Error writing camera file to memory!") }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val loaded = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                try {
                    android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                } catch (ex: Exception) {
                    null
                }
            }
            if (loaded != null) {
                activeBitmap = loaded
                importStep = BillImportStep.CROP
            } else {
                scope.launch { snackbarHostState.showSnackbar("Failed to prepare selected file!") }
            }
        }
    }

    fun triggerCameraCapture() {
        try {
            val tempFile = java.io.File.createTempFile("camera_bill_", ".jpg", context.cacheDir).apply {
                deleteOnExit()
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            photoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Failed to start camera device: ${e.message}") }
        }
    }

    // --- NAVIGATION ROUTING SYSTEM ---
    var currentRoute by remember { mutableStateOf<ScreenRoute>(ScreenRoute.Onboarding) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Dashboard, 1: Inventory, 2: Settings
    var onboardingStep by remember { mutableStateOf(1) } // 1..5

    // Handle initial state and onboarding skips on database updates
    LaunchedEffect(user, store) {
        if (user != null && store != null) {
            currentRoute = ScreenRoute.MainHost
        } else {
            currentRoute = ScreenRoute.Onboarding
        }
    }

    // Dynamic Back Handler
    BackHandler(enabled = currentRoute !is ScreenRoute.Onboarding) {
        when (currentRoute) {
            is ScreenRoute.ProductDetail, ScreenRoute.RecycleBin, ScreenRoute.AuditLogs -> {
                currentRoute = ScreenRoute.MainHost
            }
            else -> {
                // Let user back out of the app
                (context as? Activity)?.finish()
            }
        }
    }

    // Host Scaffolding
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val route = currentRoute) {
            is ScreenRoute.Onboarding -> {
                OnboardingScreenContainer(
                    onboardingStep = onboardingStep,
                    onStepChanged = { onboardingStep = it },
                    googleId = user?.googleId,
                    onGoogleLogin = { id, name, email, photo ->
                        viewModel.handleGoogleSignIn(id, name, email, photo) {
                            scope.launch { snackbarHostState.showSnackbar("Logged in successfully as $name!") }
                        }
                    },
                    onRestoreSelected = { filename ->
                        viewModel.restoreSelectedBackup(filename) { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onSetupStore = { name, owner, phone, email, address, gst ->
                        viewModel.registerStore(name, owner, phone, email, address, gst)
                        scope.launch { snackbarHostState.showSnackbar("Store details registered completely!") }
                    },
                    cloudBackups = cloudBackups,
                    onFinishOnboarding = {
                        currentRoute = ScreenRoute.MainHost
                    }
                )
            }

            is ScreenRoute.MainHost -> {
                if (importStep != BillImportStep.IDLE) {
                    when (importStep) {
                        BillImportStep.CROP -> {
                            if (activeBitmap != null) {
                                BillCropperScreen(
                                    bitmap = activeBitmap!!,
                                    onCropped = { cropped ->
                                        importStep = BillImportStep.EXTRACTING
                                        scope.launch {
                                            try {
                                                val items = com.example.data.GeminiService.extractItemsFromBill(cropped)
                                                if (items.isNotEmpty()) {
                                                    extractedItemsList = items
                                                    reviewIndex = 0
                                                    importStep = BillImportStep.REVIEW
                                                } else {
                                                    importStep = BillImportStep.IDLE
                                                    snackbarHostState.showSnackbar("❌ Could not extract any products. Try cropping closer with better lighting.")
                                                }
                                            } catch (e: Exception) {
                                                importStep = BillImportStep.IDLE
                                                snackbarHostState.showSnackbar("❌ Critical extraction error. Please check your network connection.")
                                            }
                                        }
                                    },
                                    onCancel = {
                                        importStep = BillImportStep.IDLE
                                        activeBitmap = null
                                    }
                                )
                            } else {
                                importStep = BillImportStep.IDLE
                            }
                        }
                        BillImportStep.EXTRACTING -> {
                            BillExtractingScreen()
                        }
                        BillImportStep.REVIEW -> {
                            BillReviewScreen(
                                extractedItems = extractedItemsList,
                                currentIndex = reviewIndex,
                                onSaveItem = { updatedItem ->
                                    viewModel.addProduct(
                                        name = updatedItem.productName,
                                        category = updatedItem.category,
                                        qty = updatedItem.quantity,
                                        unit = updatedItem.unit,
                                        purchasePrice = updatedItem.purchasePrice,
                                        sellingPrice = updatedItem.sellingPrice,
                                        minStock = updatedItem.minStock,
                                        barcode = updatedItem.barcode,
                                        batch = updatedItem.batchNumber,
                                        hsn = updatedItem.hsnCode,
                                        exp = updatedItem.expiryDate,
                                        supplier = updatedItem.supplier,
                                        notes = updatedItem.notes
                                    )
                                    if (reviewIndex + 1 < extractedItemsList.size) {
                                        reviewIndex += 1
                                    } else {
                                        importStep = BillImportStep.IDLE
                                        activeBitmap = null
                                        extractedItemsList = emptyList()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("🎉 All items successfully verified & registered into active SQLite catalog!")
                                        }
                                    }
                                },
                                onSkipItem = {
                                    if (reviewIndex + 1 < extractedItemsList.size) {
                                        reviewIndex += 1
                                    } else {
                                        importStep = BillImportStep.IDLE
                                        activeBitmap = null
                                        extractedItemsList = emptyList()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Import wizard finished.")
                                        }
                                    }
                                },
                                onAbort = {
                                    importStep = BillImportStep.IDLE
                                    activeBitmap = null
                                    extractedItemsList = emptyList()
                                }
                            )
                        }
                        else -> {
                            importStep = BillImportStep.IDLE
                        }
                    }
                } else {
                    Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(imageVector = if (selectedTab == 0) Icons.Filled.Dashboard else Icons.Outlined.Dashboard, contentDescription = "Dashboard") },
                                label = { Text("Dashboard") },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryTeal.copy(alpha = 0.12f), selectedIconColor = PrimaryTeal)
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(imageVector = if (selectedTab == 1) Icons.Filled.ListAlt else Icons.Outlined.ListAlt, contentDescription = "Stocks Catalog") },
                                label = { Text("Inventory") },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryTeal.copy(alpha = 0.12f), selectedIconColor = PrimaryTeal)
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(imageVector = if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "Settings Control") },
                                label = { Text("Settings") },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryTeal.copy(alpha = 0.12f), selectedIconColor = PrimaryTeal)
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(imageVector = if (selectedTab == 3) Icons.Filled.SmartToy else Icons.Outlined.SmartToy, contentDescription = "PharmaAI Assistant") },
                                label = { Text("AI Bot") },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryTeal.copy(alpha = 0.12f), selectedIconColor = PrimaryTeal)
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> DashboardScreen(
                                items = rawItems,
                                logs = logs,
                                syncStatus = syncStatus,
                                pendingSyncCount = pendingChangesStr,
                                lastSyncTime = lastSyncTime,
                                storeName = store?.name ?: "Inventory Store",
                                onNavigateToCategory = { cat ->
                                    viewModel.setCategoryFilter(cat)
                                    selectedTab = 1
                                },
                                onQuickAction = { act ->
                                    when (act) {
                                        "ADD" -> { currentRoute = ScreenRoute.ProductDetail(0) }
                                        "IMPORT" -> { selectedTab = 2 }
                                        "SYNC" -> { viewModel.triggerManualSync() }
                                        "RECYCLE" -> { currentRoute = ScreenRoute.RecycleBin }
                                        "AUDIT" -> { currentRoute = ScreenRoute.AuditLogs }
                                        "LOW_STOCK_FILTER" -> {
                                            viewModel.toggleLowStockFilter()
                                            selectedTab = 1
                                        }
                                        "EXPIRY_FILTER" -> {
                                            viewModel.toggleExpired()
                                            selectedTab = 1
                                        }
                                        "EXPORT" -> {
                                            viewModel.exportInventoryToMyDukaan { filename, parentPath ->
                                                scope.launch {
                                                    if (filename != null) {
                                                        val folderPath = viewModel.getMyDukaanFolderAbsolutePath()
                                                        snackbarHostState.showSnackbar("Export saved in $folderPath/exports/$filename!")
                                                    } else {
                                                        snackbarHostState.showSnackbar("Export failed: ${parentPath ?: "Unknown error"}")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                             )

                             1 -> InventoryListScreen(
                                items = filteredItems,
                                searchQuery = searchQuery,
                                onSearchChange = viewModel::setSearchQuery,
                                selectedCategory = selectedCategory,
                                onCategorySelected = viewModel::setCategoryFilter,
                                selectedSupplier = selectedSupplier,
                                onSupplierSelected = viewModel::setSupplierFilter,
                                filterLowStock = filterLowStock,
                                onToggleLowStock = viewModel::toggleLowStockFilter,
                                filterExpiring = filterExpiringSoon,
                                onToggleExpiring = viewModel::toggleExpiringSoon,
                                filterExpired = filterExpired,
                                onToggleExpired = viewModel::toggleExpired,
                                sortBy = sortBy,
                                onSortSelected = viewModel::setSorting,
                                onProductClicked = { id ->
                                    currentRoute = ScreenRoute.ProductDetail(id)
                                },
                                onAddProductRequested = { currentRoute = ScreenRoute.ProductDetail(0) },
                                onSeedDemoRequested = {
                                    viewModel.seedTemplateData()
                                    scope.launch { snackbarHostState.showSnackbar("Pharmaceutical catalog successfully loaded!") }
                                },
                                onCSVDataImport = { text ->
                                    viewModel.importCsvData(text) { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                },
                                onWipeInventoryRequested = {
                                    viewModel.clearInventoryAll()
                                    scope.launch { snackbarHostState.showSnackbar("All items removed from the pharmacy catalog.") }
                                },
                                onBackClicked = { selectedTab = 0 },
                                alertDays = settings?.expiryAlertDays ?: 30
                            )

                            2 -> SettingsAndStorageScreen(
                                googleName = user?.name ?: "Unregistered Client",
                                googleEmail = user?.email ?: "guest.owner@gmail.com",
                                googlePhoto = user?.profilePhotoUrl ?: "",
                                autoBackup = settings?.autoBackup ?: true,
                                notificationsEnabled = settings?.notificationsEnabled ?: true,
                                expiryAlertDays = settings?.expiryAlertDays ?: 30,
                                darkMode = settings?.darkMode ?: true,
                                storageStats = storageStats,
                                backupHistoryList = cloudBackups,
                                onSavePreferences = { auto, notify, days, dark ->
                                    viewModel.saveUserPreferences(auto, notify, days, dark)
                                },
                                onSyncNowRequested = {
                                    viewModel.triggerManualSync()
                                    scope.launch { snackbarHostState.showSnackbar("Pushed automatic SHA-256 backup onto Google Drive container.") }
                                },
                                onRestoreBackupRequested = { filename ->
                                    viewModel.restoreSelectedBackup(filename) { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                },
                                onCSVDataImport = { text ->
                                    viewModel.importCsvData(text) { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                },
                                onWipeInventoryRequested = {
                                    viewModel.clearInventoryAll()
                                    scope.launch { snackbarHostState.showSnackbar("All items removed from the pharmacy catalog.") }
                                },
                                onLogoutRequested = {
                                    viewModel.handleGoogleSignOut()
                                    selectedTab = 0
                                    onboardingStep = 1
                                    currentRoute = ScreenRoute.Onboarding
                                },
                                onNavigateToAudit = {
                                    currentRoute = ScreenRoute.AuditLogs
                                }
                            )

                            3 -> AIBotScreen(
                                items = rawItems,
                                transactions = transactions,
                                logs = logs,
                                storeName = store?.name ?: "Inventory Store",
                                alertDays = settings?.expiryAlertDays ?: 30,
                                onTriggerSearch = { term ->
                                    viewModel.setSearchQuery(term)
                                    selectedTab = 1
                                },
                                onTriggerFilterLowStock = {
                                    if (!filterLowStock) {
                                        viewModel.toggleLowStockFilter()
                                    }
                                    selectedTab = 1
                                },
                                onTriggerFilterExpired = {
                                    if (!filterExpired) {
                                        viewModel.toggleExpired()
                                    }
                                    selectedTab = 1
                                },
                                onTriggerResetFilters = {
                                    viewModel.setSearchQuery("")
                                    viewModel.setCategoryFilter(null)
                                    viewModel.setSupplierFilter(null)
                                    if (filterLowStock) {
                                        viewModel.toggleLowStockFilter()
                                    }
                                    if (filterExpired) {
                                        viewModel.toggleExpired()
                                    }
                                    selectedTab = 1
                                },
                                onTriggerSync = {
                                    viewModel.triggerManualSync()
                                },
                                onTriggerSeed = {
                                    viewModel.seedTemplateData()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Sample data successfully loaded!")
                                    }
                                    selectedTab = 1
                                },
                                onNavigateBack = {
                                    selectedTab = 0
                                }
                            )
                        }
                    }
                }
              }
            }

            // A. Product detailed viewer & stock adjustments controller screen
            is ScreenRoute.ProductDetail -> {
                val activeItem = remember(route.id, rawItems) {
                    rawItems.find { it.id == route.id }
                }
                val productTransactions = remember(route.id, transactions) {
                    transactions.filter { it.productId == route.id }
                }

                ProductDetailScreen(
                    productId = route.id,
                    existingItem = activeItem,
                    transactionsList = productTransactions,
                    activeItemsList = rawItems,
                    onSaveProduct = { name, cat, qty, u, pur, sel, min, b, bch, h, mfg, exp, sup, n, margin ->
                        if (route.id == 0) {
                            viewModel.addProduct(name, cat, qty, u, pur, sel, min, b, bch, h, mfg, exp, sup, n, margin)
                            scope.launch { snackbarHostState.showSnackbar("Product '$name' successfully added!") }
                        } else {
                            viewModel.updateProductDetails(route.id, name, cat, qty, u, pur, sel, min, b, bch, h, mfg, exp, sup, n, margin)
                            scope.launch { snackbarHostState.showSnackbar("Product edited successfully!") }
                        }
                    },
                    onAdjustStock = { diff, remarks ->
                        viewModel.adjustStockLevelBy(route.id, diff, remarks)
                        scope.launch { snackbarHostState.showSnackbar("Stock adjusted successfully by $diff units!") }
                    },
                    onRecycleProduct = {
                        viewModel.recycleProduct(route.id)
                        scope.launch { snackbarHostState.showSnackbar("Product moved to Recycle Bin.") }
                        currentRoute = ScreenRoute.MainHost
                    },
                    onNavigateBack = {
                        currentRoute = ScreenRoute.MainHost
                    },
                    onTriggerCamera = {
                        triggerCameraCapture()
                    },
                    onTriggerGallery = {
                        galleryLauncher.launch("image/*")
                    }
                )
            }

            // B. Recycled Bin screen
            is ScreenRoute.RecycleBin -> {
                RecycleBinScreen(
                    deletedItems = deletedItems,
                    onRestoreItem = { id ->
                        viewModel.restoreRecycledProduct(id)
                        scope.launch { snackbarHostState.showSnackbar("Product restored to stocks catalog!") }
                    },
                    onPermanentDeleteItem = { id ->
                        viewModel.deleteProductPermanently(id)
                        scope.launch { snackbarHostState.showSnackbar("Product permanently purged.") }
                    },
                    onNavigateBack = {
                        currentRoute = ScreenRoute.MainHost
                    }
                )
            }

            // C. Support actions AuditLogsScreen
            is ScreenRoute.AuditLogs -> {
                AuditLogsScreen(
                    logs = logs,
                    onClearLogs = {
                        scope.launch { snackbarHostState.showSnackbar("Log database cannot be manually erased for security audits compliance.") }
                    },
                    onNavigateBack = {
                        currentRoute = ScreenRoute.MainHost
                    }
                )
            }
        }
    }
}
