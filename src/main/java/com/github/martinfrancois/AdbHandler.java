package com.github.martinfrancois;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AdbHandler implements AutoCloseable {
    private static final String PLATFORM_TOOLS_PREFIX = "https://dl.google.com/android/repository/platform-tools-latest-";
    private final File adbExecutable;
    private Process process;

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

    public void forwardPortToChromeDevtools(String adbPort) {
        try {
            process = new ProcessBuilder(adbExecutable.getAbsolutePath(), "forward", "tcp:" + adbPort, "localabstract:chrome_devtools_remote")
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start();

            String output = readStream(process.getInputStream());
            String error = readStream(process.getErrorStream());

            // Wait for the process to finish
            int exitCode = process.waitFor();

            if (error.contains("no devices/emulators found")) {
                System.err.println("No android device found. Make sure your phone is plugged in, USB debugging is enabled and you allowed USB debugging.");
                System.exit(1);
            }
            if (!((adbPort.equals(output) || output.isEmpty()) && exitCode == 0) && !error.isEmpty()) {
                System.err.println("adb command did not behave as expected, resulted in:\n" + "Output: " + output + "\nError: " + error + "\nExit code: " + exitCode);
                System.exit(1);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        stopProcess();
        deleteAdbExecutable();
    }

    private void stopProcess() {
        if (process != null) {
            process.destroy();
        }
    }

    private void deleteAdbExecutable() {
        if (adbExecutable == null || !adbExecutable.delete()) {
            System.err.println("Failed to delete ADB executable.");
            System.exit(1);
        }
    }
}
