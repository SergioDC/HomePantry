package com.homepantry.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.homepantry.app.data.ConnectivityObserver
import com.homepantry.app.data.DishesRepository
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.splitMonthIdFromLink
import com.homepantry.app.data.Item
import com.homepantry.app.data.ItemsRepository
import com.homepantry.app.data.MealEntriesRepository
import com.homepantry.app.data.MembersRepository
import com.homepantry.app.data.MenuImportDraftStore
import com.homepantry.app.data.NotificationsRepository
import com.homepantry.app.data.PeopleRepository
import com.homepantry.app.data.PurchasesRepository
import com.homepantry.app.data.SplitExpensesRepository
import com.homepantry.app.data.SplitMonthsRepository
import com.homepantry.app.data.SplitPhotosRepository
import com.homepantry.app.data.StorageRepository
import com.homepantry.app.data.UserPrefs
import com.homepantry.app.data.ZonesRepository
import com.homepantry.app.data.initialOffersCheck
import com.homepantry.app.data.isValidNotificationId
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.NotificationsViewModel
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.components.BottomNavBar
import com.homepantry.app.ui.components.BottomNavItem
import com.homepantry.app.ui.screens.AddItemSheet
import com.homepantry.app.ui.screens.DefaultProductsSheet
import com.homepantry.app.ui.screens.EditNameDialog
import com.homepantry.app.ui.screens.PurchaseStatsScreen
import com.homepantry.app.ui.screens.HomeScreen
import com.homepantry.app.ui.screens.JoinHouseholdScreen
import com.homepantry.app.ui.screens.SplitArchivedScreen
import com.homepantry.app.ui.screens.SplitExpenseScreen
import com.homepantry.app.ui.screens.SplitMonthEditScreen
import com.homepantry.app.ui.components.RetainSplitMonth
import com.homepantry.app.ui.screens.MainListScreen
import com.homepantry.app.ui.screens.ManageZonesScreen
import com.homepantry.app.ui.screens.MenuScreen
import com.homepantry.app.ui.screens.NotificationDetailScreen
import com.homepantry.app.ui.screens.NotificationsScreen
import com.homepantry.app.ui.screens.PurchaseDetailScreen
import com.homepantry.app.ui.screens.PurchaseHistoryScreen
import com.homepantry.app.ui.screens.PurchaseProductsScreen
import com.homepantry.app.ui.screens.PurchaseTicketDetailScreen
import com.homepantry.app.ui.screens.SearchScreen
import com.homepantry.app.ui.screens.SplitMonthScreen
import com.homepantry.app.ui.screens.SplitMonthsScreen
import com.homepantry.app.ui.screens.ZoneDetailScreen
import com.homepantry.app.ui.screens.ZonesDashboardScreen
import com.homepantry.app.ui.theme.ListaDeLaCasaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.ZonedDateTime

/**
 * Punto de entrada. Firma anónimamente (Firebase Auth) antes de tocar
 * Firestore, luego decide JoinHousehold vs. la app según haya o no código
 * de casa guardado en DataStore (SPEC.md sec 1.1 y 3).
 */
class MainActivity : ComponentActivity() {
    /** Grupo de SNcount de un enlace abierto, pendiente hasta que la app navega a él. */
    private val pendingSplitMonthId = mutableStateOf<String?>(null)

    /** Aviso que hay que abrir porque se ha tocado su notificación (ver OffersCheckJobService). */
    private var pendingNotificationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Solo en el primer arranque: al recrear la actividad (rotación) el intent sigue siendo el mismo.
        if (savedInstanceState == null) pendingNotificationId = intent?.getStringExtra(EXTRA_NOTIFICATION_ID)?.takeIf(::isValidNotificationId)
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Solo en el arranque real: al recrear la actividad (girar) el intent es el mismo y ya se navegó.
        if (savedInstanceState == null) pendingSplitMonthId.value = splitMonthIdFromLink(intent?.dataString)
        // Salida del splash: el logo crece un poco y se desvanece junto al fondo,
        // revelando la app por debajo.
        splash.setOnExitAnimationListener { provider ->
            val duration = 450L
            val interpolator = AccelerateDecelerateInterpolator()
            provider.iconView.animate()
                .scaleX(1.35f).scaleY(1.35f).alpha(0f)
                .setDuration(duration).setInterpolator(interpolator).start()
            provider.view.animate()
                .alpha(0f)
                .setDuration(duration).setInterpolator(interpolator)
                .withEndAction { provider.remove() }
                .start()
        }
        // Fondo siempre oscuro: iconos claros en las barras del sistema, sea cual sea el modo del móvil.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            ListaDeLaCasaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ListaDeLaCasaApp(
                        pendingSplitMonthId = pendingSplitMonthId.value,
                        onSplitLinkHandled = { pendingSplitMonthId.value = null },
                        pendingNotificationId = pendingNotificationId,
                        onNotificationOpened = { pendingNotificationId = null }
                    )
                }
            }
        }
    }

    /**
     * Con `singleTop`, un enlace de SNcount o la notificación de ofertas con la app ya en marcha
     * llegan aquí y no a onCreate.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        splitMonthIdFromLink(intent.dataString)?.let { pendingSplitMonthId.value = it }
        intent.getStringExtra(EXTRA_NOTIFICATION_ID)?.takeIf(::isValidNotificationId)?.let { pendingNotificationId = it }
    }
}

private val BOTTOM_NAV_ROUTES = setOf("mainList", "zonesDashboard", "menu", "search")

@Composable
fun ListaDeLaCasaApp(
    pendingSplitMonthId: String? = null,
    onSplitLinkHandled: () -> Unit = {},
    pendingNotificationId: String? = null,
    onNotificationOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val userPrefs = remember { UserPrefs(context) }
    val geminiApiKeyStore = remember { GeminiApiKeyStore(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    var authReady by remember { mutableStateOf(false) }
    var authFailed by remember { mutableStateOf(false) }
    var authAttempt by remember { mutableIntStateOf(0) }

    // Sin red en el primer arranque (aún no hay usuario anónimo) signInAnonymously falla: sin
    // capturarlo, la excepción tumbaba la app. Se muestra el error con un botón para reintentar.
    LaunchedEffect(authAttempt) {
        authFailed = false
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            authReady = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            authFailed = true
        }
    }

    if (!authReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (authFailed) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(stringResource(R.string.auth_error), textAlign = TextAlign.Center)
                    Button(onClick = { authAttempt++ }, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.auth_retry))
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
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
    // El Flow se crea una sola vez: `observe()` devuelve uno nuevo en cada llamada, y collectAsState
    // con un Flow distinto en cada recomposición volvía a registrar el callback de red cada vez.
    val connectivity = remember { ConnectivityObserver(context).observe() }
    val isOnline by connectivity.collectAsState(initial = true)

    val uid = auth.currentUser?.uid.orEmpty()

    // Los ViewModels van por código de casa y se reutilizan aunque cambie el nombre; el nombre
    // nuevo se les pasa abajo con updateUserName.
    val viewModel: AppViewModel = viewModel(
        key = code,
        factory = remember(code) {
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

    val menuViewModel: MenuViewModel = viewModel(
        key = "menu-$code",
        factory = remember(code) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MenuViewModel(
                        dishesRepository = DishesRepository(firestore, code),
                        entriesRepository = MealEntriesRepository(firestore, code),
                        itemsRepository = ItemsRepository(firestore, code),
                        peopleRepository = PeopleRepository(firestore, code),
                        importDraftStore = MenuImportDraftStore(
                            File(context.applicationContext.filesDir, "menu_import_draft_$code.json")
                        ),
                        userName = name
                    ) as T
                }
            }
        }
    )

    val splitViewModel: SplitViewModel = viewModel(
        key = "split-$code",
        factory = remember(code) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return SplitViewModel(
                        monthsRepository = SplitMonthsRepository(firestore, code),
                        expensesRepository = SplitExpensesRepository(firestore, code),
                        photosRepository = SplitPhotosRepository(firestore, code),
                        membersRepository = MembersRepository(firestore, code),
                        storageRepository = StorageRepository(storage, code, context.applicationContext),
                        currentUid = uid
                    ) as T
                }
            }
        }
    )

    val notificationsViewModel: NotificationsViewModel = viewModel(
        key = "notifications-$code",
        factory = remember(code) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return NotificationsViewModel(
                        notificationsRepository = NotificationsRepository(firestore, code),
                        itemsRepository = ItemsRepository(firestore, code),
                        zonesRepository = ZonesRepository(firestore, code),
                        uid = uid,
                        userName = name
                    ) as T
                }
            }
        }
    )

    LaunchedEffect(viewModel, menuViewModel, notificationsViewModel, name) {
        viewModel.updateUserName(name)
        menuViewModel.updateUserName(name)
        notificationsViewModel.updateUserName(name)
    }
    val notificationsState by notificationsViewModel.state.collectAsState()

    // Comprobación semanal del aviso de ofertas; si ya hay una programada no se toca.
    LaunchedEffect(code) {
        ensureOffersChannel(context)
        scheduleOffersCheck(context, initialOffersCheck(ZonedDateTime.now()), replace = false)
    }

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

    // Se ha tocado la notificación de ofertas: se abre el aviso cuando el NavHost ya tiene grafo
    // (la ruta deja de ser null). Desde ahí, atrás vuelve a Home.
    val navReady = currentRoute != null
    LaunchedEffect(pendingNotificationId, navReady) {
        val id = pendingNotificationId
        if (id != null && navReady) {
            navController.navigate("notification/$id") { launchSingleTop = true }
            onNotificationOpened()
        }
    }
    // La barra es solo del área Almacén. Con `home` de inicio, la ruta es null el primer fotograma:
    // se exige que exista para que la barra no aparezca un instante al arrancar.
    val showBottomBar = currentRoute != null &&
        (BOTTOM_NAV_ROUTES.contains(currentRoute) || currentRoute.startsWith("zoneDetail/"))

    Scaffold(
        bottomBar = {
            // AnimatedVisibility en vez de un `if` a secas: al entrar/salir de pantallas sin
            // barra inferior (p.ej. Ajustes) el Scaffold recalculaba el padding del contenido
            // de golpe al aparecer/desaparecer la barra, dando un parpadeo/salto visual.
            AnimatedVisibility(visible = showBottomBar) {
                val selectedRoute = if (currentRoute?.startsWith("zoneDetail/") == true) "zonesDashboard" else currentRoute
                BottomNavBar(
                    items = listOf(
                        BottomNavItem("home", stringResource(R.string.nav_inicio), Icons.Filled.Home),
                        BottomNavItem("zonesDashboard", stringResource(R.string.nav_almacen), Icons.Filled.Inventory2),
                        BottomNavItem("mainList", stringResource(R.string.nav_lista), Icons.Filled.ShoppingCart, badgeCount = pendingCount),
                        BottomNavItem("menu", stringResource(R.string.nav_menu), Icons.Filled.RestaurantMenu),
                        BottomNavItem("search", stringResource(R.string.nav_buscar), Icons.Filled.Search)
                    ),
                    selectedRoute = selectedRoute ?: "zonesDashboard",
                    onSelect = { route ->
                        // popUpTo(inicio) apunta a `home`: atrás desde cualquier pestaña vuelve a Home.
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
            startDestination = "home",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    splitViewModel = splitViewModel,
                    userName = name,
                    onOpenSettings = { navController.navigate("manageZones") },
                    onOpenStorage = { navController.navigate("zonesDashboard") },
                    onOpenSpending = { navController.navigate("purchaseHistory") },
                    onOpenSplit = { navController.navigate("splitMonths") },
                    unreadNotifications = notificationsState.unreadCount,
                    onOpenNotifications = { navController.navigate("notifications") }
                )
            }
            composable("notifications") {
                NotificationsScreen(
                    viewModel = notificationsViewModel,
                    uid = uid,
                    onBack = { navController.popBackStack() },
                    onOpenNotification = { id -> navController.navigate("notification/$id") }
                )
            }
            composable("notification/{id}") { backStackEntry ->
                NotificationDetailScreen(
                    viewModel = notificationsViewModel,
                    notificationId = backStackEntry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("mainList") {
                MainListScreen(
                    viewModel = viewModel,
                    isOnline = isOnline,
                    onAddItem = {
                        addItemZoneOverride = null
                        showAddItem = true
                    },
                    onEditItem = { item -> itemBeingEdited = item },
                    onQuickAddDefaults = { showDefaultProducts = true }
                )
            }
            composable("menu") {
                MenuScreen(menuViewModel = menuViewModel, appViewModel = viewModel)
            }
            composable("zonesDashboard") {
                ZonesDashboardScreen(
                    viewModel = viewModel,
                    userName = name,
                    onOpenZone = { zoneId -> navController.navigate("zoneDetail/$zoneId") }
                )
            }
            composable("zoneDetail/{zoneId}") { backStackEntry ->
                val zoneId = backStackEntry.arguments?.getString("zoneId") ?: return@composable
                ZoneDetailScreen(
                    viewModel = viewModel,
                    zoneId = zoneId,
                    onBack = { navController.popBackStack() },
                    onAddItem = { targetZoneId ->
                        addItemZoneOverride = targetZoneId
                        showAddItem = true
                    },
                    onEditItem = { item -> itemBeingEdited = item },
                    onOpenZone = { subzoneId -> navController.navigate("zoneDetail/$subzoneId") }
                )
            }
            composable("search") {
                SearchScreen(viewModel = viewModel)
            }
            composable("manageZones") {
                ManageZonesScreen(
                    viewModel = viewModel,
                    householdCode = code,
                    userName = name,
                    userPrefs = userPrefs,
                    geminiApiKeyStore = geminiApiKeyStore,
                    onBack = { navController.popBackStack() },
                    onEditName = { showEditName = true }
                )
            }
            composable("purchaseHistory") {
                PurchaseHistoryScreen(
                    viewModel = viewModel,
                    geminiApiKeyStore = geminiApiKeyStore,
                    onBack = { navController.popBackStack() },
                    onOpenTicket = { ticketKey ->
                        navController.navigate("purchaseTicket/${Uri.encode(ticketKey)}")
                    },
                    onOpenProducts = { navController.navigate("purchaseProducts") },
                    onOpenStats = { navController.navigate("purchaseStats") }
                )
            }
            composable("purchaseProducts") {
                PurchaseProductsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProduct = { normalizedName ->
                        navController.navigate("purchaseDetail/${Uri.encode(normalizedName)}")
                    }
                )
            }
            composable("purchaseStats") {
                PurchaseStatsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenProduct = { normalizedName ->
                        navController.navigate("purchaseDetail/${Uri.encode(normalizedName)}")
                    }
                )
            }
            composable("purchaseDetail/{normalizedName}") { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("normalizedName") ?: return@composable
                PurchaseDetailScreen(
                    viewModel = viewModel,
                    normalizedName = Uri.decode(encodedName),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("purchaseTicket/{ticketKey}") { backStackEntry ->
                val encodedKey = backStackEntry.arguments?.getString("ticketKey") ?: return@composable
                PurchaseTicketDetailScreen(
                    viewModel = viewModel,
                    ticketKey = Uri.decode(encodedKey),
                    onBack = { navController.popBackStack() }
                )
            }
            composable("splitMonths") {
                SplitMonthsScreen(
                    viewModel = splitViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenMonth = { monthId -> navController.navigate("splitMonth/$monthId") },
                    onEditMonth = { monthId -> navController.navigate("splitMonthEdit/$monthId") },
                    onOpenArchived = { navController.navigate("splitArchived") }
                )
            }
            composable("splitArchived") {
                SplitArchivedScreen(
                    viewModel = splitViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenMonth = { monthId -> navController.navigate("splitMonth/$monthId") },
                    onEditMonth = { monthId -> navController.navigate("splitMonthEdit/$monthId") }
                )
            }
            composable("splitMonthEdit/{monthId}") { backStackEntry ->
                val monthId = backStackEntry.arguments?.getString("monthId") ?: return@composable
                RetainSplitMonth(splitViewModel, backStackEntry, monthId)
                SplitMonthEditScreen(
                    viewModel = splitViewModel,
                    monthId = monthId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("splitMonth/{monthId}") { backStackEntry ->
                val monthId = backStackEntry.arguments?.getString("monthId") ?: return@composable
                RetainSplitMonth(splitViewModel, backStackEntry, monthId)
                SplitMonthScreen(
                    viewModel = splitViewModel,
                    monthId = monthId,
                    onEditMonth = { navController.navigate("splitMonthEdit/$monthId") },
                    onOpenExpense = { expenseId ->
                        navController.navigate(
                            if (expenseId == null) "splitExpense/$monthId" else "splitExpense/$monthId?expenseId=$expenseId"
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "splitExpense/{monthId}?expenseId={expenseId}",
                arguments = listOf(navArgument("expenseId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val monthId = backStackEntry.arguments?.getString("monthId") ?: return@composable
                RetainSplitMonth(splitViewModel, backStackEntry, monthId)
                SplitExpenseScreen(
                    viewModel = splitViewModel,
                    monthId = monthId,
                    expenseId = backStackEntry.arguments?.getString("expenseId"),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    // Enlace de SNcount compartido por otro miembro: se navega por Home → SNcount → el grupo
    // para que «atrás» funcione como si se hubiera llegado a mano. Si el grupo no es de esta casa,
    // SplitMonthScreen vuelve sola a la lista. Va después del NavHost para que el grafo ya exista.
    LaunchedEffect(pendingSplitMonthId) {
        val monthId = pendingSplitMonthId ?: return@LaunchedEffect
        navController.navigate("splitMonths") {
            popUpTo("home")
            launchSingleTop = true
        }
        navController.navigate("splitMonth/$monthId")
        onSplitLinkHandled()
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
