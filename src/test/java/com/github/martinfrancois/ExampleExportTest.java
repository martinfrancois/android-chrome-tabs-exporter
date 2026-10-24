package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import com.github.martinfrancois.testsupport.CapturedConsole;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the export pipeline on the synthetic DevTools payload in {@code examples/tabs.json}
 * and leaves the resulting workbook in {@code build/examples/tabs.xlsx}.
 *
 * <p>This is the same code path {@link Main#export} takes after the JSON has arrived from the
 * phone: parse, deduplicate, sort, write. Only the adb and HTTP steps are skipped, because they
 * need a device. The README documents this test as the way to see the output without a phone.
 */
class ExampleExportTest {

    private static final Path EXAMPLE_INPUT = Path.of("examples", "tabs.json");
    private static final Path EXAMPLE_OUTPUT = Path.of("build", "examples", "tabs.xlsx");

    private CapturedConsole console;

    @BeforeEach
    void setUp() {
        console = new CapturedConsole();
    }

    @AfterEach
    void tearDown() {
        console.close();
    }

    @Test
    void exportsTheExamplePayloadToAWorkbookWithTitleAndUrlColumns() throws Exception {
        String json = Files.readString(EXAMPLE_INPUT, StandardCharsets.UTF_8);
        TreeSet<Tab> tabs = AndroidChromeTabsProcessor.toTreeSet(json);

        Files.createDirectories(EXAMPLE_OUTPUT.getParent());
        try (OutputStream out = Files.newOutputStream(EXAMPLE_OUTPUT)) {
            ExcelWriter.tabsToExcel(tabs, out);
        }

        // Six entries in the file: one is blank and two share a URL, so four rows remain.
        assertThat(console.progress()).containsExactly("Found 6 tabs.");

        List<List<String>> rows = new ArrayList<>();
        try (InputStream in = Files.newInputStream(EXAMPLE_OUTPUT);
             Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet("Tabs");
            for (int rowNumber = 0; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
                Row row = sheet.getRow(rowNumber);
                rows.add(List.of(row.getCell(0).getStringCellValue(), row.getCell(1).getStringCellValue()));
            }
        }

        assertThat(rows).containsExactly(
                List.of("Title", "URL"),
                List.of("Recipe: Café au lait", "http://recipes.example.net/cafe-au-lait"),
                List.of("Docs & Guides · Getting started", "https://docs.example.org/getting-started?lang=en&section=2"),
                List.of("Example Domain", "https://example.com/"),
                List.of("Tickets 2026 <early bird>", "https://shop.example.com/tickets/2026"));
    }
}
