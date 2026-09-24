# Battery Analytics — R8 rules.
#
# The app uses no reflection-based serialization, no DI framework, and no
# annotation processors. Everything R8 needs to keep is reachable from the
# manifest entry points (MainActivity, BatteryMonitorService) and from the
# Compose runtime's own consumer rules that AGP applies automatically.
#
# This file exists so that if a future change adds a reflective library,
# there is a documented place to add keep rules. Do not add wildcards
# "just in case" — they defeat R8.

# Keep our own source file names and line numbers in stack traces so a crash
# in the field can be mapped back to a real location without a mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin coroutines: internal names are looked up by the coroutine machinery.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Suppress warnings from optional dependencies we do not use. These are
# emitted by various AndroidX libraries that check for the presence of other
# libraries at runtime; the checks are guarded, so the warnings are noise.
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.coroutines.debug.**
