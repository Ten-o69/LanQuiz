package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val board = ui.scoreboard
    val top3 = board.take(3)
    val rest = board.drop(3)

    Scaffold(topBar = { TopAppBar(title = { Text("Результаты") }) }) { pad ->
        NeonBackground(contentPadding = pad) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeonCard(Modifier.fillMaxWidth()) {
                    Text("Топ 3", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (top3.isEmpty()) {
                        Text("Пока нет результатов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        top3.forEachIndexed { i, s ->
                            Text("#${i + 1}  ${s.name}", style = MaterialTheme.typography.titleLarge)
                            Text("✅ ${s.correct}   ❌ ${s.wrong}")
                            if (i != top3.lastIndex) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                NeonCard(Modifier.fillMaxWidth().weight(1f)) {
                    Text("Остальные", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (rest.isEmpty()) {
                        Text("Больше участников нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            itemsIndexed(rest) { idx, s ->
                                ListItem(
                                    headlineContent = { Text("#${idx + 4}  ${s.name}") },
                                    supportingContent = { Text("✅ ${s.correct}   ❌ ${s.wrong}") }
                                )
                                HorizontalDivider()
                            }
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
}
