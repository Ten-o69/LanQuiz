package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val board = ui.scoreboard
    val top3 = board.take(3)
    val rest = board.drop(3)

    Scaffold(topBar = { TopAppBar(title = { Text("Результаты") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text("Топ 3", style = MaterialTheme.typography.titleLarge)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                top3.forEachIndexed { i, s ->
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("#${i + 1}", style = MaterialTheme.typography.titleMedium)
                            Text(s.name, style = MaterialTheme.typography.titleLarge)
                            Text("✅ ${s.correct}   ❌ ${s.wrong}")
                        }
                    }
                }
            }

            Text("Остальные", style = MaterialTheme.typography.titleMedium)

            // “Серое окно” снизу
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                LazyColumn {
                    itemsIndexed(rest) { idx, s ->
                        ListItem(
                            headlineContent = { Text("#${idx + 4}  ${s.name}") },
                            supportingContent = { Text("✅ ${s.correct}   ❌ ${s.wrong}") }
                        )
                        Divider()
                    }
                }
            }

            Button(
                onClick = {
                    vm.stopAll()
                    nav.navigate("home") { popUpTo("home") { inclusive = true } }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Выйти") }
        }
    }
}