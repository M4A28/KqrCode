package com.mohammed.mosa.qrscanner



import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import com.mohammed.mosa.qrscanner.data.AppSettings
import com.mohammed.mosa.qrscanner.data.ScanRepository
import com.mohammed.mosa.qrscanner.data.SettingsRepository
import com.mohammed.mosa.qrscanner.generate.GeneratorScreen
import com.mohammed.mosa.qrscanner.history.HistoryScreen
import com.mohammed.mosa.qrscanner.scanner.ScannerScreen
import com.mohammed.mosa.qrscanner.settings.SettingsScreen
import com.mohammed.mosa.qrscanner.ui.theme.*

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Scan(R.string.tab_scan, Icons.Rounded.QrCodeScanner),
    Create(R.string.tab_create, Icons.Rounded.QrCode2),
    History(R.string.tab_history, Icons.Rounded.History),
    Settings(R.string.tab_settings, Icons.Rounded.Settings),
}

class MainActivity : ComponentActivity() {

    /** Splash stays up until the saved theme/accent has actually loaded. */
    private val appReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !appReady.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Launched via "share → QR Scanner" with an image: decode it in the Scan tab.
        @Suppress("DEPRECATION")
        val sharedImage: Uri? = intent?.getParcelableExtra(Intent.EXTRA_STREAM)

        setContent {
            val repository = remember { ScanRepository.get(applicationContext) }
            val settingsRepo = remember { SettingsRepository.get(applicationContext) }
            val settings by settingsRepo.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            // Reveal the app only after the real settings emission, so the
            // saved theme and accent are applied behind the splash — no flicker.
            LaunchedEffect(Unit) {
                settingsRepo.settings.first()
                appReady.value = true
            }

            LaunchedEffect(settings.accent) {
                Brand = runCatching {
                    Color(android.graphics.Color.parseColor(settings.accent))
                }.getOrDefault(Color(0xFF7C5CFC))
            }

            QrScannerTheme(settings.themeMode) {
                val pc = PC
                DisposableEffect(pc.isDark) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !pc.isDark
                    controller.isAppearanceLightNavigationBars = !pc.isDark
                    onDispose { }
                }

                var tab by rememberSaveable { mutableStateOf(Tab.Scan) }
                var pendingImage by remember { mutableStateOf(sharedImage) }

                Column(Modifier.fillMaxSize().background(pc.bg)) {
                    Box(Modifier.weight(1f)) {
                        Crossfade(tab, animationSpec = tween(Ui.duration, easing = Ui.easing)) { t ->
                            when (t) {
                                Tab.Scan -> ScannerScreen(
                                    repository,
                                    incomingImage = pendingImage,
                                    onIncomingImageHandled = { pendingImage = null },
                                )
                                Tab.Create -> GeneratorScreen(repository)
                                Tab.History -> HistoryScreen(repository)
                                Tab.Settings -> SettingsScreen(repository, settingsRepo)
                            }
                        }
                    }
                    NavigationBar(containerColor = pc.navBar, tonalElevation = 0.dp) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = stringResource(t.labelRes)) },
                                label = { Text(stringResource(t.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Brand,
                                    selectedTextColor = Brand,
                                    indicatorColor = Brand.copy(alpha = 0.2f),
                                    unselectedIconColor = pc.text3,
                                    unselectedTextColor = pc.text3,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
