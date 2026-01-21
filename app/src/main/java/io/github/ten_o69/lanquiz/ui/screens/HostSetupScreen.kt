package io.github.ten_o69.lanquiz.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ten_o69.lanquiz.ui.Routes
import io.github.ten_o69.lanquiz.ui.AppTopBar
import io.github.ten_o69.lanquiz.vm.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostSetupScreen(vm: QuizViewModel, nav: NavController) {
    val ui by vm.ui.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importQuestions(uri)
    }

    Scaffold(topBar = { AppTopBar("Создание комнаты", vm) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Вопросов загружено: ${ui.questions.size}")

            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Импортировать вопросы (JSON/TXT/XLSX)") }

            if (ui.error != null) Text(ui.error!!, color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    vm.startHosting()
                    nav.navigate(Routes.LOBBY)
                },
                enabled = ui.questions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Создать комнату") }
        }
    }
}