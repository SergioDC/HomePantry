package com.homepantry.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import com.homepantry.app.R
import com.homepantry.app.data.ExpenseCategory
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.data.expenseCategoryOf
import com.homepantry.app.ui.SplitUiState
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.theme.NocturneOnSurfaceVariant
import com.homepantry.app.ui.theme.StoreColors

/*
 * Piezas comunes de las pantallas de SNcount.
 */

/** Céntimos a texto es-ES ("1.284,10 €"). Los importes se guardan en céntimos y solo se formatean al pintar. */
fun centsText(cents: Long): String = euros(cents / 100.0)

/**
 * Nombre de un participante. Un uid que ya no está en `members` (p. ej. un móvil reinstalado)
 * o un miembro sin nombre se muestra como «Miembro anterior» en vez de dejar el hueco vacío.
 */
@Composable
fun splitMemberName(state: SplitUiState, id: String): String =
    state.memberName(id)?.takeIf { it.isNotBlank() } ?: stringResource(R.string.split_unknown_member)

/** Icono Material de cada tipo de gasto. */
fun ExpenseCategory.icon(): ImageVector = when (this) {
    ExpenseCategory.SUPERMARKET -> Icons.Filled.ShoppingCart
    ExpenseCategory.COFFEE -> Icons.Filled.LocalCafe
    ExpenseCategory.RESTAURANT -> Icons.Filled.Restaurant
    ExpenseCategory.DRINKS -> Icons.Filled.LocalBar
    ExpenseCategory.CAR -> Icons.Filled.DirectionsCar
    ExpenseCategory.TRANSPORT -> Icons.Filled.Train
    ExpenseCategory.FLIGHT -> Icons.Filled.Flight
    ExpenseCategory.HOTEL -> Icons.Filled.Hotel
    ExpenseCategory.HOME -> Icons.Filled.Home
    ExpenseCategory.SHOPPING -> Icons.Filled.ShoppingBag
    ExpenseCategory.GIFT -> Icons.Filled.CardGiftcard
    ExpenseCategory.LEISURE -> Icons.Filled.TheaterComedy
    ExpenseCategory.HEALTH -> Icons.Filled.LocalHospital
    ExpenseCategory.PETS -> Icons.Filled.Pets
    ExpenseCategory.SPORT -> Icons.Filled.FitnessCenter
    ExpenseCategory.OTHER -> Icons.Filled.Payments
}

/** Icono de un gasto: el de su tipo, o el de intercambio si es un reembolso. */
fun SplitExpense.icon(): ImageVector =
    if (reimbursement) Icons.Filled.SwapHoriz else expenseCategoryOf(title).icon()

/** Icono de un grupo: calendario si es un mes; si es actividad, el tipo que sugiere su nombre. */
fun SplitMonth.icon(): ImageVector {
    if (!activity) return Icons.Filled.CalendarMonth
    val category = expenseCategoryOf(name)
    return if (category == ExpenseCategory.OTHER) Icons.Filled.Groups else category.icon()
}

/*
 * Colores: los mismos de la paleta de tiendas de Gastos (StoreColors), para que SNcount tenga
 * el mismo aire que el resto. Sin coral (alertas, deudas y borrar) ni morado (títulos).
 */
private val Green = StoreColors[0]
private val Blue = StoreColors[1]
private val Amber = StoreColors[2]
private val Lime = StoreColors[3]
private val Turquoise = StoreColors[4]
private val Sand = StoreColors[5]

/** Color de cada tipo de gasto, agrupando tipos parecidos en el mismo tono. */
fun ExpenseCategory.color(): Color = when (this) {
    ExpenseCategory.SUPERMARKET, ExpenseCategory.HEALTH, ExpenseCategory.SPORT -> Green
    ExpenseCategory.CAR, ExpenseCategory.TRANSPORT, ExpenseCategory.FLIGHT -> Blue
    ExpenseCategory.RESTAURANT, ExpenseCategory.DRINKS, ExpenseCategory.GIFT -> Amber
    ExpenseCategory.HOME, ExpenseCategory.PETS -> Lime
    ExpenseCategory.HOTEL, ExpenseCategory.LEISURE -> Turquoise
    ExpenseCategory.COFFEE, ExpenseCategory.SHOPPING, ExpenseCategory.OTHER -> Sand
}

/** Color de un gasto: el de su tipo; un reembolso va en gris porque no es gasto. */
fun SplitExpense.color(): Color =
    if (reimbursement) NocturneOnSurfaceVariant else expenseCategoryOf(title).color()

/** Verde para «le deben» (el primero de la paleta); las deudas van en coral, el color de alerta. */
val SplitPositiveColor: Color = Green

/** Nombre visible de cada tipo de gasto (leyenda de la barra de reparto). */
@Composable
fun ExpenseCategory.label(): String = stringResource(
    when (this) {
        ExpenseCategory.SUPERMARKET -> R.string.split_cat_supermarket
        ExpenseCategory.COFFEE -> R.string.split_cat_coffee
        ExpenseCategory.RESTAURANT -> R.string.split_cat_restaurant
        ExpenseCategory.DRINKS -> R.string.split_cat_drinks
        ExpenseCategory.CAR -> R.string.split_cat_car
        ExpenseCategory.TRANSPORT -> R.string.split_cat_transport
        ExpenseCategory.FLIGHT -> R.string.split_cat_flight
        ExpenseCategory.HOTEL -> R.string.split_cat_hotel
        ExpenseCategory.HOME -> R.string.split_cat_home
        ExpenseCategory.SHOPPING -> R.string.split_cat_shopping
        ExpenseCategory.GIFT -> R.string.split_cat_gift
        ExpenseCategory.LEISURE -> R.string.split_cat_leisure
        ExpenseCategory.HEALTH -> R.string.split_cat_health
        ExpenseCategory.PETS -> R.string.split_cat_pets
        ExpenseCategory.SPORT -> R.string.split_cat_sport
        ExpenseCategory.OTHER -> R.string.split_cat_other
    }
)

/**
 * Color fijo de cada participante de un grupo, por su posición en `memberIds` (que no cambia
 * tras crear el grupo). Un uid ajeno al grupo (ex-miembro) va en gris.
 */
fun memberColors(memberIds: List<String>): Map<String, Color> =
    memberIds.withIndex().associate { (index, id) -> id to StoreColors[index % StoreColors.size] }

fun Map<String, Color>.memberColor(id: String): Color = this[id] ?: NocturneOnSurfaceVariant

/**
 * Color fijo de cada grupo, por orden de creación: crear grupos nuevos no cambia el de los
 * antiguos (igual que el color de cada tienda en Gastos).
 */
fun groupColors(months: List<SplitMonth>): Map<String, Color> =
    months.sortedWith(compareBy<SplitMonth> { it.createdAt }.thenBy { it.id })
        .withIndex()
        .associate { (index, month) -> month.id to StoreColors[index % StoreColors.size] }

/** Icono en un cuadrado redondeado con el fondo de su color suavizado (filas de gastos y cabeceras). */
@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** Como [IconTile], pero con [emoji] en lugar del icono si se eligió uno (gastos y grupos). */
@Composable
fun SplitIcon(emoji: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    if (emoji.isBlank()) {
        IconTile(icon, color, modifier, size)
        return
    }
    Box(
        modifier
            .size(size)
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = with(LocalDensity.current) { (size * 0.55f).toSp() })
    }
}

/** Círculo con la inicial del participante en su color. */
@Composable
fun MemberAvatar(name: String, color: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).background(color.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/** Botón redondo «+» centrado abajo con su texto debajo (añadir gasto o foto). */
@Composable
fun SplitAddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Mantiene abierto el grupo [monthId] mientras exista la entrada de navegación [entry], aunque la
 * pantalla salga de la composición al navegar hacia delante (ver [com.homepantry.app.ui.SplitMonthHolders]).
 *
 * El observer no se quita en `onDispose`: la entrada puede destruirse cuando ya no está en
 * composición (dos `popBackStack` seguidos) y entonces nadie liberaría el grupo. Retener y liberar
 * son idempotentes, así que repetir el observer al volver a componer no descuadra nada.
 */
@Composable
fun RetainSplitMonth(viewModel: SplitViewModel, entry: NavBackStackEntry, monthId: String) {
    DisposableEffect(entry, monthId) {
        viewModel.retainMonth(entry.id, monthId)
        entry.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) viewModel.releaseMonth(entry.id)
        })
        onDispose { }
    }
}
