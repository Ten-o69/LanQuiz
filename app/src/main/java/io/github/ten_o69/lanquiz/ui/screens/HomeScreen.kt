package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.ui.Routes
import io.github.ten_o69.lanquiz.vm.AppRole
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val canUndo by vm.canUndo.collectAsState() // <-- нужно, чтобы кнопка Undo включалась/выключалась

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("LAN Quiz", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(
                        onClick = { vm.undo() },
                        enabled = canUndo
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Отмена")
                    }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
                    }
                }
            )
        }
    ) { pad ->
        NeonBackground(contentPadding = pad) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NeonCard(Modifier.fillMaxWidth()) {
                    Text("Профиль", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ui.nickname,
                        onValueChange = vm::setNickname,
                        label = { Text("Ник") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ui.roomCode,
                        onValueChange = vm::setRoomCode,
                        label = { Text("Код комнаты") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ui.password,
                        onValueChange = vm::setPassword,
                        label = { Text("Пароль (опционально)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                NeonCard(Modifier.fillMaxWidth()) {
                    Text("Роль", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoleToggleButton(
                            text = "Хост",
                            selected = ui.selectedRole == AppRole.HOST,
                            onClick = { vm.setSelectedRole(AppRole.HOST) },
                            modifier = Modifier.weight(1f)
                        )
                        RoleToggleButton(
                            text = "Игрок",
                            selected = ui.selectedRole == AppRole.CLIENT,
                            onClick = { vm.setSelectedRole(AppRole.CLIENT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (ui.error != null) {
                    Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (ui.selectedRole == AppRole.HOST) {
                                nav.navigate(Routes.HOST)
                            } else {
                                vm.startJoinDiscovery()
                                nav.navigate(Routes.JOIN)
                            }
                        }
                    ) {
                        Text(if (ui.selectedRole == AppRole.HOST) "Создать" else "Подключиться")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (ui.selectedRole == AppRole.HOST) {
                                vm.startJoinDiscovery()
                                nav.navigate(Routes.JOIN)
                            } else {
                                nav.navigate(Routes.HOST)
                            }
                        }
                    ) {
                        Text(if (ui.selectedRole == AppRole.HOST) "Я игрок" else "Я хост")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}
