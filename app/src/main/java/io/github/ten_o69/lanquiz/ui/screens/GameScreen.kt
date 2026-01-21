package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.data.QuestionKind
import io.github.ten_o69.lanquiz.vm.QuizViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val q = ui.currentQuestion

    var remaining by remember(q?.index) { mutableStateOf(q?.durationMs ?: 0L) }
    var answered = (ui.answeredForIndex == q?.index)

    LaunchedEffect(q?.index) {
        if (q == null) return@LaunchedEffect
        remaining = q.durationMs
        answered = false
        val step = 100L
        while (remaining > 0) {
            delay(step)
            remaining -= step
        }
    }

    LaunchedEffect(ui.stage) {
        if (ui.stage.name == "RESULTS") nav.navigate("results")
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Вопрос") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (q == null) {
                Text("Ждём вопрос...")
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }

            Text("Вопрос ${q.index + 1} / ${q.total}")
            LinearProgressIndicator(
                progress = { (remaining.toFloat() / q.durationMs.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            Text(q.text, style = MaterialTheme.typography.titleLarge)

            when (q.kind) {
                QuestionKind.YESNO -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !answered,
                            onClick = { answered = true; vm.answerYesNo(true) }
                        ) { Text("Да") }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !answered,
                            onClick = { answered = true; vm.answerYesNo(false) }
                        ) { Text("Нет") }
                    }
                }
                QuestionKind.MULTI -> {
                    q.options.forEachIndexed { idx, opt ->
                        OutlinedButton(
                            enabled = !answered,
                            onClick = { answered = true; vm.answerIndex(idx) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(opt) }
                    }
                }
            }

            if (ui.lastReveal != null && ui.lastReveal!!.questionIndex == q.index) {
                val r = ui.lastReveal!!
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ответ раскрыт", style = MaterialTheme.typography.titleMedium)
                        Text("Таблица обновлена (см. результаты после игры)")
                    }
                }
            }

            if (ui.error != null) Text(ui.error!!, color = MaterialTheme.colorScheme.error)
        }
    }
}