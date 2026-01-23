package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.ui.Routes
import io.github.ten_o69.lanquiz.ui.components.BannerTone
import io.github.ten_o69.lanquiz.ui.components.HeroHeader
import io.github.ten_o69.lanquiz.ui.components.SectionHeader
import io.github.ten_o69.lanquiz.ui.components.StatPill
import io.github.ten_o69.lanquiz.ui.components.StatusBanner
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()

    LaunchedEffect(ui.stage) {
        // когда подключился — уйдём в лобби
        if (ui.stage.name == "LOBBY") {
            nav.navigate(Routes.LOBBY) { popUpTo(Routes.JOIN) { inclusive = true } }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Подключение") }) }) { pad ->
        NeonBackground(contentPadding = pad) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroHeader(
                    title = "Подключение",
                    subtitle = "Ищем комнату по локальной сети"
                )

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Поиск комнаты",
                        subtitle = "Код понадобится ведущему"
                    )
                    Spacer(Modifier.height(10.dp))
                    StatPill(text = "Код: ${ui.roomCode}")
                    Spacer(Modifier.height(10.dp))
                    if (ui.resolvedHost != null && ui.resolvedPort != null) {
                        StatusBanner(
                            text = "Найдена: ${ui.resolvedHost}:${ui.resolvedPort}",
                            tone = BannerTone.Success
                        )
                    } else {
                        Text(
                            "Ищем активную комнату…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                if (ui.error != null) {
                    StatusBanner(
                        text = ui.error!!,
                        tone = BannerTone.Error
                    )
                }

                OutlinedButton(
                    onClick = { vm.startJoinDiscovery() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                ) { Text("Повторить поиск") }
            }
        }
    }
}
