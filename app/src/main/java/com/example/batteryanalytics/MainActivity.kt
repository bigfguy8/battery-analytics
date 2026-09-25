package com.example.batteryanalytics

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.painterResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.batteryanalytics.data.prefs.Prefs
import com.example.batteryanalytics.service.BatteryMonitorService
import com.example.batteryanalytics.service.SamplingController
import com.example.batteryanalytics.ui.capabilities.CapabilitiesScreen
import com.example.batteryanalytics.ui.dashboard.DashboardScreen
import com.example.batteryanalytics.ui.health.HealthScreen
import com.example.batteryanalytics.ui.history.HistoryScreen
import com.example.batteryanalytics.ui.sessions.SessionsScreen
import com.example.batteryanalytics.ui.settings.SettingsScreen
import com.example.batteryanalytics.ui.theme.BatteryAnalyticsTheme
import com.example.batteryanalytics.ui.theme.GlassColors
import com.example.batteryanalytics.ui.theme.Palette

private enum class Tab(
    val label: String,
    val accent: ComposeColor,
    @DrawableRes val icon: Int
) {
    DASHBOARD("Dash",     Palette.NavDash,     R.drawable.ic_tab_dash),
    HISTORY("History",    Palette.NavHistory,  R.drawable.ic_tab_history),
    SESSIONS("Sessions",  Palette.NavSessions, R.drawable.ic_tab_sessions),
    HEALTH("Health",      Palette.ChipGreen,   R.drawable.ic_tab_health)
}

class MainActivity : ComponentActivity() {

    private lateinit var controller: SamplingController
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        controller = SamplingController.get(applicationContext)
        prefs = Prefs(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            BatteryAnalyticsTheme {
                AppRoot(controller)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (prefs.backgroundMonitoringEnabled) {
            BatteryMonitorService.start(this)
        }
        controller.onUiForeground()
    }

    override fun onStop() {
        super.onStop()
        controller.onUiBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.onUiBackground()
    }
}

@Composable
private fun AppRoot(controller: SamplingController) {
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var showSettings by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    BackHandler(enabled = showDiagnostics) { showDiagnostics = false }
    BackHandler(enabled = showSettings && !showDiagnostics) { showSettings = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GlassColors.BaseBackgroundTop, GlassColors.BaseBackgroundBottom)
                )
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Palette.BlobTopLeft.copy(alpha = 0.55f),
                            ComposeColor.Transparent
                        ),
                        radius = 900f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Palette.BlobBottomRight.copy(alpha = 0.40f),
                            ComposeColor.Transparent
                        ),
                        radius = 1100f
                    )
                )
        )

        when {
            showDiagnostics -> CapabilitiesScreen()
            showSettings -> SettingsScreen(
                repository = controller.repository(),
                onBack = { showSettings = false },
                onOpenDiagnostics = { showDiagnostics = true }
            )
            else -> Scaffold(
                containerColor = ComposeColor.Transparent,
                bottomBar = {
                    NavigationBar(
                        containerColor = GlassColors.GlassTint,
                        contentColor = GlassColors.TextPrimary
                    ) {
                        NavItem(tab == Tab.DASHBOARD, Tab.DASHBOARD) { tab = Tab.DASHBOARD }
                        NavItem(tab == Tab.HISTORY, Tab.HISTORY) { tab = Tab.HISTORY }
                        NavItem(tab == Tab.SESSIONS, Tab.SESSIONS) { tab = Tab.SESSIONS }
                        NavItem(tab == Tab.HEALTH, Tab.HEALTH) { tab = Tab.HEALTH }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        Tab.DASHBOARD -> DashboardScreen(
                            controller.snapshots,
                            controller.repository(),
                            onOpenSettings = { showSettings = true }
                        )
                        Tab.HISTORY -> HistoryScreen(controller.repository())
                        Tab.SESSIONS -> SessionsScreen(controller.repository())
                        Tab.HEALTH -> HealthScreen(controller.repository(), controller.snapshots)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    tab: Tab,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painter = painterResource(tab.icon),
                contentDescription = tab.label
            )
        },
        label = { Text(tab.label, maxLines = 1) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = tab.accent,
            selectedTextColor = tab.accent,
            indicatorColor = tab.accent.copy(alpha = 0.18f),
            unselectedTextColor = GlassColors.TextSecondary
        )
    )
}
