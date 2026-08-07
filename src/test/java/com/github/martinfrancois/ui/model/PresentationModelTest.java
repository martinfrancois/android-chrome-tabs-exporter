package com.github.martinfrancois.ui.model;

import com.github.martinfrancois.MainUi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link PresentationModel} against the real JavaFX property/binding engine and the
 * real {@code java.util.prefs} backing store.
 *
 * <p>Nothing is stubbed. The {@code pathSet} and {@code executable} bindings are evaluated by
 * JavaFX itself, and theme preferences really round-trip through the user preferences node
 * that the shipping application uses.
 *
 * <p>Every test seeds a stored theme preference first so that the assertions here stay
 * independent of whatever theme the host operating system happens to report. The
 * no-preference path, which consults the operating system through jSystemThemeDetector, is
 * covered by {@link OsThemeDetectorLaunchTest}.
 */
class PresentationModelTest {

    private static final String THEME_PREFERENCE_KEY = "themePreference";
    private static final String PATH_PLACEHOLDER = "No directory selected";

    private Preferences prefs;
    private String savedThemePreference;
    private String savedUserHome;

    @BeforeEach
    void setUp() {
        prefs = Preferences.userNodeForPackage(MainUi.class);
        savedThemePreference = prefs.get(THEME_PREFERENCE_KEY, null);
        savedUserHome = System.getProperty("user.home");
        prefs.put(THEME_PREFERENCE_KEY, Theme.LIGHT.name());
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        if (savedThemePreference == null) {
            prefs.remove(THEME_PREFERENCE_KEY);
        } else {
            prefs.put(THEME_PREFERENCE_KEY, savedThemePreference);
        }
        prefs.flush();
        if (savedUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", savedUserHome);
        }
    }

    @Test
    void startsAtTheUsersHomeDirectoryAndAnnouncesItOnTheConsole() {
        System.setProperty("user.home", "/home/tester");

        PresentationModel model = new PresentationModel();

        assertThat(model.getPath()).isEqualTo("/home/tester");
        assertThat(model.pathProperty().get()).isEqualTo("/home/tester");
        assertThat(model.consoleProperty().get()).isEqualTo("Selected directory: /home/tester");
    }

    @Test
    void pathSetBindingIsTrueOnceARealDirectoryIsSelected() {
        PresentationModel model = new PresentationModel();

        assertThat(model.pathSetProperty().get()).isTrue();
        assertThat(model.pathSetProperty().isBound()).isTrue();
    }

    @Test
    void pathSetBindingStaysFalseWhileThePathIsStillThePlaceholder() {
        // An empty user.home means the constructor's updatePath call is a no-op, which is the
        // only way the shipping application can be left sitting on the placeholder text.
        System.setProperty("user.home", "");

        PresentationModel model = new PresentationModel();

        assertThat(model.getPath()).isEqualTo(PATH_PLACEHOLDER);
        assertThat(model.pathSetProperty().get()).isFalse();
        assertThat(model.executableProperty().get())
                .as("export must stay disabled until a directory is chosen")
                .isFalse();
        assertThat(model.consoleProperty().get()).isNull();
    }

    @Test
    void updatePathIgnoresNullAndEmptyDirectories() {
        System.setProperty("user.home", "/home/tester");
        PresentationModel model = new PresentationModel();

        model.updatePath(null);
        model.updatePath("");

        assertThat(model.getPath()).isEqualTo("/home/tester");
        assertThat(model.consoleProperty().get()).isEqualTo("Selected directory: /home/tester");
    }

    @Test
    void updatePathReplacesThePathAndAppendsToTheConsole() {
        System.setProperty("user.home", "/home/tester");
        PresentationModel model = new PresentationModel();

        model.updatePath("/tmp/exports");

        assertThat(model.getPath()).isEqualTo("/tmp/exports");
        assertThat(model.consoleProperty().get()).isEqualTo(
                "Selected directory: /home/tester\nSelected directory: /tmp/exports");
    }

    @Test
    void executableBindingCombinesPathSetAndInProgress() {
        PresentationModel model = new PresentationModel();
        assertThat(model.executableProperty().get()).isTrue();

        model.startProgress();
        assertThat(model.executableProperty().get())
                .as("export button must be disabled while an export runs")
                .isFalse();

        model.stopProgress();
        assertThat(model.executableProperty().get()).isTrue();
    }

    @Test
    void progressLifecycleWritesTheExpectedConsoleTranscript() {
        System.setProperty("user.home", "/home/tester");
        PresentationModel model = new PresentationModel();

        model.startProgress();
        model.appendConsoleText("Found 3 tabs.");
        model.stopProgress();

        assertThat(model.consoleProperty().get().lines()).containsExactly(
                "Selected directory: /home/tester",
                "Exporting Android Tabs...",
                "Found 3 tabs.",
                "-----------------");
    }

    @Test
    void firstConsoleLineIsNotPrefixedWithANewline() {
        System.setProperty("user.home", "");
        PresentationModel model = new PresentationModel();

        model.appendConsoleText("first");

        assertThat(model.consoleProperty().get()).isEqualTo("first");
    }

    @Test
    void appendingToAnEmptyConsoleDoesNotIntroduceABlankLine() {
        System.setProperty("user.home", "");
        PresentationModel model = new PresentationModel();
        model.appendConsoleText("");

        model.appendConsoleText("second");

        assertThat(model.consoleProperty().get()).isEqualTo("second");
    }

    @Test
    void errorsArePrefixedSoTheyStandOutInTheConsole() {
        System.setProperty("user.home", "");
        PresentationModel model = new PresentationModel();

        model.appendConsoleError("No android device found.");
        model.appendConsoleText("plain line");

        assertThat(model.consoleProperty().get().lines()).containsExactly(
                "ERROR: No android device found.",
                "plain line");
    }

    @Test
    void consolePropertyNotifiesListenersOnEveryAppend() {
        System.setProperty("user.home", "");
        PresentationModel model = new PresentationModel();
        List<String> observed = new ArrayList<>();
        model.consoleProperty().addListener((observable, oldValue, newValue) -> observed.add(newValue));

        model.appendConsoleText("one");
        model.appendConsoleText("two");

        assertThat(observed).containsExactly("one", "one\ntwo");
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    void aStoredThemePreferenceIsRestoredWithoutConsultingTheOperatingSystem(Theme theme) {
        prefs.put(THEME_PREFERENCE_KEY, theme.name());

        PresentationModel model = new PresentationModel();

        assertThat(model.getTheme()).isEqualTo(theme);
        assertThat(model.themeProperty().get()).isEqualTo(theme);
    }

    @Test
    void updatePreferredThemePersistsTheChoiceForTheNextLaunch() throws BackingStoreException {
        PresentationModel model = new PresentationModel();
        assertThat(model.getTheme()).isEqualTo(Theme.LIGHT);

        model.updatePreferredTheme(Theme.DARK);
        prefs.flush();

        assertThat(model.getTheme()).isEqualTo(Theme.DARK);
        assertThat(prefs.get(THEME_PREFERENCE_KEY, null)).isEqualTo("DARK");
        assertThat(new PresentationModel().getTheme())
                .as("a freshly launched application must honour the stored preference")
                .isEqualTo(Theme.DARK);
    }

    @Test
    void themePropertyNotifiesListenersSoTheUiCanRestyleItself() {
        PresentationModel model = new PresentationModel();
        List<Theme> observed = new ArrayList<>();
        model.themeProperty().addListener((observable, oldValue, newValue) -> observed.add(newValue));

        model.updatePreferredTheme(Theme.DARK);
        model.updatePreferredTheme(Theme.LIGHT);

        assertThat(observed).containsExactly(Theme.DARK, Theme.LIGHT);
    }
}
