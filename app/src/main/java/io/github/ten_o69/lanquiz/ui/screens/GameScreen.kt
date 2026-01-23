package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.data.QuestionKind
import io.github.ten_o69.lanquiz.data.WsMsg
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.ui.components.BannerTone
import io.github.ten_o69.lanquiz.ui.components.HeroHeader
import io.github.ten_o69.lanquiz.ui.components.StatPill
import io.github.ten_o69.lanquiz.ui.components.StatusBanner
import io.github.ten_o69.lanquiz.vm.AppRole
import io.github.ten_o69.lanquiz.vm.AppStage
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
        if (ui.stage == AppStage.HOME) {
            nav.navigate("home") { popUpTo("home") { inclusive = true } }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Игра") }) },
        bottomBar = {
            if (ui.role == AppRole.HOST && ui.manualAdvance) {
                BottomAppBar {
                    Button(
                        onClick = { vm.hostNext() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .heightIn(min = 50.dp)
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroHeader(
                    title = "Вопрос",
                    subtitle = if (q != null) "Раунд ${q.index + 1} из ${q.total}" else "Ожидание ведущего"
                )

                if (q == null) {
                    NeonCard(Modifier.fillMaxWidth()) {
                        Text("Ждём вопрос...", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    return@Column
                }

                NeonCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatPill(text = "Вопрос ${q.index + 1}/${q.total}")
                        if (q.durationMs > 0L) {
                            StatPill(text = "⏱ ${(remaining / 1000L).coerceAtLeast(0L)} сек")
                        } else {
                            StatPill(text = "Без таймера")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (q.durationMs > 0L) {
                        LinearProgressIndicator(
                            progress = {
                                (remaining.toFloat() / q.durationMs.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(q.text, style = MaterialTheme.typography.titleLarge)
                    if (q.durationMs > 0L) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val progress =
                                (remaining.toFloat() / q.durationMs.toFloat()).coerceIn(0f, 1f)
                            CircularProgressIndicator(
                                progress = { progress },
                                strokeWidth = 6.dp,
                                modifier = Modifier.height(84.dp)
                            )
                            Text(
                                "${(remaining / 1000L).coerceAtLeast(0L)}",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }

                when (q.kind) {
                    QuestionKind.YESNO -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
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

                if (ui.error != null) {
                    StatusBanner(
                        text = ui.error!!,
                        tone = BannerTone.Error
                    )
                }
            }
        }
    }
}
