package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryItem
import com.example.data.InventoryTransaction
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.AccentEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: Int, // 0 means new addition
    existingItem: InventoryItem?,
    transactionsList: List<InventoryTransaction> = emptyList(),
    activeItemsList: List<InventoryItem> = emptyList(),
    onSaveProduct: (
        name: String, cat: String, qty: Double, unit: String,
        purchasePrice: Double, sellingPrice: Double, minStock: Double,
        barcode: String, batch: String, hsn: String,
        mfg: String, exp: String, supplier: String, notes: String,
        marginPercent: Double
    ) -> Unit,
    onAdjustStock: (diff: Double, remarks: String) -> Unit,
    onRecycleProduct: () -> Unit,
    onNavigateBack: () -> Unit,
    onTriggerCamera: (() -> Unit)? = null,
    onTriggerGallery: (() -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(productId == 0) }

    // Form inputs state variables
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var purchasePriceStr by remember { mutableStateOf("") }
    var sellingPriceStr by remember { mutableStateOf("") }
    var minStockStr by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var batchNumber by remember { mutableStateOf("") }
    var hsnCode by remember { mutableStateOf("") }
    var manufacturingDate by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var marginPercentStr by remember { mutableStateOf("") }

    // Adjust Stock state variables
    var showAdjustDialog by remember { mutableStateOf(false) }
    var adjustAmountStr by remember { mutableStateOf("") }
    var adjustIsAdd by remember { mutableStateOf(true) }
    var adjustRemarks by remember { mutableStateOf("") }

    var formError by remember { mutableStateOf<String?>(null) }

    // Seed variables on entry/update
    LaunchedEffect(existingItem) {
        if (existingItem != null) {
            name = existingItem.productName
            category = existingItem.category
            quantityStr = existingItem.quantity.toString()
            unit = existingItem.unit
            
            // Recompute base price before margin for visual input field
            val basePrice = if (existingItem.marginPercent > 0.0) {
                existingItem.purchasePrice / (1.0 + existingItem.marginPercent / 100.0)
            } else {
                existingItem.purchasePrice
            }
            purchasePriceStr = if (basePrice % 1.0 == 0.0) {
                basePrice.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.2f", basePrice)
            }

            marginPercentStr = if (existingItem.marginPercent > 0.0) {
                if (existingItem.marginPercent % 1.0 == 0.0) {
                    existingItem.marginPercent.toInt().toString()
                } else {
                    existingItem.marginPercent.toString()
                }
            } else {
                ""
            }

            sellingPriceStr = if (existingItem.sellingPrice % 1.0 == 0.0) {
                existingItem.sellingPrice.toInt().toString()
            } else {
                existingItem.sellingPrice.toString()
            }
            
            minStockStr = existingItem.minStock.toString()
            barcode = existingItem.barcode
            batchNumber = existingItem.batchNumber
            hsnCode = existingItem.hsnCode
            manufacturingDate = existingItem.manufacturingDate
            expiryDate = existingItem.expiryDate
            supplier = existingItem.supplier
            notes = existingItem.notes
        } else {
            category = "General"
            unit = "Units"
            barcode = ""
            hsnCode = ""
            manufacturingDate = ""
            marginPercentStr = ""
        }
    }

    Scaffold(
        topBar = {
            if (isEditing) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (productId == 0) "Add Product" else "Edit Product",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (productId == 0) {
                                onNavigateBack()
                            } else {
                                isEditing = false
                            }
                        }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (productId != 0) {
                            IconButton(onClick = onRecycleProduct) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete to Bin", tint = Color.Red)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            } else {
                // PREMIUM DETAILED TOP BAR - CUSTOM STYLED MATCHING SCREENSHOT 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .background(Color.White)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1F2937))
                    }
                    Text(
                        text = "Item Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                    IconButton(onClick = { isEditing = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit details",
                            tint = Color(0xFF2563EB), // Premium blue pencil matching screenshot!
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (!isEditing && existingItem != null) {
                // ADJUST STOCK BOTTOM PILL BUTTON MATCHING SCREENSHOT 2
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(Color.White),
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { showAdjustDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)), // Premium Royal Blue
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(48.dp)
                                .testTag("adjust_stock_button"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "Adjust Stock",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isEditing) MaterialTheme.colorScheme.background else Color.White)
        ) {
            if (isEditing) {
                // EXCITING EDIT FORM GRAPH
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (productId == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onTriggerCamera?.invoke() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("add_product_camera_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Scan Bill with Camera",
                                    modifier = Modifier.size(18.dp),
                                    tint = PrimaryTeal
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Camera", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { onTriggerGallery?.invoke() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("add_product_gallery_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Import from Gallery",
                                    modifier = Modifier.size(18.dp),
                                    tint = PrimaryTeal
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose Image", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Text(
                        "CORE SPECIFICATIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; formError = null },
                        label = { Text("Product Name *") },
                        placeholder = { Text("e.g. Paracetamol 650mg") },
                        modifier = Modifier.fillMaxWidth().testTag("product_name_input").padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            label = { Text("Stock Quantity *") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = minStockStr,
                            onValueChange = { minStockStr = it },
                            label = { Text("Minimum Stock Warning") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "VALUATING & PRICING (₹)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it },
                            label = { Text("Purchase Price *") },
                            placeholder = { Text("₹ 10.0") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = marginPercentStr,
                            onValueChange = { marginPercentStr = it },
                            label = { Text("Margin (%)") },
                            placeholder = { Text("e.g. 10") },
                            modifier = Modifier.weight(1f).testTag("product_margin_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = sellingPriceStr,
                        onValueChange = { sellingPriceStr = it },
                        label = { Text("Selling Price *") },
                        placeholder = { Text("₹ 15.0") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "MANUFACTURING & REGULATORY CODES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = batchNumber,
                            onValueChange = { batchNumber = it },
                            label = { Text("Batch Number") },
                            placeholder = { Text("e.g. B-PCM12") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = supplier,
                            onValueChange = { supplier = it },
                            label = { Text("Supplier Info") },
                            placeholder = { Text("Cipla Labs") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "QUALITY CONTROL DATES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = { expiryDate = it },
                            label = { Text("Expiry Date (MM-YYYY)") },
                            placeholder = { Text("06-2027") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Store Notes & Shelf Location") },
                        placeholder = { Text("e.g. Shelf A-3, Store in cold storage.") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )

                    if (formError != null) {
                        Text(
                            text = formError!!,
                            color = Color.Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val qty = quantityStr.toDoubleOrNull()
                            val basePur = purchasePriceStr.toDoubleOrNull()
                            val pct = marginPercentStr.toDoubleOrNull() ?: 0.0
                            val sel = sellingPriceStr.toDoubleOrNull()
                            val minStk = minStockStr.toDoubleOrNull() ?: 0.0

                            val hasDuplicate = activeItemsList.any {
                                it.id != productId && it.productName.trim().lowercase() == name.trim().lowercase()
                            }

                            if (name.isBlank() || qty == null || basePur == null || sel == null) {
                                formError = "Please complete all marked (*) mandatory fields with valid numbers."
                            } else if (hasDuplicate) {
                                formError = "Duplicate Item! A product with this name already exists."
                            } else {
                                val finalPur = basePur * (1.0 + pct / 100.0)
                                onSaveProduct(
                                    name.trim(), 
                                    category.trim().takeIf { it.isNotEmpty() } ?: "General", 
                                    qty, 
                                    unit.trim().takeIf { it.isNotEmpty() } ?: "Units",
                                    finalPur, sel, minStk,
                                    barcode.trim(), batchNumber.trim(), hsnCode.trim(),
                                    manufacturingDate.trim(), expiryDate.trim(), supplier.trim(), notes.trim(),
                                    pct
                                )
                                if (productId == 0) {
                                    onNavigateBack()
                                } else {
                                    isEditing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("save_product_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Entry Record", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    if (productId != 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel Edit Operation", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (existingItem != null) {
                // DISPLAY VIEWER MODE METADATA MATCHING SCREENSHOT 2 PERFECTLY
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {


                    // Spacer/Padding inside
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Product Name Header
                        Text(
                            text = existingItem.productName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF1F2937),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 3 Column Price and Stock Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text("Sale Price", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                Text(
                                    "₹ ${"%.2f".format(existingItem.sellingPrice)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF374151)
                                )
                            }
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text("Purchase Price", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                Text(
                                    "₹ ${"%.2f".format(existingItem.purchasePrice)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF374151)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("In Stock", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                Text(
                                    "${"%.1f".format(existingItem.quantity)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF10B981) // Matching emerald teal green label
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stock Value Singular column metrics
                        Column {
                            Text("Stock Value", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                            val stockVal = existingItem.quantity * existingItem.purchasePrice
                            Text(
                                "₹ ${"%,.2f".format(stockVal)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF374151)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)

                    // Stock Transactions Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Stock Transactions",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF374151)
                        )
                    }

                    // Stock Transactions List Content
                    if (transactionsList.isEmpty()) {
                        // STUNNING DESIGN MATCHING SCREENSHOT 2 EMPTY STATE ILLUSTRATION
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Empty State graphics with lines, shapes and dots
                            Box(
                                modifier = Modifier.size(100.dp, 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.width(90.dp)
                                ) {
                                    // Row 1
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(Color(0xFF86EFAC), shape = RoundedCornerShape(3.dp))
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Box(modifier = Modifier.size(50.dp, 5.dp).background(Color(0xFFD1FAE5), shape = RoundedCornerShape(2.dp)))
                                            Box(modifier = Modifier.size(30.dp, 5.dp).background(Color(0xFFD1FAE5), shape = RoundedCornerShape(2.dp)))
                                        }
                                    }
                                    // Row 2
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(Color(0xFFFDE047), shape = RoundedCornerShape(7.dp))
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Box(modifier = Modifier.size(50.dp, 5.dp).background(Color(0xFFFEF9C3), shape = RoundedCornerShape(2.dp)))
                                            Box(modifier = Modifier.size(30.dp, 5.dp).background(Color(0xFFFEF9C3), shape = RoundedCornerShape(2.dp)))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "You have not made any stock transactions yet.",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        // BEAUTIFUL COMPREHENSIVE LIST OF TRANSACTIONS GIVING REAL COMPILING FEEDBACK
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            transactionsList.forEach { txn ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = txn.remarks.takeIf { it.isNotBlank() } ?: "Manual Audit Transaction",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1F2937)
                                        )
                                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
                                        Text(
                                            text = sdf.format(java.util.Date(txn.createdAt)),
                                            fontSize = 11.sp,
                                            color = Color(0xFF9CA3AF)
                                        )
                                    }
                                    val isAdd = txn.quantity > 0
                                    Text(
                                        text = if (isAdd) "+${txn.quantity}" else "${txn.quantity}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isAdd) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                }
                                Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                            }
                        }
                    }
                    // Bottom Spacer to prevent overlap with bottomBar
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    // STOCK ADJUST QUICK COMPONENT POPUP
    if (showAdjustDialog && existingItem != null) {
        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("Stock Level Adjustment") },
            text = {
                Column {
                    Text("Modify physical counts for: ${existingItem.productName}. Current: ${existingItem.quantity} ${existingItem.unit}")
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { adjustIsAdd = true }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (adjustIsAdd) PrimaryTeal.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (adjustIsAdd) PrimaryTeal else Color.LightGray, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            color = Color.Transparent
                        ) {
                            Text("+ ADD STOCK", fontWeight = FontWeight.Bold, color = if (adjustIsAdd) PrimaryTeal else Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { adjustIsAdd = false }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!adjustIsAdd) Color.Red.copy(alpha = 0.1f) else Color.Transparent)
                                .border(1.dp, if (!adjustIsAdd) Color.Red else Color.LightGray, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            color = Color.Transparent
                        ) {
                            Text("- DEDUCT", fontWeight = FontWeight.Bold, color = if (!adjustIsAdd) Color.Red else Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = adjustAmountStr,
                        onValueChange = { adjustAmountStr = it },
                        label = { Text("Quantity Offset") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = adjustRemarks,
                        onValueChange = { adjustRemarks = it },
                        label = { Text("Reason / Remarks *") },
                        placeholder = { Text("e.g. Sales, Damaged, Returned") },
                        modifier = Modifier.fillMaxWidth().testTag("adjust_remarks_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = adjustAmountStr.toDoubleOrNull()
                        if (amt != null && amt > 0.0 && adjustRemarks.isNotBlank()) {
                            val finalOffset = if (adjustIsAdd) amt else -amt
                            onAdjustStock(finalOffset, adjustRemarks.trim())
                            showAdjustDialog = false
                            adjustAmountStr = ""
                            adjustRemarks = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Apply Audit", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailValRow(label: String, valText: String, valColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valColor)
    }
}

// Check Expired Utility
private fun isDetailExpired(dateStr: String): Boolean {
    if (dateStr.isEmpty()) return false
    return try {
        val sdf = java.text.SimpleDateFormat("MM-yyyy", java.util.Locale.ROOT)
        val date = sdf.parse(dateStr) ?: return false
        date.before(java.util.Date())
    } catch (e: Exception) {
        false
    }
}
