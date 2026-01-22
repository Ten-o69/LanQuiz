package io.github.ten_o69.lanquiz

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ten_o69.lanquiz.ui.AppNav
import io.github.ten_o69.lanquiz.ui.theme.LanQuizTheme
import io.github.ten_o69.lanquiz.vm.QuizViewModel
import io.github.ten_o69.lanquiz.vm.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: QuizViewModel = viewModel()
            val ui by vm.ui.collectAsState()
            val darkTheme = when (ui.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            LanQuizTheme(darkTheme = darkTheme) {
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
    val activity = ctx as? Activity

    // --- 1) Диалог про Wi-Fi ---
    var showWifiDialog by remember { mutableStateOf(false) }

    DisposableEffect(requireWifi) {
        if (!requireWifi) {
            showWifiDialog = false
            return@DisposableEffect onDispose {}
        }

        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                showWifiDialog = !isLanAvailable(caps)
            }
            override fun onLost(network: Network) {
                showWifiDialog = !isLanAvailable(ctx)
            }
        }

        showWifiDialog = !isLanAvailable(ctx)
        cm.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
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
        var granted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
            )
        }
        var showPermissionDialog by remember { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            granted = isGranted
            showPermissionDialog = !isGranted
        }

        LaunchedEffect(Unit) {
            if (!granted) {
                launcher.launch(perm)
            }
        }

        if (!granted && showPermissionDialog && activity != null) {
            val rationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Нужно разрешение") },
                text = {
                    Text(
                        if (rationale) {
                            "Разрешение Nearby Wi-Fi Devices нужно для поиска комнат в локальной сети."
                        } else {
                            "Разрешение выключено. Открой настройки приложения и включи его для поиска комнат."
                        }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showPermissionDialog = false
                        if (rationale) launcher.launch(perm) else openAppSettings(ctx)
                    }) {
                        Text(if (rationale) "Разрешить" else "Открыть настройки")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showPermissionDialog = false }) { Text("Позже") }
                }
            )
        }
    }
}

// --- helpers ---

private fun isLanAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return isLanAvailable(caps)
}

private fun isLanAvailable(caps: NetworkCapabilities): Boolean {
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
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

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
