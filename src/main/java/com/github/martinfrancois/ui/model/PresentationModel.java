package com.github.martinfrancois.ui.model;

import com.github.martinfrancois.MainUi;
import com.jthemedetecor.OsThemeDetector;
import javafx.application.Platform;
import javafx.beans.property.*;

import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class PresentationModel {
    private static final String PATH_PLACEHOLDER = "No directory selected";
    private static final String THEME_PREFERENCE_KEY = "themePreference";

    private final StringProperty path = new SimpleStringProperty(PATH_PLACEHOLDER);
    private final StringProperty console = new SimpleStringProperty();
    private final BooleanProperty pathSet = new SimpleBooleanProperty();
    private final BooleanProperty inProgress = new SimpleBooleanProperty();
    private final BooleanProperty executable = new SimpleBooleanProperty();
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>();

    private final Preferences prefs = Preferences.userNodeForPackage(MainUi.class);


    public PresentationModel() {
        // Set initial directory to user's home directory
        updatePath(System.getProperty("user.home"));

        // Bind pathSet to be true when the path contains a directory path
        pathSetProperty().bind(pathProperty().isNotEqualTo(PATH_PLACEHOLDER));

        // Bind executable to be true when pathSet is true and inProgress is false
        executableProperty().bind(pathSetProperty().and(inProgressProperty().not()));

        // Dark mode handling
        String savedThemeName = prefs.get(THEME_PREFERENCE_KEY, null);
        if (savedThemeName == null) {
            final OsThemeDetector detector = OsThemeDetector.getDetector();
            Consumer<Boolean> darkThemeListener = isDark -> {
                String savedTheme = prefs.get(THEME_PREFERENCE_KEY, null);
                if(savedTheme == null) { // do not override user's preference
                    Platform.runLater(() -> setTheme(isDark ? Theme.DARK : Theme.LIGHT));
                }
            };
            darkThemeListener.accept(detector.isDark()); // Apply initial theme
            detector.registerListener(darkThemeListener);
        } else {
            setTheme(Theme.valueOf(savedThemeName));
        }
    }

    public void updatePath(String path) {
        if (path != null && !path.isEmpty()) {
            setPath(path);
            appendConsoleText("Selected directory: " + path);
        }
    }

    public void updatePreferredTheme(Theme theme) {
        prefs.put(THEME_PREFERENCE_KEY, theme.name());
        setTheme(theme);
    }

    public void appendConsoleText(String appendText) {
        String consoleText = getConsole();
        if(consoleText == null || consoleText.isEmpty()) {
            setConsole(appendText);
        } else {
            setConsole(consoleText + "\n" + appendText);
        }
    }

    public void appendConsoleError(String appendError) {
        appendConsoleText("ERROR: " + appendError);
    }

    public void startProgress() {
        setInProgress(true);
        appendConsoleText("Exporting Android Tabs...");
    }

    public void stopProgress() {
        setInProgress(false);
        appendConsoleText("-----------------");
    }

    public String getPath() {
        return path.get();
    }

    public StringProperty pathProperty() {
        return path;
    }

    private void setPath(String path) {
        this.path.set(path);
    }

    private String getConsole() {
        return console.get();
    }

    public StringProperty consoleProperty() {
        return console;
    }

    private void setConsole(String console) {
        this.console.set(console);
    }

    private boolean isPathSet() {
        return pathSet.get();
    }

    public BooleanProperty pathSetProperty() {
        return pathSet;
    }

    private void setPathSet(boolean pathSet) {
        this.pathSet.set(pathSet);
    }

    private boolean isInProgress() {
        return inProgress.get();
    }

    private BooleanProperty inProgressProperty() {
        return inProgress;
    }

    private void setInProgress(boolean inProgress) {
        this.inProgress.set(inProgress);
    }

    private boolean isExecutable() {
        return executable.get();
    }

    public BooleanProperty executableProperty() {
        return executable;
    }

    private void setExecutable(boolean executable) {
        this.executable.set(executable);
    }

    public Theme getTheme() {
        return theme.get();
    }

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    private void setTheme(Theme theme) {
        this.theme.set(theme);
    }
}
