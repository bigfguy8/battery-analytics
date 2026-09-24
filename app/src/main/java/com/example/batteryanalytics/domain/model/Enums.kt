package com.example.batteryanalytics.domain.model

/**
 * Where a metric value came from. Every Metric carries one of these.
 * Never present an ESTIMATED or CALCULATED value as if it were a direct measurement.
 */
enum class Source {
    API,          // read directly from an Android framework API
    SYSFS,        // read directly from /sys/class/power_supply or equivalent
    CALCULATED,   // arithmetic on two or more direct reads (e.g. P = V x I)
    ESTIMATED,    // derived from history / regression
    UNAVAILABLE   // device did not expose it, or source could not be read
}

/** Confidence in a metric. HIGH for direct API/sysfs reads, MEDIUM for heuristic
 *  unit detection, LOW for weak inference, UNAVAILABLE when no value exists. */
enum class Confidence { HIGH, MEDIUM, LOW, UNAVAILABLE }

/** Unit of a normalized (SI) value. label is what the UI prints. */
enum class Unit(val label: String) {
    PERCENT("%"),
    VOLT("V"),
    AMP("A"),
    WATT("W"),
    WATTHOUR("Wh"),
    AMPHOUR("Ah"),
    CELSIUS("\u00B0C"),
    COUNT(""),
    NONE("")
}

/** Anything that wants a pretty print name implements this. */
interface Labelled { val label: String }

enum class ChargeStatus(override val label: String) : Labelled {
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    NOT_CHARGING("Not charging"),
    FULL("Full"),
    UNKNOWN("Unknown")
}

enum class PlugType(override val label: String) : Labelled {
    NONE("Unplugged"),
    AC("AC"),
    USB("USB"),
    WIRELESS("Wireless"),
    DOCK("Dock"),
    UNKNOWN("Unknown")
}

enum class BatteryHealth(override val label: String) : Labelled {
    UNKNOWN("Unknown"),
    GOOD("Good"),
    OVERHEAT("Overheat"),
    DEAD("Dead"),
    OVER_VOLTAGE("Over voltage"),
    UNSPECIFIED_FAILURE("Unspecified failure"),
    COLD("Cold")
}
