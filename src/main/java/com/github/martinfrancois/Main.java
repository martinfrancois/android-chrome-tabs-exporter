package com.github.martinfrancois;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {

    public static final String HEADER_URL = "URL";
    public static final String HEADER_TITLE = "Title";
    public static final String ADB_KEY_TITLE = "title";
    public static final String ADB_KEY_URL = "url";
    public static final String FILENAME_DATE_FORMAT = "yyyy-MM-dd";
    public static final String FILENAME_SUFFIX = " Android Tabs";
    public static final String EXCEL_SHEET_NAME = "Tabs";
    public static final String PLATFORM_TOOLS_PREFIX = "https://dl.google.com/android/repository/platform-tools-latest-";
    public static final String ADB_PORT = "9222";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide a path to a folder in which to save the Excel file with the tabs. For example: java -jar android-tabs.jar /home/user");
            return;
        }

        File adbExecutable = null;
        try {
            adbExecutable = downloadAdbExecutable();

            if (adbExecutable == null || !adbExecutable.exists()) {
                System.err.println("Failed to download ADB executable.");
                System.exit(1);
            }

            executeAdbCommand(adbExecutable);

            String tabsJson = makeHttpRequest("http://localhost:" + ADB_PORT + "/json/list");

            if (tabsJson != null && !tabsJson.isEmpty()) {
                JSONArray tabArray = new JSONArray(tabsJson);

                // Using TreeSet to automatically sort and deduplicate entries based on URL
                TreeSet<Map<String, String>> sortedTabs = new TreeSet<>(Comparator.comparing(entry -> entry.get(HEADER_URL)));

                for (int i = 0; i < tabArray.length(); i++) {
                    JSONObject tab = tabArray.getJSONObject(i);
                    String title = tab.optString(ADB_KEY_TITLE);
                    String url = tab.optString(ADB_KEY_URL);

                    if (title.isBlank() && url.isBlank()) {
                        continue;
                    }
                    sortedTabs.add(Map.of(HEADER_TITLE, title, HEADER_URL, url));
                }

                // Create workbook and sheet
                Workbook workbook = new XSSFWorkbook();
                Sheet sheet = workbook.createSheet(EXCEL_SHEET_NAME);

                // Adding header row
                Row headerRow = sheet.createRow(0);
                Cell headerTitle = headerRow.createCell(0);
                Cell headerUrl = headerRow.createCell(1);
                headerTitle.setCellValue(HEADER_TITLE);
                headerUrl.setCellValue(HEADER_URL);

                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFillForegroundColor(IndexedColors.SKY_BLUE.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                headerTitle.setCellStyle(headerStyle);
                headerUrl.setCellStyle(headerStyle);

                // Write sorted data to the sheet
                int rowNumber = 1; // Start from the next row after the header
                for (Map<String, String> tab : sortedTabs) {
                    Row dataRow = sheet.createRow(rowNumber++);
                    Cell cellTitle = dataRow.createCell(0);
                    Cell cellUrl = dataRow.createCell(1);

                    cellTitle.setCellValue(tab.get(HEADER_TITLE));
                    cellUrl.setCellValue(tab.get(HEADER_URL));
                }

                // Create a table
                int lastRowNum = sheet.getLastRowNum();
                int lastColNum = headerRow.getLastCellNum() - 1;
                sheet.setAutoFilter(new CellRangeAddress(0, lastRowNum, 0, lastColNum));

                try {
                    String fileName = generateFileName(args);
                    try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                        workbook.write(fileOut);
                        System.out.println("Excel file created successfully: " + fileName);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } finally {
                    try {
                        workbook.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }

        } finally {
            // Cleanup: Delete the ADB executable
            if (adbExecutable == null || !adbExecutable.delete()) {
                System.err.println("Failed to delete ADB executable.");
                System.exit(1);
            }
        }
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
                    Path adbExecutable = Paths.get(String.valueOf(tempDir), "adb");
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

    private static void executeAdbCommand(File adbExecutable) {
        try {
            Process process = new ProcessBuilder(adbExecutable.getAbsolutePath(), "forward", "tcp:" + ADB_PORT, "localabstract:chrome_devtools_remote")
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start();

            // Read the output and error streams
            String output = readStream(process.getInputStream());
            String error = readStream(process.getErrorStream());

            // Wait for the process to finish
            int exitCode = process.waitFor();

            if (error.contains("no devices/emulators found")) {
                System.err.println("No android device found. Make sure your phone is plugged in, USB debugging is enabled and you allowed USB debugging.");
                System.exit(1);
            }
            if (!((ADB_PORT.equals(output) || output.isEmpty()) && exitCode == 0) && !error.isEmpty()) {
                System.err.println("adb command did not behave as expected, resulted in:\n" + "Output: " + output + "\nError: " + error + "\nExit code: " + exitCode);
                System.exit(1);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readStream(InputStream inputStream) {
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }

    private static String makeHttpRequest(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .build();
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                System.err.println("HTTP Request failed with status code: " + response.statusCode());
                System.exit(1);
            }
        } catch (ConnectException e) {
            System.err.println("Failed to connect to the phone. Make sure your phone is plugged in, USB debugging is enabled and you allowed USB debugging.");
            System.exit(1);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private static String generateFileName(String[] path) {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILENAME_DATE_FORMAT);
        String formattedDate = currentDate.format(formatter);
        return path[0] + File.separator + formattedDate + FILENAME_SUFFIX + ".xlsx";
    }

}
