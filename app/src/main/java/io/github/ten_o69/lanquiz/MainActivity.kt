package io.github.ten_o69.lanquiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ten_o69.lanquiz.ui.AppNav
import io.github.ten_o69.lanquiz.ui.theme.LanQuizTheme
import io.github.ten_o69.lanquiz.vm.QuizViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LanQuizTheme {
                val vm: QuizViewModel = viewModel()
                AppNav(vm)
            }
        }
    }
}