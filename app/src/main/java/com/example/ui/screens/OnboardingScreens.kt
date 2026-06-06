package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.AccentEmerald

@Composable
fun OnboardingScreenContainer(
    onboardingStep: Int,
    onStepChanged: (Int) -> Unit,
    googleId: String?,
    onGoogleLogin: (id: String, name: String, email: String, photo: String) -> Unit,
    onRestoreSelected: (String) -> Unit,
    onSetupStore: (name: String, owner: String, phone: String, email: String, address: String, gst: String) -> Unit,
    cloudBackups: List<Map<String, String>>,
    onFinishOnboarding: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        when (onboardingStep) {
            1 -> WelcomeScreen(onNext = { onStepChanged(2) })
            2 -> GoogleSignInScreen(onLoggedIn = { id, name, email, photo ->
                onGoogleLogin(id, name, email, photo)
                onStepChanged(3)
            })
            3 -> CheckBackupScreen(
                cloudBackups = cloudBackups,
                onRestore = { filename ->
                    onRestoreSelected(filename)
                    onFinishOnboarding()
                },
                onStartFresh = { onStepChanged(4) }
            )
            4 -> StoreSetupScreen(
                onSave = { name, owner, phone, email, addr, gst ->
                    onSetupStore(name, owner, phone, email, addr, gst)
                    onStepChanged(5)
                }
            )
            5 -> NotificationPermissionScreen(
                onCompleted = {
                    onFinishOnboarding()
                }
            )
        }
    }
}

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // App Icon Placeholder
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(PrimaryTeal, AccentEmerald)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Inventory,
                contentDescription = "Inventory Icon",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Inventory Manager",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Professional. Secure. Offline-First.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Feature bullet items
        WelcomeFeatureItem(
            icon = Icons.Default.CloudQueue,
            title = "Zero Server Cloud Integration",
            description = "Your inventory data resides completely offline on your device, backed up securely to your own private Google Drive folder."
        )

        WelcomeFeatureItem(
            icon = Icons.Default.Warning,
            title = "Expiry & Low Stock Monitors",
            description = "Get automatic notification triggers for expired batches, expiring products, and critical stock level warnings."
        )

        WelcomeFeatureItem(
            icon = Icons.Default.RestoreFromTrash,
            title = "Reversible Data Restores",
            description = "Automatic rollback points before major database adjustments, preserving previous states seamlessly."
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Get Started", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Next")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun WelcomeFeatureItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}

@Composable
fun GoogleSignInScreen(onLoggedIn: (String, String, String, String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.VpnKey,
            contentDescription = "Authenticator Key",
            tint = PrimaryTeal,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Google Account Login",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "To sync your inventory with your own personal cloud storage, please sign in with your Google Account.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Choose an account to continue",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Simulated highly responsive Google account selector card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    onLoggedIn(
                        "99238411",
                        "Alok Gupta",
                        "agupta118258@gmail.com",
                        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
                    )
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PrimaryTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Text("AG", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Alok Gupta", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("agupta118258@gmail.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Google App Integration", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    onLoggedIn(
                        "55318490",
                        "Guest Owner",
                        "guest.store@gmail.com",
                        ""
                    )
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = borderStroke()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("GO", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Guest Retailer", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("guest.store@gmail.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Google App Integration", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your data is strictly yours. Powered by your private Google Drive space with no intermediate servers involved.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun CheckBackupScreen(
    cloudBackups: List<Map<String, String>>,
    onRestore: (String) -> Unit,
    onStartFresh: () -> Unit
) {
    var checkingState by remember { mutableStateOf(0) } // 0: Checking, 1: Found, 2: Not Found
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        checkingState = if (cloudBackups.isNotEmpty()) 1 else 2
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (checkingState == 0) {
            CircularProgressIndicator(color = PrimaryTeal, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Scanning Google Drive Storage...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Looking for existing 'inventory_backup.json' files...",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (checkingState == 1) {
            val backup = cloudBackups.first()
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Cloud Backup Detected",
                tint = AccentEmerald,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cloud Backup Detected!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "We found records stored under your Gmail.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("File: ${backup["fileName"]}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Date: ${backup["date"]}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Inventory Metrics: ${backup["itemCount"]}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Backup File Weight: ${backup["size"]}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { onRestore(backup["fileName"] ?: "") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Backup, contentDescription = "Restore", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore Backup & Enter Dashboard", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onStartFresh,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                border = borderStroke()
            ) {
                Text("Start Fresh (Skip Restore)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "No Backup Found",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Previous Cloud Backups Found",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Create a pristine store profile to start managing.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 36.dp)
            )

            Button(
                onClick = onStartFresh,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Create Store Profile", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun StoreSetupScreen(
    onSave: (name: String, owner: String, phone: String, email: String, address: String, gst: String) -> Unit
) {
    var storeName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var gst by remember { mutableStateOf("") }

    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Storefront,
            contentDescription = "Store Details",
            tint = PrimaryTeal,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Store Information Setup",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "These details are used to draft reports, export listings, and configure local operations.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = storeName,
            onValueChange = { storeName = it; errorText = null },
            label = { Text("Store Name *") },
            placeholder = { Text("e.g. Apex Health Pharmacy") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(imageVector = Icons.Default.Business, contentDescription = null) }
        )

        OutlinedTextField(
            value = ownerName,
            onValueChange = { ownerName = it; errorText = null },
            label = { Text("Owner Name *") },
            placeholder = { Text("e.g. Alok Gupta") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) }
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it; errorText = null },
            label = { Text("Phone Number *") },
            placeholder = { Text("e.g. +91 98765 43210") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            leadingIcon = { Icon(imageVector = Icons.Default.Phone, contentDescription = null) }
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Store Email (Optional)") },
            placeholder = { Text("e.g. apex.health@gmail.com") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = null) }
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it; errorText = null },
            label = { Text("Physical Address *") },
            placeholder = { Text("e.g. Shop 4B, Sector 15, New Delhi") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(imageVector = Icons.Default.Map, contentDescription = null) }
        )

        OutlinedTextField(
            value = gst,
            onValueChange = { gst = it },
            label = { Text("GST/Tax Number (Optional)") },
            placeholder = { Text("e.g. 07AAAAA1111A1Z1") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(imageVector = Icons.Default.Receipt, contentDescription = null) }
        )

        if (errorText != null) {
            Text(
                errorText!!,
                color = androidx.compose.ui.graphics.Color.Red,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (storeName.isBlank() || ownerName.isBlank() || phone.isBlank() || address.isBlank()) {
                    errorText = "Please complete all marked (*) mandatory fields."
                } else {
                    onSave(storeName.trim(), ownerName.trim(), phone.trim(), email.trim(), address.trim(), gst.trim())
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Register Store & Continue", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NotificationPermissionScreen(
    onCompleted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = "Notifications active",
            tint = PrimaryTeal,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Low Stock & Expiry Alerts",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Enable notifications to receive immediate warning alerts whenever items drop below critical thresholds, or are closer to expiration dates.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onCompleted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Enable Notifications & Enter Dashboard", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onCompleted
        ) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun borderStroke() = androidx.compose.foundation.BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.surfaceVariant
)
