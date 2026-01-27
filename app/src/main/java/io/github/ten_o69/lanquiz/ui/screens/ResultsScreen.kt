package io.github.ten_o69.lanquiz.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.NeonBackground
import io.github.ten_o69.lanquiz.ui.NeonCard
import io.github.ten_o69.lanquiz.ui.components.HeroHeader
import io.github.ten_o69.lanquiz.ui.components.SectionHeader
import io.github.ten_o69.lanquiz.ui.theme.SuccessGreen
import io.github.ten_o69.lanquiz.vm.AppStage
import io.github.ten_o69.lanquiz.vm.QuizViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val board = ui.scoreboard
    val top3 = board.take(3)
    val rest = board.drop(3)

    var showFirst by remember { mutableStateOf(false) }
    var showSecond by remember { mutableStateOf(false) }
    var showThird by remember { mutableStateOf(false) }
    var showRest by remember { mutableStateOf(false) }

    LaunchedEffect(ui.stage) {
        if (ui.stage == AppStage.HOME) {
            nav.navigate("home") { popUpTo("home") { inclusive = true } }
        }
    }

    LaunchedEffect(top3, rest) {
        showFirst = false
        showSecond = false
        showThird = false
        showRest = false
        if (top3.isNotEmpty()) {
            delay(80)
            showFirst = true
            delay(120)
            showSecond = true
            delay(120)
            showThird = true
            delay(160)
            showRest = true
        } else {
            showRest = true
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
                        PodiumRow(
                            top3 = top3,
                            showFirst = showFirst,
                            showSecond = showSecond,
                            showThird = showThird
                        )
                    }
                }

                NeonCard(Modifier.fillMaxWidth().weight(1f)) {
                    SectionHeader(title = "Остальные", subtitle = "Все участники")
                    Spacer(Modifier.height(10.dp))
                    if (rest.isEmpty()) {
                        Text("Больше участников нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        AnimatedVisibility(
                            visible = showRest,
                            enter = fadeIn(tween(420)) + expandVertically(tween(420))
                        ) {
                            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                                itemsIndexed(rest) { idx, s ->
                                    ListItem(
                                        headlineContent = { Text("#${idx + 4}  ${s.name}") },
                                        supportingContent = { ScoreRow(correct = s.correct, wrong = s.wrong) }
                                    )
                                    HorizontalDivider()
                                }
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
private fun PodiumRow(
    top3: List<io.github.ten_o69.lanquiz.data.ScoreDto>,
    showFirst: Boolean,
    showSecond: Boolean,
    showThird: Boolean
) {
    val first = top3.getOrNull(0)
    val second = top3.getOrNull(1)
    val third = top3.getOrNull(2)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        AnimatedVisibility(
            visible = showSecond,
            enter = fadeIn(tween(360)) + slideInVertically(tween(360)) { it / 2 },
            modifier = Modifier.weight(1f)
        ) {
            PodiumTile(place = 2, score = second, height = 110.dp, modifier = Modifier.fillMaxWidth())
        }
        AnimatedVisibility(
            visible = showFirst,
            enter = fadeIn(tween(360)) + slideInVertically(tween(360)) { it / 2 },
            modifier = Modifier.weight(1f)
        ) {
            PodiumTile(place = 1, score = first, height = 150.dp, modifier = Modifier.fillMaxWidth())
        }
        AnimatedVisibility(
            visible = showThird,
            enter = fadeIn(tween(360)) + slideInVertically(tween(360)) { it / 2 },
            modifier = Modifier.weight(1f)
        ) {
            PodiumTile(place = 3, score = third, height = 95.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PodiumTile(
    place: Int,
    score: io.github.ten_o69.lanquiz.data.ScoreDto?,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val palette = podiumPalette(place, MaterialTheme.colorScheme.primary)
    Surface(
        modifier = modifier
            .padding(horizontal = 6.dp)
            .height(height),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(palette.container, palette.container.copy(alpha = 0.6f))
                    )
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(palette.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (place == 1) Icons.Outlined.EmojiEvents else Icons.Outlined.MilitaryTech,
                        contentDescription = null,
                        tint = palette.accent
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "#$place",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent
                )
                Spacer(Modifier.height(6.dp))
                if (score == null) {
                    Text("—", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(
                        score.name,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    ScoreRow(correct = score.correct, wrong = score.wrong)
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(correct: Int, wrong: Int) {
    val okColor = SuccessGreen
    val badColor = MaterialTheme.colorScheme.error
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = "Верно", tint = okColor)
            Text("$correct", style = MaterialTheme.typography.bodyMedium, color = okColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cancel, contentDescription = "Неверно", tint = badColor)
            Text("$wrong", style = MaterialTheme.typography.bodyMedium, color = badColor)
        }
    }
}

private data class PodiumPalette(
    val container: Color,
    val accent: Color
)

@Composable
private fun podiumPalette(place: Int, fallback: Color): PodiumPalette {
    return when (place) {
        1 -> PodiumPalette(container = Color(0xFFFFE7B3), accent = Color(0xFFD79A00))
        2 -> PodiumPalette(container = Color(0xFFDDE6F6), accent = Color(0xFF5F6B82))
        3 -> PodiumPalette(container = Color(0xFFF0D1B7), accent = Color(0xFF8B5E34))
        else -> PodiumPalette(container = MaterialTheme.colorScheme.surfaceVariant, accent = fallback)
    }
}
