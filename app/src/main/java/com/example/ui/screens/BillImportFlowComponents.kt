package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ExtractedItem
import com.example.ui.theme.PrimaryTeal
import kotlinx.coroutines.delay

@Composable
fun BillCropperScreen(
    bitmap: Bitmap,
    onCropped: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var leftCut by remember { mutableStateOf(0.0f) }
    var rightCut by remember { mutableStateOf(1.0f) }
    var topCut by remember { mutableStateOf(0.1f) }
    var bottomCut by remember { mutableStateOf(0.9f) }

    val originalWidth = bitmap.width
    val originalHeight = bitmap.height

    // Calculate real-time cropped preview
    val cropPreviewBitmap = remember(bitmap, leftCut, rightCut, topCut, bottomCut) {
        try {
            val x = (leftCut * originalWidth).toInt().coerceIn(0, originalWidth - 1)
            val y = (topCut * originalHeight).toInt().coerceIn(0, originalHeight - 1)
            val w = ((rightCut - leftCut) * originalWidth).toInt().coerceAtLeast(10).coerceAtMost(originalWidth - x)
            val h = ((bottomCut - topCut) * originalHeight).toInt().coerceAtLeast(10).coerceAtMost(originalHeight - y)
            Bitmap.createBitmap(bitmap, x, y, w, h)
        } catch (e: Exception) {
            bitmap
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "CROP THE ITEMS TABLE",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTeal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Drag the boundaries to isolate index/items chart. Exclude header/footer to improve AI extraction speed.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Main Image view with Cropping Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, PrimaryTeal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Invoice Crop View",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Canvas Crop Selector overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val left = leftCut * w
                val right = rightCut * w
                val top = topCut * h
                val bottom = bottomCut * h

                // Draw dimmed outer shadows
                drawRect(color = Color.Black.copy(alpha = 0.6f), size = androidx.compose.ui.geometry.Size(w, top))
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, bottom),
                    size = androidx.compose.ui.geometry.Size(w, h - bottom)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(left, bottom - top)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(right, top),
                    size = androidx.compose.ui.geometry.Size(w - right, bottom - top)
                )

                // Draw glowing dotted box
                drawRect(
                    color = Color(0xFF009688), // PrimaryTeal
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sliders controlling margins
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "ADJUST CROPPING BOUNDARIES",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = PrimaryTeal,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Left limit", fontSize = 11.sp, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = leftCut,
                        onValueChange = { leftCut = it.coerceAtMost(rightCut - 0.1f) },
                        colors = SliderDefaults.colors(thumbColor = PrimaryTeal, activeTrackColor = PrimaryTeal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Right limit", fontSize = 11.sp, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = rightCut,
                        onValueChange = { rightCut = it.coerceAtLeast(leftCut + 0.1f) },
                        colors = SliderDefaults.colors(thumbColor = PrimaryTeal, activeTrackColor = PrimaryTeal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Top limit", fontSize = 11.sp, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = topCut,
                        onValueChange = { topCut = it.coerceAtMost(bottomCut - 0.1f) },
                        colors = SliderDefaults.colors(thumbColor = PrimaryTeal, activeTrackColor = PrimaryTeal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bottom limit", fontSize = 11.sp, modifier = Modifier.width(60.dp), fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = bottomCut,
                        onValueChange = { bottomCut = it.coerceAtLeast(topCut + 0.1f) },
                        colors = SliderDefaults.colors(thumbColor = PrimaryTeal, activeTrackColor = PrimaryTeal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cropped live preview inset & Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini cropped preview frame
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .background(Color.DarkGray)
            ) {
                Image(
                    bitmap = cropPreviewBitmap.asImageBitmap(),
                    contentDescription = "Drawn crop layout preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(vertical = 1.dp)
                ) {
                    Text("Selected", color = Color.White, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            // Buttons
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onCropped(cropPreviewBitmap) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Parse crop items", Modifier.padding(end = 4.dp))
                    Text("Extract Items", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Abort selection", Modifier.size(16.dp).padding(end = 4.dp))
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun BillExtractingScreen() {
    var loadingPromptIndex by remember { mutableStateOf(0) }
    val prompts = listOf(
        "Scanning invoice/receipt boundaries...",
        "Running intelligent OCR on table columns...",
        "Identifying 'product name', 'goods' and semantic columns...",
        "Extracting unit purchase rates and selling prices...",
        "Parsing expiry dates in MM-YYYY format...",
        "Excluding redundant summary headers/totals rows...",
        "Assembling your items review stack..."
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(2500)
            loadingPromptIndex = (loadingPromptIndex + 1) % prompts.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(100.dp),
                color = PrimaryTeal,
                strokeWidth = 5.dp
            )
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = "Scanner",
                tint = PrimaryTeal,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "INTELLIGENT OCR PARSING",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTeal,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = prompts[loadingPromptIndex],
            transitionSpec = {
                fadeIn() + slideInVertically { it } togetherWith fadeOut() + slideOutVertically { -it }
            },
            label = "Prompt transitions"
        ) { text ->
            Text(
                text = text,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            "Our multimodal Gemini AI is inspecting the cropped invoice to read and map all columns automatically. Please wait...",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillReviewScreen(
    extractedItems: List<ExtractedItem>,
    currentIndex: Int,
    onSaveItem: (ExtractedItem) -> Unit,
    onSkipItem: () -> Unit,
    onAbort: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val item = extractedItems.getOrNull(currentIndex) ?: return

    var name by remember(currentIndex) { mutableStateOf(item.productName) }
    var category by remember(currentIndex) { mutableStateOf(item.category) }
    var quantityStr by remember(currentIndex) { mutableStateOf(item.quantity.toString()) }
    var unit by remember(currentIndex) { mutableStateOf(item.unit) }
    var purchasePriceStr by remember(currentIndex) { mutableStateOf(item.purchasePrice.toString()) }
    var sellingPriceStr by remember(currentIndex) { mutableStateOf(item.sellingPrice.toString()) }
    var minStockStr by remember(currentIndex) { mutableStateOf(item.minStock.toString()) }
    var barcode by remember(currentIndex) { mutableStateOf(item.barcode) }
    var batchNumber by remember(currentIndex) { mutableStateOf(item.batchNumber) }
    var hsnCode by remember(currentIndex) { mutableStateOf(item.hsnCode) }
    var expiryDate by remember(currentIndex) { mutableStateOf(item.expiryDate) }
    var supplier by remember(currentIndex) { mutableStateOf(item.supplier) }
    var notes by remember(currentIndex) { mutableStateOf(item.notes) }

    var formError by remember { mutableStateOf<String?>(null) }

    val progress = (currentIndex + 1).toFloat() / extractedItems.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bill Review Wizard", fontWeight = FontWeight.Bold)
                        Text(
                            "Verified OCR matches and adjustments",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onAbort) {
                        Text("Cancel Import", color = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Progressive indicator card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryTeal.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "REVIEWING ITEM ${currentIndex + 1} OF ${extractedItems.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTeal,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Review and verify fields before registering product.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PrimaryTeal,
                            trackColor = PrimaryTeal.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            if (formError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Error, contentDescription = "Error icon", tint = MaterialTheme.colorScheme.error)
                        Text(
                            formError!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Forms Fields Sections
            Text(
                "REQUIRED CATALOG FIELD INFO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTeal,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; formError = null },
                label = { Text("Product Name *") },
                placeholder = { Text("e.g. Amoxicillin") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Quantity *") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (Strips/Rolls/Pcs)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = purchasePriceStr,
                    onValueChange = { purchasePriceStr = it },
                    label = { Text("Purchase Unit Price *") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = sellingPriceStr,
                    onValueChange = { sellingPriceStr = it },
                    label = { Text("Retail MRP Price *") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "REGULATORY & BATCH SPECIFICATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTeal,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = batchNumber,
                    onValueChange = { batchNumber = it },
                    label = { Text("Batch Number") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = { expiryDate = it },
                    label = { Text("Expiry Date (MM-YYYY)") },
                    placeholder = { Text("06-2027") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = hsnCode,
                    onValueChange = { hsnCode = it },
                    label = { Text("HSN / Tariff") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = minStockStr,
                    onValueChange = { minStockStr = it },
                    label = { Text("Min Alert Boundary") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = supplier,
                onValueChange = { supplier = it },
                label = { Text("Primary Supplier Name") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Manager Notes / Comments") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action row buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSkipItem,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Discard from staging area")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Skip Item", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val qty = quantityStr.toDoubleOrNull()
                        val pur = purchasePriceStr.toDoubleOrNull()
                        val sel = sellingPriceStr.toDoubleOrNull()
                        val minStk = minStockStr.toDoubleOrNull() ?: 10.0

                        if (name.isBlank() || qty == null || pur == null || sel == null) {
                            formError = "Please prefill Name, Quantity, Purchase Price and Selling Price with valid numeric numbers."
                        } else {
                            onSaveItem(
                                ExtractedItem(
                                    productName = name.trim(),
                                    category = category.trim().takeIf { it.isNotEmpty() } ?: "General",
                                    quantity = qty,
                                    unit = unit.trim().takeIf { it.isNotEmpty() } ?: "Units",
                                    purchasePrice = pur,
                                    sellingPrice = sel,
                                    minStock = minStk,
                                    barcode = barcode.trim(),
                                    batchNumber = batchNumber.trim(),
                                    hsnCode = hsnCode.trim(),
                                    expiryDate = expiryDate.trim(),
                                    supplier = supplier.trim(),
                                    notes = notes.trim()
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Insert into databases")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save & Next", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
