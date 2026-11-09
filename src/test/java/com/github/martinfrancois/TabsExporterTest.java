package com.github.martinfrancois;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.martinfrancois.model.Tab;
import com.github.martinfrancois.testsupport.UtilityClasses;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabsExporterTest {

    private static final LocalDate EXPORT_DATE = LocalDate.of(2026, 11, 9);

    @Test
    void firstExportOfTheDayUsesThePlainName(@TempDir Path directory) throws IOException {
        Path exportFile = TabsExporter.createExportFile(directory, EXPORT_DATE);

        assertThat(exportFile).isEqualTo(directory.resolve("2026-11-09 Android Tabs.xlsx"));
        assertThat(exportFile).isEmptyFile();
    }

    @Test
    void laterExportsOfTheSameDayKeepTheEarlierFiles(@TempDir Path directory) throws IOException {
        Path firstPhone = TabsExporter.createExportFile(directory, EXPORT_DATE);
        Files.writeString(firstPhone, "first phone");
        Path secondPhone = TabsExporter.createExportFile(directory, EXPORT_DATE);
        Files.writeString(secondPhone, "second phone");

        Path thirdPhone = TabsExporter.createExportFile(directory, EXPORT_DATE);

        assertThat(secondPhone).isEqualTo(directory.resolve("2026-11-09 Android Tabs (2).xlsx"));
        assertThat(thirdPhone).isEqualTo(directory.resolve("2026-11-09 Android Tabs (3).xlsx"));
        assertThat(firstPhone).hasContent("first phone");
        assertThat(secondPhone).hasContent("second phone");
    }

    @Test
    void failedExportLeavesNoFileBehind(@TempDir Path directory) throws IOException {
        // Excel cells hold at most 32,767 characters, so the writer rejects this title.
        TreeSet<Tab> tabs = new TreeSet<>(Comparator.comparing(Tab::url));
        tabs.add(new Tab("x".repeat(40_000), "example.com"));

        assertThatThrownBy(() -> TabsExporter.writeExport(directory, EXPORT_DATE, tabs)).isInstanceOf(IllegalArgumentException.class);

        try (var remaining = Files.list(directory)) {
            assertThat(remaining).isEmpty();
        }
    }

    @Test
    void utilityClassCannotBeInstantiated() throws Exception {
        Throwable thrown = UtilityClasses.invokeConstructorAndCaptureThrowable(TabsExporter.class);

        assertThat(thrown)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("utility class");
    }
}
