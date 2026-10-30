package com.github.martinfrancois.ui.model;

import com.github.martinfrancois.MainUi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class MissingGsettingsTest {
    @TempDir
    Path directory;

    private final Preferences prefs = Preferences.userNodeForPackage(MainUi.class);
    private String savedTheme;

    @BeforeEach
    void removeSavedTheme() {
        savedTheme = prefs.get("themePreference", null);
        prefs.remove("themePreference");
    }

    @AfterEach
    void restoreSavedTheme() throws BackingStoreException {
        if (savedTheme == null) {
            prefs.remove("themePreference");
        } else {
            prefs.put("themePreference", savedTheme);
        }
        prefs.flush();
    }

    @Test
    void missingCommandUsesLightThemeAndStillAllowsManualSelection() {
        PresentationModel model = new PresentationModel("Linux", directory.toString());

        assertThat(model.getTheme()).isEqualTo(Theme.LIGHT);
        assertThat(prefs.get("themePreference", null)).isNull();
        model.updatePreferredTheme(Theme.DARK);
        assertThat(model.getTheme()).isEqualTo(Theme.DARK);
        assertThat(prefs.get("themePreference", null)).isEqualTo("DARK");
    }

    @Test
    void missingCommandPreservesSavedTheme() {
        prefs.put("themePreference", "DARK");

        assertThat(new PresentationModel("Linux", directory.toString()).getTheme()).isEqualTo(Theme.DARK);
    }

    @Test
    void absentPathUsesLightTheme() {
        assertThat(new PresentationModel("Linux", null).getTheme()).isEqualTo(Theme.LIGHT);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void lookupRequiresAnExecutableRegularFileAndSearchesEveryDirectory() throws Exception {
        assertThat(PresentationModel.hasGsettings(directory.toString())).isFalse();
        Path command = Files.createFile(directory.resolve("gsettings"));
        Files.setPosixFilePermissions(command, PosixFilePermissions.fromString("rw-------"));
        assertThat(PresentationModel.hasGsettings(directory.toString())).isFalse();

        Files.setPosixFilePermissions(command, PosixFilePermissions.fromString("rwx------"));
        String path = directory.resolve("missing") + File.pathSeparator + directory;
        assertThat(PresentationModel.hasGsettings(path)).isTrue();

        Files.delete(command);
        Files.createDirectory(command);
        assertThat(PresentationModel.hasGsettings(directory.toString())).isFalse();
    }
}
