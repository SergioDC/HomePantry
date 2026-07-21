package com.listacasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.listacasa.app.data.ConnectivityObserver
import com.listacasa.app.data.Item
import com.listacasa.app.data.ItemsRepository
import com.listacasa.app.data.StorageRepository
import com.listacasa.app.data.UserPrefs
import com.listacasa.app.data.ZonesRepository
import com.listacasa.app.ui.AppViewModel
import com.listacasa.app.ui.components.BottomNavBar
import com.listacasa.app.ui.components.BottomNavItem
import com.listacasa.app.ui.screens.AddItemSheet
import com.listacasa.app.ui.screens.EditNameDialog
import com.listacasa.app.ui.screens.JoinHouseholdScreen
import com.listacasa.app.ui.screens.MainListScreen
import com.listacasa.app.ui.screens.ManageZonesScreen
import com.listacasa.app.ui.screens.SearchScreen
import com.listacasa.app.ui.screens.ZoneDetailScreen
import com.listacasa.app.ui.screens.ZonesDashboardScreen
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

private val BOTTOM_NAV_ROUTES = setOf("mainList", "zonesDashboard", "search")

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
    val connectivityObserver = remember { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.observe().collectAsState(initial = true)

    val viewModel: AppViewModel = viewModel(
        key = code,
        factory = remember(code, name) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        itemsRepository = ItemsRepository(firestore, code),
                        zonesRepository = ZonesRepository(firestore, code),
                        storageRepository = StorageRepository(storage, code, context.applicationContext),
                        userName = name
                    ) as T
                }
            }
        }
    )

    var showAddItem by remember { mutableStateOf(false) }
    var addItemZoneOverride by remember { mutableStateOf<String?>(null) }
    var itemBeingEdited by remember { mutableStateOf<Item?>(null) }
    var showEditName by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == null ||
        BOTTOM_NAV_ROUTES.contains(currentRoute) ||
        currentRoute.startsWith("zoneDetail/")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                val selectedRoute = if (currentRoute?.startsWith("zoneDetail/") == true) "zonesDashboard" else currentRoute
                BottomNavBar(
                    items = listOf(
                        BottomNavItem("mainList", stringResource(R.string.nav_lista)),
                        BottomNavItem("zonesDashboard", stringResource(R.string.nav_almacen)),
                        BottomNavItem("search", stringResource(R.string.nav_buscar))
                    ),
                    selectedRoute = selectedRoute ?: "mainList",
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "mainList",
            modifier = Modifier.padding(padding)
        ) {
            composable("mainList") {
                MainListScreen(
                    viewModel = viewModel,
                    isOnline = isOnline,
                    onAddItem = {
                        addItemZoneOverride = null
                        showAddItem = true
                    },
                    onEditItem = { item -> itemBeingEdited = item },
                    onEditName = { showEditName = true }
                )
            }
            composable("zonesDashboard") {
                ZonesDashboardScreen(
                    viewModel = viewModel,
                    userName = name,
                    onManageZones = { navController.navigate("manageZones") },
                    onOpenZone = { zoneId -> navController.navigate("zoneDetail/$zoneId") }
                )
            }
            composable("zoneDetail/{zoneId}") { backStackEntry ->
                val zoneId = backStackEntry.arguments?.getString("zoneId") ?: return@composable
                ZoneDetailScreen(
                    viewModel = viewModel,
                    zoneId = zoneId,
                    onBack = { navController.popBackStack() },
                    onManageZones = { navController.navigate("manageZones") },
                    onAddItem = {
                        addItemZoneOverride = zoneId
                        showAddItem = true
                    },
                    onEditItem = { item -> itemBeingEdited = item }
                )
            }
            composable("search") {
                SearchScreen(viewModel = viewModel, onEditItem = { item -> itemBeingEdited = item })
            }
            composable("manageZones") {
                ManageZonesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }

    if (showAddItem || itemBeingEdited != null) {
        val state by viewModel.state.collectAsState()
        AddItemSheet(
            viewModel = viewModel,
            initialZoneId = addItemZoneOverride ?: state.selectedZoneId,
            itemToEdit = itemBeingEdited,
            onDismiss = {
                showAddItem = false
                addItemZoneOverride = null
                itemBeingEdited = null
            }
        )
    }

    if (showEditName) {
        EditNameDialog(userPrefs = userPrefs, currentName = name, onDismiss = { showEditName = false })
    }
}
