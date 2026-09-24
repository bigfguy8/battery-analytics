package com.example.batteryanalytics.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.batteryanalytics.data.repo.BatteryRepository
import com.example.batteryanalytics.domain.model.BatterySnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes CSV and JSON into app-private storage (`filesDir/exports/`) and
 * returns shareable content URIs via FileProvider.
 *
 * No external storage permission is needed. No file is written outside the
 * app sandbox. The user is the only one who can move the file out, via the
 * share sheet.
 */
class Exporter(private val context: Context) {

    data class Result(
        val samplesCsv: File,
        val sessionsCsv: File,
        val fullJson: File
    ) {
        fun sampleUri(appContext: Context): Uri =
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", samplesCsv)
        fun sessionsUri(appContext: Context): Uri =
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", sessionsCsv)
        fun jsonUri(appContext: Context): Uri =
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", fullJson)
    }

    private fun exportsDir(): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun exportAll(
        repository: BatteryRepository,
        latest: BatterySnapshot?,
        appVersion: String
    ): Result {
        val dir = exportsDir()
        val st = stamp()

        val samples = repository.recentSamples(limit = 200_000)
        val sessions = repository.recentSessions(limit = 10_000)
        val capabilities = repository.capabilities()

        val samplesCsv = File(dir, "samples_${st}.csv")
        samplesCsv.bufferedWriter().use { w ->
            CsvWriter.writeSamples(w, samples)
        }

        val sessionsCsv = File(dir, "sessions_${st}.csv")
        sessionsCsv.bufferedWriter().use { w ->
            CsvWriter.writeSessions(w, sessions)
        }

        val fullJson = File(dir, "battery_${st}.json")
        fullJson.bufferedWriter().use { w ->
            JsonWriter.writeAll(
                w = w,
                samples = samples,
                sessions = sessions,
                capabilities = capabilities,
                latest = latest,
                appVersion = appVersion,
                nowMs = System.currentTimeMillis()
            )
        }

        // Cleanup old exports: keep only the 5 most recent of each type.
        pruneOldExports(dir, keepPerType = 5)

        return Result(samplesCsv, sessionsCsv, fullJson)
    }

    fun shareIntentFor(uri: Uri, mimeType: String): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Share export")
    }

    private fun pruneOldExports(dir: File, keepPerType: Int) {
        val groups = dir.listFiles()
            ?.filter { it.isFile }
            ?.groupBy { f ->
                when {
                    f.name.startsWith("samples_") -> "samples"
                    f.name.startsWith("sessions_") -> "sessions"
                    f.name.startsWith("battery_") -> "json"
                    else -> "other"
                }
            } ?: return
        for ((_, files) in groups) {
            files.sortedByDescending { it.lastModified() }
                .drop(keepPerType)
                .forEach { it.delete() }
        }
    }
}
