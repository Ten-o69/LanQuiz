package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.ui.Routes
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeonCard(Modifier.fillMaxWidth()) {
                    Text("Ищем комнату", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Код: ${ui.roomCode}")
                    Spacer(Modifier.height(8.dp))
                    if (ui.resolvedHost != null && ui.resolvedPort != null) {
                        Text("Найдена: ${ui.resolvedHost}:${ui.resolvedPort}")
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                if (ui.error != null) Text(ui.error!!, color = MaterialTheme.colorScheme.error)

                OutlinedButton(
                    onClick = { vm.startJoinDiscovery() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Повторить поиск") }
            }
        }
    }
}
