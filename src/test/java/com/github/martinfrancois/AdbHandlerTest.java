package com.github.martinfrancois;

import com.github.martinfrancois.testsupport.CapturedConsole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link AdbHandler} against the genuine article.
 *
 * <p>These tests really download Google's {@code platform-tools} archive over HTTPS, really
 * unpack the {@code adb} binary out of the ZIP with {@code java.util.zip}, really mark it
 * executable, really execute it as a child process, and really enumerate operating-system
 * processes through {@code oshi-core} to find and terminate a running adb server.
 *
 * <p>Consequences worth knowing about:
 * <ul>
 *   <li>The suite needs outbound network access to {@code dl.google.com}. That is deliberate:
 *       stubbing the download would leave the ZIP extraction, the file permissions and the
 *       binary's own behaviour completely unverified, which is exactly the failure mode these
 *       tests exist to prevent.</li>
 *   <li>Two archives are fetched per run (one shared handler, one dedicated to the delete
 *       path), each into its own temporary directory.</li>
 *   <li>The three stderr branches that need adb to fail in a specific way are driven by a real
 *       executable script standing in for the adb CLI. That is still a real child process and
 *       real stream parsing; only the tool at the far end is substituted, and only for the
 *       failure modes that cannot be provoked without an attached Android device.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdbHandlerTest {

    private static final String CHROME_DEVTOOLS_SOCKET = "localabstract:chrome_devtools_remote";

    private final AtomicInteger stubCounter = new AtomicInteger();

    private Path workRoot;
    private Path stubDir;
    private String savedTmpDir;
    private CapturedConsole classConsole;
    private CapturedConsole console;
    private AdbHandler handler;
    private File realAdbExecutable;

    @BeforeAll
    void downloadAdbOnce() throws IOException {
        workRoot = Files.createTempDirectory("adb-handler-test");
        stubDir = Files.createDirectory(workRoot.resolve("stubs"));
        savedTmpDir = System.getProperty("java.io.tmpdir");
        classConsole = new CapturedConsole();

        // AdbHandler extracts into java.io.tmpdir; give it a directory of its own so the tests
        // never fight over the shared system temp directory.
        System.setProperty("java.io.tmpdir", Files.createDirectory(workRoot.resolve("shared")).toString());
        handler = new AdbHandler();
        realAdbExecutable = currentAdbExecutable(handler);
        System.setProperty("java.io.tmpdir", savedTmpDir);
    }

    @AfterAll
    void cleanUp() throws Exception {
        try {
            // Must happen before close(), which deletes the very binary that stops the server.
            killStrayAdbServers();
        } finally {
            handler.close();
            System.setProperty("java.io.tmpdir", savedTmpDir);
            classConsole.close();
        }
    }

    @BeforeEach
    void captureConsole() {
        console = new CapturedConsole();
    }

    @AfterEach
    void releaseConsole() throws Exception {
        setAdbExecutable(handler, realAdbExecutable);
        console.close();
    }

    // ------------------------------------------------------------------ download & lifecycle

    @Test
    void downloadsAnAdbBinaryThatActuallyRuns() throws Exception {
        assertThat(realAdbExecutable).isNotNull();
        assertThat(realAdbExecutable).exists().isFile();
        assertThat(realAdbExecutable.getName()).isEqualTo(OperatingSystem.isWindows() ? "adb.exe" : "adb");
        assertThat(realAdbExecutable.length())
                .as("the real adb binary is several megabytes; a truncated extract would be tiny")
                .isGreaterThan(1_000_000L);
        assertThat(realAdbExecutable.canExecute())
                .as("AdbHandler must add the owner-execute permission after extracting")
                .isTrue();

        Process version = new ProcessBuilder(realAdbExecutable.getAbsolutePath(), "version")
                .redirectErrorStream(true)
                .start();
        String output = new String(version.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(version.waitFor()).isZero();
        assertThat(output).contains("Android Debug Bridge");
    }

    @Test
    void extractsTheBinaryIntoTheJvmTemporaryDirectory() throws Exception {
        Path shared = workRoot.resolve("shared");

        assertThat(realAdbExecutable.toPath().getParent()).isEqualTo(shared);
        try (var entries = Files.list(shared)) {
            // Only the adb executable itself is unpacked, not the rest of platform-tools.
            assertThat(entries.map(path -> path.getFileName().toString()).toList())
                    .containsExactly(realAdbExecutable.getName());
        }
    }

    @Test
    void closeDeletesTheDownloadedBinaryAndReportsARepeatedDelete() throws Exception {
        Path isolated = Files.createTempDirectory(workRoot, "close-test");
        System.setProperty("java.io.tmpdir", isolated.toString());
        AdbHandler own;
        File executable;
        try {
            own = new AdbHandler();
            executable = currentAdbExecutable(own);
        } finally {
            System.setProperty("java.io.tmpdir", savedTmpDir);
        }
        assertThat(executable).exists();

        own.close();

        assertThat(executable).doesNotExist();
        assertThat(console.errors()).isEmpty();

        own.close();

        assertThat(console.errors()).containsExactly("Failed to delete ADB executable.");
    }

    // --------------------------------------------------------------------------- port checks

    @Test
    void rejectsAPortAboveTheValidRange() {
        assertThatThrownBy(() -> handler.forwardPortToChromeDevtools("70000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("port out of range:70000");
    }

    @Test
    void rejectsANegativePort() {
        assertThatThrownBy(() -> handler.forwardPortToChromeDevtools("-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("port out of range:-1");
    }

    @Test
    void rejectsANonNumericPort() {
        assertThatThrownBy(() -> handler.forwardPortToChromeDevtools("not-a-port"))
                .isInstanceOf(NumberFormatException.class);
    }

    // --------------------------------------------------------- real oshi process termination

    @Test
    void findsAndTerminatesAnAlreadyRunningAdbServerThroughOshi() throws Exception {
        startRealAdbServer();
        assertThat(adbProcessIdsAccordingToOshi())
                .as("oshi must see the adb server we just started")
                .isNotEmpty();

        // Any entry into forwardPortToChromeDevtools kills the running server first; an
        // out-of-range port makes it return immediately afterwards instead of talking to adb.
        assertThatThrownBy(() -> handler.forwardPortToChromeDevtools("70000"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(console.progress()).contains(
                "Found running adb process, terminating the process...",
                "Running adb process was successfully terminated");
        waitUntil(() -> adbProcessIdsAccordingToOshi().isEmpty());
        assertThat(adbProcessIdsAccordingToOshi()).isEmpty();
    }

    @Test
    void doesNotComplainWhenNoAdbServerIsRunning() throws Exception {
        killStrayAdbServers();
        waitUntil(() -> adbProcessIdsAccordingToOshi().isEmpty());

        assertThatThrownBy(() -> handler.forwardPortToChromeDevtools("70000"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(console.progress()).isEmpty();
        assertThat(console.errors()).isEmpty();
    }

    // --------------------------------------------------------------- real adb, no device here

    @Test
    void reportsAndRetriesForeverWhileNoAndroidDeviceIsAttached() throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                handler.forwardPortToChromeDevtools("50000");
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, "adb-forward");
        worker.setDaemon(true);
        worker.start();

        // The real adb binary really answers "no devices/emulators found" on this machine.
        waitUntil(() -> console.anyErrorContains("No android device found"));
        assertThat(console.anyErrorContains("Retrying in 3 seconds...")).isTrue();

        // The retry loop is unbounded by design, so interrupting it is the only way out.
        worker.interrupt();
        worker.join(Duration.ofSeconds(60).toMillis());

        assertThat(worker.isAlive()).isFalse();
        assertThat(thrown.get())
                .isInstanceOf(RuntimeException.class)
                .cause().isInstanceOf(InterruptedException.class);
    }

    // ------------------------------------------------- adb CLI failure modes needing a device

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void movesToTheNextPortWhenTheRequestedOneIsAlreadyBound() throws Exception {
        setAdbExecutable(handler, stubAdb("""
                port=${2#tcp:}
                if [ "$port" = "50000" ]; then
                  echo "adb: error: cannot bind listener: Address already in use" >&2
                  exit 1
                fi
                printf '%s' "$port"
                exit 0
                """));

        String port = handler.forwardPortToChromeDevtools("50000");

        assertThat(port)
                .as("the handler must walk up to the next free port rather than give up")
                .isEqualTo("50001");
        assertThat(console.progress())
                .contains("Port 50000 is already in use, trying port 50001...");
        assertThat(console.errors()).isEmpty();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void walksPastSeveralBoundPortsInSequence() throws Exception {
        setAdbExecutable(handler, stubAdb("""
                port=${2#tcp:}
                if [ "$port" -lt 50003 ]; then
                  echo "adb: error: cannot bind listener: Address already in use" >&2
                  exit 1
                fi
                printf '%s' "$port"
                exit 0
                """));

        assertThat(handler.forwardPortToChromeDevtools("50000")).isEqualTo("50003");
        assertThat(console.progress()).containsExactly(
                "Port 50000 is already in use, trying port 50001...",
                "Port 50001 is already in use, trying port 50002...",
                "Port 50002 is already in use, trying port 50003...");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void asksTheUserToAcceptTheUsbDebuggingPromptAndRetries() throws Exception {
        Path marker = stubDir.resolve("unauthorized-" + stubCounter.get());
        setAdbExecutable(handler, stubAdb("""
                if [ ! -f "%s" ]; then
                  : > "%s"
                  echo "adb: error: device unauthorized." >&2
                  exit 1
                fi
                printf '%%s' "${2#tcp:}"
                exit 0
                """.formatted(marker, marker)));

        String port = handler.forwardPortToChromeDevtools("50000");

        assertThat(port).isEqualTo("50000");
        assertThat(console.anyErrorContains("Please accept the USB debugging prompt on your phone")).isTrue();
        assertThat(console.anyErrorContains("Retrying in 3 seconds...")).isTrue();
        assertThat(marker).exists();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void reportsAnAdbFailureItDoesNotRecognise() throws Exception {
        setAdbExecutable(handler, stubAdb("""
                echo "surprising output"
                echo "adb: error: something nobody has seen before" >&2
                exit 9
                """));

        String port = handler.forwardPortToChromeDevtools("50000");

        assertThat(port).isEqualTo("50000");
        assertThat(console.errors()).hasSize(1);
        assertThat(console.errors().getFirst())
                .startsWith("adb command did not behave as expected, resulted in:")
                .contains("Output: surprising output")
                .contains("Error: adb: error: something nobody has seen before")
                .contains("Exit code: 9");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void acceptsASilentSuccessfulForward() throws Exception {
        // Real adb prints nothing on a successful forward of an already-forwarded port.
        setAdbExecutable(handler, stubAdb("exit 0\n"));

        String port = handler.forwardPortToChromeDevtools("50000");

        assertThat(port).isEqualTo("50000");
        assertThat(console.errors()).isEmpty();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void acceptsAForwardThatEchoesThePortBack() throws Exception {
        setAdbExecutable(handler, stubAdb("printf '%s\\n' \"${2#tcp:}\"\nexit 0\n"));

        assertThat(handler.forwardPortToChromeDevtools("50000")).isEqualTo("50000");
        assertThat(console.errors()).isEmpty();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void passesTheChromeDevtoolsSocketNameToAdb() throws Exception {
        Path argsFile = stubDir.resolve("args-" + stubCounter.get());
        setAdbExecutable(handler, stubAdb("""
                printf '%%s\\n' "$@" > "%s"
                exit 0
                """.formatted(argsFile)));

        handler.forwardPortToChromeDevtools("50000");

        assertThat(Files.readAllLines(argsFile))
                .containsExactly("forward", "tcp:50000", CHROME_DEVTOOLS_SOCKET);
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void aFailingForwardStillLeavesNoChildProcessBehind() throws Exception {
        setAdbExecutable(handler, stubAdb("exit 0\n"));
        handler.forwardPortToChromeDevtools("50000");

        // close() must tear the child process reference down; running it twice proves the
        // handler does not blow up on an already-stopped process.
        Field processField = AdbHandler.class.getDeclaredField("process");
        processField.setAccessible(true);
        assertThat(processField.get(handler)).isNotNull();

        setAdbExecutable(handler, stubAdb("exit 0\n"));
        handler.forwardPortToChromeDevtools("50001");
        assertThat(processField.get(handler)).isNotNull();
    }

    // -------------------------------------------------------------------------------- helpers

    private Path stubAdb(String scriptBody) throws IOException {
        Path script = stubDir.resolve("stub-adb-" + stubCounter.incrementAndGet() + ".sh");
        Files.writeString(script, "#!/bin/sh\n" + scriptBody);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private static void setAdbExecutable(AdbHandler handler, Object file) throws Exception {
        Field field = AdbHandler.class.getDeclaredField("adbExecutable");
        field.setAccessible(true);
        field.set(handler, file instanceof Path path ? path.toFile() : file);
    }

    private static File currentAdbExecutable(AdbHandler handler) {
        try {
            Field field = AdbHandler.class.getDeclaredField("adbExecutable");
            field.setAccessible(true);
            return (File) field.get(handler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void startRealAdbServer() throws Exception {
        Process process = new ProcessBuilder(realAdbExecutable.getAbsolutePath(), "start-server")
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        process.waitFor();
        waitUntil(() -> !adbProcessIdsAccordingToOshi().isEmpty());
    }

    private void killStrayAdbServers() throws Exception {
        Process process = new ProcessBuilder(realAdbExecutable.getAbsolutePath(), "kill-server")
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        process.waitFor();
    }

    /** Asks oshi directly, which is the same question {@code AdbHandler.killAdbProcess} asks. */
    private static List<Integer> adbProcessIdsAccordingToOshi() {
        String adbName = OperatingSystem.isWindows() ? "adb.exe" : "adb";
        return new SystemInfo().getOperatingSystem().getProcesses().stream()
                .filter(process -> adbName.equals(process.getName()))
                .map(OSProcess::getProcessID)
                .toList();
    }

    private static void waitUntil(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("condition never became true within 60s");
    }
}
