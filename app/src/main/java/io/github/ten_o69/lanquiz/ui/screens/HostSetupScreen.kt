package io.github.ten_o69.lanquiz.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import io.github.ten_o69.lanquiz.ui.components.BannerTone
import io.github.ten_o69.lanquiz.ui.components.HeroHeader
import io.github.ten_o69.lanquiz.ui.components.SectionHeader
import io.github.ten_o69.lanquiz.ui.components.StatPill
import io.github.ten_o69.lanquiz.ui.components.StatusBanner
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroHeader(
                    title = "Создание комнаты",
                    subtitle = "Подготовь вопросы и правила игры"
                )

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Вопросы",
                        subtitle = "JSON, TXT или XLSX"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill(text = "Загружено: ${ui.questions.size}")
                    }
                    Spacer(Modifier.height(10.dp))
                    ui.questions.take(2).forEachIndexed { i, q ->
                        Text("${i + 1}. ${q.text}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Импортировать (JSON/TXT/XLSX)") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.clearQuestions() },
                        enabled = ui.questions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Сбросить вопросы") }
                }

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Правила",
                        subtitle = "Режим ведущего и таймер"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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

                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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

                    Spacer(Modifier.height(10.dp))
                    Text("Длительность: ${ui.timerSeconds} сек")
                    Slider(
                        value = ui.timerSeconds.toFloat(),
                        onValueChange = { vm.setTimerSeconds(it.toInt()) },
                        valueRange = 5f..60f,
                        steps = 10,
                        enabled = ui.timerEnabled
                    )
                }

                if (ui.error != null) {
                    StatusBanner(
                        text = ui.error!!,
                        tone = BannerTone.Error
                    )
                }

                Button(
                    onClick = {
                        vm.startHosting()
                        nav.navigate(Routes.LOBBY)
                    },
                    enabled = ui.questions.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                ) { Text("Создать комнату") }
            }
        }
    }
}
