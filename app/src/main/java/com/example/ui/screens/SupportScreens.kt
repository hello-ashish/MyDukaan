package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuditLog
import com.example.data.InventoryItem
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.AccentEmerald
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    deletedItems: List<InventoryItem>,
    onRestoreItem: (Int) -> Unit,
    onPermanentDeleteItem: (Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Items in the Recycle Bin will be automatically cleared after 30 days. You can restore or permanently prune them below.",
                        fontSize = 11.sp,
                        color = Color(0xFF991B1B),
                        lineHeight = 15.sp
                    )
                }
            }

            if (deletedItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Bin is Empty", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deletedItems, key = { it.id }) { item ->
                        DeletedItemRow(
                            item = item,
                            onRestore = { onRestoreItem(item.id) },
                            onDelete = { onPermanentDeleteItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeletedItemRow(
    item: InventoryItem,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Category: ${item.category} • Original Quantity: ${item.quantity} ${item.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (item.deletedAt != null) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.deletedAt))
                    Text("Deleted: $dateStr", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRestore) {
                    Icon(imageVector = Icons.Default.Restore, contentDescription = "Restore item", tint = PrimaryTeal)
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = "Permanent erase", tint = Color.Red)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogsScreen(
    logs: List<AuditLog>,
    onClearLogs: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit History Trail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClearLogs) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear all", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No audit movements recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { log ->
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.createdAt))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = log.action,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PrimaryTeal,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(PrimaryTeal.copy(alpha = 0.08f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Text(date, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    log.description,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
