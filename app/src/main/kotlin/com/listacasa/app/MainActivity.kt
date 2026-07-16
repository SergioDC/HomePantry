package com.listacasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.listacasa.app.data.ItemsRepository
import com.listacasa.app.data.StorageRepository
import com.listacasa.app.data.UserPrefs
import com.listacasa.app.data.ZonesRepository
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.screens.AddItemSheet
import com.listacasa.app.ui.screens.JoinHouseholdScreen
import com.listacasa.app.ui.screens.MainListScreen
import com.listacasa.app.ui.screens.ManageZonesScreen
import com.listacasa.app.ui.theme.ListaDeLaCasaTheme
import kotlinx.coroutines.tasks.await

/**
 * Punto de entrada. Firma anónimamente (Firebase Auth) antes de tocar
 * Firestore, luego decide JoinHousehold vs. la app según haya o no código
 * de casa guardado en DataStore (SPEC.md sec 1.1 y 3).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ListaDeLaCasaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ListaDeLaCasaApp()
                }
            }
        }
    }
}

@Composable
fun ListaDeLaCasaApp() {
    val context = LocalContext.current
    val userPrefs = remember { UserPrefs(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    var authReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        authReady = true
    }

    if (!authReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val householdCode by userPrefs.householdCode.collectAsState(initial = null)
    val userName by userPrefs.userName.collectAsState(initial = null)

    val code = householdCode
    val name = userName

    if (code.isNullOrBlank() || name.isNullOrBlank()) {
        JoinHouseholdScreen(userPrefs = userPrefs, onJoined = { /* recomposes from the DataStore flow */ })
        return
    }

    val firestore = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val viewModel: AppViewModel = viewModel(
        key = code,
        factory = remember(code, name) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        itemsRepository = ItemsRepository(firestore, code),
                        zonesRepository = ZonesRepository(firestore, code),
                        storageRepository = StorageRepository(storage, code),
                        userName = name
                    ) as T
                }
            }
        }
    )

    var showAddItem by remember { mutableStateOf(false) }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "mainList") {
        composable("mainList") {
            MainListScreen(
                viewModel = viewModel,
                onAddItem = { showAddItem = true },
                onManageZones = { navController.navigate("manageZones") }
            )
        }
        composable("manageZones") {
            ManageZonesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }

    if (showAddItem) {
        val state by viewModel.state.collectAsState()
        AddItemSheet(
            viewModel = viewModel,
            initialZoneId = state.selectedZoneId,
            onDismiss = { showAddItem = false }
        )
    }
}
