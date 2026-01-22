package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.data.QuestionKind
import io.github.ten_o69.lanquiz.data.WsMsg
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.vm.AppRole
import io.github.ten_o69.lanquiz.vm.QuizViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val q = ui.currentQuestion

    var remaining by remember(q?.index) { mutableStateOf(q?.durationMs ?: 0L) }
    val answered = (ui.answeredForIndex == q?.index)
    val reveal = ui.lastReveal?.takeIf { it.questionIndex == q?.index }
    val timeExpired = q?.durationMs?.let { it > 0L && remaining <= 0L } ?: false
    val canAnswer = !answered && reveal == null && !timeExpired

    LaunchedEffect(q?.index, q?.durationMs) {
        if (q == null) return@LaunchedEffect
        remaining = q.durationMs
        if (q.durationMs <= 0L) return@LaunchedEffect
        val step = 100L
        while (remaining > 0) {
            delay(step)
            remaining = (remaining - step).coerceAtLeast(0L)
        }
    }

    LaunchedEffect(ui.stage) {
        if (ui.stage.name == "RESULTS") nav.navigate("results")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Вопрос") }) },
        bottomBar = {
            if (ui.role == AppRole.HOST && ui.manualAdvance) {
                BottomAppBar {
                    Button(
                        onClick = { vm.hostNext() },
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Text(if (reveal != null) "Далее" else "Открыть ответ")
                    }
                }
            }
        }
    ) { pad ->
        NeonBackground(contentPadding = pad) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (q == null) {
                    NeonCard(Modifier.fillMaxWidth()) {
                        Text("Ждём вопрос...", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    return@Column
                }

                NeonCard(Modifier.fillMaxWidth()) {
                    Text("Вопрос ${q.index + 1} / ${q.total}")
                    Spacer(Modifier.height(8.dp))
                    if (q.durationMs > 0L) {
                        LinearProgressIndicator(
                            progress = {
                                (remaining.toFloat() / q.durationMs.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Осталось: ${(remaining / 1000L).coerceAtLeast(0L)} сек")
                    } else {
                        Text("Ожидаем ведущего", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(q.text, style = MaterialTheme.typography.titleLarge)
                }

                when (q.kind) {
                    QuestionKind.YESNO -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = canAnswer,
                                onClick = { vm.answerYesNo(true) }
                            ) { Text("Да") }
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = canAnswer,
                                onClick = { vm.answerYesNo(false) }
                            ) { Text("Нет") }
                        }
                    }
                    QuestionKind.MULTI -> {
                        q.options.forEachIndexed { idx, opt ->
                            OutlinedButton(
                                enabled = canAnswer,
                                onClick = { vm.answerIndex(idx) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(opt) }
                        }
                    }
                }

                if (reveal != null) {
                    val answerText = when (val correct = reveal.correct) {
                        is WsMsg.CorrectPayload.Bool -> if (correct.value) "Да" else "Нет"
                        is WsMsg.CorrectPayload.Index -> q.options.getOrNull(correct.value) ?: "—"
                    }
                    NeonCard(Modifier.fillMaxWidth()) {
                        Text("Ответ", style = MaterialTheme.typography.titleMedium)
                        Text(answerText, style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (ui.error != null) Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
