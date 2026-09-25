#!/data/data/com.termux/files/usr/bin/bash
# Pre-push verification for Battery Analytics.
# Non-destructive: reads only, changes nothing.

cd "$(dirname "$0")/.."

pass=0
fail=0
check() {
    local name="$1" expected="$2" actual="$3"
    if [ "$actual" = "$expected" ]; then
        printf "  [OK]   %-40s = %s\n" "$name" "$actual"
        pass=$((pass+1))
    else
        printf "  [FAIL] %-40s expected %s, got %s\n" "$name" "$expected" "$actual"
        fail=$((fail+1))
    fi
}

echo
echo "=== Source hygiene ==="
check "secrets in repo"           "0" "$(git ls-files | grep -cE 'local\.properties|keystore\.properties|\.jks$|\.keystore$')"
check "INTERNET references"       "0" "$(grep -rc 'INTERNET' app/src/ 2>/dev/null | grep -v ':0$' | wc -l)"
check "analytics SDKs"            "0" "$(grep -rc 'Firebase\|Crashlytics\|Sentry\|Mixpanel' app/src/ app/build.gradle.kts 2>/dev/null | grep -v ':0$' | wc -l)"
check "android imports in domain" "0" "$(grep -rl '^import android\.' app/src/main/java/com/example/batteryanalytics/domain/ 2>/dev/null | wc -l)"
check "TODOs in source"           "0" "$(grep -rl 'TODO\|FIXME' app/src/main/java/ 2>/dev/null | wc -l)"
check "backup files staged"       "0" "$(git ls-files | grep -c '^\.backup/')"

echo
echo "=== New code wired ==="
check "smoothingWindow call sites" "3" "$(grep -c 'smoothingWindow =' app/src/main/java/com/example/batteryanalytics/ui/history/HistoryScreen.kt)"
check "MetricDetailDialog in Health" "2" "$(grep -c 'MetricDetailDialog' app/src/main/java/com/example/batteryanalytics/ui/health/HealthScreen.kt)"
check "dischargeAh > 0.0"         "1" "$(grep -c 'dischargeAh > 0.0' app/src/main/java/com/example/batteryanalytics/data/repo/BatteryRepository.kt)"
check "tab icons present"         "4" "$(ls app/src/main/res/drawable/ic_tab_*.xml 2>/dev/null | wc -l)"

echo
echo "=== Counts ==="
echo "  source files: $(find app/src/main/java -name '*.kt' | wc -l)"
echo "  test files:   $(find app/src/test -name '*.kt' | wc -l)"
echo "  docs files:   $(find docs -name '*.md' | wc -l)"

echo
echo "=== Working tree ==="
git status --short | head -30

echo
echo "=== Tests ==="
./gradlew :app:testDebugUnitTest 2>&1 | grep -E "tests completed|FAILED|BUILD SUCCESSFUL|BUILD FAILED"

echo
echo "=== APK permissions (must NOT list INTERNET) ==="
APK=app/build/outputs/apk/release/app-release.apk
if [ -f "$APK" ]; then
    "$PREFIX/bin/aapt2" dump permissions "$APK" 2>/dev/null | grep uses-permission || echo "  (none)"
    echo "  APK size: $(du -h "$APK" | cut -f1)"
else
    echo "  no release APK yet"
fi

echo
echo "==============================================="
printf "  %d passed, %d failed\n" "$pass" "$fail"
echo "==============================================="
echo

[ "$fail" = "0" ] && exit 0 || exit 1
