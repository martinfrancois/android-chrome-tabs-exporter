package com.github.martinfrancois;

public class OperatingSystem {
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean windows = OS_NAME.contains("win");
    private static final boolean mac = OS_NAME.contains("mac");
    private static final boolean linux = !windows && !mac;

    private OperatingSystem() {
        throw new UnsupportedOperationException("OperatingSystem is a utility class and should not be instantiated.");
    }

    public static boolean isWindows() {
        return windows;
    }

    public static boolean isMac() {
        return mac;
    }

    public static boolean isLinux() {
        return linux;
    }
}