package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import java.io.InputStream
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryListScreen(
    items: List<InventoryItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    selectedSupplier: String?,
    onSupplierSelected: (String?) -> Unit,
    filterLowStock: Boolean,
    onToggleLowStock: () -> Unit,
    filterExpiring: Boolean,
    onToggleExpiring: () -> Unit,
    filterExpired: Boolean,
    onToggleExpired: () -> Unit,
    sortBy: String,
    onSortSelected: (String) -> Unit,
    onProductClicked: (Int) -> Unit,
    onAddProductRequested: () -> Unit,
    onSeedDemoRequested: () -> Unit,
    onBackClicked: () -> Unit,
    alertDays: Int = 30,
    onCSVDataImport: (String) -> Unit = {},
    onWipeInventoryRequested: () -> Unit = {}
) {
    var showFilterDialog by remember { mutableStateOf(false) }
    var showWipeConfirmationDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val fileDetails = getFileNameAndExtension(context, uri)
                val isXlsx = fileDetails.second == "xlsx" || fileDetails.second == "xls"
                
                val fileContent: String
                if (isXlsx) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        fileContent = com.example.data.XlsxParser.parseXlsx(inputStream)
                        inputStream.close()
                    } else {
                        throw Exception("Cannot open stream for Excel template file.")
                    }
                } else {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    val sb = java.lang.StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                    }
                    reader.close()
                    inputStream?.close()
                    fileContent = sb.toString()
                }

                if (fileContent.isNotBlank()) {
                    onCSVDataImport(fileContent)
                }
            } catch (e: Exception) {
                android.util.Log.e("InventoryListScreen", "Error importing: ${e.localizedMessage}")
            }
        }
    }

    // Dynamic categories from catalog
    val categories = remember(items) {
        items.map { it.category }.distinct().filter { it.isNotEmpty() }.sorted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Inventory Items",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Funnel Filter Trigger
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt, 
                            contentDescription = "Filters", 
                            tint = if (filterLowStock || filterExpired || filterExpiring || selectedCategory != null || selectedSupplier != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    // Vertical Options Action Trigger
                    var showDropdownMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showDropdownMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showDropdownMenu,
                            onDismissRequest = { showDropdownMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import CSV/Excel") },
                                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                onClick = {
                                    showDropdownMenu = false
                                    filePickerLauncher.launch("*/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Seed Demo Catalog") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showDropdownMenu = false
                                    onSeedDemoRequested()
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Wipe All Products", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showDropdownMenu = false
                                    showWipeConfirmationDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            // MATCHING THEMED PILL ACTION BUTTON CENTERED AT BOTTOM
            Button(
                onClick = onAddProductRequested,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp), // Capsule Pill Style
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .height(46.dp)
                    .testTag("add_product_fab"),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Add Product",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Product",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Elegant search input box matching Theme
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { 
                    Text(
                        text = "Search Items by Name or Code",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    ) 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search, 
                        contentDescription = "Search", 
                        tint = MaterialTheme.colorScheme.primary, // Premium theme colored magnifier
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // MINI STATS QUICK HEADER LEDGER
            val totalCount = items.size
            val lowStockCount = items.count { it.quantity <= it.minStock }
            val expiringCount = items.count { isItemExpiringSoon(it.expiryDate, alertDays) }
            val expiredCount = items.count { isItemExpired(it.expiryDate) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total Products
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Total Items", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "$totalCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Low Stock Items
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (lowStockCount > 0) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    border = if (lowStockCount > 0) BorderStroke(1.dp, Color(0xFFFCA5A5)) else null
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Low Stock", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (lowStockCount > 0) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "$lowStockCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (lowStockCount > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Alerts (Expired / Expiring)
                val alertsCount = expiredCount + expiringCount
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (alertsCount > 0) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    border = if (alertsCount > 0) BorderStroke(1.dp, Color(0xFFFDE68A)) else null
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Alerts / Expiry", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (alertsCount > 0) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "$alertsCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (alertsCount > 0) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // QUICK CHIPS ROW (Direct main-screen toggles)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Toggles:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // 1. Low stock toggle chip
                FilterChip(
                    selected = filterLowStock,
                    onClick = onToggleLowStock,
                    label = { Text("Low Stock", fontSize = 11.sp) },
                    leadingIcon = if (filterLowStock) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                    } else null
                )

                // 2. Expiring soon toggle chip
                FilterChip(
                    selected = filterExpiring,
                    onClick = onToggleExpiring,
                    label = { Text("Expiring soon", fontSize = 11.sp) },
                    leadingIcon = if (filterExpiring) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                    } else null
                )

                // 3. Expired toggle chip
                FilterChip(
                    selected = filterExpired,
                    onClick = onToggleExpired,
                    label = { Text("Expired only", fontSize = 11.sp) },
                    leadingIcon = if (filterExpired) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                    } else null
                )

                // Category status tag clear reference
                if (selectedCategory != null) {
                    SuggestionChip(
                        onClick = { onCategorySelected(null) },
                        label = { Text("$selectedCategory") },
                        icon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                    )
                }

                // "Clear Filter" indicator if any are checked
                if (filterLowStock || filterExpiring || filterExpired || selectedCategory != null) {
                    TextButton(
                        onClick = {
                            if (filterLowStock) onToggleLowStock()
                            if (filterExpiring) onToggleExpiring()
                            if (filterExpired) onToggleExpired()
                            onCategorySelected(null)
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Reset", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Products list rendering
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Matching Products Found",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onSeedDemoRequested,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Seed Demo items", color = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(items, key = { it.id }) { product ->
                        FlatProductListItem(
                            product = product,
                            onClick = { onProductClicked(product.id) },
                            alertDays = alertDays
                        )
                    }
                }
            }
        }
    }

    // CONTROL PANEL DIALOG FOR FILTERS IN APP THEME
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter & Organize", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Sort options
                    Column {
                        Text("Sort Results By", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("NAME_ASC" to "A-Z", "QTY_ASC" to "Low Stock", "PRICE_DESC" to "High Price").forEach { (key, label) ->
                                FilterChip(
                                    selected = sortBy == key,
                                    onClick = { onSortSelected(key) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Toggle filter buttons
                    Column {
                        Text("Quick State Filters", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterLowStock,
                                onClick = onToggleLowStock,
                                label = { Text("Low Stock", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = filterExpired,
                                onClick = onToggleExpired,
                                label = { Text("Expired Only", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = filterExpiring,
                                onClick = onToggleExpiring,
                                label = { Text("Expiring Soon", fontSize = 11.sp) }
                            )
                        }
                    }

                    // Category dropdown filter
                    if (categories.isNotEmpty()) {
                        Column {
                            Text("Category Filter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { onCategorySelected(null) },
                                    label = { Text("All", fontSize = 11.sp) }
                                )
                                categories.forEach { cat ->
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = { onCategorySelected(cat) },
                                        label = { Text(cat, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFilterDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Apply", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        )
    }

    if (showWipeConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmationDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Wipe All Products?") },
            text = { Text("This will permanently clear all items in your pharmacy catalog. This action is IRREVERSIBLE. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirmationDialog = false
                        onWipeInventoryRequested()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Delete All", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWipeConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FlatProductListItem(
    product: InventoryItem,
    onClick: () -> Unit,
    alertDays: Int = 30
) {
    val isLowStock = product.quantity <= product.minStock
    val isExpiredNow = isItemExpired(product.expiryDate)
    val isExpiringNear = isItemExpiringSoon(product.expiryDate, alertDays)

    // Dynamic color coding based on status health
    val stateColor = when {
        isExpiredNow -> MaterialTheme.colorScheme.error
        isExpiringNear -> Color(0xFFF59E0B) // Amber 500
        isLowStock -> Color(0xFFEF4444) // Orange/Red
        else -> MaterialTheme.colorScheme.primary
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("product_item_card_${product.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpiredNow || isLowStock || isExpiringNear) stateColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual health indicator left-bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(stateColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Category Tag and Expiry Status Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (product.category.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = product.category.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    when {
                        isExpiredNow -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "EXPIRED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        isExpiringNear -> {
                            Surface(
                                color = Color(0xFFFEF3C7), // Amber 100
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "EXPIRING SOON",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E), // Amber 800
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        isLowStock -> {
                            Surface(
                                color = Color(0xFFFEE2E2), // Red 100
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "LOW STOCK",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B), // Red 800
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Product Name Line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = product.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowOutward,
                        contentDescription = "Details indicator",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Expiry and Batch Code Row
                if (product.batchNumber.isNotEmpty() || product.expiryDate.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (product.batchNumber.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Batch: ${product.batchNumber}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (product.expiryDate.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = if (isExpiredNow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Exp: ${product.expiryDate}",
                                    fontSize = 10.sp,
                                    fontWeight = if (isExpiredNow || isExpiringNear) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isExpiredNow) MaterialTheme.colorScheme.error else if (isExpiringNear) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Price and Quantity Grid Columns (WCAG AAA compliant text styling)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SALE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹ ${"%.2f".format(product.sellingPrice)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PURCHASE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹ ${"%.2f".format(product.purchasePrice)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "STOCK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val unitLabel = if (product.unit.isNotEmpty()) " ${product.unit.uppercase()}" else " PCS"
                        Text(
                            text = "${"%.2f".format(product.quantity)}$unitLabel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

// Local Helper utilities for state checking matching viewmodel configurations
private fun isItemExpired(expiryDateStr: String): Boolean {
    if (expiryDateStr.isEmpty()) return false
    return try {
        val sdf = java.text.SimpleDateFormat("MM-yyyy", java.util.Locale.ROOT)
        val expDate = sdf.parse(expiryDateStr) ?: return false
        expDate.before(java.util.Date())
    } catch (e: Exception) {
        try {
            val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
            val expDate = sdf2.parse(expiryDateStr) ?: return false
            expDate.before(java.util.Date())
        } catch (e2: Exception) {
            false
        }
    }
}

private fun isItemExpiringSoon(expiryDateStr: String, alertDays: Int): Boolean {
    if (expiryDateStr.isEmpty()) return false
    return try {
        val sdf = java.text.SimpleDateFormat("MM-yyyy", java.util.Locale.ROOT)
        val expDate = sdf.parse(expiryDateStr) ?: return false
        val diffMs = expDate.time - System.currentTimeMillis()
        val diffDays = diffMs / (1000L * 60 * 60 * 24)
        diffDays in 0..alertDays
    } catch (e: Exception) {
        try {
            val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
            val expDate = sdf2.parse(expiryDateStr) ?: return false
            val diffMs = expDate.time - System.currentTimeMillis()
            val diffDays = diffMs / (1000L * 60 * 60 * 24)
            diffDays in 0..alertDays
        } catch (e2: Exception) {
            false
        }
    }
}
