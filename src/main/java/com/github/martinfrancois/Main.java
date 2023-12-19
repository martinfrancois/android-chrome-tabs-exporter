package com.github.martinfrancois;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {

    public static final String HEADER_URL = "URL";
    public static final String HEADER_TITLE = "Title";
    public static final String ADB_KEY_TITLE = "title";
    public static final String ADB_KEY_URL = "url";
    public static final String FILENAME_DATE_FORMAT = "yyyy-MM-dd";
    public static final String FILENAME_SUFFIX = " Android Tabs";
    public static final String EXCEL_SHEET_NAME = "Tabs";
    public static final String ADB_PORT = "9222";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide a path to a folder in which to save the Excel file with the tabs. " +
                    "If your path contains spaces, please wrap the path in quotation marks." +
                    "For example: java -jar android-chrome-tabs-exporter.jar \"/home/user/my folder\"");
            return;
        }

        try (AdbHandler adbHandler = new AdbHandler()) {
            adbHandler.forwardPortToChromeDevtools(ADB_PORT);
            String tabsJson = RequestHandler.get("http://localhost:" + ADB_PORT + "/json/list");

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

                try (Workbook workbook = new XSSFWorkbook()) {
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

                    String fileName = generateFileName(args);
                    try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                        workbook.write(fileOut);
                        System.out.println("Excel file created successfully: " + fileName);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    private static String generateFileName(String[] path) {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILENAME_DATE_FORMAT);
        String formattedDate = currentDate.format(formatter);
        return path[0] + File.separator + formattedDate + FILENAME_SUFFIX + ".xlsx";
    }

}
