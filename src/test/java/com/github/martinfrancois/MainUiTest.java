package com.github.martinfrancois;

import com.github.martinfrancois.testsupport.JavaFxToolkit;
import com.github.martinfrancois.ui.ConsoleView;
import com.github.martinfrancois.ui.ExportButton;
import com.github.martinfrancois.ui.FxThreadConsole;
import com.github.martinfrancois.ui.model.ConsoleLine;
import com.github.martinfrancois.ui.model.Theme;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.HPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

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
        // A stored preference keeps these assertions independent of the host operating system's
        // theme. The no-preference path is covered by OsThemeDetectorLaunchTest.
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

    private ConsoleView console() {
        return find(ConsoleView.class, 0);
    }

    private List<String> consoleTexts() {
        return console().getItems().stream().map(ConsoleLine::text).toList();
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
        assertThat(buttonWithText(ExportButton.IDLE_TEXT)).isNotNull();
        assertThat(buttonWithText("Clear Console")).isNotNull();
        assertThat(console().getContextMenu().getItems()).extracting(MenuItem::getText).containsExactly("Copy");
        assertThat(directoryField().isEditable()).isFalse();
    }

    @Test
    void directoryFieldIsBoundToTheModelAndStartsAtTheUsersHome() {
        assertThat(directoryField().textProperty().isBound()).isTrue();
        assertThat(directoryField().getText()).isEqualTo(System.getProperty("java.io.tmpdir"));
    }

    @Test
    void consoleShowsTheModelsLinesAndAlreadyReportsTheChosenDirectory() {
        assertThat(consoleTexts())
                .containsExactly("Selected directory: " + System.getProperty("java.io.tmpdir"));
    }

    @Test
    void errorLinesAreRedWithEnoughContrastInBothThemes() {
        JavaFxToolkit.runAndWait(() -> showLines(
                new ConsoleLine("Exporting Android Tabs...", false),
                new ConsoleLine("ERROR: No android device found.", true)));

        for (int toggle = 0; toggle < 2; toggle++) {
            JavaFxToolkit.runAndWait(() -> {
                layOut();
                ListCell<?> error = cellShowing("ERROR: No android device found.");
                ListCell<?> plain = cellShowing("Exporting Android Tabs...");
                Color text = (Color) error.getTextFill();
                Color background = backgroundOf(error);

                assertThat(error.getPseudoClassStates()).contains(ERROR);
                assertThat(plain.getPseudoClassStates()).doesNotContain(ERROR);
                assertThat(text.getRed()).as("error text is red").isGreaterThan(text.getGreen() + 0.25);
                assertThat(contrast(text, background))
                        .as("error text contrast (WCAG AAA for small text) in " + scene.getRoot().getStyle())
                        .isGreaterThanOrEqualTo(7.0);
                assertThat(contrast((Color) plain.getTextFill(), background))
                        .as("plain text contrast in " + scene.getRoot().getStyle())
                        .isGreaterThanOrEqualTo(7.0);
                assertThat(backgroundOf(plain)).as("rows aren't striped").isEqualTo(background);
            });
            JavaFxToolkit.runAndWait(themeToggleButton()::fire);
        }
    }

    @Test
    void longLinesWrapInsteadOfRunningPastTheConsolesEdge() {
        JavaFxToolkit.runAndWait(() -> {
            showLines(new ConsoleLine("short", false), new ConsoleLine("long ".repeat(100), true));
            layOut();
            ListCell<?> shortCell = cellShowing("short");
            ListCell<?> longCell = cellShowing("long ".repeat(100));

            assertThat(longCell.getWidth()).isLessThanOrEqualTo(console().getWidth());
            assertThat(longCell.getHeight()).isGreaterThan(2 * shortCell.getHeight());
        });
    }

    @Test
    void selectedLinesStandOutAndKeepEnoughContrastInBothThemesWithAndWithoutFocus() {
        for (int toggle = 0; toggle < 2; toggle++) {
            JavaFxToolkit.runAndWait(() -> {
                showLines(new ConsoleLine("plain", false), new ConsoleLine("ERROR: failed", true),
                        new ConsoleLine("not selected", false));
                console().getSelectionModel().selectRange(0, 2);
                for (boolean focused : new boolean[]{true, false}) {
                    if (focused) {
                        console().requestFocus();
                    } else {
                        buttonWithText("Choose Directory").requestFocus();
                    }
                    layOut();
                    assertThat(console().isFocused()).isEqualTo(focused);
                    for (String line : List.of("plain", "ERROR: failed")) {
                        ListCell<?> cell = cellShowing(line);
                        String state = line + " selected, focused " + focused + ", " + scene.getRoot().getStyle();
                        assertThat(contrast((Color) cell.getTextFill(), backgroundOf(cell)))
                                .as("text contrast of " + state)
                                .isGreaterThanOrEqualTo(7.0);
                        assertThat(contrast(backgroundOf(cell), backgroundOf(cellShowing("not selected"))))
                                .as("contrast against an unselected row of " + state)
                                .isGreaterThanOrEqualTo(3.0);
                    }
                }
            });
            JavaFxToolkit.runAndWait(themeToggleButton()::fire);
        }
    }

    @Test
    void selectingAnOddRowHighlightsIt() {
        JavaFxToolkit.runAndWait(() -> {
            showLines(new ConsoleLine("zero", false), new ConsoleLine("one", false), new ConsoleLine("two", false));
            console().getSelectionModel().select(1);
            layOut();

            assertThat(backgroundOf(cellShowing("one"))).isNotEqualTo(backgroundOf(cellShowing("two")));
        });
    }

    @Test
    void copyPutsTheSelectedLinesOnTheClipboardInConsoleOrder() {
        JavaFxToolkit.runAndWait(() -> {
            showLines(new ConsoleLine("first", false), new ConsoleLine("ERROR: second", true));
            console().getSelectionModel().select(1);
            console().getSelectionModel().select(0);
            console().getContextMenu().getItems().getFirst().fire();

            assertThat(Clipboard.getSystemClipboard().getString()).isEqualTo("first\nERROR: second");
        });
    }

    @Test
    void theCopyShortcutCopiesTheSelectedLines() {
        JavaFxToolkit.runAndWait(() -> {
            showLines(new ConsoleLine("first", false), new ConsoleLine("second", false));
            console().getSelectionModel().select(1);
            // Ctrl is the shortcut key off macOS, and the tests run on Linux.
            KeyEvent copy = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, true, false, false);

            Event.fireEvent(console(), copy);

            assertThat(Clipboard.getSystemClipboard().getString()).isEqualTo("second");
        });
    }

    @Test
    void copyingWithNothingSelectedLeavesTheClipboardAlone() {
        JavaFxToolkit.runAndWait(() -> {
            ClipboardContent earlier = new ClipboardContent();
            earlier.putString("earlier");
            Clipboard.getSystemClipboard().setContent(earlier);
            showLines(new ConsoleLine("first", false));
            console().getSelectionModel().clearSelection();

            console().getContextMenu().getItems().getFirst().fire();

            assertThat(Clipboard.getSystemClipboard().getString()).isEqualTo("earlier");
        });
    }

    @Test
    void clearConsoleEmptiesTheConsoleAndIsDisabledWhileItIsEmpty() {
        Button clear = buttonWithText("Clear Console");
        assertThat(GridPane.getRowIndex(clear)).as("right below the console").isEqualTo(GridPane.getRowIndex(console()) + 1);
        assertThat(GridPane.getHalignment(clear)).isEqualTo(HPos.RIGHT);
        assertThat(clear.isDisabled()).as("the console starts with the chosen directory").isFalse();

        JavaFxToolkit.runAndWait(clear::fire);

        assertThat(console().getItems()).isEmpty();
        assertThat(clear.isDisabled()).isTrue();
    }

    @Test
    void exportButtonIsEnabledOnlyWhileADirectoryIsSetAndNoExportIsRunning() {
        Button export = buttonWithText(ExportButton.IDLE_TEXT);

        assertThat(export).isInstanceOf(ExportButton.class);
        assertThat(export.disableProperty().isBound()).isTrue();
        assertThat(export.isDisabled()).isFalse();
    }

    @Test
    void whileTheModelExportsTheButtonSpinsWithEnoughContrastInBothThemes() {
        JavaFxToolkit.runAndWait(() -> mainUi.model().startProgress());
        Button export = buttonWithText(ExportButton.BUSY_TEXT);

        for (int toggle = 0; toggle < 2; toggle++) {
            JavaFxToolkit.runAndWait(() -> assertSpinnerContrast(export));
            JavaFxToolkit.runAndWait(themeToggleButton()::fire);
        }
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
            JavaFxToolkit.runAndWait(() -> buttonWithText(ExportButton.IDLE_TEXT).fire());

            waitUntilOnFxThread(this::exportHasFinished);
            JavaFxToolkit.runAndWait(this::assertFailedExportReported);
        } finally {
            restoreProperty("https.proxyHost", savedProxyHost);
            restoreProperty("https.proxyPort", savedProxyPort);
        }
    }

    @Test
    void exportConsoleMessagesFromABackgroundThreadReachTheConsoleInOrder() throws InterruptedException {
        JavaFxToolkit.runAndWait(() -> mainUi.model().clearConsole());
        FxThreadConsole exportConsole = new FxThreadConsole(mainUi.model());

        Thread export = new Thread(() -> reportProgressThenError(exportConsole));
        export.start();
        export.join();
        JavaFxToolkit.drainFxEvents();

        assertThat(console().getItems()).containsExactly(
                new ConsoleLine("Found 3 tabs.", false),
                new ConsoleLine("ERROR: Failed to delete ADB executable.", true));
    }

    private static void reportProgressThenError(FxThreadConsole exportConsole) {
        exportConsole.progress("Found 3 tabs.");
        exportConsole.error("Failed to delete ADB executable.");
    }

    private boolean exportHasFinished() {
        return consoleTexts().contains("-----------------")
                && !buttonWithText(ExportButton.IDLE_TEXT).isDisabled();
    }

    private void assertFailedExportReported() {
        assertThat(consoleTexts()).contains("Exporting Android Tabs...", "-----------------");
        assertThat(console().getItems()).anyMatch(ConsoleLine::error);
        assertThat(buttonWithText(ExportButton.IDLE_TEXT).isDisabled())
                .as("the export button must be re-enabled once the task finishes")
                .isFalse();
    }

    private void assertSpinnerContrast(Button export) {
        layOut();
        Arc arc = (Arc) ((Group) export.getGraphic()).getChildren().getLast();
        assertThat(arc.getStroke()).as("the spinner has a color").isInstanceOf(Color.class);
        for (Color body : colorsOf(export.getBackground().getFills().getLast().getFill())) {
            assertThat(contrast((Color) arc.getStroke(), body))
                    .as("spinner contrast against the button in " + scene.getRoot().getStyle())
                    .isGreaterThanOrEqualTo(3.0);
        }
    }

    private void layOut() {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }

    /** Replaces the console's lines, so a test needs no failing export to show an error. */
    private void showLines(ConsoleLine... lines) {
        console().setItems(FXCollections.observableArrayList(lines));
    }

    private ListCell<?> cellShowing(String text) {
        return console().lookupAll(".list-cell").stream()
                .map(ListCell.class::cast)
                .filter(cell -> text.equals(cell.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no console row shows " + text));
    }

    private static Color backgroundOf(ListCell<?> cell) {
        List<BackgroundFill> fills = cell.getBackground().getFills();
        return (Color) fills.getLast().getFill();
    }

    /** The colors a fill paints with: every stop of a gradient, so a check covers its whole range. */
    private static List<Color> colorsOf(Paint fill) {
        if (fill instanceof LinearGradient gradient) {
            return gradient.getStops().stream().map(Stop::getColor).toList();
        }
        return List.of((Color) fill);
    }

    /** WCAG 2.2 contrast ratio of two opaque colors. */
    private static double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static void waitUntilOnFxThread(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        AtomicBoolean satisfied = new AtomicBoolean();
        while (Instant.now().isBefore(deadline)) {
            // Draining queued events alone still lets the exporter append during a later read.
            JavaFxToolkit.runAndWait(() -> satisfied.set(condition.getAsBoolean()));
            if (satisfied.get()) {
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
