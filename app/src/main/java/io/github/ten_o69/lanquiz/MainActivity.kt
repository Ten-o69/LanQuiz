package io.github.ten_o69.lanquiz

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

                // ✅ один раз при входе: проверка Wi-Fi + (опционально) запрос NEARBY_WIFI_DEVICES
                StartupChecks(
                    requireWifi = true,
                    requestNearbyWifiPermission = true
                )

                AppNav(vm)
            }
        }
    }
}

/**
 * Проверки/разрешения, которые удобно делать при запуске приложения.
 *
 * - requireWifi: если true, покажем диалог, когда устройство не в Wi-Fi сети
 * - requestNearbyWifiPermission: если true, на Android 13+ попросим NEARBY_WIFI_DEVICES
 *   (если ты используешь nearby/wifi discovery и хочешь перестраховаться)
 */
@Composable
private fun StartupChecks(
    requireWifi: Boolean,
    requestNearbyWifiPermission: Boolean
) {
    val ctx = LocalContext.current

    // --- 1) Диалог про Wi-Fi ---
    var showWifiDialog by remember { mutableStateOf(false) }

    // Обновляем при первом запуске + когда возвращаемся в app (простая версия: периодическая проверка)
    LaunchedEffect(requireWifi) {
        if (requireWifi) {
            showWifiDialog = !isOnWifi(ctx)
        }
    }

    if (showWifiDialog) {
        AlertDialog(
            onDismissRequest = { /* можно не давать закрыть, но UX лучше позволить */ },
            title = { Text("Нужен Wi-Fi") },
            text = { Text("Для игры по локальной сети подключись к Wi-Fi (или включи хотспот).") },
            confirmButton = {
                Button(onClick = { openInternetSettings(ctx) }) { Text("Открыть настройки") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWifiDialog = false }) { Text("Позже") }
            }
        )
    }

    // --- 2) Runtime permission (Android 13+) ---
    if (requestNearbyWifiPermission && Build.VERSION.SDK_INT >= 33) {
        val perm = Manifest.permission.NEARBY_WIFI_DEVICES
        val granted = remember {
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* granted -> можно сохранить флаг/показать подсказку */ }

        LaunchedEffect(Unit) {
            if (!granted) {
                launcher.launch(perm)
            }
        }
    }
}

// --- helpers ---

private fun isOnWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

private fun openInternetSettings(context: Context) {
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}