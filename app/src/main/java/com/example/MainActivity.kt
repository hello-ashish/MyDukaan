package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.data.AppDatabase
import com.example.data.InventoryRepository
import com.example.ui.screens.MainAppContainer
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.InventoryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SQLite Room database & business repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = InventoryRepository(applicationContext, database)
        
        // Inject dependencies using clean Constructor-Factory MVVM pattern
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return InventoryViewModel(application, repository) as T
            }
        }
        val viewModel = ViewModelProvider(this, factory)[InventoryViewModel::class.java]

        enableEdgeToEdge()
        
        setContent {
            val settings by viewModel.settingsState.collectAsState(initial = null)
            val isDark = settings?.darkMode ?: false
            MyApplicationTheme(darkTheme = isDark) {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}
