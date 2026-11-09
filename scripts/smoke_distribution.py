#!/usr/bin/env python3
"""Launch the distributed application under an existing X11 display."""

import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile


# Workaround: during the JVM's shutdown after SIGTERM, JavaFX can still submit a paint job to its
# stopped renderer and log this trace (JDK-8094660). It says nothing about the packaged app, so
# it is ignored, and only in the part of the log written after SIGTERM. Remove it once a JavaFX
# upgrade no longer logs the trace; issue #192 has the check.
SHUTDOWN_PAINT_RACE = re.compile(
    r"^java\.util\.concurrent\.RejectedExecutionException: Task com\.sun\.javafx\.tk\.quantum\.PaintRenderJob@\S+ .*"
    r"rejected from com\.sun\.javafx\.tk\.quantum\.QuantumRenderer@\S+\[Terminated,.*\n(?:\tat .*\n)*",
    re.MULTILINE,
)


def main():
    if len(sys.argv) != 2:
        sys.exit('cannot check distribution "arguments": expected one ZIP path')
    if not os.environ.get("DISPLAY") or not shutil.which("xwininfo"):
        print('cannot check display "DISPLAY": run with Xvfb and install x11-utils', file=sys.stderr)
        return 2
    display = subprocess.run(["xwininfo", "-root"], capture_output=True, text=True)
    if display.returncode:
        print('cannot open display "DISPLAY": start an X server or run with xvfb-run', file=sys.stderr)
        return 2

    with tempfile.TemporaryDirectory(prefix="android package smoke ") as directory:
        root = Path(directory)
        with zipfile.ZipFile(sys.argv[1]) as archive:
            launchers = [name for name in archive.namelist() if name.endswith("/bin/android-chrome-tabs-exporter")]
            if len(launchers) != 1:
                raise AssertionError('cannot inspect distribution "launcher": expected exactly one packaged launcher')
            launcher_info = archive.getinfo(launchers[0])
            if not (launcher_info.external_attr >> 16) & 0o111:
                raise AssertionError('cannot execute launcher "permissions": expected an executable archive entry')
            archive.extractall(root)
            launcher = root / launchers[0]
            launcher.chmod(0o755)

        application = launcher.parent.parent
        with zipfile.ZipFile(application / "lib/android-chrome-tabs-exporter.jar") as jar:
            if "javafx/application/Application.class" in jar.namelist():
                raise AssertionError('cannot accept JavaFX "classpath": expected JavaFX classes only on the module path')
            source = Path(__file__).resolve().parent.parent
            for name in ("LICENSE", "NOTICE.md", "licenses/Apache-2.0.txt"):
                expected = (source / name).read_bytes()
                assert (application / name).read_bytes() == expected
                packaged_name = "META-INF/android-chrome-tabs-exporter/" + Path(name).name
                assert jar.read(packaged_name) == expected

        environment = dict(os.environ)
        environment.pop("JDK_JAVA_OPTIONS", None)
        environment.pop("_JAVA_OPTIONS", None)
        environment["JAVA_TOOL_OPTIONS"] = f'-Duser.home="{root / "home"}"'
        log_path = root / "startup.log"
        with log_path.open("w+") as log:
            process = subprocess.Popen([str(launcher)], cwd=root, env=environment,
                                       stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 20
                while time.monotonic() < deadline:
                    if process.poll() is not None:
                        raise AssertionError(f'cannot start application "exit {process.returncode}": inspect the startup log')
                    windows = subprocess.run(["xwininfo", "-root", "-tree"], capture_output=True, text=True)
                    if '"Android Chrome Tabs Exporter"' in windows.stdout:
                        break
                    time.sleep(0.1)
                else:
                    raise AssertionError('cannot find window "Android Chrome Tabs Exporter": inspect the startup log')
                startup_size = log_path.stat().st_size
                process.terminate()
                process.wait(timeout=10)
                if process.returncode not in (0, -15, 143):
                    raise AssertionError(f'cannot confirm shutdown "exit {process.returncode}": expected normal exit or SIGTERM')
                written = log_path.read_bytes()
                startup = written[:startup_size].decode(errors="replace")
                shutdown = written[startup_size:].decode(errors="replace")
                text = startup + SHUTDOWN_PAINT_RACE.sub("", shutdown)
                if SHUTDOWN_PAINT_RACE.search(shutdown):
                    print("NOTE: ignored the JavaFX shutdown paint race (JDK-8094660, issue #192) in the log below")
                if "Unsupported JavaFX configuration" in text:
                    raise AssertionError('cannot accept JavaFX "unnamed module": put JavaFX on the module path')
                if "AT-SPI: Error retrieving accessibility bus address" in text:
                    raise AssertionError('cannot connect accessibility bus "org.a11y.Bus": install at-spi2-core and use dbus-run-session')
                if " ERROR " in text or "Exception" in text:
                    raise AssertionError('cannot accept startup log "exception or error": inspect the startup log')
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()
                print(log_path.read_text(), end="")
    print("PASS: packaged window opened without the JavaFX warning and stopped on SIGTERM")
    return 0


if __name__ == "__main__":
    sys.exit(main())
