package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.ui.components.HeroHeader
import io.github.ten_o69.lanquiz.ui.components.SectionHeader
import io.github.ten_o69.lanquiz.ui.components.SegmentedControl
import io.github.ten_o69.lanquiz.vm.QuizViewModel
import io.github.ten_o69.lanquiz.vm.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { pad ->
        NeonBackground(contentPadding = pad) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroHeader(
                    title = "Настройки",
                    subtitle = "Подстрой внешний вид под себя"
                )

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Тема",
                        subtitle = "Светлая, тёмная или системная"
                    )
                    SegmentedControl(
                        options = listOf("Светлая", "Тёмная", "Системная"),
                        selectedIndex = when (ui.themeMode) {
                            ThemeMode.LIGHT -> 0
                            ThemeMode.DARK -> 1
                            ThemeMode.SYSTEM -> 2
                        },
                        onSelect = { idx ->
                            when (idx) {
                                0 -> vm.setThemeMode(ThemeMode.LIGHT)
                                1 -> vm.setThemeMode(ThemeMode.DARK)
                                else -> vm.setThemeMode(ThemeMode.SYSTEM)
                            }
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}
