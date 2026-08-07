package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import com.github.martinfrancois.testsupport.UtilityClasses;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link ExcelWriter} against the real Apache POI / poi-ooxml stack.
 *
 * <p>Nothing here is stubbed: every assertion round-trips through bytes that POI produced
 * and then re-parses them with POI (and, for the packaging assertions, with the JDK's own
 * ZIP reader). A POI that stopped emitting genuine OOXML would fail these tests rather
 * than silently keep the coverage number green.
 */
class ExcelWriterTest {

    private static TreeSet<Tab> tabsOrderedByUrl(Tab... tabs) {
        TreeSet<Tab> set = new TreeSet<>(Comparator.comparing(Tab::url));
        set.addAll(List.of(tabs));
        return set;
    }

    private static byte[] write(TreeSet<Tab> tabs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelWriter.tabsToExcel(tabs, out);
        return out.toByteArray();
    }

    @Test
    void producesARealOoxmlPackageAndNotJustBytes() throws Exception {
        byte[] bytes = write(tabsOrderedByUrl(new Tab("GitHub", "https://github.com")));

        // OOXML files are ZIP containers; these entries are mandated by ECMA-376.
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }

        assertThat(bytes).startsWith((byte) 'P', (byte) 'K');
        assertThat(entries)
                .contains("[Content_Types].xml")
                .anyMatch(name -> name.startsWith("xl/") && name.endsWith("workbook.xml"));
    }

    @Test
    void writesHeaderRowWithSkyBlueSolidFill() throws Exception {
        byte[] bytes = write(tabsOrderedByUrl(new Tab("GitHub", "https://github.com")));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(sheet.getSheetName()).isEqualTo("Tabs");

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Title");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("URL");
            assertThat(header.getCell(0).getCellType()).isEqualTo(CellType.STRING);

            for (int column = 0; column <= 1; column++) {
                CellStyle style = header.getCell(column).getCellStyle();
                assertThat(style.getFillForegroundColor())
                        .as("header column %d fill colour", column)
                        .isEqualTo(IndexedColors.SKY_BLUE.getIndex());
                assertThat(style.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            }
        }
    }

    @Test
    void writesEveryTabInTreeSetIterationOrder() throws Exception {
        TreeSet<Tab> tabs = tabsOrderedByUrl(
                new Tab("Stack Overflow", "https://stackoverflow.com"),
                new Tab("GitHub", "https://github.com"),
                new Tab("Google", "https://google.com"));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(write(tabs)))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getLastRowNum()).isEqualTo(3);

            List<String> titles = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            for (int rowNumber = 1; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
                titles.add(sheet.getRow(rowNumber).getCell(0).getStringCellValue());
                urls.add(sheet.getRow(rowNumber).getCell(1).getStringCellValue());
            }

            assertThat(urls).containsExactly(
                    "https://github.com", "https://google.com", "https://stackoverflow.com");
            assertThat(titles).containsExactly("GitHub", "Google", "Stack Overflow");
        }
    }

    @Test
    void setsAutoFilterOverTheHeaderAndAllDataRows() throws Exception {
        TreeSet<Tab> tabs = tabsOrderedByUrl(
                new Tab("GitHub", "https://github.com"),
                new Tab("Google", "https://google.com"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(write(tabs)))) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            // Header row plus two data rows -> rows 1..3 in Excel's 1-based addressing.
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:B3");
        }
    }

    @Test
    void emptyTabSetStillProducesAHeaderOnlySheetWithAutoFilter() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(write(tabsOrderedByUrl())))) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getLastRowNum()).isZero();
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 2);
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:B1");
        }
    }

    @Test
    void preservesUnicodeAndEmbeddedNewlinesThroughTheSharedStringsTable() throws Exception {
        Tab tab = new Tab("Ümlaut – café 😀\nsecond line", "https://xn--caf-dma.example/é");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(write(tabsOrderedByUrl(tab))))) {
            Row row = workbook.getSheetAt(0).getRow(1);

            assertThat(row.getCell(0).getStringCellValue()).isEqualTo(tab.title());
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo(tab.url());
        }
    }

    @Test
    void nullTitleIsWrittenAsABlankCellRatherThanFailing() throws Exception {
        // AndroidChromeTabsProcessor can emit a Tab with a null title (JSONObject.NULL input).
        try (Workbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(write(tabsOrderedByUrl(new Tab(null, "https://github.com")))))) {
            Row row = workbook.getSheetAt(0).getRow(1);

            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("https://github.com");
        }
    }

    @Test
    void writesAFileThatPoiCanReopenFromDisk(@TempDir Path tempDir) throws Exception {
        TreeSet<Tab> tabs = tabsOrderedByUrl(
                new Tab("GitHub", "https://github.com"),
                new Tab("Google", "https://google.com"));
        Path file = tempDir.resolve("tabs.xlsx");

        try (OutputStream out = Files.newOutputStream(file)) {
            ExcelWriter.tabsToExcel(tabs, out);
        }

        assertThat(Files.size(file)).isPositive();
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            assertThat(workbook.getSheet("Tabs").getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    void scalesToALargeTabSet() throws Exception {
        TreeSet<Tab> tabs = new TreeSet<>(Comparator.comparing(Tab::url));
        for (int i = 0; i < 5000; i++) {
            tabs.add(new Tab("Tab " + i, String.format("https://example.com/%05d", i)));
        }

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(write(tabs)))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getLastRowNum()).isEqualTo(5000);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("https://example.com/00000");
            assertThat(sheet.getRow(5000).getCell(1).getStringCellValue()).isEqualTo("https://example.com/04999");
        }
    }

    @Test
    void aFailingOutputStreamAbortsTheExportWithPoisOwnRuntimeException() {
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("disk full");
            }
        };

        // This pins the behaviour POI actually has today rather than the behaviour ExcelWriter
        // appears to expect: POI catches the underlying IOException inside ZipPackage.saveImpl and
        // rethrows it as an OpenXML4JRuntimeException, so ExcelWriter's `catch (IOException)` never
        // engages and callers see a POI runtime exception instead of an UncheckedIOException.
        // If a POI upgrade ever starts propagating the raw IOException, this test fails loudly.
        assertThatThrownBy(() -> ExcelWriter.tabsToExcel(tabsOrderedByUrl(new Tab("GitHub", "https://github.com")), failing))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to save");
    }

    @Test
    void utilityClassCannotBeInstantiated() throws Exception {
        Throwable thrown = UtilityClasses.invokeConstructorAndCaptureThrowable(ExcelWriter.class);

        assertThat(thrown)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("utility class");
    }
}
