package com.example.batteryanalytics.data.source

import java.io.File

/**
 * Enumerates every node under /sys/class/power_supply/ (does NOT assume "battery/"
 * exists), reads every interesting file from every node, and keeps the raw strings.
 *
 * Read failures (permission denied on Android 11+, missing files) return null.
 * This source never invents a value; if nothing is readable, it returns empty lists.
 */
class SysfsPowerSupplySource(private val rootPath: String = "/sys/class/power_supply") {

    data class NodeReadings(
        val nodeName: String,
        val nodePath: String,
        val nodeType: String?,              // "Battery", "USB", "AC", "Mains", "Wireless", etc.
        val files: Map<String, String>      // filename -> raw first-line content
    )

    data class Readings(
        val batteries: List<NodeReadings>,
        val allNodes: List<NodeReadings>
    ) {
        val primaryBattery: NodeReadings? get() = batteries.firstOrNull()
    }

    fun read(): Readings {
        val root = File(rootPath)
        if (!root.isDirectory || !root.canRead()) return Readings(emptyList(), emptyList())

        val entries = try { root.listFiles() } catch (_: Throwable) { null }
            ?: return Readings(emptyList(), emptyList())

        val nodes = mutableListOf<NodeReadings>()
        for (entry in entries.sortedBy { it.name }) {
            if (!entry.isDirectory) continue
            val type = readFile(entry, "type")
            val files = mutableMapOf<String, String>()
            for (name in INTERESTING_FILES) {
                readFile(entry, name)?.let { files[name] = it }
            }
            nodes += NodeReadings(
                nodeName = entry.name,
                nodePath = entry.absolutePath,
                nodeType = type,
                files = files
            )
        }
        val batteries = nodes.filter { it.nodeType == "Battery" || it.nodeType == "UPS" }
        return Readings(batteries, nodes)
    }

    private fun readFile(dir: File, name: String): String? {
        val f = File(dir, name)
        if (!f.isFile || !f.canRead()) return null
        return try {
            f.readText().trim().takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        val INTERESTING_FILES: List<String> = listOf(
            "capacity", "capacity_level",
            "charge_counter", "charge_full", "charge_full_design",
            "current_now", "current_avg",
            "energy_counter", "energy_full", "energy_full_design",
            "health", "status", "temp", "technology", "type",
            "uevent", "voltage_now", "power_now", "cycle_count"
        )
    }
}
