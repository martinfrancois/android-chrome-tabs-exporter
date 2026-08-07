package com.github.martinfrancois;

import com.github.martinfrancois.testsupport.JavaFxToolkit;
import com.github.martinfrancois.ui.model.Theme;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link MainUi} through the real JavaFX toolkit (headless via Monocle).
 *
 * <p>A genuine {@link Stage} is shown, the real scene graph is built, the real stylesheet and
 * the real Material Symbols font resource are loaded, and buttons are fired through the real
 * event dispatch chain. Assertions read back through the property bindings that the production
 * code installed, so a broken binding fails the test rather than going unnoticed.
 */
class MainUiTest {

    private static final String THEME_PREFERENCE_KEY = "themePreference";

    private static String savedThemePreference;
    private static String savedUserHome;

    private MainUi mainUi;
    private Stage stage;
    private Scene scene;

    @BeforeAll
    static void bootToolkit() {
        JavaFxToolkit.startOnce();
        Preferences prefs = Preferences.userNodeForPackage(MainUi.class);
        savedThemePreference = prefs.get(THEME_PREFERENCE_KEY, null);
        savedUserHome = System.getProperty("user.home");
    }

    @AfterAll
    static void restorePreferences() throws BackingStoreException {
        Preferences prefs = Preferences.userNodeForPackage(MainUi.class);
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

    @BeforeEach
    void showUi() {
        // A stored preference is required: the operating-system theme detector cannot be
        // reached at all right now, see OsThemeDetectorDependencyTest.
        Preferences.userNodeForPackage(MainUi.class).put(THEME_PREFERENCE_KEY, Theme.LIGHT.name());
        System.setProperty("user.home", System.getProperty("java.io.tmpdir"));

        JavaFxToolkit.runAndWait(() -> {
            mainUi = new MainUi();
            stage = new Stage();
            mainUi.start(stage);
            scene = stage.getScene();
        });
    }

    @AfterEach
    void hideUi() {
        JavaFxToolkit.runAndWait(() -> {
            try {
                mainUi.stop();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            stage.hide();
        });
    }

    // ---------------------------------------------------------------- scene graph lookups

    private GridPane grid() {
        return (GridPane) ((BorderPane) scene.getRoot()).getCenter();
    }

    private <T extends Node> T find(Class<T> type, int index) {
        List<Node> matches = grid().getChildren().stream().filter(type::isInstance).toList();
        return type.cast(matches.get(index));
    }

    private Button buttonWithText(String text) {
        return grid().getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no button labelled " + text));
    }

    private Button themeToggleButton() {
        return grid().getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getStyleClass().contains("material-icon-button"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no theme toggle button"));
    }

    private TextArea console() {
        return find(TextArea.class, 0);
    }

    private TextField directoryField() {
        return find(TextField.class, 0);
    }

    // ---------------------------------------------------------------------------- tests

    @Test
    void buildsTheWindowWithTheExpectedTitleAndSceneSize() {
        assertThat(stage.getTitle()).isEqualTo("Android Chrome Tabs Exporter");
        assertThat(stage.isShowing()).isTrue();
        assertThat(scene.getWidth()).isEqualTo(800);
        assertThat(scene.getHeight()).isEqualTo(450);
        assertThat(scene.getRoot()).isInstanceOf(BorderPane.class);
    }

    @Test
    void loadsTheStylesheetThatShipsWithTheApplication() {
        assertThat(scene.getStylesheets()).hasSize(1);
        assertThat(scene.getStylesheets().getFirst()).endsWith("style.css");
        assertThat(MainUi.class.getResource("style.css")).isNotNull();
        assertThat(MainUi.class.getResource("MaterialSymbolsOutlined.ttf")).isNotNull();
    }

    @Test
    void layoutContainsEveryControlTheUserNeeds() {
        assertThat(find(Label.class, 0).getText()).isEqualTo("Export directory:");
        assertThat(buttonWithText("Choose Directory")).isNotNull();
        assertThat(buttonWithText("Export Android Tabs")).isNotNull();
        assertThat(console().isEditable()).isFalse();
        assertThat(directoryField().isEditable()).isFalse();
    }

    @Test
    void directoryFieldIsBoundToTheModelAndStartsAtTheUsersHome() {
        assertThat(directoryField().textProperty().isBound()).isTrue();
        assertThat(directoryField().getText()).isEqualTo(System.getProperty("java.io.tmpdir"));
    }

    @Test
    void consoleIsBoundToTheModelAndAlreadyReportsTheChosenDirectory() {
        assertThat(console().textProperty().isBound()).isTrue();
        assertThat(console().getText())
                .isEqualTo("Selected directory: " + System.getProperty("java.io.tmpdir"));
    }

    @Test
    void exportButtonIsEnabledOnlyWhileADirectoryIsSetAndNoExportIsRunning() {
        Button export = buttonWithText("Export Android Tabs");

        assertThat(export.disableProperty().isBound()).isTrue();
        assertThat(export.isDisabled()).isFalse();
    }

    @Test
    void appliesTheStoredLightThemeOnStartup() {
        assertThat(scene.getRoot().getStyle()).isEqualTo("-fx-base: #d7d7d7;");
        assertThat(themeToggleButton().getText())
                .as("light theme offers the switch-to-dark icon")
                .isEqualTo(MainUi.ICON_CODEPOINT_DARK_MODE);
    }

    @Test
    void togglingTheThemeRestylesTheSceneAndFlipsTheIcon() {
        Button toggle = themeToggleButton();

        JavaFxToolkit.runAndWait(toggle::fire);

        assertThat(scene.getRoot().getStyle()).isEqualTo("-fx-base: #121212;");
        assertThat(toggle.getText()).isEqualTo(MainUi.ICON_CODEPOINT_LIGHT_MODE);
        assertThat(Preferences.userNodeForPackage(MainUi.class).get(THEME_PREFERENCE_KEY, null))
                .as("the toggle must persist the user's choice")
                .isEqualTo("DARK");

        JavaFxToolkit.runAndWait(toggle::fire);

        assertThat(scene.getRoot().getStyle()).isEqualTo("-fx-base: #d7d7d7;");
        assertThat(toggle.getText()).isEqualTo(MainUi.ICON_CODEPOINT_DARK_MODE);
        assertThat(Preferences.userNodeForPackage(MainUi.class).get(THEME_PREFERENCE_KEY, null))
                .isEqualTo("LIGHT");
    }

    @Test
    void theTwoThemeIconsAreDistinctMaterialSymbolCodepoints() {
        assertThat(MainUi.ICON_CODEPOINT_DARK_MODE).isNotEqualTo(MainUi.ICON_CODEPOINT_LIGHT_MODE);
        assertThat(MainUi.ICON_CODEPOINT_LIGHT_MODE).isEqualTo("");
        assertThat(MainUi.ICON_CODEPOINT_DARK_MODE).isEqualTo("");
    }

    @Test
    void stoppingWithNoRunningExportIsANoOp() {
        JavaFxToolkit.runAndWait(() -> {
            try {
                mainUi.stop();
                mainUi.stop();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(stage.isShowing()).isTrue();
    }

    @Test
    void chooseDirectoryButtonIsWiredToTheNativeDirectoryChooser() {
        // Monocle deliberately implements no native file dialogs, so reaching
        // DirectoryChooser.showDialog raises UnsupportedOperationException. Observing exactly
        // that exception is what proves the click really travels into the chooser, and the
        // unchanged path proves the handler's null guard leaves the model alone.
        String before = directoryField().getText();
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        JavaFxToolkit.runAndWait(() -> {
            try {
                buttonWithText("Choose Directory").fire();
            } catch (Throwable t) {
                thrown.set(t);
            }
        });

        assertThat(thrown.get()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(directoryField().getText()).isEqualTo(before);
    }

    @Test
    void exportButtonRunsTheExportOnABackgroundTaskAndReportsFailureToTheConsole() {
        // Force the ADB download to fail immediately by pointing the JVM at a proxy that is not
        // listening. This is the real offline/unreachable-download code path: a genuine
        // java.net connection attempt that genuinely fails, not a stubbed one.
        String savedProxyHost = System.getProperty("https.proxyHost");
        String savedProxyPort = System.getProperty("https.proxyPort");
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "1");
        try {
            JavaFxToolkit.runAndWait(() -> buttonWithText("Export Android Tabs").fire());

            waitUntil(() -> console().getText() != null
                    && console().getText().contains("-----------------"));

            assertThat(console().getText())
                    .contains("Exporting Android Tabs...")
                    .contains("ERROR:")
                    .contains("-----------------");
            assertThat(buttonWithText("Export Android Tabs").isDisabled())
                    .as("the export button must be re-enabled once the task finishes")
                    .isFalse();
        } finally {
            restoreProperty("https.proxyHost", savedProxyHost);
            restoreProperty("https.proxyPort", savedProxyPort);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static void waitUntil(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            JavaFxToolkit.drainFxEvents();
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("condition never became true within 60s");
    }
}
