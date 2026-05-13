#!/usr/bin/env bash
# start-tablet-avd.sh — boot the headless `pixel_tablet` AVD profile for
# tablet master-detail verification (Phase 2.1.H).
#
# Sibling to start-avd.sh (which boots the medium_phone AVD); this script
# does the same plumbing for the 10" tablet target so two-pane layouts can
# be smoke-tested on the actual Medium/Expanded WindowSizeClass.
#
# Usage:
#   scripts/start-tablet-avd.sh                  # start AVD if not running, attach scrcpy
#   scripts/start-tablet-avd.sh --no-mirror      # start AVD only, skip scrcpy
#   scripts/start-tablet-avd.sh --kill           # stop the AVD (and scrcpy)
#
# Prereqs (one-time):
#   - SDK + emulator + system image installed
#   - AVD profile `pixel_tablet` created. Suggested:
#       avdmanager create avd \
#         --name pixel_tablet \
#         --device "pixel_tablet" \
#         --package "system-images;android-36;google_apis;x86_64"
#     Confirm the profile reports 1600x2560 mdpi (~160dpi) — this is the
#     10" tablet target the Round 2.1.H plan asks for.

set -euo pipefail

AVD_NAME="pixel_tablet"
EMULATOR_BIN="${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator"
# Tablet AVD lands on a different serial than the phone AVD; the second
# emulator booted concurrently is `emulator-5556` (the next even port pair).
# When this is the *only* emulator up, it will be `emulator-5554` again.
# We detect either, preferring whichever is currently advertised by adb.
SCRCPY_TITLE="strictlykeptboy-tablet-AVD"

action="${1:-start}"

detect_device() {
    local serials
    serials=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')
    # Prefer 5556 (second emulator) if both exist; otherwise the only one.
    if echo "$serials" | grep -q "emulator-5556"; then
        echo "emulator-5556"
    elif echo "$serials" | grep -q "emulator-5554"; then
        echo "emulator-5554"
    else
        echo ""
    fi
}

is_avd_running() {
    # Tablet runs on whatever port adb sees; we just check that *some*
    # emulator advertising the pixel_tablet AVD is up by querying the
    # emu console name on each connected emulator-* serial.
    local serials
    serials=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1}')
    for s in $serials; do
        local name
        name=$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')
        if [ "$name" = "$AVD_NAME" ]; then
            return 0
        fi
    done
    return 1
}

is_scrcpy_running() {
    pgrep -af "scrcpy.*${SCRCPY_TITLE}" >/dev/null 2>&1
}

start_avd() {
    if is_avd_running; then
        echo "[start-tablet-avd] AVD ${AVD_NAME} already running"
        return 0
    fi
    echo "[start-tablet-avd] booting ${AVD_NAME} headless..."
    "${EMULATOR_BIN}" \
        -avd "${AVD_NAME}" \
        -no-window -no-audio -no-snapshot -no-boot-anim \
        -gpu swiftshader_indirect \
        > /tmp/strictlykeptboy-tablet-emulator.log 2>&1 &
    echo "[start-tablet-avd] waiting for adb to see device..."
    until is_avd_running; do sleep 5; done
    local serial
    serial=$(detect_device)
    echo "[start-tablet-avd] waiting for boot to complete on ${serial}..."
    until [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 3
    done
    echo "[start-tablet-avd] booted on ${serial}: $(adb -s "$serial" shell getprop ro.build.version.release) (API $(adb -s "$serial" shell getprop ro.build.version.sdk))"
    echo "[start-tablet-avd] size: $(adb -s "$serial" shell wm size | tr -d '\r')"
    echo "[start-tablet-avd] density: $(adb -s "$serial" shell wm density | tr -d '\r')"
}

start_scrcpy() {
    if is_scrcpy_running; then
        echo "[start-tablet-avd] scrcpy already attached"
        return 0
    fi
    if ! command -v scrcpy >/dev/null 2>&1; then
        echo "[start-tablet-avd] scrcpy not installed — skipping mirror" >&2
        return 0
    fi
    local serial
    serial=$(detect_device)
    [ -z "$serial" ] && { echo "[start-tablet-avd] no device detected for scrcpy" >&2; return 1; }
    echo "[start-tablet-avd] attaching scrcpy to ${serial}..."
    scrcpy -s "${serial}" --no-audio --window-title="${SCRCPY_TITLE}" \
        > /tmp/strictlykeptboy-tablet-scrcpy.log 2>&1 &
    sleep 1
    if is_scrcpy_running; then
        echo "[start-tablet-avd] scrcpy window: ${SCRCPY_TITLE}"
    else
        echo "[start-tablet-avd] scrcpy failed — check /tmp/strictlykeptboy-tablet-scrcpy.log" >&2
    fi
}

stop_all() {
    if is_scrcpy_running; then
        echo "[start-tablet-avd] stopping scrcpy..."
        pkill -f "scrcpy.*${SCRCPY_TITLE}" || true
    fi
    if is_avd_running; then
        local serial
        serial=$(detect_device)
        echo "[start-tablet-avd] stopping AVD on ${serial}..."
        adb -s "${serial}" emu kill || true
        sleep 2
    fi
    echo "[start-tablet-avd] stopped"
}

case "${action}" in
    start)
        start_avd
        start_scrcpy
        ;;
    --no-mirror|--headless)
        start_avd
        ;;
    --kill|stop)
        stop_all
        ;;
    *)
        echo "usage: $0 [start|--no-mirror|--kill]" >&2
        exit 1
        ;;
esac
