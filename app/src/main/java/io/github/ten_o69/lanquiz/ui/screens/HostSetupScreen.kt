package io.github.ten_o69.lanquiz.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.ui.Routes
import io.github.ten_o69.lanquiz.ui.components.*
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostSetupScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(vm::importQuestions)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Создание комнаты") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    vm.startHosting()
                    nav.navigate(Routes.LOBBY)
                },
                enabled = ui.questions.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(min = 54.dp)
            ) {
                Text("Создать комнату")
            }
        }
    ) { pad ->
        NeonBackground(contentPadding = pad) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {

                item {
                    HeroHeader(
                        title = "Создание комнаты",
                        subtitle = "Подготовь вопросы и правила игры"
                    )
                }

                item {
                    NeonCard(
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        SectionHeader(
                            title = "Вопросы",
                            subtitle = "JSON, TXT или XLSX"
                        )

                        Spacer(Modifier.height(10.dp))

                        StatPill(text = "Загружено: ${ui.questions.size}")

                        AnimatedVisibility(
                            visible = ui.questions.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                ui.questions.forEachIndexed { i, q ->
                                    Text(
                                        text = "${i + 1}. ${q.text}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Импортировать (JSON/TXT/XLSX)")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = vm::clearQuestions,
                            enabled = ui.questions.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Сбросить вопросы")
                        }
                    }
                }

                item {
                    NeonCard(
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        SectionHeader(
                            title = "Правила",
                            subtitle = "Режим ведущего и таймер"
                        )

                        Spacer(Modifier.height(10.dp))

                        RuleSwitch(
                            title = "Ручной режим",
                            subtitle = "Хост управляет вопросами",
                            checked = ui.manualAdvance,
                            onChecked = vm::setManualAdvance
                        )

                        Spacer(Modifier.height(10.dp))

                        RuleSwitch(
                            title = "Таймер",
                            subtitle = if (ui.timerEnabled)
                                "Отсчёт времени активен"
                            else "Без таймера",
                            checked = ui.timerEnabled,
                            onChecked = vm::setTimerEnabled
                        )

                        AnimatedVisibility(
                            visible = ui.timerEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Text("Длительность: ${ui.timerSeconds} сек")
                                Slider(
                                    value = ui.timerSeconds.toFloat(),
                                    onValueChange = { vm.setTimerSeconds(it.toInt()) },
                                    valueRange = 5f..60f,
                                    steps = 10
                                )
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = ui.error != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        StatusBanner(
                            text = ui.error.orEmpty(),
                            tone = BannerTone.Error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
