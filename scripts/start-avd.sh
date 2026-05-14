#!/usr/bin/env bash
# start-avd.sh — boot the headless `medium_phone` AVD and mirror it via scrcpy.
#
# Usage:
#   scripts/start-avd.sh                  # start AVD if not running, then attach scrcpy
#   scripts/start-avd.sh --no-mirror      # start AVD only, skip scrcpy
#   scripts/start-avd.sh --kill           # stop the AVD (and scrcpy)
#   scripts/start-avd.sh --tablet [...]   # delegate to scripts/start-tablet-avd.sh (Phase 2.1.H)
#
# Prereqs (one-time):
#   - Android CLI 0.7+ installed at ~/.local/bin/android (see README)
#   - SDK + emulator + system image (`android sdk install ...`)
#   - AVD profile created (`android emulator create --profile=medium_phone`)
#   - scrcpy installed (`sudo pacman -S scrcpy` on Arch, or equivalent)

set -euo pipefail

AVD_NAME="medium_phone"
EMULATOR_BIN="${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator"
ADB_DEVICE="emulator-5554"
SCRCPY_TITLE="strictlykeptboy-AVD"

action="${1:-start}"

# Phase 2.1.H — `--tablet` delegates to the tablet-AVD sibling so callers
# can fork a 10" tablet (pixel_tablet 1600×2560 mdpi) for two-pane testing
# without remembering a second script name. Pass-through any extra args.
if [ "${action}" = "--tablet" ]; then
    shift || true
    exec "$(dirname "$0")/start-tablet-avd.sh" "$@"
fi

is_avd_running() {
    adb devices 2>/dev/null | grep -qE "^${ADB_DEVICE}\s+device$"
}

is_scrcpy_running() {
    pgrep -af "scrcpy.*${SCRCPY_TITLE}" >/dev/null 2>&1
}

start_avd() {
    if is_avd_running; then
        echo "[start-avd] AVD already running on ${ADB_DEVICE}"
        return 0
    fi
    echo "[start-avd] booting ${AVD_NAME} headless..."
    "${EMULATOR_BIN}" \
        -avd "${AVD_NAME}" \
        -no-window -no-audio -no-snapshot -no-boot-anim \
        -gpu swiftshader_indirect \
        > /tmp/strictlykeptboy-emulator.log 2>&1 &
    echo "[start-avd] waiting for adb to see device..."
    until is_avd_running; do sleep 5; done
    echo "[start-avd] waiting for boot to complete..."
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 3
    done
    echo "[start-avd] booted: $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))"
}

start_scrcpy() {
    if is_scrcpy_running; then
        echo "[start-avd] scrcpy already attached"
        return 0
    fi
    if ! command -v scrcpy >/dev/null 2>&1; then
        echo "[start-avd] scrcpy not installed — skipping mirror" >&2
        echo "[start-avd] install with: sudo pacman -S scrcpy" >&2
        return 0
    fi
    echo "[start-avd] attaching scrcpy..."
    scrcpy -s "${ADB_DEVICE}" --no-audio --window-title="${SCRCPY_TITLE}" \
        > /tmp/strictlykeptboy-scrcpy.log 2>&1 &
    sleep 1
    if is_scrcpy_running; then
        echo "[start-avd] scrcpy window: ${SCRCPY_TITLE}"
    else
        echo "[start-avd] scrcpy failed to launch — check /tmp/strictlykeptboy-scrcpy.log" >&2
    fi
}

stop_all() {
    if is_scrcpy_running; then
        echo "[start-avd] stopping scrcpy..."
        pkill -f "scrcpy.*${SCRCPY_TITLE}" || true
    fi
    if is_avd_running; then
        echo "[start-avd] stopping AVD..."
        adb -s "${ADB_DEVICE}" emu kill || true
        sleep 2
    fi
    echo "[start-avd] stopped"
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
