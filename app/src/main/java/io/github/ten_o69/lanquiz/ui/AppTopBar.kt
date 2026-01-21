package io.github.ten_o69.lanquiz.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, vm: QuizViewModel) {
    val canUndo by vm.canUndo.collectAsState()
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(
                enabled = canUndo,
                onClick = { vm.undo() }
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Отмена")
            }
        }
    )
}