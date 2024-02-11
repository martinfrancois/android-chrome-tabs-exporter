package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import com.google.common.base.Throwables;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.TreeSet;
import java.util.function.Consumer;

public class Main {
    private static final String FILENAME_DATE_FORMAT = "yyyy-MM-dd";
    private static final String FILENAME_SUFFIX = " Android Tabs";
    private static final String DEFAULT_ADB_PORT = "50000";

    public static Consumer<String> updateProgress; // TODO implement more nicely
    public static Consumer<String> updateError; // TODO implement more nicely

    public static boolean export(Consumer<String> updateProgress, Consumer<String> updateError, String filePath) {
        Main.updateProgress = updateProgress;
        Main.updateError = updateError;
        try (AdbHandler adbHandler = new AdbHandler()) {
            String port = adbHandler.forwardPortToChromeDevtools(DEFAULT_ADB_PORT);
            String tabsJson = RequestHandler.get("http://localhost:" + port + "/json/list");
            TreeSet<Tab> tabs = AndroidChromeTabsProcessor.toTreeSet(tabsJson);

            String fileName = generateFileName(filePath);
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                ExcelWriter.tabsToExcel(tabs, fileOut);
                Main.updateProgress.accept("Excel file created successfully: " + fileName);
                return true;
            }
        } catch(Exception e) {
            Main.updateError.accept(Throwables.getStackTraceAsString(e));
            return false;
        }
    }

    private static String generateFileName(String path) {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILENAME_DATE_FORMAT);
        String formattedDate = currentDate.format(formatter);
        return path + File.separator + formattedDate + FILENAME_SUFFIX + ".xlsx";
    }

}
