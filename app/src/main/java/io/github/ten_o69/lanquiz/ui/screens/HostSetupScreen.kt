package io.github.ten_o69.lanquiz.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun HostSetupScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importQuestions(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Создание комнаты") },
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
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NeonCard(Modifier.fillMaxWidth()) {
                    Text("Вопросы", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Загружено: ${ui.questions.size}")
                    Spacer(Modifier.height(8.dp))
                    ui.questions.take(2).forEachIndexed { i, q ->
                        Text("${i + 1}. ${q.text}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Импортировать (JSON/TXT/XLSX)") }
                }

                NeonCard(Modifier.fillMaxWidth()) {
                    Text("Режим игры", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Ручной режим (Далее)")
                            Text(
                                "Хост раскрывает ответы и переключает вопросы",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ui.manualAdvance,
                            onCheckedChange = vm::setManualAdvance
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Таймер")
                            Text(
                                if (ui.timerEnabled) "Отсчёт времени активен" else "Без таймера",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ui.timerEnabled,
                            onCheckedChange = vm::setTimerEnabled
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Длительность: ${ui.timerSeconds} сек")
                    Slider(
                        value = ui.timerSeconds.toFloat(),
                        onValueChange = { vm.setTimerSeconds(it.toInt()) },
                        valueRange = 5f..60f,
                        steps = 10,
                        enabled = ui.timerEnabled
                    )
                }

                if (ui.error != null) Text(ui.error!!, color = MaterialTheme.colorScheme.error)

                Button(
                    onClick = {
                        vm.startHosting()
                        nav.navigate(Routes.LOBBY)
                    },
                    enabled = ui.questions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Создать комнату") }
            }
        }
    }
}
