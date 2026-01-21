package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.Routes
import io.github.ten_o69.lanquiz.vm.AppRole
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()

    LaunchedEffect(ui.stage) {
        if (ui.stage.name == "GAME") nav.navigate(Routes.GAME)
        if (ui.stage.name == "RESULTS") nav.navigate(Routes.RESULTS)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Лобби") }) },
        bottomBar = {
            if (ui.role == AppRole.HOST) {
                BottomAppBar {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = { vm.hostStartGame() }) { Text("Начать") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.hostCancelGame() }) { Text("Отменить") }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.role == AppRole.HOST) {
                Text("Комната: QUIZ-${ui.roomCode}")
                Text("Порт: ${ui.hostPort ?: "-"}")
            }

            Text("Игроки: ${ui.players.size}")

            LazyColumn(Modifier.fillMaxWidth()) {
                items(ui.players) {
                    ListItem(headlineContent = { Text(it.name) }, supportingContent = { Text(it.id.take(8)) })
                    Divider()
                }
            }

            if (ui.error != null) Text(ui.error!!, color = MaterialTheme.colorScheme.error)
        }
    }
}