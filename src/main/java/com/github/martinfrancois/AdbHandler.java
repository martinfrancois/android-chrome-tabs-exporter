package com.github.martinfrancois;

import com.google.common.base.Throwables;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AdbHandler implements AutoCloseable {
    private static final String PLATFORM_TOOLS_PREFIX = "https://dl.google.com/android/repository/platform-tools-latest-";
    private final File adbExecutable;
    private Process process;
    private boolean retry = false;

    public AdbHandler() {
        adbExecutable = downloadAdbExecutable();
        validateAdbExecutable();
    }

    private static File downloadAdbExecutable() {
        String adbExecutableName = getAdbExecutableName();
        String adbDownloadUrl = getAdbDownloadUrl();
        ZipEntry zipEntry;
        File tempDir = new File(System.getProperty("java.io.tmpdir"));

        try (InputStream in = URI.create(adbDownloadUrl).toURL().openStream();
             ZipInputStream zipInputStream = new ZipInputStream(in)) {
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                // only extract the adb executable
                if (zipEntry.getName().endsWith(adbExecutableName)) {
                    Path adbExecutable = Paths.get(String.valueOf(tempDir), adbExecutableName);
                    Files.copy(zipInputStream, adbExecutable, StandardCopyOption.REPLACE_EXISTING);
                    // fix permission issues when not running on Windows
                    if (!OperatingSystem.isWindows()) {
                        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(adbExecutable);
                        perms.add(PosixFilePermission.OWNER_EXECUTE);
                        Files.setPosixFilePermissions(adbExecutable, perms);
                    }
                    return adbExecutable.toFile();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    private static String getAdbExecutableName() {
        return OperatingSystem.isWindows() ? "adb.exe" : "adb";
    }

    private static String getAdbDownloadUrl() {
        if (OperatingSystem.isWindows()) {
            return PLATFORM_TOOLS_PREFIX + "windows.zip";
        } else if (OperatingSystem.isMac()) {
            return PLATFORM_TOOLS_PREFIX + "darwin.zip";
        } else {
            return PLATFORM_TOOLS_PREFIX + "linux.zip";
        }
    }

    private static String readStream(InputStream inputStream) {
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }

    private void validateAdbExecutable() {
        if (adbExecutable == null || !adbExecutable.exists()) {
            System.err.println("Failed to download ADB executable.");
            System.exit(1);
        }
    }

    /**
     * Forwards the port to the chrome devtools.
     * If the port is already in use, it will try the next port, until it finds one that is not in use.
     * @param adbPort the port to forward to the chrome devtools
     * @return the adb port that was forwarded to the chrome devtools
     */
    public String forwardPortToChromeDevtools(String adbPort) {
        do {
            validatePort(adbPort);
            retry = false;
            try {
                // prevent running into the error "cannot bind listener: Address already in use"
                killAdbProcess();

                process = new ProcessBuilder(adbExecutable.getAbsolutePath(), "forward", "tcp:" + adbPort, "localabstract:chrome_devtools_remote")
                        .redirectOutput(ProcessBuilder.Redirect.PIPE)
                        .redirectError(ProcessBuilder.Redirect.PIPE)
                        .start();

                String output = readStream(process.getInputStream());
                String error = readStream(process.getErrorStream());

                // Wait for the process to finish
                int exitCode = process.waitFor();

                if (error.contains("no devices/emulators found")) {
                    System.err.println("No android device found. Make sure your phone is plugged in, USB debugging is enabled and you allowed USB debugging. Retrying in 3 seconds...");
                    retry();
                } else if (error.contains("cannot bind listener: Address already in use")) {
                    String nextPort = String.valueOf(Integer.parseInt(adbPort) + 1);
                    System.out.println("Port " + adbPort + " is already in use, trying port " + nextPort + "...");
                    adbPort = nextPort;
                    retry(0);
                } else if (error.contains("device unauthorized")) {
                    System.err.println("Please accept the USB debugging prompt on your phone to continue. Retrying in 3 seconds...");
                    retry();
                } else if (!((adbPort.equals(output.trim()) || output.isBlank()) && exitCode == 0) && !error.isBlank()) {
                    System.err.println("adb command did not behave as expected, resulted in:\n" + "Output: " + output + "\nError: " + error + "\nExit code: " + exitCode);
                    System.exit(1);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while (retry);
        return adbPort;
    }

    private static void validatePort(String portString) {
        int port = Integer.parseInt(portString);
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("port out of range:" + port);
        }
    }

    private void retry(int timeMilliseconds) throws InterruptedException {
        stopProcess();
        retry = true;
        Thread.sleep(timeMilliseconds);
    }

    private void retry() throws InterruptedException {
        retry(3000);
    }

    /**
     * Synchronous, will wait for 30 seconds for adb to be killed,
     * and otherwise will log an error and exit.
     * When there is no such process, it will do nothing.
     */
    private void killAdbProcess() {
        SystemInfo systemInfo = new SystemInfo();
        var os = systemInfo.getOperatingSystem();
        for (OSProcess process : os.getProcesses()) {
            if (getAdbExecutableName().equals(process.getName())) {
                System.out.println("Found running adb process, terminating the process...");
                ProcessHandle.of(process.getProcessID()).ifPresent(procHandle -> {
                    procHandle.destroy();
                    try {
                        procHandle.onExit().get(30, TimeUnit.SECONDS);
                        System.out.println("Running adb process was successfully terminated");
                    } catch (InterruptedException | ExecutionException e) {
                        System.err.println("Unexpected error when attempting to terminate adb: " + Throwables.getStackTraceAsString(e));
                    } catch (TimeoutException e) {
                        System.err.println("Failed to terminate adb process within 30 seconds.");
                    }
                });
                return;
            }
        }
    }

    public void close() {
        stopProcess();
        deleteAdbExecutable();
    }

    private void stopProcess() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    private void deleteAdbExecutable() {
        if (adbExecutable == null || !adbExecutable.delete()) {
            System.err.println("Failed to delete ADB executable.");
            System.exit(1);
        }
    }
}
