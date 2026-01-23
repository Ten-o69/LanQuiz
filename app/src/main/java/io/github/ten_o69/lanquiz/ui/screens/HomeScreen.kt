package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.ten_o69.lanquiz.ui.Routes
import io.github.ten_o69.lanquiz.ui.components.HeroHeader
import io.github.ten_o69.lanquiz.ui.components.SectionHeader
import io.github.ten_o69.lanquiz.ui.components.SegmentedControl
import io.github.ten_o69.lanquiz.ui.components.StatusBanner
import io.github.ten_o69.lanquiz.ui.components.BannerTone
import io.github.ten_o69.lanquiz.vm.AppRole
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val canUndo by vm.canUndo.collectAsState() // <-- нужно, чтобы кнопка Undo включалась/выключалась
    val isHost = ui.selectedRole == AppRole.HOST

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
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroHeader(
                    title = "LAN Quiz",
                    subtitle = "Аркадная викторина по локальной сети"
                )

                SegmentedControl(
                    options = listOf("Хост", "Игрок"),
                    selectedIndex = if (isHost) 0 else 1,
                    onSelect = { idx ->
                        vm.setSelectedRole(if (idx == 0) AppRole.HOST else AppRole.CLIENT)
                    }
                )

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Профиль",
                        subtitle = "Имя будет видно другим участникам"
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ui.nickname,
                        onValueChange = vm::setNickname,
                        label = { Text("Ник") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Комната",
                        subtitle = if (isHost) {
                            "Код понадобится игрокам для подключения"
                        } else {
                            "Введи код, который дал ведущий"
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ui.roomCode,
                        onValueChange = vm::setRoomCode,
                        label = { Text("Код комнаты") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ui.password,
                        onValueChange = vm::setPassword,
                        label = { Text("Пароль (опционально)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (ui.error != null) {
                    StatusBanner(
                        text = ui.error!!,
                        tone = BannerTone.Error
                    )
                }

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    onClick = {
                        if (isHost) {
                            nav.navigate(Routes.HOST)
                        } else {
                            vm.startJoinDiscovery()
                            nav.navigate(Routes.JOIN)
                        }
                    }
                ) {
                    Text(if (isHost) "Создать комнату" else "Подключиться")
                }
            }
        }
    }
}
