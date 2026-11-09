#!/usr/bin/env bash
# Mounts the .dmg, starts the application from it and waits for its window, then stops it.
# Run on a macOS CI runner. A window's title needs the screen recording permission, which a runner
# does not grant, so the check looks for an on-screen window that the application's process owns.
set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo 'cannot check installer "arguments": expected one .dmg path' >&2
    exit 2
fi

mount_point=$(mktemp -d)
hdiutil attach -nobrowse -readonly -mountpoint "$mount_point" "$1" >/dev/null
log=$(mktemp)
pid=""
cleanup() {
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        kill "$pid"
        wait "$pid" 2>/dev/null || true
    fi
    cat "$log"
    hdiutil detach "$mount_point" >/dev/null
}
trap cleanup EXIT

app="$mount_point/Android Chrome Tabs Exporter.app"
launcher="$app/Contents/MacOS/Android Chrome Tabs Exporter"
if [ ! -x "$launcher" ]; then
    echo "cannot execute launcher \"$launcher\": expected the application bundle in the disk image" >&2
    exit 1
fi

script_dir=$(cd "$(dirname "$0")" && pwd)
window_check=$(mktemp)
swiftc -O -o "$window_check" "$script_dir/macos_window_of_process.swift"

home=$(mktemp -d)
JAVA_TOOL_OPTIONS="-Duser.home=$home" "$launcher" >"$log" 2>&1 &
pid=$!

for _ in $(seq 1 150); do
    if ! kill -0 "$pid" 2>/dev/null; then
        echo 'cannot start application "exited": the launcher stopped before its window opened' >&2
        exit 1
    fi
    if "$window_check" "$pid"; then
        if grep -q -e 'Exception' -e ' ERROR ' -e 'Unsupported JavaFX configuration' "$log"; then
            echo 'cannot accept startup log "exception or error": inspect the startup log' >&2
            exit 1
        fi
        echo "PASS: application window opened from the disk image"
        exit 0
    fi
    sleep 0.2
done
echo 'cannot find window "Android Chrome Tabs Exporter": no window of the application within 30 seconds' >&2
exit 1
