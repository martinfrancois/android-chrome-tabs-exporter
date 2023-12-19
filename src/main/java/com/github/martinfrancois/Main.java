package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.github.martinfrancois.Constants.HEADER_TITLE;
import static com.github.martinfrancois.Constants.HEADER_URL;

public class Main {
    private static final String FILENAME_DATE_FORMAT = "yyyy-MM-dd";
    private static final String FILENAME_SUFFIX = " Android Tabs";
    private static final String EXCEL_SHEET_NAME = "Tabs";
    private static final String ADB_PORT = "9222";

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
            TreeSet<Tab> tabs = AndroidChromeTabsProcessor.toTreeSet(tabsJson);

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
                for (Tab tab : tabs) {
                    Row dataRow = sheet.createRow(rowNumber++);
                    Cell cellTitle = dataRow.createCell(0);
                    Cell cellUrl = dataRow.createCell(1);

                    cellTitle.setCellValue(tab.title());
                    cellUrl.setCellValue(tab.url());
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

    private static String generateFileName(String[] path) {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILENAME_DATE_FORMAT);
        String formattedDate = currentDate.format(formatter);
        return path[0] + File.separator + formattedDate + FILENAME_SUFFIX + ".xlsx";
    }

}
