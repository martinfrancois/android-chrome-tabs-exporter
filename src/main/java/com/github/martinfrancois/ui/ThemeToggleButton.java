package com.github.martinfrancois.ui;

import com.github.martinfrancois.ui.model.PresentationModel;
import com.github.martinfrancois.ui.model.Theme;
import javafx.scene.control.Button;

/** Switches between the light and dark theme. Its icon shows the theme a click switches to. */
public class ThemeToggleButton extends Button {
    // from: https://fonts.google.com/icons?selected=Material+Symbols+Outlined:light_mode:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=light+mode&icon.platform=web
    public static final String ICON_CODEPOINT_LIGHT_MODE = "\ue518";
    // from: https://fonts.google.com/icons?selected=Material+Symbols+Outlined:dark_mode:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=dark+mode&icon.platform=web
    public static final String ICON_CODEPOINT_DARK_MODE = "\uf036";

    public ThemeToggleButton(PresentationModel model) {
        setStyle("-fx-background-color: transparent;");
        getStyleClass().add("material-icon-button");
        setOnAction(e -> model.updatePreferredTheme(opposite(model.getTheme())));
        model.themeProperty().addListener((observable, oldValue, newValue) -> showIconFor(newValue));
        showIconFor(model.getTheme());
    }

    private static Theme opposite(Theme theme) {
        return theme == Theme.DARK ? Theme.LIGHT : Theme.DARK;
    }

    private void showIconFor(Theme theme) {
        setText(theme == Theme.DARK ? ICON_CODEPOINT_LIGHT_MODE : ICON_CODEPOINT_DARK_MODE);
    }
}
