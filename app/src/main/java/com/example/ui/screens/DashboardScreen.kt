package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuditLog
import com.example.data.InventoryItem
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.AccentEmerald
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    items: List<InventoryItem>,
    logs: List<AuditLog>,
    syncStatus: String,
    pendingSyncCount: Int,
    lastSyncTime: String?,
    storeName: String,
    onNavigateToCategory: (String?) -> Unit,
    onQuickAction: (String) -> Unit // "ADD", "IMPORT", "SYNC", "RECYCLE", "AUDIT", "EXPORT"
) {
    // Analytics calculations
    val totalProducts = items.size
    val totalQty = items.sumOf { it.quantity }
    val lowStockCount = items.count { it.quantity <= it.minStock }
    
    val expiredCount = items.count { isExpired(it.expiryDate) }
    val expiringCount = items.count { isExpiringSoon(it.expiryDate, 30) }

    val totalPurchaseValue = items.sumOf { it.quantity * it.purchasePrice }
    val totalSellingValue = items.sumOf { it.quantity * it.sellingPrice }
    val expectedProfit = totalSellingValue - totalPurchaseValue

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Welcome and Store Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = storeName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Pharmaceutical & Retail Analytics",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Cloud backup indicator pill
                SyncStatusPill(
                    status = syncStatus,
                    pendingCount = pendingSyncCount,
                    lastSync = lastSyncTime,
                    onClick = { onQuickAction("SYNC") }
                )
            }
        }

        // Metrics Grid Row 1
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).clickable { onNavigateToCategory(null) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Active Products", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalProducts items", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total Quantity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${"%,.1f".format(totalQty)} units", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }

        // Metrics Grid Row 2 (Alerts)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).clickable { onQuickAction("LOW_STOCK_FILTER") },
                    colors = CardDefaults.cardColors(containerColor = if (lowStockCount > 0) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (lowStockCount > 0) Color.Red else PrimaryTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Low Stock items", fontSize = 12.sp, color = if (lowStockCount > 0) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$lowStockCount warnings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (lowStockCount > 0) Color.Red else MaterialTheme.colorScheme.onBackground)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).clickable { onQuickAction("EXPIRY_FILTER") },
                    colors = CardDefaults.cardColors(containerColor = if (expiredCount > 0) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = if (expiredCount > 0) Color(0xFFD97706) else PrimaryTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Expired / Impending", fontSize = 12.sp, color = if (expiredCount > 0) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$expiredCount exp / $expiringCount soon", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (expiredCount > 0) Color(0xFFD97706) else MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }

        // Store Valuation Chart/Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "STOCKS ESTIMATION SUMMARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Net Inventory Valuation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "₹${"%,.2f".format(totalPurchaseValue)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Expected Profit", fontSize = 12.sp, color = AccentEmerald)
                            Text(
                                "₹${"%,.2f".format(expectedProfit)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Wholesale Assets Size", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%,.1f".format(totalPurchaseValue)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Retail Retail Sales Target", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${"%,.1f".format(totalSellingValue)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Average Profit Margin", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val margin = if (totalPurchaseValue > 0) (expectedProfit / totalPurchaseValue) * 100 else 0.0
                            Text("${"%.1f".format(margin)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentEmerald)
                        }
                    }
                }
            }
        }

        // Quick Admin Shortcut keys
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "QUICK ACTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    QuickActionIcon(Icons.Default.FileUpload, "Import csv", Color(0xFF4F46E5)) { onQuickAction("IMPORT") }
                    QuickActionIcon(Icons.Default.FileDownload, "Export csv", Color(0xFFD97706)) { onQuickAction("EXPORT") }
                    QuickActionIcon(Icons.Default.CloudSync, "Sync Drive", Color(0xFF10B981)) { onQuickAction("SYNC") }
                    QuickActionIcon(Icons.Default.DeleteSweep, "Recycle Bin", Color(0xFFEF4444)) { onQuickAction("RECYCLE") }
                }
            }
        }

    }
}

@Composable
fun SyncStatusPill(
    status: String,
    pendingCount: Int,
    lastSync: String?,
    onClick: () -> Unit
) {
    val text = when (status) {
        "SYNCED" -> "Synced"
        "PENDING" -> "Pending (${pendingCount})"
        else -> "Sync Failed"
    }

    val color = when (status) {
        "SYNCED" -> AccentEmerald
        "PENDING" -> Color(0xFFD97706)
        else -> Color.Red
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun QuickActionIcon(
    icon: ImageVector,
    label: String,
    bg: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = bg, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AuditRow(log: AuditLog) {
    val date = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.createdAt))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val color = when (log.action) {
                "Product Added" -> AccentEmerald
                "Product Recycled" -> Color.Red
                "CSV Export", "CSV Import" -> Color(0xFF4F46E5)
                "Local Backup", "Cloud Backup" -> PrimaryTeal
                else -> Color.Gray
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (log.action) {
                    "Product Added" -> Icons.Default.Add
                    "Product Recycled" -> Icons.Default.DeleteOutline
                    "CSV Export" -> Icons.Default.FileDownload
                    "CSV Import" -> Icons.Default.FileUpload
                    "Local Backup", "Cloud Backup" -> Icons.Default.CloudQueue
                    else -> Icons.Default.Check
                }
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(log.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text("${log.action} • $date", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Check Expired Utility
fun isExpired(dateStr: String): Boolean {
    if (dateStr.isEmpty()) return false
    return try {
        val sdf = SimpleDateFormat("MM-yyyy", Locale.ROOT)
        val date = sdf.parse(dateStr) ?: return false
        date.before(Date())
    } catch (e: Exception) {
        false
    }
}

// Expiring Soon Utility (e.g. 30 days)
fun isExpiringSoon(dateStr: String, days: Int): Boolean {
    if (dateStr.isEmpty()) return false
    return try {
        val sdf = SimpleDateFormat("MM-yyyy", Locale.ROOT)
        val date = sdf.parse(dateStr) ?: return false
        val diff = date.time - System.currentTimeMillis()
        val diffDays = diff / (1000 * 60 * 60 * 24)
        diffDays in 0..days
    } catch (e: Exception) {
        false
    }
}
