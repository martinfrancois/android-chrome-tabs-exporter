package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import com.google.common.base.Throwables;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
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

            Path exportFile = writeExport(Path.of(filePath), LocalDate.now(), tabs);
            Main.updateProgress.accept("Excel file created successfully: " + exportFile);
            return true;
        } catch(Exception e) {
            Main.updateError.accept(Throwables.getStackTraceAsString(e));
            return false;
        }
    }

    /** Writes the tabs to a new export file in the directory and returns that file. */
    static Path writeExport(Path directory, LocalDate date, TreeSet<Tab> tabs) throws IOException {
        Path exportFile = createExportFile(directory, date);
        try (OutputStream fileOut = Files.newOutputStream(exportFile)) {
            ExcelWriter.tabsToExcel(tabs, fileOut);
        } catch (IOException | RuntimeException writeFailure) {
            // A broken file would keep the plain name and push the retry to " (2)".
            try {
                Files.deleteIfExists(exportFile);
            } catch (IOException deleteFailure) {
                writeFailure.addSuppressed(deleteFailure);
            }
            throw writeFailure;
        }
        return exportFile;
    }

    /**
     * Creates a new, empty export file for the given date in the directory. An existing export,
     * for example of another phone on the same day, is kept: the name gets a " (2)", " (3)", ...
     * suffix instead. Creating the file is atomic, so two exports can't pick the same name.
     */
    static Path createExportFile(Path directory, LocalDate date) throws IOException {
        String baseName = date.format(DateTimeFormatter.ofPattern(FILENAME_DATE_FORMAT)) + FILENAME_SUFFIX;
        for (int number = 1; ; number++) {
            String numberSuffix = number == 1 ? "" : " (" + number + ")";
            try {
                return Files.createFile(directory.resolve(baseName + numberSuffix + ".xlsx"));
            } catch (FileAlreadyExistsException alreadyExported) {
                // keep the earlier export and try the next number
            }
        }
    }

}
