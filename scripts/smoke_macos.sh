#!/usr/bin/env bash
# Installs the application from the .dmg into /Applications the way a user drags it there, starts
# it and waits for its window, then stops it and removes it.
# Run on a macOS CI runner. A window's title needs the screen recording permission, which a runner
# does not grant, so the check looks for an on-screen window that the application's process owns.
# JavaFX logs "Timeout while waiting for app reactivation" on a runner because the runner's session
# never activates the application; the window still opens, so the log check does not fail on it.
set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo 'cannot check installer "arguments": expected one .dmg path' >&2
    exit 2
fi

app_name="Android Chrome Tabs Exporter.app"
app="/Applications/$app_name"
if [ -e "$app" ]; then
    echo "cannot install application \"$app\": it already exists" >&2
    exit 1
fi
log=$(mktemp)
pid=""
mount_point=""
cleanup() {
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
        wait "$pid" 2>/dev/null || true
    fi
    cat "$log"
    if [ -n "$mount_point" ]; then
        hdiutil detach "$mount_point" >/dev/null || true
    fi
    rm -rf "${app:?}"
}
trap cleanup EXIT

mount_point=$(mktemp -d)
hdiutil attach -nobrowse -readonly -mountpoint "$mount_point" "$1" >/dev/null
if [ ! -d "$mount_point/$app_name" ]; then
    echo "cannot find application \"$app_name\": expected the application bundle in the disk image" >&2
    exit 1
fi
ditto "$mount_point/$app_name" "$app"
hdiutil detach "$mount_point" >/dev/null
mount_point=""

launcher="$app/Contents/MacOS/Android Chrome Tabs Exporter"

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
        echo "PASS: installed application opened its window"
        exit 0
    fi
    sleep 0.2
done
echo 'cannot find window "Android Chrome Tabs Exporter": no window of the application within 30 seconds' >&2
exit 1
