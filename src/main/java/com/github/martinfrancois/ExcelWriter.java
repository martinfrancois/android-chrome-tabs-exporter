package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.TreeSet;

public class ExcelWriter {

    private static final String EXCEL_SHEET_NAME = "Tabs";

    private ExcelWriter() {
        throw new UnsupportedOperationException("ExcelWriter is a utility class and should not be instantiated.");
    }

    public static void tabsToExcel(TreeSet<Tab> tabs, OutputStream outputStream) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(EXCEL_SHEET_NAME);

            // Adding header row
            Row headerRow = sheet.createRow(0);
            Cell headerTitle = headerRow.createCell(0);
            Cell headerUrl = headerRow.createCell(1);
            headerTitle.setCellValue("Title");
            headerUrl.setCellValue("URL");

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

            workbook.write(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
