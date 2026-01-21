package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.Routes
import io.github.ten_o69.lanquiz.vm.QuizViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val canUndo by vm.canUndo.collectAsState() // <-- нужно, чтобы кнопка Undo включалась/выключалась

    LaunchedEffect(Unit) {
        vm.ui.collectLatest {
            // переходы делаем кнопками, тут ничего
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LAN Quiz") },
                navigationIcon = {
                    IconButton(
                        onClick = { nav.popBackStack() },
                        enabled = nav.previousBackStackEntry != null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.undo() },
                        enabled = canUndo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Отмена")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = ui.nickname,
                onValueChange = vm::setNickname,
                label = { Text("Ник") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ui.roomCode,
                onValueChange = vm::setRoomCode,
                label = { Text("Код комнаты") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ui.password,
                onValueChange = vm::setPassword,
                label = { Text("Пароль (опционально)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (ui.error != null) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate(Routes.HOST) }
                ) { Text("Создать") }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.startJoinDiscovery()
                        nav.navigate(Routes.JOIN)
                    }
                ) { Text("Подключиться") }
            }
        }
    }
}