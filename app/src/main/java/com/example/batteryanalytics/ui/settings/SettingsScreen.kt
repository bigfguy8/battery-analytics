package com.example.batteryanalytics.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.data.prefs.Prefs
import com.example.batteryanalytics.data.export.Exporter
import com.example.batteryanalytics.data.repo.BatteryRepository
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors
import com.example.batteryanalytics.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    repository: BatteryRepository,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var samplingMs by remember { mutableStateOf(prefs.samplingIntervalMs) }
    var retentionFull by remember { mutableStateOf(prefs.retentionFullDays) }
    var retentionDown by remember { mutableStateOf(prefs.retentionDownsampleDays) }
    var backgroundEnabled by remember { mutableStateOf(prefs.backgroundMonitoringEnabled) }
    var ratedMahText by remember {
        mutableStateOf(prefs.ratedCapacityMah?.toString() ?: "")
    }

    var stats by remember { mutableStateOf<BatteryRepository.DbStats?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.example.batteryanalytics.service.BatteryMonitorService.start(context)
        } else {
            // Permission denied: turn the toggle back off and tell the user.
            backgroundEnabled = false
            prefs.backgroundMonitoringEnabled = false
            Toast.makeText(
                context,
                "Background monitoring needs notification permission.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    var confirmDelete by remember { mutableStateOf(false) }
    var deletedFlash by remember { mutableStateOf(false) }

    LaunchedEffect(deletedFlash) {
        stats = withContext(Dispatchers.IO) { repository.stats() }
    }

    confirmDelete.takeIf { it }?.let {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deletedFlash = !deletedFlash  // trigger LaunchedEffect refresh
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This removes every sample, session, rollup, and stored " +
                        "capability row. It cannot be undone."
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("\u2190 Back") }
        }
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.TextPrimary
        )

        // ---------- Sampling rate ----------
        SettingCard(
            title = "Sampling rate",
            subtitle = "How often the engine reads the sensors while the app is open."
        ) {
            val options = listOf(1_000L to "1 s", 2_000L to "2 s", 5_000L to "5 s",
                                 10_000L to "10 s", 30_000L to "30 s", 60_000L to "60 s")
            Row {
                for ((ms, label) in options) {
                    FilterChip(
                        selected = ms == samplingMs,
                        onClick = {
                            samplingMs = ms
                            prefs.samplingIntervalMs = ms
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Text(
                "Lower values give finer charts but cost battery. " +
                    "Changes take effect on the next tick.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }

        // ---------- Retention ----------
        SettingCard(
            title = "Raw sample retention",
            subtitle = "How long individual samples are kept at full resolution."
        ) {
            val options = listOf(7, 14, 30, 60, 90)
            Row {
                for (d in options) {
                    FilterChip(
                        selected = d == retentionFull,
                        onClick = {
                            retentionFull = d
                            prefs.retentionFullDays = d
                            // Bump downsample if it would become invalid
                            if (retentionDown <= d) {
                                retentionDown = d + 30
                                prefs.retentionDownsampleDays = retentionDown
                            }
                        },
                        label = { Text("$d d") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Older samples are downsampled to one per minute, then deleted at " +
                    "the horizon below. Sessions and rollups are kept forever.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }

        SettingCard(
            title = "Downsample horizon",
            subtitle = "Age beyond which raw samples are deleted after downsampling."
        ) {
            val options = listOf(30, 44, 90, 180, 365)
            Row {
                for (d in options.filter { it > retentionFull }) {
                    FilterChip(
                        selected = d == retentionDown,
                        onClick = {
                            retentionDown = d
                            prefs.retentionDownsampleDays = d
                        },
                        label = { Text("$d d") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
        }

        // ---------- Background monitoring ----------
        SettingCard(
            title = "Background monitoring",
            subtitle = "Sample while the app is not in the foreground."
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (backgroundEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = backgroundEnabled,
                    onCheckedChange = { wants ->
                        if (!wants) {
                            backgroundEnabled = false
                            prefs.backgroundMonitoringEnabled = false
                            com.example.batteryanalytics.service.BatteryMonitorService.stop(context)
                            return@Switch
                        }
                        // Enabling: request POST_NOTIFICATIONS first on API 33+.
                        if (Build.VERSION.SDK_INT >= 33) {
                            val granted = context.checkSelfPermission(
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                backgroundEnabled = true
                                prefs.backgroundMonitoringEnabled = true
                                com.example.batteryanalytics.service.BatteryMonitorService.start(context)
                            } else {
                                notifPermissionLauncher.launch(
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        } else {
                            backgroundEnabled = true
                            prefs.backgroundMonitoringEnabled = true
                            com.example.batteryanalytics.service.BatteryMonitorService.start(context)
                        }
                    }
                )
            }
            Text(
                "When enabled, the app keeps sampling while it is not on screen, " +
                    "and shows a persistent notification. Android requires the " +
                    "notification for any app that samples in the background. " +
                    "No data leaves the device.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }

        // ---------- Storage stats + delete ----------
        SettingCard(
            title = "Stored data",
            subtitle = "What this app currently keeps on this device."
        ) {
            val s = stats
            if (s == null) {
                Text("Reading\u2026", color = GlassColors.TextSecondary)
            } else {
                StatLine("Samples", s.sampleCount.toString())
                StatLine("Sessions", s.sessionCount.toString())
                StatLine("Daily rollups", s.rollupCount.toString())
                StatLine("Capability rows", s.capabilityCount.toString())
                s.oldestSampleTs?.let {
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    StatLine("Oldest sample", fmt.format(Date(it)))
                }
                s.newestSampleTs?.let {
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    StatLine("Newest sample", fmt.format(Date(it)))
                }
            }
            Spacer(Modifier.height(8.dp))
            DeleteButton(onClick = {
                confirmDelete = true
                // perform the deletion as soon as the dialog confirms
            }, onConfirmed = {
                // not used; kept for API symmetry
            })
        }

        // ---------- Rated capacity (user-supplied) ----------
        SettingCard(
            title = "Rated battery capacity",
            subtitle = "User-supplied. Not read from this device \u2014 Android does not expose it."
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = ratedMahText,
                    onValueChange = { new ->
                        // digits only, at most 5 chars
                        val cleaned = new.filter { it.isDigit() }.take(5)
                        ratedMahText = cleaned
                        val v = cleaned.toIntOrNull()
                        if (v != null && v >= 500) {
                            prefs.ratedCapacityMah = v
                        } else if (cleaned.isEmpty()) {
                            prefs.ratedCapacityMah = null
                        }
                    },
                    label = { Text("mAh") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "If you enter a value, it will be shown on the Health screen as " +
                    "\"Rated capacity (user-supplied)\" and used as a stand-in for " +
                    "design capacity. Health percentages computed against it carry " +
                    "LOW confidence by design.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }

        // ---------- Export ----------
        SettingCard(
            title = "Export",
            subtitle = "Write CSV and JSON to app storage, then share via any app."
        ) {
            ExportButton(
                label = "Export all (CSV + JSON)",
                onClick = {
                    val appCtx = context.applicationContext
                    val exporter = Exporter(appCtx)
                    val result = exporter.exportAll(
                        repository = repository,
                        latest = null,   // snapshot not reachable from Settings; export still valid
                        appVersion = "0.5.0-phase5"
                    )
                    val uri = result.jsonUri(appCtx)
                    val intent = exporter.shareIntentFor(uri, "application/json")
                    context.startActivity(intent)
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Files are written to the app\'s private storage. Only you can " +
                    "move them out, via the share sheet. No storage permission " +
                    "is requested.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }

        // ---------- Diagnostics ----------
        SettingCard(
            title = "Diagnostics",
            subtitle = "What this device exposes and how each metric is obtained."
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenDiagnostics),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Open device capability report",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text("\u2192", color = GlassColors.TextSecondary)
            }
        }

        // ---------- About ----------
        SettingCard(
            title = "About",
            subtitle = "No account. No cloud. No network permission."
        ) {
            Text(
                "All data stays in app-private storage on this device. " +
                    "No permissions beyond those strictly required to read battery state.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }
    }

    // Perform actual deletion once the user confirms
    LaunchedEffect(confirmDelete, deletedFlash) {
        if (!confirmDelete && deletedFlash) {
            withContext(Dispatchers.IO) {
                repository.deleteAllData()
            }
            deletedFlash = false
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = GlassColors.TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextTertiary
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExportButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(Palette.ChipBlue.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Palette.ChipBlue)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Palette.ChipBlue
        )
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit, onConfirmed: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(Palette.ChipRose.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Palette.ChipRose)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Delete all data",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Palette.ChipRose
        )
    }
}
