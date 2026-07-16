package com.listacasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Punto de entrada. La navegación real (JoinHousehold -> MainList -> AddItem ->
 * ManageZones) se define aquí. Cada pantalla vive en ui/screens/ — son stubs por
 * completar, ver checklist en SPEC.md sección 5.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ListaDeLaCasaApp()
        }
    }
}

@Composable
fun ListaDeLaCasaApp() {
    MaterialTheme {
        Surface(modifier = Modifier) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "joinHousehold") {
                composable("joinHousehold") {
                    // TODO (Claude Code): JoinHouseholdScreen — pedir/crear código
                    // de casa + nombre, guardar en DataStore, navegar a "mainList"
                }
                composable("mainList") {
                    // TODO (Claude Code): MainListScreen — ver SPEC.md sección 1.4
                }
                composable("addItem") {
                    // TODO (Claude Code): AddItemSheet — ver SPEC.md sección 1.5
                    // (incluye CameraX + ML Kit Barcode Scanning + Open Food Facts)
                }
                composable("manageZones") {
                    // TODO (Claude Code): ManageZonesScreen — ver SPEC.md sección 1.6
                }
            }
        }
    }
}
