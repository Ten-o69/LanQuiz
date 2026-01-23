package io.github.ten_o69.lanquiz.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import io.github.ten_o69.lanquiz.vm.AppRole
import io.github.ten_o69.lanquiz.vm.AppStage
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    LaunchedEffect(ui.stage) {
        if (ui.stage.name == "GAME") nav.navigate(Routes.GAME)
        if (ui.stage.name == "RESULTS") nav.navigate(Routes.RESULTS)
        if (ui.stage == AppStage.HOME) {
            nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Лобби") }) },
        bottomBar = {
            if (ui.role == AppRole.HOST) {
                BottomAppBar {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp),
                            onClick = { vm.hostStartGame() }
                        ) { Text("Начать") }
                        OutlinedButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp),
                            onClick = {
                                vm.hostCancelGame()
                                nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                            }
                        ) { Text("Отменить") }
                    }
                }
            }
        }
    ) { pad ->
        NeonBackground(contentPadding = pad) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroHeader(
                    title = "Лобби",
                    subtitle = "Ожидаем игроков"
                )

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Комната",
                        subtitle = "Поделись кодом с игроками"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill(text = "Код: QUIZ-${ui.roomCode}")
                        StatPill(text = "Порт: ${ui.hostPort ?: "-"}")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString("QUIZ-${ui.roomCode}"))
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Скопировать код")
                        }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "LAN Quiz\nКод комнаты: QUIZ-${ui.roomCode}"
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(Intent.createChooser(intent, "Поделиться"))
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Поделиться")
                        }
                    }
                }

                NeonCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "Игроки",
                        subtitle = "В комнате: ${ui.players.size}"
                    )
                    Spacer(Modifier.height(8.dp))
                    if (ui.players.isEmpty()) {
                        Text(
                            "Пока никого нет",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(ui.players) {
                                ListItem(
                                    headlineContent = { Text(it.name) },
                                    supportingContent = { Text(it.id.take(8)) }
                                )
                                HorizontalDivider()
                            }
                        }
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
