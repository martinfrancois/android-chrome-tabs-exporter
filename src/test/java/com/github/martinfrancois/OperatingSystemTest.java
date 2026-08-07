package com.github.martinfrancois;

import com.github.martinfrancois.testsupport.UtilityClasses;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OperatingSystemTest {

    @Test
    void exactlyOneOperatingSystemFamilyIsReported() {
        int matches = 0;
        if (OperatingSystem.isWindows()) {
            matches++;
        }
        if (OperatingSystem.isMac()) {
            matches++;
        }
        if (OperatingSystem.isLinux()) {
            matches++;
        }

        assertThat(matches)
                .as("os.name=%s must classify into exactly one family", System.getProperty("os.name"))
                .isEqualTo(1);
    }

    @Test
    void classificationAgreesWithTheJvmReportedOsName() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        assertThat(OperatingSystem.isWindows()).isEqualTo(osName.contains("win"));
        assertThat(OperatingSystem.isMac()).isEqualTo(osName.contains("mac"));
        assertThat(OperatingSystem.isLinux()).isEqualTo(!osName.contains("win") && !osName.contains("mac"));
    }

    @Test
    void linuxIsTheFallbackFamily() {
        // Anything that is neither Windows nor macOS is treated as Linux, so the three
        // predicates must always partition the space rather than leave a gap.
        assertThat(OperatingSystem.isLinux())
                .isEqualTo(!OperatingSystem.isWindows() && !OperatingSystem.isMac());
    }

    @Test
    void utilityClassCannotBeInstantiated() throws Exception {
        Throwable thrown = UtilityClasses.invokeConstructorAndCaptureThrowable(OperatingSystem.class);

        assertThat(thrown)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("utility class");
    }
}
