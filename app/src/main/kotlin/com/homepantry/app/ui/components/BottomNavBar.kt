package com.homepantry.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector, val badgeCount: Int = 0)

/**
 * Barra de navegación inferior Nocturne, 3 destinos. Llevaba solo un punto de
 * color sin icono -- sin apoyo visual no quedaba claro qué representaba cada
 * pestaña, así que ahora cada una lleva su icono Material.
 */
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth().navigationBarsPadding(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { navItem ->
                val isSelected = navItem.route == selectedRoute
                val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier.clickable { onSelect(navItem.route) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BadgedBox(
                        badge = {
                            if (navItem.badgeCount > 0) {
                                Badge { Text(if (navItem.badgeCount > 99) "99+" else navItem.badgeCount.toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = navItem.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = navItem.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
