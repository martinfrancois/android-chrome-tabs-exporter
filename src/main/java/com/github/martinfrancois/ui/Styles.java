package com.github.martinfrancois.ui;

import com.github.martinfrancois.ui.model.Theme;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.text.Font;

/** The application's stylesheet, icon font, and theme colors. */
public final class Styles {
    private static final String DARK_STYLE = "-fx-base: #121212;";
    private static final String LIGHT_STYLE = "-fx-base: #d7d7d7;";

    private Styles() {
        throw new UnsupportedOperationException("Styles is a utility class and should not be instantiated.");
    }

    /** Loads the application's font and stylesheet into scene. */
    public static void apply(Scene scene) {
        Font.loadFont(Styles.class.getResourceAsStream("MaterialSymbolsOutlined.ttf"), 24); // workaround for error: "Could not load @font-face font"
        scene.getStylesheets().add(Styles.class.getResource("style.css").toExternalForm());
    }

    /** Colors scene for the current theme and again whenever theme changes. */
    public static void followTheme(Scene scene, ObservableValue<Theme> theme) {
        theme.addListener((observable, oldValue, newValue) -> applyTheme(scene, newValue));
        applyTheme(scene, theme.getValue());
    }

    private static void applyTheme(Scene scene, Theme theme) {
        scene.getRoot().setStyle(theme == Theme.DARK ? DARK_STYLE : LIGHT_STYLE);
    }
}
