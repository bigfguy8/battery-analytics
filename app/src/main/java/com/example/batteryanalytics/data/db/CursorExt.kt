package com.example.batteryanalytics.data.db

import android.database.Cursor

/**
 * Cursor accessors that respect the difference between "this column is null"
 * and "this column is 0". Every nullable domain field is read through these.
 */
internal fun Cursor.intOrNull(idx: Int): Int? = if (isNull(idx)) null else getInt(idx)
internal fun Cursor.longOrNull(idx: Int): Long? = if (isNull(idx)) null else getLong(idx)
internal fun Cursor.doubleOrNull(idx: Int): Double? = if (isNull(idx)) null else getDouble(idx)
internal fun Cursor.stringOrNull(idx: Int): String? = if (isNull(idx)) null else getString(idx)

internal fun Cursor.intOrNull(name: String): Int? = intOrNull(getColumnIndexOrThrow(name))
internal fun Cursor.longOrNull(name: String): Long? = longOrNull(getColumnIndexOrThrow(name))
internal fun Cursor.doubleOrNull(name: String): Double? = doubleOrNull(getColumnIndexOrThrow(name))
internal fun Cursor.stringOrNull(name: String): String? = stringOrNull(getColumnIndexOrThrow(name))
