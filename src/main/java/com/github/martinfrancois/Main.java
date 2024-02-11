package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.TreeSet;

public class Main {
    private static final String FILENAME_DATE_FORMAT = "yyyy-MM-dd";
    private static final String FILENAME_SUFFIX = " Android Tabs";
    private static final String DEFAULT_ADB_PORT = "50000";

    public static void main(String[] args) {
        String excelFolderPath = getFolderPath(args);
        validateFolderPath(excelFolderPath);

        try (AdbHandler adbHandler = new AdbHandler()) {
            String port = adbHandler.forwardPortToChromeDevtools(DEFAULT_ADB_PORT);
            String tabsJson = RequestHandler.get("http://localhost:" + port + "/json/list");
            TreeSet<Tab> tabs = AndroidChromeTabsProcessor.toTreeSet(tabsJson);

            String fileName = generateFileName(excelFolderPath);
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                ExcelWriter.tabsToExcel(tabs, fileOut);
                System.out.println("Excel file created successfully: " + fileName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void validateFolderPath(String excelFolderPath) {
        Path path = Paths.get(excelFolderPath);
        if (!Files.exists(path)) {
            System.err.println("The provided path does not exist: " + excelFolderPath);
            System.exit(1);
        }
        if (!Files.isDirectory(path)) {
            System.err.println("The provided path is not a folder: " + excelFolderPath);
            System.exit(1);
        }
    }

    private static String getFolderPath(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide a path to a folder in which to save the Excel file with the tabs. " +
                    "If your path contains spaces, please wrap the path in quotation marks." +
                    "For example: java -jar android-chrome-tabs-exporter.jar \"/home/user/my folder\"");
            System.exit(1);
        }
        return args[0];
    }

    private static String generateFileName(String path) {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILENAME_DATE_FORMAT);
        String formattedDate = currentDate.format(formatter);
        return path + File.separator + formattedDate + FILENAME_SUFFIX + ".xlsx";
    }

}
