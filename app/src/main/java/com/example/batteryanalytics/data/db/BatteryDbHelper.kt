package com.example.batteryanalytics.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Single SQLiteOpenHelper for the whole app. No Room, no codegen, no KSP —
 * the schema is small and hand-written so aarch64 builds stay cheap.
 *
 * Journal mode is WAL for concurrent reads during sampling. Foreign keys are
 * enabled now even though v1 has no FK relations, so future migrations can add
 * them without changing the connection setup.
 *
 * Every column except primary keys and timestamps is NULLABLE. A null means
 * "we did not observe this"; the app never writes 0 to mean "unknown".
 */
class BatteryDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "battery.db"
        const val DB_VERSION = 1

        const val T_SAMPLES = "telemetry_samples"
        const val T_SESSIONS = "sessions"
        const val T_ROLLUPS = "daily_rollups"
        const val T_CAPABILITIES = "device_capabilities"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        setWriteAheadLoggingEnabled(true)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_SAMPLES (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                ts_ms         INTEGER NOT NULL,
                soc_pct       INTEGER,
                voltage_v     REAL,
                current_a     REAL,
                power_w       REAL,
                temp_c        REAL,
                status_txt    TEXT,
                plug_txt      TEXT,
                source_flags  INTEGER NOT NULL DEFAULT 0,
                quality_flags INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_samples_ts ON $T_SAMPLES(ts_ms)")
        db.execSQL("CREATE INDEX idx_samples_ts_soc ON $T_SAMPLES(ts_ms, soc_pct)")

        db.execSQL(
            """
            CREATE TABLE $T_SESSIONS (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                start_ts     INTEGER NOT NULL,
                end_ts       INTEGER,
                soc_start    INTEGER,
                soc_end      INTEGER,
                charge_ah    REAL,
                energy_wh    REAL,
                plug_type    TEXT,
                peak_power_w REAL,
                avg_power_w  REAL,
                temp_min_c   REAL,
                temp_mean_c  REAL,
                temp_max_c   REAL,
                taper_json   TEXT,
                quality      TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sessions_start ON $T_SESSIONS(start_ts DESC)")

        db.execSQL(
            """
            CREATE TABLE $T_ROLLUPS (
                date_yyyymmdd INTEGER PRIMARY KEY,
                min_soc       INTEGER,
                max_soc       INTEGER,
                avg_temp_c    REAL,
                discharge_ah  REAL,
                sample_count  INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $T_CAPABILITIES (
                metric_key     TEXT PRIMARY KEY,
                available      INTEGER NOT NULL,
                source         TEXT NOT NULL,
                unit           TEXT NOT NULL,
                confidence     TEXT NOT NULL,
                method         TEXT NOT NULL,
                first_seen_ts  INTEGER NOT NULL,
                last_probed_ts INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the initial schema. If you add a column or table in a future
        // version, append a `when (oldVersion) { ... }` chain here that
        // performs the migration incrementally. Do NOT drop tables — user
        // data must survive upgrades.
        //
        // Anything that reaches this point without a matching migration path
        // is a programming error, not a user error. Fail loudly rather than
        // silently continue with a schema that may not match the code.
        if (oldVersion != DB_VERSION) {
            throw IllegalStateException(
                "No migration path from schema v$oldVersion to v$newVersion. " +
                    "Update BatteryDbHelper.onUpgrade before releasing a schema change."
            )
        }
    }
}
