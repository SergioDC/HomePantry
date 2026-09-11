package com.homepantry.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.homepantry.app.data.ConnectivityObserver
import com.homepantry.app.data.Item
import com.homepantry.app.data.ItemsRepository
import com.homepantry.app.data.MembersRepository
import com.homepantry.app.data.PurchasesRepository
import com.homepantry.app.data.StorageRepository
import com.homepantry.app.data.UserPrefs
import com.homepantry.app.data.ZonesRepository
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.BottomNavBar
import com.homepantry.app.ui.components.BottomNavItem
import com.homepantry.app.ui.screens.AddItemSheet
import com.homepantry.app.ui.screens.DefaultProductsSheet
import com.homepantry.app.ui.screens.EditNameDialog
import com.homepantry.app.ui.screens.JoinHouseholdScreen
import com.homepantry.app.ui.screens.MainListScreen
import com.homepantry.app.ui.screens.ManageZonesScreen
import com.homepantry.app.ui.screens.SearchScreen
import com.homepantry.app.ui.screens.ZoneDetailScreen
import com.homepantry.app.ui.screens.ZonesDashboardScreen
import com.homepantry.app.ui.theme.ListaDeLaCasaTheme
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

    val uid = auth.currentUser?.uid.orEmpty()

    val viewModel: AppViewModel = viewModel(
        key = code,
        factory = remember(code, name) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        itemsRepository = ItemsRepository(firestore, code),
                        zonesRepository = ZonesRepository(firestore, code),
                        membersRepository = MembersRepository(firestore, code),
                        purchasesRepository = PurchasesRepository(firestore, code),
                        storageRepository = StorageRepository(storage, code, context.applicationContext),
                        userName = name,
                        uid = uid
                    ) as T
                }
            }
        }
    )

    val state by viewModel.state.collectAsState()
    val pendingCount = state.items.count { !it.done }

    var showAddItem by remember { mutableStateOf(false) }
    var addItemZoneOverride by remember { mutableStateOf<String?>(null) }
    var itemBeingEdited by remember { mutableStateOf<Item?>(null) }
    var showEditName by remember { mutableStateOf(false) }
    var showDefaultProducts by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == null ||
        BOTTOM_NAV_ROUTES.contains(currentRoute) ||
        currentRoute.startsWith("zoneDetail/")

    Scaffold(
        bottomBar = {
            // AnimatedVisibility en vez de un `if` a secas: al entrar/salir de pantallas sin
            // barra inferior (p.ej. Ajustes) el Scaffold recalculaba el padding del contenido
            // de golpe al aparecer/desaparecer la barra, dando un parpadeo/salto visual.
            AnimatedVisibility(visible = showBottomBar) {
                val selectedRoute = if (currentRoute?.startsWith("zoneDetail/") == true) "zonesDashboard" else currentRoute
                BottomNavBar(
                    items = listOf(
                        BottomNavItem("zonesDashboard", stringResource(R.string.nav_almacen), Icons.Filled.Inventory2),
                        BottomNavItem("mainList", stringResource(R.string.nav_lista), Icons.Filled.ShoppingCart, badgeCount = pendingCount),
                        BottomNavItem("search", stringResource(R.string.nav_buscar), Icons.Filled.Search)
                    ),
                    selectedRoute = selectedRoute ?: "zonesDashboard",
                    onSelect = { route ->
                        // Sin saveState/restoreState: cada pestaña siempre vuelve a su
                        // pantalla raíz (p.ej. Almacén nunca deja "colgada" la última
                        // zona abierta -- antes restauraba ese zoneDetail en vez de ir
                        // al listado de zonas).
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "zonesDashboard",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
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
                    onEditName = { showEditName = true },
                    onQuickAddDefaults = { showDefaultProducts = true }
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
                    onAddItem = {
                        addItemZoneOverride = zoneId
                        showAddItem = true
                    },
                    onEditItem = { item -> itemBeingEdited = item }
                )
            }
            composable("search") {
                SearchScreen(viewModel = viewModel)
            }
            composable("manageZones") {
                ManageZonesScreen(
                    viewModel = viewModel,
                    householdCode = code,
                    userPrefs = userPrefs,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (showAddItem || itemBeingEdited != null) {
        AddItemSheet(
            viewModel = viewModel,
            initialZoneId = addItemZoneOverride ?: state.selectedZoneId,
            isFromZone = addItemZoneOverride != null,
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

    if (showDefaultProducts) {
        DefaultProductsSheet(viewModel = viewModel, onDismiss = { showDefaultProducts = false })
    }
}
