package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.MenuViewModel

/** Pestaña «Menú»: calendario y lista de platos. Esqueleto: la Task 12 lo completa. */
@Composable
fun MenuScreen(menuViewModel: MenuViewModel, appViewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.menu_tab_menu)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.menu_tab_dishes)) }
                )
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(if (selectedTab == 0) R.string.menu_tab_menu else R.string.menu_tab_dishes))
            }
        }
    }
}
