package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.ui.components.HeroHeader
import io.github.ten_o69.lanquiz.ui.components.SectionHeader
import io.github.ten_o69.lanquiz.vm.AppStage
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val board = ui.scoreboard
    val top3 = board.take(3)
    val rest = board.drop(3)

    LaunchedEffect(ui.stage) {
        if (ui.stage == AppStage.HOME) {
            nav.navigate("home") { popUpTo("home") { inclusive = true } }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Результаты") }) }) { pad ->
        NeonBackground(contentPadding = pad) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroHeader(
                    title = "Результаты",
                    subtitle = "Игра завершена"
                )

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(title = "Подиум", subtitle = "Топ 3 игроков")
                    Spacer(Modifier.height(12.dp))
                    if (top3.isEmpty()) {
                        Text("Пока нет результатов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        PodiumRow(top3)
                    }
                }

                NeonCard(Modifier.fillMaxWidth().weight(1f)) {
                    SectionHeader(title = "Остальные", subtitle = "Все участники")
                    Spacer(Modifier.height(10.dp))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                ) { Text("На главный экран") }
            }
        }
    }
}

@Composable
private fun PodiumRow(top3: List<io.github.ten_o69.lanquiz.data.ScoreDto>) {
    val first = top3.getOrNull(0)
    val second = top3.getOrNull(1)
    val third = top3.getOrNull(2)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        PodiumTile(place = 2, score = second, height = 110.dp, modifier = Modifier.weight(1f))
        PodiumTile(place = 1, score = first, height = 150.dp, modifier = Modifier.weight(1f))
        PodiumTile(place = 3, score = third, height = 95.dp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PodiumTile(
    place: Int,
    score: io.github.ten_o69.lanquiz.data.ScoreDto?,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 6.dp)
            .height(height),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("#$place", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                if (score == null) {
                    Text("—", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(score.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("✅ ${score.correct}  ❌ ${score.wrong}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
