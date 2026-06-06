package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.AccentEmerald
import java.io.BufferedReader
import java.io.InputStreamReader
import android.util.Log
import com.example.data.XlsxParser

fun getFileNameAndExtension(context: android.content.Context, uri: android.net.Uri): Pair<String, String> {
    var name = ""
    var extension = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: ""
                val dotIndex = name.lastIndexOf('.')
                if (dotIndex != -1) {
                    extension = name.substring(dotIndex + 1).lowercase()
                }
            }
        }
    } catch (e: Exception) {
        Log.e("SettingsAndStorage", "Failed querying name for URI: ${e.message}")
    }
    
    if (name.isEmpty()) {
        val path = uri.path ?: ""
        val lastSlash = path.lastIndexOf('/')
        name = if (lastSlash != -1) path.substring(lastSlash + 1) else path
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex != -1) {
            extension = name.substring(dotIndex + 1).lowercase()
        }
    }
    return Pair(name, extension)
}

@Composable
fun SettingsAndStorageScreen(
    googleName: String,
    googleEmail: String,
    googlePhoto: String,
    autoBackup: Boolean,
    notificationsEnabled: Boolean,
    expiryAlertDays: Int,
    darkMode: Boolean,
    storageStats: Map<String, String>,
    backupHistoryList: List<Map<String, String>>,
    onSavePreferences: (auto: Boolean, notify: Boolean, days: Int, dark: Boolean) -> Unit,
    onSyncNowRequested: () -> Unit,
    onRestoreBackupRequested: (String) -> Unit,
    onCSVDataImport: (String) -> Unit,
    onLogoutRequested: () -> Unit,
    onWipeInventoryRequested: () -> Unit,
    onNavigateToAudit: () -> Unit
) {
    var expiryDaysStr by remember { mutableStateOf(expiryAlertDays.toString()) }
    var localAutoBackup by remember { mutableStateOf(autoBackup) }
    var localNotify by remember { mutableStateOf(notificationsEnabled) }
    var localDark by remember { mutableStateOf(darkMode) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

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
                        fileContent = XlsxParser.parseXlsx(inputStream)
                        inputStream.close()
                    } else {
                        throw Exception("Cannot open stream for Excel template file.")
                    }
                } else {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
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
                    statusIsError = false
                    statusMessage = if (isXlsx) {
                        "Inventory successfully imported from Excel Spreadsheet!"
                    } else {
                        "Inventory successfully imported from CSV File!"
                    }
                } else {
                    statusIsError = true
                    statusMessage = if (isXlsx) "Selected Excel file is empty or unparseable" else "Selected CSV file is empty"
                }
            } catch (e: Exception) {
                statusIsError = true
                statusMessage = "Error importing file: ${e.localizedMessage}"
            }
        }
    }

    LaunchedEffect(autoBackup, notificationsEnabled, expiryAlertDays, darkMode) {
        localAutoBackup = autoBackup
        localNotify = notificationsEnabled
        localDark = darkMode
        expiryDaysStr = expiryAlertDays.toString()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Google Account Details Profile Card (Simplified & Professional)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PrimaryTeal.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Account profile info",
                            tint = PrimaryTeal,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = googleName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = googleEmail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onLogoutRequested,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout icon",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 2. Local Storage Statistics Cards Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "DATABASE & TELEMETRY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PrimaryTeal,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Data size card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = storageStats["dbSize"] ?: "0 KB",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "SQLite Database",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Product total card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = storageStats["totalProducts"]?.substringBefore(" ") ?: "0",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Active Items",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Recycle bin card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.Red.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = storageStats["totalRecycle"]?.substringBefore(" ") ?: "0",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Red,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Deleted Queue",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onNavigateToAudit,
                        modifier = Modifier.fillMaxWidth().testTag("view_audit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "View Audit Logs Icon",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View System Audit Logs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var showWipeConfirmation by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { showWipeConfirmation = true },
                        modifier = Modifier.fillMaxWidth().testTag("wipe_db_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Remove All Items Icon",
                            tint = Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove All Items", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Red)
                    }

                    if (showWipeConfirmation) {
                        AlertDialog(
                            onDismissRequest = { showWipeConfirmation = false },
                            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
                            title = { Text("Wipe All Products?") },
                            text = { Text("This will permanently clear all items in your pharmacy catalog. This action is IRREVERSIBLE. Are you sure?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showWipeConfirmation = false
                                        onWipeInventoryRequested()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                ) {
                                    Text("Yes, Delete All", color = Color.White)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { showWipeConfirmation = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }

        // 3. Simple & Highly Professional Cloud Sync Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CLOUD ENCRYPTION SYNC",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = PrimaryTeal,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Google Drive Secure Container",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onSyncNowRequested,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Manual sync trigger",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (backupHistoryList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "No drive backups found yet. Sync now to secure your pharmaceutical records.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "AVAILABLE CLOUD RESTORE POINTS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            backupHistoryList.take(3).forEachIndexed { idx, bh ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRestoreBackupRequested(bh["fileName"] ?: "") },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = "Cloud backup icon",
                                                tint = AccentEmerald,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Revision #${backupHistoryList.size - idx} (${bh["size"]})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${bh["date"]}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryTeal)
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowRight,
                                                contentDescription = "Initiate restore",
                                                tint = PrimaryTeal,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Clean, Professional Spreadsheet CSV File Importer (Text Area removed)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SPREADSHEET DATA INTEGRATION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PrimaryTeal,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Bulk import product records into local active catalogs.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimal dashboard visual for file import
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { filePickerLauncher.launch("*/*") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AppRegistration,
                            contentDescription = "csv document template icon",
                            tint = PrimaryTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Select CSV or Excel Spreadsheet",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTeal
                            )
                            Text(
                                text = "Load products catalog from offline device folders.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Helpful compact CSV guidelines instead of cluttered text pasting
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "REQUIRED COLUMNS TEMPLATE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "productName, category, quantity, unit, purchasePrice, sellingPrice, minStock, [batchNumber, expiryDate, supplier]",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 12.sp
                            )
                        }
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (statusIsError) MaterialTheme.colorScheme.errorContainer 
                                                 else AccentEmerald.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (statusIsError) Icons.Default.Error else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (statusIsError) MaterialTheme.colorScheme.error else AccentEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = statusMessage!!,
                                    fontSize = 11.sp,
                                    color = if (statusIsError) MaterialTheme.colorScheme.onErrorContainer else AccentEmerald,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. System Preferences
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SYSTEM PREFERENCES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PrimaryTeal,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto Local Backups Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Local Recovery", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Generate backups before CSV catalog imports.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = localAutoBackup,
                            onCheckedChange = {
                                localAutoBackup = it
                                onSavePreferences(localAutoBackup, localNotify, expiryDaysStr.toIntOrNull() ?: 30, localDark)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Notifications Guard Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart Stock Reminders", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Notify when quantity reaches minimum limit thresholds.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = localNotify,
                            onCheckedChange = {
                                localNotify = it
                                onSavePreferences(localAutoBackup, localNotify, expiryDaysStr.toIntOrNull() ?: 30, localDark)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dark Theme Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Slate Dark Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Enable eye-safe dim dark values.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = localDark,
                            onCheckedChange = {
                                localDark = it
                                onSavePreferences(localAutoBackup, localNotify, expiryDaysStr.toIntOrNull() ?: 30, localDark)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = expiryDaysStr,
                        onValueChange = {
                            expiryDaysStr = it
                            onSavePreferences(localAutoBackup, localNotify, it.toIntOrNull() ?: 30, localDark)
                        },
                        label = { Text("Days Left to Trigger Expiry Notifications") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }
    }
}
